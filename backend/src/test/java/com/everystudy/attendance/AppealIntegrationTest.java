package com.everystudy.attendance;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.auth.FakeSocialProvider;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 이의 신청과 내 출석 기록 (PLAN T-034, PRD 4.9). 이벤트는 SQL 로 직접 넣고 발생 시각을 현재 기준으로 옮겨 기한(48시간)의
 * 안팎을 만든다.
 */
@Import(FakeSocialProvider.class)
class AppealIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private long hostId;
    private long memberId;
    private long otherId;
    private Cookie host;
    private Cookie member;
    private Cookie other;
    private Cookie csrf;
    private long studyId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        host = login(2001L);
        member = login(2002L);
        other = login(2003L);
        hostId = userIdOf(2001L);
        memberId = userIdOf(2002L);
        otherId = userIdOf(2003L);
        onboard(hostId, "주최자");
        onboard(memberId, "참여자");
        onboard(otherId, "타인");
        csrf = mvc.get().uri("/api/v1/me").cookie(member).exchange().getResponse().getCookie("XSRF-TOKEN");
        studyId = study();
    }

    @Test
    @DisplayName("노쇼 후보에 이의를 신청하면 201 과 OPEN 이의가 생기고, 기한은 판정 시각 + 48시간이다")
    void fileAppeal() {
        Instant occurredAt = now.minus(Duration.ofHours(1));
        long eventId = event(studyId, memberId, "NO_SHOW_CANDIDATE", occurredAt);

        MvcTestResult result = file(eventId, member, "{\"reason\":\"  지하철 사고로 늦었어요  \"}");

        assertThat(result).hasStatus(201).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.status").isEqualTo("OPEN");
            assertThat(json).extractingPath("$.reason").isEqualTo("지하철 사고로 늦었어요");
            assertThat(json).extractingPath("$.verdictType").isEqualTo("NO_SHOW_CANDIDATE");
            assertThat(json).extractingPath("$.studyTitle").isEqualTo("모각코");
        });
        assertThat(jdbc.queryForObject("SELECT TIMESTAMPDIFF(HOUR, ?, due_at) FROM appeal", Integer.class,
                utc(occurredAt))).isEqualTo(48);
    }

    @Test
    @DisplayName("지각 판정에도 이의를 신청할 수 있고, 출석(ATTENDED)에는 409 APPEAL_NOT_ALLOWED")
    void lateYesAttendedNo() {
        long late = event(studyId, memberId, "LATE", now.minus(Duration.ofHours(1)));
        long otherStudy = study();
        long attended = event(otherStudy, memberId, "ATTENDED", now.minus(Duration.ofHours(1)));

        assertThat(file(late, member, "{\"reason\":\"x\"}")).hasStatus(201);
        assertCode(file(attended, member, "{\"reason\":\"x\"}"), 409, "APPEAL_NOT_ALLOWED");
    }

    @Test
    @DisplayName("기한(48시간) 정각까지는 되고 1초만 지나도 409 APPEAL_CLOSED 다")
    void deadline() {
        long onTime = event(studyId, memberId, "NO_SHOW_CANDIDATE", now.minus(Duration.ofHours(48)).plusSeconds(30));
        long lateStudy = study();
        long over = event(lateStudy, memberId, "NO_SHOW_CANDIDATE", now.minus(Duration.ofHours(48)).minusSeconds(1));

        assertThat(file(onTime, member, "{\"reason\":\"x\"}")).hasStatus(201);
        assertCode(file(over, member, "{\"reason\":\"x\"}"), 409, "APPEAL_CLOSED");
        assertThat(count("appeal")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 판정에 두 번째 이의는 409 ALREADY_APPEALED 이고 1건만 남는다")
    void secondAppealRejected() {
        long eventId = event(studyId, memberId, "NO_SHOW_CANDIDATE", now.minus(Duration.ofHours(1)));

        assertThat(file(eventId, member, "{\"reason\":\"x\"}")).hasStatus(201);
        assertCode(file(eventId, member, "{\"reason\":\"y\"}"), 409, "ALREADY_APPEALED");
        assertThat(count("appeal")).isEqualTo(1);
    }

    @Test
    @DisplayName("타인의 판정은 403, 없는 판정은 404, 사유가 비면 400")
    void permissionsAndValidation() {
        long eventId = event(studyId, memberId, "NO_SHOW_CANDIDATE", now.minus(Duration.ofHours(1)));

        assertCode(file(eventId, other, "{\"reason\":\"x\"}"), 403, "FORBIDDEN");
        assertCode(file(999_999L, member, "{\"reason\":\"x\"}"), 404, "NOT_FOUND");
        assertCode(file(eventId, member, "{\"reason\":\"   \"}"), 400, "VALIDATION_FAILED");
        assertCode(file(eventId, member, "{\"reason\":\"" + "가".repeat(501) + "\"}"), 400, "VALIDATION_FAILED");
        assertThat(count("appeal")).isZero();
    }

    @Test
    @DisplayName("뒤따르는 판정(이의 인정 정정 등)이 생겨 현재 판정이 아니면 409 APPEAL_NOT_ALLOWED")
    void supersededVerdict() {
        long eventId = event(studyId, memberId, "NO_SHOW_CANDIDATE", now.minus(Duration.ofHours(2)));
        event(studyId, memberId, "NO_SHOW_CLEARED", now.minus(Duration.ofHours(1)));

        assertCode(file(eventId, member, "{\"reason\":\"x\"}"), 409, "APPEAL_NOT_ALLOWED");
    }

    @Test
    @DisplayName("주최자만 1차 의견을 남기고, 다시 보내면 덮어쓰며, 비주최자는 403, 결정된 이의는 409 APPEAL_CLOSED")
    void hostOpinion() {
        long eventId = event(studyId, memberId, "NO_SHOW_CANDIDATE", now.minus(Duration.ofHours(1)));
        file(eventId, member, "{\"reason\":\"x\"}");
        long appealId = jdbc.queryForObject("SELECT id FROM appeal", Long.class);

        assertCode(opinion(appealId, member, "{\"opinion\":\"본인\"}"), 403, "FORBIDDEN");
        assertCode(opinion(appealId, other, "{\"opinion\":\"타인\"}"), 403, "FORBIDDEN");
        assertCode(opinion(999_999L, host, "{\"opinion\":\"x\"}"), 404, "NOT_FOUND");
        assertCode(opinion(appealId, host, "{\"opinion\":\"\"}"), 400, "VALIDATION_FAILED");

        assertThat(opinion(appealId, host, "{\"opinion\":\"처음 의견\"}")).hasStatusOk();
        assertThat(opinion(appealId, host, "{\"opinion\":\"고친 의견\"}")).hasStatusOk().bodyJson()
                .extractingPath("$.hostOpinion").isEqualTo("고친 의견");

        jdbc.update("UPDATE appeal SET status = 'REJECTED' WHERE id = ?", appealId);
        assertCode(opinion(appealId, host, "{\"opinion\":\"늦은 의견\"}"), 409, "APPEAL_CLOSED");
    }

    @Test
    @DisplayName("주최자는 자기 스터디의 이의를 닉네임·사유와 함께 최근 것부터 보고, 다른 사람은 403, 없는 스터디는 404")
    void studyAppeals() {
        long second = study();
        file(event(studyId, memberId, "NO_SHOW_CANDIDATE", now.minus(Duration.ofHours(2))), member,
                "{\"reason\":\"첫째\"}");
        file(event(studyId, otherId, "LATE", now.minus(Duration.ofHours(1))), member, "{\"reason\":\"무시됨\"}");
        file(event(second, memberId, "LATE", now.minus(Duration.ofHours(1))), member, "{\"reason\":\"다른 스터디\"}");

        assertThat(mvc.get().uri("/api/v1/studies/" + studyId + "/appeals").cookie(host).exchange()).hasStatusOk()
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.length()").isEqualTo(1);
                    assertThat(json).extractingPath("$[0].reason").isEqualTo("첫째");
                    assertThat(json).extractingPath("$[0].applicantNickname").isEqualTo("참여자");
                    assertThat(json).extractingPath("$[0].verdictType").isEqualTo("NO_SHOW_CANDIDATE");
                    assertThat(json).extractingPath("$[0].status").isEqualTo("OPEN");
                });
        assertCode(mvc.get().uri("/api/v1/studies/" + studyId + "/appeals").cookie(member).exchange(), 403,
                "FORBIDDEN");
        assertCode(mvc.get().uri("/api/v1/studies/999999/appeals").cookie(host).exchange(), 404, "NOT_FOUND");
    }

    @Test
    @DisplayName("내 이의 내역은 최근 것부터 커서로 나뉘고 남의 이의는 보이지 않는다")
    void myAppeals() throws Exception {
        for (int i = 0; i < 3; i++) {
            long id = study();
            file(event(id, memberId, "NO_SHOW_CANDIDATE", now.minus(Duration.ofHours(1))), member,
                    "{\"reason\":\"사유" + i + "\"}");
        }
        long otherEvent = event(study(), otherId, "LATE", now.minus(Duration.ofHours(1)));
        Cookie otherCsrf = mvc.get().uri("/api/v1/me").cookie(other).exchange().getResponse().getCookie("XSRF-TOKEN");
        mvc.post().uri("/api/v1/attendance-events/" + otherEvent + "/appeals").cookie(other, otherCsrf)
                .header("X-XSRF-TOKEN", otherCsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"남\"}").exchange();

        MvcTestResult first = mvc.get().uri("/api/v1/me/appeals?size=2").cookie(member).exchange();
        assertThat(first).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items.length()").isEqualTo(2);
            assertThat(json).extractingPath("$.items[0].reason").isEqualTo("사유2");
            assertThat(json).extractingPath("$.nextCursor").isNotNull();
        });
        String cursor = (String) new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(first.getResponse().getContentAsString(), java.util.Map.class).get("nextCursor");

        assertThat(mvc.get().uri("/api/v1/me/appeals?size=2&cursor=" + cursor).cookie(member).exchange())
                .hasStatusOk().bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.items.length()").isEqualTo(1);
                    assertThat(json).extractingPath("$.items[0].reason").isEqualTo("사유0");
                    assertThat(json).extractingPath("$.nextCursor").isNull();
                });
        assertCode(mvc.get().uri("/api/v1/me/appeals?cursor=%25%25").cookie(member).exchange(), 400,
                "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("내 출석 기록은 스터디마다 현재 판정을 주고, 이의 가능 여부·기한·이의 id 를 함께 준다")
    void myAttendance() {
        long attendedStudy = study();
        long attended = event(attendedStudy, memberId, "ATTENDED", now.minus(Duration.ofDays(5)));
        long candidate = event(studyId, memberId, "NO_SHOW_CANDIDATE", now.minus(Duration.ofHours(1)));
        long filedStudy = study();
        long filed = event(filedStudy, memberId, "LATE", now.minus(Duration.ofHours(2)));
        file(filed, member, "{\"reason\":\"x\"}");
        long clearedStudy = study();
        event(clearedStudy, memberId, "NO_SHOW_CANDIDATE", now.minus(Duration.ofHours(3)));
        event(clearedStudy, memberId, "NO_SHOW_CLEARED", now.minus(Duration.ofHours(2)));
        long closedStudy = study();
        long closed = event(closedStudy, memberId, "NO_SHOW_CANDIDATE", now.minus(Duration.ofHours(60)));
        event(study(), otherId, "ATTENDED", now.minus(Duration.ofHours(1)));

        assertThat(mvc.get().uri("/api/v1/me/attendance").cookie(member).exchange()).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    // 최근 쌓인 이벤트부터: closed, cleared, filed, candidate, attended
                    assertThat(json).extractingPath("$.items.length()").isEqualTo(5);
                    assertThat(json).extractingPath("$.items[0].eventId").isEqualTo((int) closed);
                    assertThat(json).extractingPath("$.items[0].appealable").isEqualTo(false);
                    assertThat(json).extractingPath("$.items[1].type").isEqualTo("NO_SHOW_CLEARED");
                    assertThat(json).extractingPath("$.items[1].appealable").isEqualTo(false);
                    assertThat(json).extractingPath("$.items[1].appealDueAt").isNull();
                    assertThat(json).extractingPath("$.items[2].eventId").isEqualTo((int) filed);
                    assertThat(json).extractingPath("$.items[2].appealable").isEqualTo(false);
                    assertThat(json).extractingPath("$.items[2].appealId").isNotNull();
                    assertThat(json).extractingPath("$.items[3].eventId").isEqualTo((int) candidate);
                    assertThat(json).extractingPath("$.items[3].appealable").isEqualTo(true);
                    assertThat(json).extractingPath("$.items[3].studyTitle").isEqualTo("모각코");
                    assertThat(json).extractingPath("$.items[3].appealDueAt").isNotNull();
                    assertThat(json).extractingPath("$.items[4].eventId").isEqualTo((int) attended);
                    assertThat(json).extractingPath("$.items[4].appealable").isEqualTo(false);
                    assertThat(json).extractingPath("$.nextCursor").isNull();
                });
    }

    @Test
    @DisplayName("로그인하지 않으면 401, size 범위를 벗어나면 400")
    void authAndSize() {
        assertThat(mvc.get().uri("/api/v1/me/attendance").exchange()).hasStatus(401);
        assertThat(mvc.get().uri("/api/v1/me/appeals").exchange()).hasStatus(401);
        assertCode(mvc.get().uri("/api/v1/me/attendance?size=0").cookie(member).exchange(), 400,
                "VALIDATION_FAILED");
    }

    private MvcTestResult file(long eventId, Cookie user, String json) {
        return send("/api/v1/attendance-events/" + eventId + "/appeals", user, json, true);
    }

    private MvcTestResult opinion(long appealId, Cookie user, String json) {
        return send("/api/v1/appeals/" + appealId + "/host-opinion", user, json, false);
    }

    private MvcTestResult send(String uri, Cookie user, String json, boolean post) {
        var request = post ? mvc.post() : mvc.put();
        return request.uri(uri).cookie(user, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static void assertCode(MvcTestResult result, int status, String code) {
        assertThat(result).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Cookie login(long kakaoId) {
        return FakeSocialProvider.login(mvc, "kakao", kakaoUser(kakaoId, null)).getCookie("access_token");
    }

    private long userIdOf(long kakaoId) {
        return jdbc.queryForObject("SELECT user_id FROM social_account WHERE provider_user_id = ?", Long.class,
                Long.toString(kakaoId));
    }

    private void onboard(long userId, String nickname) {
        jdbc.update("UPDATE user SET nickname = ?, job_code = 'IT_BACKEND', district_code = 'SUWON_JANGAN' WHERE id = ?",
                nickname, userId);
    }

    private long study() {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SUWON_JANGAN', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, ?, 120, ?, 4, NULL, TRUE, 'ENDED', ?, ?)
                """, hostId, utc(now.minus(Duration.ofDays(1))),
                now.minus(Duration.ofDays(1)).atZone(TimeProvider.KOREA).toLocalDate(), utc(now), utc(now));
        long id = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', ?)", id,
                hostId, utc(now));
        return id;
    }

    private long event(long studyId, long userId, String type, Instant occurredAt) {
        jdbc.update("INSERT INTO attendance_event (study_id, user_id, type, occurred_at) VALUES (?, ?, ?, ?)",
                studyId, userId, type, utc(occurredAt));
        return jdbc.queryForObject("SELECT MAX(id) FROM attendance_event", Long.class);
    }
}
