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
 * 위치 체크인과 체크인 현황 (PLAN T-027, PRD 4.6). 스터디는 SQL 로 직접 넣고, 장소는 (37.3, 127.01) 이다. 시간창·출석 경계는
 * 고정 시각으로 {@code CheckinRulesTest} 가 본다. 여기서는 시작 시각을 현재 기준으로 옮겨 구간을 만든다.
 */
@Import(FakeSocialProvider.class)
class CheckinIntegrationTest extends IntegrationTestSupport {

    /** 장소에서 약 111m 떨어진 지점 (반경 150m 안) */
    private static final double NEAR_LAT = 37.301;
    /** 장소에서 약 556m 떨어진 지점 (반경 밖) */
    private static final double FAR_LAT = 37.305;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private long hostId;
    private long memberId;
    private long outsiderId;
    private Cookie host;
    private Cookie member;
    private Cookie outsider;
    private Cookie csrf;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        host = login(1001L);
        member = login(1002L);
        outsider = login(1003L);
        hostId = userIdOf(1001L);
        memberId = userIdOf(1002L);
        outsiderId = userIdOf(1003L);
        onboard(hostId, "주최자");
        onboard(memberId, "참여자");
        onboard(outsiderId, "외부인");
        csrf = mvc.get().uri("/api/v1/me").cookie(member).exchange().getResponse().getCookie("XSRF-TOKEN");
    }

    @Test
    @DisplayName("시작 직전에 반경 안에서 체크인하면 출석이다: 거리·정확도·방식만 저장하고 ATTENDED 이벤트를 남긴다")
    void attended() {
        long id = study(now.plus(Duration.ofMinutes(5)));
        addMember(id, memberId);

        MvcTestResult result = checkIn(id, member, NEAR_LAT, 127.01, 12.4);

        assertThat(result).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.result").isEqualTo("ATTENDED");
            assertThat(json).extractingPath("$.distanceM").isEqualTo(111);
        });
        assertThat(jdbc.queryForMap(
                "SELECT method, result, distance_m, accuracy_m FROM checkin WHERE study_id = ? AND user_id = ?",
                id, memberId))
                .containsEntry("method", "LOCATION").containsEntry("result", "ATTENDED")
                .containsEntry("distance_m", 111).containsEntry("accuracy_m", 12);
        assertThat(events(id, memberId)).containsExactly("ATTENDED");
    }

    @Test
    @DisplayName("시작 10분을 넘겨 마감 전에 체크인하면 지각이다")
    void late() {
        long id = study(now.minus(Duration.ofMinutes(15)));
        addMember(id, memberId);

        assertThat(checkIn(id, member, 37.3, 127.01, 5)).hasStatusOk().bodyJson()
                .extractingPath("$.result").isEqualTo("LATE");
        assertThat(events(id, memberId)).containsExactly("LATE");
    }

    @Test
    @DisplayName("주최자도 같은 규칙으로 체크인한다")
    void hostChecksIn() {
        long id = study(now.plus(Duration.ofMinutes(5)));

        assertThat(checkIn(id, host, 37.3, 127.01, 5)).hasStatusOk().bodyJson()
                .extractingPath("$.result").isEqualTo("ATTENDED");
        assertThat(events(id, hostId)).containsExactly("ATTENDED");
    }

    @Test
    @DisplayName("반경을 넘으면 422 CHECKIN_OUT_OF_RANGE 이고 아무것도 기록하지 않는다")
    void outOfRange() {
        long id = study(now.plus(Duration.ofMinutes(5)));
        addMember(id, memberId);

        assertCode(checkIn(id, member, FAR_LAT, 127.01, 5), 422, "CHECKIN_OUT_OF_RANGE");

        assertThat(checkinCount(id)).isZero();
        assertThat(events(id, memberId)).isEmpty();
    }

    @Test
    @DisplayName("체크인 가능 시간(시작 10분 전~30분 뒤)이 아니면 422 CHECKIN_WINDOW_CLOSED")
    void windowClosed() {
        long early = study(now.plus(Duration.ofHours(1)));
        addMember(early, memberId);
        long over = study(now.minus(Duration.ofHours(1)));
        addMember(over, memberId);

        assertCode(checkIn(early, member, 37.3, 127.01, 5), 422, "CHECKIN_WINDOW_CLOSED");
        assertCode(checkIn(over, member, 37.3, 127.01, 5), 422, "CHECKIN_WINDOW_CLOSED");
        assertThat(checkinCount(early) + checkinCount(over)).isZero();
    }

    @Test
    @DisplayName("같은 사람이 다시 요청하면 처음 결과를 200 으로 돌려주고 행·이벤트는 늘지 않는다 (멱등)")
    void idempotent() {
        long id = study(now.plus(Duration.ofMinutes(5)));
        addMember(id, memberId);
        checkIn(id, member, NEAR_LAT, 127.01, 5);

        // 두 번째는 위치가 멀어도 처음 결과다
        MvcTestResult again = checkIn(id, member, FAR_LAT, 127.01, 5);

        assertThat(again).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.result").isEqualTo("ATTENDED");
            assertThat(json).extractingPath("$.distanceM").isEqualTo(111);
        });
        assertThat(checkinCount(id)).isEqualTo(1);
        assertThat(events(id, memberId)).containsExactly("ATTENDED");
    }

    @Test
    @DisplayName("참여자가 아니거나 나간 사람은 403, 없는 스터디는 404, 종료·파투는 409 STUDY_CLOSED")
    void permissions() {
        long id = study(now.plus(Duration.ofMinutes(5)));
        addMember(id, memberId);
        jdbc.update("UPDATE study_member SET status = 'LEFT' WHERE study_id = ? AND user_id = ?", id, memberId);

        assertCode(checkIn(id, outsider, 37.3, 127.01, 5), 403, "FORBIDDEN");
        assertCode(checkIn(id, member, 37.3, 127.01, 5), 403, "FORBIDDEN");
        assertCode(checkIn(999_999L, host, 37.3, 127.01, 5), 404, "NOT_FOUND");

        jdbc.update("UPDATE study SET status = 'CANCELLED' WHERE id = ?", id);
        assertCode(checkIn(id, host, 37.3, 127.01, 5), 409, "STUDY_CLOSED");
        assertThat(checkinCount(id)).isZero();
    }

    @Test
    @DisplayName("좌표 범위를 벗어나거나 필드가 없으면 400, 로그인하지 않으면 401")
    void validation() {
        long id = study(now.plus(Duration.ofMinutes(5)));

        assertThat(checkIn(id, host, 91, 127.01, 5)).hasStatus(400).bodyJson()
                .extractingPath("$.errors[*].field").asArray().contains("latitude");
        assertThat(post(id, host, "{\"latitude\": 37.3, \"longitude\": 127.01}")).hasStatus(400)
                .bodyJson().extractingPath("$.errors[*].field").asArray().contains("accuracyM");
        assertThat(mvc.post().uri("/api/v1/studies/{id}/checkins", id).cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\": 37.3, \"longitude\": 127.01, \"accuracyM\": 5}").exchange())
                .hasStatus(401);
    }

    @Test
    @DisplayName("체크인 현황: 참여자는 자기 것만, 주최자는 전원의 체크인 여부만 본다")
    void attendance() {
        long id = study(now.plus(Duration.ofMinutes(5)));
        addMember(id, memberId);
        checkIn(id, member, 37.3, 127.01, 5);

        assertThat(attendance(id, member)).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.mine.checkedIn").isEqualTo(true);
            assertThat(json).extractingPath("$.mine.result").isEqualTo("ATTENDED");
            assertThat(json).doesNotHavePath("$.members");
        });
        assertThat(attendance(id, host)).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.mine.checkedIn").isEqualTo(false);
            assertThat(json).doesNotHavePath("$.mine.result");
            assertThat(json).extractingPath("$.members[*].checkedIn").asArray().containsExactly(false, true);
            assertThat(json).extractingPath("$.members[*].nickname").asArray().containsExactly("주최자", "참여자");
            // 주최자는 다른 사람의 판정 결과를 보지 못한다
            assertThat(json).doesNotHavePath("$.members[1].result");
        });
        assertCode(attendance(id, outsider), 403, "FORBIDDEN");
        assertCode(attendance(999_999L, host), 404, "NOT_FOUND");
    }

    private MvcTestResult checkIn(long studyId, Cookie user, double latitude, double longitude, double accuracy) {
        return post(studyId, user, "{\"latitude\": " + latitude + ", \"longitude\": " + longitude
                + ", \"accuracyM\": " + accuracy + "}");
    }

    private MvcTestResult post(long studyId, Cookie user, String json) {
        return mvc.post().uri("/api/v1/studies/{id}/checkins", studyId).cookie(user, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(json)
                .exchange();
    }

    private MvcTestResult attendance(long studyId, Cookie user) {
        return mvc.get().uri("/api/v1/studies/{id}/attendance", studyId).cookie(user).exchange();
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
        return study(startAt, 4);
    }

    private long study(Instant startAt, int capacity) {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SEOUL', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, ?, 120, ?, ?, NULL, TRUE, 'ACTIVE', ?, ?)
                """, hostId, utc(startAt), startAt.atZone(TimeProvider.KOREA).toLocalDate(), capacity,
                utc(now), utc(now));
        long id = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        addMember(id, hostId);
        return id;
    }

    private void addMember(long studyId, long userId) {
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', ?)",
                studyId, userId, utc(now));
    }
}
