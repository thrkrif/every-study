package com.everystudy.admin;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.auth.FakeSocialProvider;
import com.everystudy.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 운영자 개별 출석 정정 (PLAN T-057, X-7). */
@Import(FakeSocialProvider.class)
@TestPropertySource(properties = "everystudy.admin.emails=admin@example.com")
class AttendanceCorrectionIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private Cookie admin;
    private Cookie normal;
    private Cookie csrf;
    private long adminId;
    private long member;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        admin = FakeSocialProvider.login(mvc, "kakao", kakaoUser(6001L, "admin@example.com")).getCookie("access_token");
        normal = FakeSocialProvider.login(mvc, "kakao", kakaoUser(6002L, "user@example.com")).getCookie("access_token");
        csrf = mvc.get().uri("/api/v1/me").cookie(admin).exchange().getResponse().getCookie("XSRF-TOKEN");
        adminId = jdbc.queryForObject("SELECT id FROM user WHERE role = 'ADMIN'", Long.class);
        jdbc.update("INSERT INTO user (nickname, role, status, created_at) VALUES ('참여자', 'USER', 'ACTIVE', NOW(6))");
        member = jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class);
    }

    @Test
    @DisplayName("확정된 노쇼를 출석으로 정정하면 무효화와 출석 정정이 남고 지표가 바뀌며 감사 로그가 남는다")
    void correctsNoShowToAttended() throws Exception {
        long study = study();
        long event = event(study, "NO_SHOW", now.minus(Duration.ofDays(1)));

        MvcTestResult result = correct(event, "ATTENDED", "서버 장애로 체크인 못 함");

        assertThat(result).hasStatus(200);
        assertThat((String) JsonPath.read(body(result), "$.verdict")).isEqualTo("ATTENDED");
        assertThat(types(study)).containsExactly("NO_SHOW", "NO_SHOW_CLEARED", "ATTENDED");
        assertThat(jdbc.queryForMap("SELECT attended, no_show FROM user_trust_summary WHERE user_id = ?", member))
                .containsEntry("attended", 1).containsEntry("no_show", 0);
        assertThat(audit("ATTENDANCE_CORRECT")).isEqualTo(1);
    }

    @Test
    @DisplayName("CLEARED 로 정정하면 판정만 무효화하고 출석으로 세지 않는다")
    void clearsCandidate() {
        long study = study();
        long event = event(study, "NO_SHOW_CANDIDATE", now.minus(Duration.ofHours(3)));

        assertThat(correct(event, "CLEARED", "오판정")).hasStatus(200);

        assertThat(types(study)).containsExactly("NO_SHOW_CANDIDATE", "NO_SHOW_CLEARED");
    }

    @Test
    @DisplayName("정정할 수 없는 경우: 이미 정정됨, 대상 아닌 종류, 열린 이의 → 409, 없는 판정 404, 사유 없음·값 오류 400")
    void rejections() {
        long study = study();
        long event = event(study, "NO_SHOW", now.minus(Duration.ofDays(1)));
        assertThat(correct(event, "CLEARED", "첫 정정")).hasStatus(200);
        assertThat(correct(event, "ATTENDED", "두 번째")).hasStatus(409);

        long attended = event(study(), "ATTENDED", now.minus(Duration.ofDays(1)));
        assertThat(correct(attended, "CLEARED", "출석은 대상 아님")).hasStatus(409);

        long study2 = study();
        long withAppeal = event(study2, "LATE", now.minus(Duration.ofHours(2)));
        jdbc.update("""
                INSERT INTO appeal (attendance_event_id, study_id, user_id, reason, status, due_at, created_at)
                VALUES (?, ?, ?, '이유', 'OPEN', ?, ?)
                """, withAppeal, study2, member, utc(now.plus(Duration.ofHours(1))), utc(now));
        assertThat(correct(withAppeal, "CLEARED", "열린 이의")).hasStatus(409);

        assertThat(correct(999999L, "CLEARED", "없음")).hasStatus(404);
        assertThat(correct(withAppeal, "CLEARED", " ")).hasStatus(400);
        assertThat(correct(withAppeal, "NOPE", "값 오류")).hasStatus(400);
    }

    @Test
    @DisplayName("정정으로 근거를 잃은 자동 제한은 해제된다. 일반 회원은 403")
    void revokesUnsupportedRestrictionAndGuardsRole() {
        event(study(), "NO_SHOW", now.minus(Duration.ofDays(3)));
        Instant lastAt = now.minus(Duration.ofDays(1));
        long event = event(study(), "NO_SHOW", lastAt);
        jdbc.update("INSERT INTO user_sanction (user_id, kind, source, reason, starts_at, ends_at) "
                + "VALUES (?, 'JOIN_RESTRICT', 'AUTO', '테스트', ?, ?)", member, utc(lastAt),
                utc(now.plus(Duration.ofDays(6))));

        assertThat(mvc.post().uri("/api/v1/admin/attendance-events/" + event + "/corrections").cookie(normal)
                .contentType("application/json").content("{\"result\":\"CLEARED\",\"note\":\"x\"}").exchange())
                .hasStatus(403);
        assertThat(correct(event, "CLEARED", "오판정")).hasStatus(200);

        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM user_sanction WHERE user_id = ?",
                Boolean.class, member)).isTrue();
        assertThat(audit("SANCTION_REVOKE")).isEqualTo(1);
    }

    private MvcTestResult correct(long eventId, String result, String note) {
        return mvc.post().uri("/api/v1/admin/attendance-events/" + eventId + "/corrections").cookie(admin, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
                .content("{\"result\": \"" + result + "\", \"note\": \"" + note + "\"}").exchange();
    }

    private static String body(MvcTestResult result) throws UnsupportedEncodingException {
        return result.getResponse().getContentAsString();
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private long study() {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SEOUL', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, '2026-11-20 03:00:00', 120, '2026-11-20', 4,
                    NULL, TRUE, 'ENDED', NOW(6), NOW(6))
                """, adminId);
        return jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
    }

    private long event(long studyId, String type, Instant occurredAt) {
        jdbc.update("INSERT INTO attendance_event (study_id, user_id, type, occurred_at) VALUES (?, ?, ?, ?)",
                studyId, member, type, utc(occurredAt));
        return jdbc.queryForObject("SELECT MAX(id) FROM attendance_event", Long.class);
    }

    private List<String> types(long studyId) {
        return jdbc.queryForList("SELECT type FROM attendance_event WHERE study_id = ? ORDER BY id", String.class,
                studyId);
    }

    private int audit(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ?", Integer.class, action);
    }
}
