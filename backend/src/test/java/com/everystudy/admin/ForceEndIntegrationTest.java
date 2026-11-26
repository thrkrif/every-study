package com.everystudy.admin;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.attendance.service.AttendanceJudgementService;
import com.everystudy.auth.FakeSocialProvider;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 운영자 강제 종료 (PLAN T-054, PRD 4.11, D-16). */
@Import(FakeSocialProvider.class)
@TestPropertySource(properties = "everystudy.admin.emails=admin@example.com")
class ForceEndIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AttendanceJudgementService judgement;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private Cookie admin;
    private Cookie normal;
    private Cookie csrf;
    private long host;
    private long member;
    private long waiter;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        admin = FakeSocialProvider.login(mvc, "kakao", kakaoUser(8001L, "admin@example.com")).getCookie("access_token");
        normal = FakeSocialProvider.login(mvc, "kakao", kakaoUser(8002L, null)).getCookie("access_token");
        csrf = mvc.get().uri("/api/v1/me").cookie(admin).exchange().getResponse().getCookie("XSRF-TOKEN");
        host = user("주최자");
        member = user("참여자");
        waiter = user("대기자");
    }

    @Test
    @DisplayName("강제 종료하면 파투(OPERATOR)가 되고 대기 행이 지워지며 참여자·대기자에게 알림, 채팅방 폐쇄, 감사 로그가 남는다")
    void forceEndsStudy() {
        long id = study("ACTIVE", now.plus(Duration.ofDays(2)));
        member(id, host);
        member(id, member);
        jdbc.update("INSERT INTO study_waiting (study_id, user_id, created_at) VALUES (?, ?, NOW(6))", id, waiter);

        assertThat(forceEnd(id, "부적절한 모집")).hasStatus(204);

        assertThat(jdbc.queryForMap("SELECT status, cancel_reason FROM study WHERE id = ?", id))
                .containsEntry("status", "CANCELLED").containsEntry("cancel_reason", "OPERATOR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_waiting WHERE study_id = ?", Integer.class, id))
                .isZero();
        assertThat(jdbc.queryForList("SELECT user_id FROM notification WHERE type = 'STUDY_CANCELLED' "
                + "AND study_id = ?", Long.class, id)).containsExactlyInAnyOrder(host, member, waiter);
        assertThat(jdbc.queryForObject("SELECT status FROM chat_room WHERE study_id = ?", String.class, id))
                .isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'STUDY_FORCE_END' "
                + "AND target_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("다시 불러도 아무것도 늘지 않는다(멱등)")
    void idempotent() {
        long id = study("ACTIVE", now.plus(Duration.ofDays(2)));
        member(id, host);
        member(id, member);

        assertThat(forceEnd(id, "첫 호출")).hasStatus(204);
        assertThat(forceEnd(id, "두 번째")).hasStatus(204);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'STUDY_CANCELLED'",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'STUDY_FORCE_END'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("체크인 마감 뒤 판정 작업이 참여자마다 VOIDED 를 한 번 남기고 지표는 그대로다")
    void judgementVoidsParticipants() {
        long id = study("ACTIVE", now.minus(Duration.ofHours(2)));
        member(id, host);
        member(id, member);
        long done = study("ENDED", now.minus(Duration.ofDays(3)));
        jdbc.update("UPDATE study SET judged_at = NOW(6) WHERE id = ?", done);
        jdbc.update("INSERT INTO attendance_event (study_id, user_id, type, occurred_at) VALUES (?, ?, 'ATTENDED', ?)",
                done, member, utc(now.minus(Duration.ofDays(3))));
        jdbc.update("INSERT INTO user_trust_summary (user_id, counted, attended, late, no_show, late_cancel, rate, "
                + "recalculated_at) VALUES (?, 1, 1, 0, 0, 0, NULL, NOW(6))", member);

        assertThat(forceEnd(id, "운영 정책 위반")).hasStatus(204);
        assertThat(judgement.judgeDueSessions()).isEqualTo(1);
        assertThat(judgement.judgeDueSessions()).isZero();

        assertThat(jdbc.queryForList("SELECT user_id FROM attendance_event WHERE study_id = ? AND type = 'VOIDED'",
                Long.class, id)).containsExactlyInAnyOrder(host, member);
        assertThat(jdbc.queryForMap("SELECT attended, no_show FROM user_trust_summary WHERE user_id = ?", member))
                .containsEntry("attended", 1).containsEntry("no_show", 0);
    }

    @Test
    @DisplayName("정상 종료된 스터디는 409, 없는 스터디는 404, 사유가 없으면 400, 일반 회원은 403")
    void guards() {
        long ended = study("ENDED", now.minus(Duration.ofDays(2)));
        assertThat(forceEnd(ended, "이미 끝남")).hasStatus(409);
        assertThat(forceEnd(999999L, "없음")).hasStatus(404);
        long id = study("ACTIVE", now.plus(Duration.ofDays(2)));
        assertThat(forceEnd(id, " ")).hasStatus(400);
        assertThat(mvc.post().uri("/api/v1/admin/studies/" + id + "/force-end").cookie(normal, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
                .content("{\"note\":\"x\"}").exchange()).hasStatus(403);
        assertThat(jdbc.queryForObject("SELECT status FROM study WHERE id = ?", String.class, id)).isEqualTo("ACTIVE");
    }

    private MvcTestResult forceEnd(long id, String note) {
        return mvc.post().uri("/api/v1/admin/studies/" + id + "/force-end").cookie(admin, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
                .content("{\"note\":\"" + note + "\"}").exchange();
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private long user(String nickname) {
        jdbc.update("INSERT INTO user (nickname, role, status, created_at) VALUES (?, 'USER', 'ACTIVE', NOW(6))",
                nickname);
        return jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class);
    }

    private long study(String status, Instant startAt) {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SEOUL', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, ?, 120, '2026-11-30', 4,
                    NULL, TRUE, ?, NOW(6), NOW(6))
                """, host, utc(startAt), status);
        long id = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        jdbc.update("INSERT INTO chat_room (study_id, status, created_at) VALUES (?, 'OPEN', NOW(6))", id);
        return id;
    }

    private void member(long studyId, long userId) {
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', NOW(6))",
                studyId, userId);
    }
}
