package com.everystudy.attendance.service;

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
import java.util.List;
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
 * 일회용 코드 발급과 코드 체크인 (PLAN T-028, PRD 4.6). 스터디는 SQL 로 직접 넣고 장소는 (37.3, 127.01) 이다. 입력 실패
 * 횟수는 메모리에 있어서 스터디 ID 가 재사용되는 DB 초기화마다 비운다.
 */
@Import(FakeSocialProvider.class)
class CheckinCodeIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CheckinCodeAttempts attempts;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private long hostId;
    private long memberId;
    private long otherId;
    private Cookie host;
    private Cookie member;
    private Cookie other;
    private Cookie csrf;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        attempts.clear();
        host = login(1001L);
        member = login(1002L);
        other = login(1003L);
        hostId = userIdOf(1001L);
        memberId = userIdOf(1002L);
        otherId = userIdOf(1003L);
        onboard(hostId, "주최자");
        onboard(memberId, "참여자");
        onboard(otherId, "다른참여자");
        csrf = mvc.get().uri("/api/v1/me").cookie(member).exchange().getResponse().getCookie("XSRF-TOKEN");
    }

    @Test
    @DisplayName("주최자가 체크인한 뒤 코드를 발급하면 6자리 숫자이고 만료는 체크인 마감이다. 참여자는 그 코드로 출석한다")
    void issueAndCheckIn() {
        long id = study(now.plus(Duration.ofMinutes(5)));
        addMember(id, memberId);
        hostChecksIn(id);

        MvcTestResult issued = issue(id, host);

        assertThat(issued).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.code").asString().matches("\\d{6}");
            assertThat(json).extractingPath("$.expiresAt").isNotNull();
        });
        assertThat(jdbc.queryForObject("SELECT expires_at FROM checkin_code WHERE study_id = ?",
                LocalDateTime.class, id)).isEqualTo(utc(now.plus(Duration.ofMinutes(35))));

        assertThat(checkInByCode(id, member, codeOf(id))).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.result").isEqualTo("ATTENDED");
            assertThat(json).doesNotHavePath("$.distanceM");
        });
        assertThat(jdbc.queryForMap(
                "SELECT method, result, distance_m, accuracy_m FROM checkin WHERE study_id = ? AND user_id = ?",
                id, memberId))
                .containsEntry("method", "CODE").containsEntry("result", "ATTENDED")
                .containsEntry("distance_m", null).containsEntry("accuracy_m", null);
        assertThat(events(id, memberId)).containsExactly("ATTENDED");
    }

    @Test
    @DisplayName("코드 체크인도 시각 기준이 같다: 시작 10분을 넘기면 지각")
    void lateByCode() {
        long id = study(now.minus(Duration.ofMinutes(15)));
        addMember(id, memberId);
        hostChecksIn(id);
        issue(id, host);

        assertThat(checkInByCode(id, member, codeOf(id))).hasStatusOk().bodyJson()
                .extractingPath("$.result").isEqualTo("LATE");
        assertThat(events(id, memberId)).containsExactly("LATE");
    }

    @Test
    @DisplayName("주최자가 체크인하기 전에는 발급도 코드 체크인도 422 HOST_NOT_CHECKED_IN")
    void hostNotCheckedIn() {
        long id = study(now.plus(Duration.ofMinutes(5)));
        addMember(id, memberId);

        assertCode(issue(id, host), 422, "HOST_NOT_CHECKED_IN");
        assertCode(checkInByCode(id, member, "123456"), 422, "HOST_NOT_CHECKED_IN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkin_code", Integer.class)).isZero();
        assertThat(checkinCount(id)).isZero();
    }

    @Test
    @DisplayName("틀린 코드, 발급 전, 만료된 코드는 각각 422 CHECKIN_CODE_INVALID 이고 아무것도 기록하지 않는다")
    void invalidCode() {
        long id = study(now.plus(Duration.ofMinutes(5)));
        addMember(id, memberId);
        hostChecksIn(id);

        // 주최자는 체크인했지만 아직 코드를 발급하지 않았다
        assertCode(checkInByCode(id, member, "123456"), 422, "CHECKIN_CODE_INVALID");

        issue(id, host);
        String right = codeOf(id);
        assertCode(checkInByCode(id, member, wrongOf(right)), 422, "CHECKIN_CODE_INVALID");

        jdbc.update("UPDATE checkin_code SET expires_at = ? WHERE study_id = ?", utc(now.minusSeconds(1)), id);
        assertCode(checkInByCode(id, member, right), 422, "CHECKIN_CODE_INVALID");

        assertThat(checkinCount(id) - 1).isZero(); // 주최자 것만 있다
        assertThat(events(id, memberId)).isEmpty();
    }

    @Test
    @DisplayName("5번 틀리면 맞는 코드도 거부하고, 다른 사람은 영향이 없다")
    void tooManyFailures() {
        long id = study(now.plus(Duration.ofMinutes(5)));
        addMember(id, memberId);
        addMember(id, otherId);
        hostChecksIn(id);
        issue(id, host);
        String right = codeOf(id);
        String wrong = wrongOf(right);

        for (int i = 0; i < 5; i++) {
            assertCode(checkInByCode(id, member, wrong), 422, "CHECKIN_CODE_INVALID");
        }
        assertCode(checkInByCode(id, member, right), 422, "CHECKIN_CODE_INVALID");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkin WHERE user_id = ?", Integer.class, memberId))
                .isZero();

        assertThat(checkInByCode(id, other, right)).hasStatusOk();
    }

    @Test
    @DisplayName("4번 틀린 뒤 맞으면 체크인된다")
    void successBeforeLimit() {
        long id = study(now.plus(Duration.ofMinutes(5)));
        addMember(id, memberId);
        hostChecksIn(id);
        issue(id, host);
        String right = codeOf(id);

        for (int i = 0; i < 4; i++) {
            assertCode(checkInByCode(id, member, wrongOf(right)), 422, "CHECKIN_CODE_INVALID");
        }
        assertThat(checkInByCode(id, member, right)).hasStatusOk();
    }

    @Test
    @DisplayName("재발급하면 이전 코드는 거부하고 새 코드는 받는다")
    void reissue() {
        long id = study(now.plus(Duration.ofMinutes(5)));
        addMember(id, memberId);
        hostChecksIn(id);
        issue(id, host);
        jdbc.update("UPDATE checkin_code SET code = '111111' WHERE study_id = ?", id);

        issue(id, host);
        String fresh = codeOf(id);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkin_code WHERE study_id = ?", Integer.class, id))
                .isEqualTo(1);
        if (!fresh.equals("111111")) {
            assertCode(checkInByCode(id, member, "111111"), 422, "CHECKIN_CODE_INVALID");
        }
        assertThat(checkInByCode(id, member, fresh)).hasStatusOk();
    }

    @Test
    @DisplayName("이미 체크인했으면 코드가 틀려도 처음 결과를 200 으로 돌려주고 행·이벤트는 늘지 않는다 (멱등)")
    void idempotent() {
        long id = study(now.plus(Duration.ofMinutes(5)));
        addMember(id, memberId);
        hostChecksIn(id);
        issue(id, host);
        String right = codeOf(id);
        checkInByCode(id, member, right);

        assertThat(checkInByCode(id, member, wrongOf(right))).hasStatusOk().bodyJson()
                .extractingPath("$.result").isEqualTo("ATTENDED");
        assertThat(checkinCount(id)).isEqualTo(2);
        assertThat(events(id, memberId)).containsExactly("ATTENDED");
    }

    @Test
    @DisplayName("발급은 주최자만, 체크인 가능 시간 안에서만 된다. 코드 체크인은 참여자만")
    void permissionsAndWindow() {
        long id = study(now.plus(Duration.ofMinutes(5)));
        addMember(id, memberId);
        hostChecksIn(id);
        long over = study(now.minus(Duration.ofHours(1)));
        addMember(over, memberId);
        jdbc.update("INSERT INTO checkin (study_id, user_id, checked_in_at, method, result) VALUES (?, ?, ?, 'LOCATION', 'ATTENDED')",
                over, hostId, utc(now.minus(Duration.ofHours(1))));
        issue(id, host);

        assertCode(issue(id, member), 403, "FORBIDDEN");
        assertCode(issue(999_999L, host), 404, "NOT_FOUND");
        assertCode(issue(over, host), 422, "CHECKIN_WINDOW_CLOSED");
        assertCode(checkInByCode(id, other, codeOf(id)), 403, "FORBIDDEN");
        assertCode(checkInByCode(over, member, "123456"), 422, "CHECKIN_WINDOW_CLOSED");
        assertCode(checkInByCode(999_999L, member, "123456"), 404, "NOT_FOUND");

        jdbc.update("UPDATE study SET status = 'CANCELLED' WHERE id = ?", id);
        assertCode(issue(id, host), 409, "STUDY_CLOSED");
        assertCode(checkInByCode(id, member, codeOf(id)), 409, "STUDY_CLOSED");
    }

    @Test
    @DisplayName("6자리 숫자가 아니면 400 이고 실패 횟수에 세지 않는다")
    void formatValidation() {
        long id = study(now.plus(Duration.ofMinutes(5)));
        addMember(id, memberId);
        hostChecksIn(id);
        issue(id, host);

        for (int i = 0; i < 6; i++) {
            assertThat(checkInByCode(id, member, "12ab")).hasStatus(400).bodyJson()
                    .extractingPath("$.errors[*].field").asArray().contains("code");
        }
        assertThat(checkInByCode(id, member, codeOf(id))).hasStatusOk();
    }

    private MvcTestResult issue(long studyId, Cookie user) {
        return mvc.put().uri("/api/v1/studies/{id}/checkin-code", studyId).cookie(user, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).exchange();
    }

    private MvcTestResult checkInByCode(long studyId, Cookie user, String code) {
        return mvc.post().uri("/api/v1/studies/{id}/checkins/code", studyId).cookie(user, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"" + code + "\"}").exchange();
    }

    private void hostChecksIn(long studyId) {
        assertThat(mvc.post().uri("/api/v1/studies/{id}/checkins", studyId).cookie(host, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\": 37.3, \"longitude\": 127.01, \"accuracyM\": 5}").exchange()).hasStatusOk();
    }

    private String codeOf(long studyId) {
        return jdbc.queryForObject("SELECT code FROM checkin_code WHERE study_id = ?", String.class, studyId);
    }

    private static String wrongOf(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }

    private int checkinCount(long studyId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM checkin WHERE study_id = ?", Integer.class, studyId);
    }

    private List<String> events(long studyId, long userId) {
        return jdbc.queryForList(
                "SELECT type FROM attendance_event WHERE study_id = ? AND user_id = ? ORDER BY id", String.class,
                studyId, userId);
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
        jdbc.update("UPDATE user SET nickname = ?, job_code = 'IT_BACKEND', district_code = 'SEOUL' WHERE id = ?",
                nickname, userId);
    }

    private long study(Instant startAt) {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SEOUL', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, ?, 120, ?, 4, NULL, TRUE, 'ACTIVE', ?, ?)
                """, hostId, utc(startAt), startAt.atZone(TimeProvider.KOREA).toLocalDate(), utc(now), utc(now));
        long id = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        addMember(id, hostId);
        return id;
    }

    private void addMember(long studyId, long userId) {
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', ?)",
                studyId, userId, utc(now));
    }
}
