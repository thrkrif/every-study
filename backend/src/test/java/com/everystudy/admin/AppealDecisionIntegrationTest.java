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

/** 이의 최종 결정 (PLAN T-056, PRD 4.9, X-12). 운영자는 HTTP 로, 기록은 SQL 로 준비한다. */
@Import(FakeSocialProvider.class)
@TestPropertySource(properties = "everystudy.admin.emails=admin@example.com")
class AppealDecisionIntegrationTest extends IntegrationTestSupport {

    private static final String APPEALS = "/api/v1/admin/appeals";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private Cookie admin;
    private Cookie normal;
    private Cookie csrf;
    private long adminId;
    private long applicant;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        admin = FakeSocialProvider.login(mvc, "kakao", kakaoUser(5001L, "admin@example.com")).getCookie("access_token");
        normal = FakeSocialProvider.login(mvc, "kakao", kakaoUser(5002L, "user@example.com")).getCookie("access_token");
        csrf = mvc.get().uri("/api/v1/me").cookie(admin).exchange().getResponse().getCookie("XSRF-TOKEN");
        adminId = jdbc.queryForObject("SELECT id FROM user WHERE role = 'ADMIN'", Long.class);
        applicant = user("신청자");
    }

    @Test
    @DisplayName("노쇼 후보 이의를 인정하면 NO_SHOW_CLEARED 가 남고 신청자에게 알림이 가며, 같은 이의는 다시 결정할 수 없다")
    void acceptsCandidate() throws Exception {
        long study = study();
        long event = event(study, applicant, "NO_SHOW_CANDIDATE", now.minus(Duration.ofHours(10)));
        long appeal = appeal(event, study);

        MvcTestResult result = decide(appeal, true, "증빙 확인");
        assertThat(result).hasStatus(200);
        assertThat((String) JsonPath.read(body(result), "$.status")).isEqualTo("ACCEPTED");

        assertThat(types(study)).containsExactly("NO_SHOW_CANDIDATE", "NO_SHOW_CLEARED");
        assertThat(jdbc.queryForObject("SELECT decided_by FROM appeal WHERE id = ?", Long.class, appeal))
                .isEqualTo(adminId);
        assertThat(jdbc.queryForList("SELECT title FROM notification WHERE type = 'APPEAL_RESULT'", String.class))
                .containsExactly("이의가 받아들여졌어요");
        assertThat(audit("APPEAL_DECIDE")).isEqualTo(1);

        assertThat(decide(appeal, false, "다시")).hasStatus(409);
        assertThat(types(study)).hasSize(2);
        assertThat(audit("APPEAL_DECIDE")).isEqualTo(1);
    }

    @Test
    @DisplayName("지각 이의를 인정하면 무효화와 함께 출석 정정이 남아 출석으로 집계된다")
    void acceptsLate() {
        long study = study();
        long event = event(study, applicant, "LATE", now.minus(Duration.ofHours(1)));
        long appeal = appeal(event, study);

        assertThat(decide(appeal, true, "교통 사고")).hasStatus(200);

        assertThat(types(study)).containsExactly("LATE", "NO_SHOW_CLEARED", "ATTENDED");
        assertThat(jdbc.queryForMap("SELECT attended, late FROM user_trust_summary WHERE user_id = ?", applicant))
                .containsEntry("attended", 1).containsEntry("late", 0);
    }

    @Test
    @DisplayName("노쇼 후보 이의를 기각하면 바로 NO_SHOW 로 확정하고 제재를 판정한다. 기다리던 확정 작업은 중복 확정하지 않는다")
    void rejectsCandidateConfirmsNoShow() {
        long previous = study();
        event(previous, applicant, "NO_SHOW", now.minus(Duration.ofDays(5)));
        long study = study();
        long event = event(study, applicant, "NO_SHOW_CANDIDATE", now.minus(Duration.ofHours(10)));
        long appeal = appeal(event, study);

        assertThat(decide(appeal, false, "증빙 부족")).hasStatus(200);

        assertThat(types(study)).containsExactly("NO_SHOW_CANDIDATE", "NO_SHOW");
        assertThat(jdbc.queryForList("SELECT kind FROM user_sanction WHERE user_id = ?", String.class, applicant))
                .containsExactly("JOIN_RESTRICT");
        assertThat(audit("NO_SHOW_CONFIRM")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT title FROM notification WHERE type = 'APPEAL_RESULT'", String.class))
                .containsExactly("이의가 받아들여지지 않았어요");
    }

    @Test
    @DisplayName("지각 이의를 기각하면 판정과 집계는 그대로다")
    void rejectsLate() {
        long study = study();
        long event = event(study, applicant, "LATE", now.minus(Duration.ofHours(1)));
        long appeal = appeal(event, study);

        assertThat(decide(appeal, false, "기록대로")).hasStatus(200);

        assertThat(types(study)).containsExactly("LATE");
        assertThat(jdbc.queryForObject("SELECT status FROM appeal WHERE id = ?", String.class, appeal))
                .isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("지각 이의 인정으로 근거를 잃은 자동 참여 신청 제한은 해제되고 감사 로그가 남는다")
    void acceptedLateRevokesUnsupportedRestriction() {
        event(study(), applicant, "NO_SHOW", now.minus(Duration.ofDays(4)));
        event(study(), applicant, "LATE", now.minus(Duration.ofDays(3)));
        event(study(), applicant, "LATE", now.minus(Duration.ofDays(2)));
        long study = study();
        Instant lateAt = now.minus(Duration.ofHours(5));
        long event = event(study, applicant, "LATE", lateAt);
        // 세 번째 지각이 기록될 때 걸린 제한 (노쇼 1 + 지각 3 = 환산 노쇼 2)
        jdbc.update("INSERT INTO user_sanction (user_id, kind, source, reason, starts_at, ends_at) "
                + "VALUES (?, 'JOIN_RESTRICT', 'AUTO', '테스트', ?, ?)", applicant, utc(lateAt),
                utc(now.plus(Duration.ofDays(6))));
        long appeal = appeal(event, study);

        assertThat(decide(appeal, true, "지각이 아니었음")).hasStatus(200);

        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM user_sanction WHERE user_id = ?",
                Boolean.class, applicant)).isTrue();
        assertThat(audit("SANCTION_REVOKE")).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 근거가 남아 있는 자동 제한은 인정 뒤에도 해제하지 않는다")
    void keepsSupportedRestriction() {
        event(study(), applicant, "NO_SHOW", now.minus(Duration.ofDays(4)));
        event(study(), applicant, "NO_SHOW", now.minus(Duration.ofDays(3)));
        long study = study();
        Instant lateAt = now.minus(Duration.ofHours(5));
        long event = event(study, applicant, "LATE", lateAt);
        jdbc.update("INSERT INTO user_sanction (user_id, kind, source, reason, starts_at, ends_at) "
                + "VALUES (?, 'JOIN_RESTRICT', 'AUTO', '테스트', ?, ?)", applicant, utc(lateAt),
                utc(now.plus(Duration.ofDays(6))));
        long appeal = appeal(event, study);

        assertThat(decide(appeal, true, "지각이 아니었음")).hasStatus(200);

        assertThat(jdbc.queryForObject("SELECT revoked_at IS NULL FROM user_sanction WHERE user_id = ?",
                Boolean.class, applicant)).isTrue();
        assertThat(audit("SANCTION_REVOKE")).isZero();
    }

    @Test
    @DisplayName("목록은 기본이 열린 이의이고 status 로 바꿀 수 있다. 일반 회원은 403, 사유가 비면 400")
    void listAndGuards() throws Exception {
        long study = study();
        long open = appeal(event(study, applicant, "LATE", now.minus(Duration.ofHours(1))), study);
        long study2 = study();
        long decided = appeal(event(study2, applicant, "LATE", now.minus(Duration.ofHours(2))), study2);
        assertThat(decide(decided, false, "기록대로")).hasStatus(200);

        MvcTestResult openOnly = mvc.get().uri(APPEALS).cookie(admin).exchange();
        assertThat(ids(openOnly)).containsExactly((int) open);
        assertThat(ids(mvc.get().uri(APPEALS + "?status=all").cookie(admin).exchange()))
                .containsExactly((int) decided, (int) open);
        assertThat(mvc.get().uri(APPEALS + "?status=nope").cookie(admin).exchange()).hasStatus(400);
        assertThat(mvc.get().uri(APPEALS).cookie(normal).exchange()).hasStatus(403);
        assertThat(decide(open, true, " ")).hasStatus(400);
    }

    private MvcTestResult decide(long appealId, boolean accept, String note) {
        return mvc.post().uri(APPEALS + "/" + appealId + "/decision").cookie(admin, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
                .content("{\"accept\": " + accept + ", \"note\": \"" + note + "\"}").exchange();
    }

    private List<Integer> ids(MvcTestResult result) throws UnsupportedEncodingException {
        return JsonPath.read(body(result), "$.items[*].id");
    }

    private static String body(MvcTestResult result) throws UnsupportedEncodingException {
        return result.getResponse().getContentAsString();
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private long user(String nickname) {
        jdbc.update("INSERT INTO user (nickname, role, status, created_at) VALUES (?, 'USER', 'ACTIVE', NOW(6))",
                nickname);
        return jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class);
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

    private long event(long studyId, long userId, String type, Instant occurredAt) {
        jdbc.update("INSERT INTO attendance_event (study_id, user_id, type, occurred_at) VALUES (?, ?, ?, ?)",
                studyId, userId, type, utc(occurredAt));
        return jdbc.queryForObject("SELECT MAX(id) FROM attendance_event", Long.class);
    }

    private long appeal(long eventId, long studyId) {
        jdbc.update("""
                INSERT INTO appeal (attendance_event_id, study_id, user_id, reason, status, due_at, created_at)
                VALUES (?, ?, ?, '사정이 있었어요', 'OPEN', ?, ?)
                """, eventId, studyId, applicant, utc(now.plus(Duration.ofHours(1))), utc(now));
        return jdbc.queryForObject("SELECT MAX(id) FROM appeal", Long.class);
    }

    private List<String> types(long studyId) {
        return jdbc.queryForList("SELECT type FROM attendance_event WHERE study_id = ? ORDER BY id", String.class,
                studyId);
    }

    private int audit(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ?", Integer.class, action);
    }
}
