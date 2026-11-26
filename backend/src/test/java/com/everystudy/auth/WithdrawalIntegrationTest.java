package com.everystudy.auth;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.notification.FakeChannelSender;
import com.everystudy.notification.service.NotificationChannel;
import com.everystudy.notification.service.NotificationDispatchService;
import com.everystudy.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 회원 탈퇴 (PLAN T-062, PRD 4.1, ADR-0002 결정 6). */
@Import({FakeSocialProvider.class, WithdrawalIntegrationTest.Config.class})
class WithdrawalIntegrationTest extends IntegrationTestSupport {

    @TestConfiguration
    static class Config {

        /** 푸시 채널의 발송 구현은 아직 없으므로(T-042) 발송 단계까지 가 보려고 가짜를 둔다 */
        @Bean
        FakeChannelSender pushSender() {
            return new FakeChannelSender(NotificationChannel.PUSH);
        }
    }

    private static final long KAKAO_ID = 9501L;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    NotificationDispatchService dispatch;

    @Autowired
    FakeChannelSender push;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private Cookie access;
    private Cookie csrf;
    private long me;
    private long other;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        MockHttpServletResponse login = FakeSocialProvider.login(mvc, "kakao", kakaoUser(KAKAO_ID, "me@example.com"));
        access = login.getCookie("access_token");
        csrf = mvc.get().uri("/api/v1/me").cookie(access).exchange().getResponse().getCookie("XSRF-TOKEN");
        me = jdbc.queryForObject("SELECT user_id FROM social_account WHERE provider_user_id = ?", Long.class,
                Long.toString(KAKAO_ID));
        jdbc.update("UPDATE user SET nickname = '탈퇴자', job_code = 'IT_BACKEND', district_code = 'SEOUL' "
                + "WHERE id = ?", me);
        other = user("남은 사람");
    }

    @Test
    @DisplayName("탈퇴하면 개인정보가 지워지고 소셜 연결·리프레시 토큰이 사라지며 로그인 쿠키가 지워진다")
    void erasesAccount() {
        MvcTestResult result = withdraw();

        assertThat(result).hasStatus(204);
        assertThat(result.getResponse().getHeaders("Set-Cookie")).anyMatch(h -> h.contains("Max-Age=0"));
        assertThat(jdbc.queryForMap("SELECT status, nickname, email, job_code, district_code, "
                + "withdrawn_at IS NOT NULL AS gone FROM user WHERE id = ?", me))
                .containsEntry("status", "WITHDRAWN").containsEntry("nickname", null)
                .containsEntry("email", null).containsEntry("job_code", null)
                .containsEntry("district_code", null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM social_account WHERE user_id = ?", Integer.class, me))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, me)).isZero();
        assertThat(mvc.get().uri("/api/v1/me").cookie(access).exchange()).hasStatus(401);
    }

    @Test
    @DisplayName("같은 소셜 계정으로 다시 로그인하면 새 회원이고, 두 번 탈퇴해도 결과가 같다")
    void rejoinCreatesNewUser() {
        assertThat(withdraw()).hasStatus(204);
        assertThat(withdraw()).hasStatus(204);

        FakeSocialProvider.login(mvc, "kakao", kakaoUser(KAKAO_ID, "me@example.com"));
        long again = jdbc.queryForObject("SELECT user_id FROM social_account WHERE provider_user_id = ?", Long.class,
                Long.toString(KAKAO_ID));
        assertThat(again).isNotEqualTo(me);
        assertThat(jdbc.queryForObject("SELECT status FROM user WHERE id = ?", String.class, again))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("진행 중 스터디: 참여자는 취소 기록, 주최자는 승계, 혼자인 주최자는 파투, 대기 행은 삭제, 시작한 스터디의 참여자는 그대로")
    void appliesLeaveRules() {
        long participating = study(other, now.plus(Duration.ofDays(2)));
        member(participating, other);
        member(participating, me);

        long hosting = study(me, now.plus(Duration.ofDays(2)));
        member(hosting, me);
        member(hosting, other);

        long aloneHosting = study(me, now.plus(Duration.ofDays(3)));
        member(aloneHosting, me);

        long started = study(other, now.minus(Duration.ofHours(1)));
        member(started, other);
        member(started, me);

        long waitingFor = study(other, now.plus(Duration.ofDays(4)));
        member(waitingFor, other);
        jdbc.update("INSERT INTO study_waiting (study_id, user_id, created_at) VALUES (?, ?, NOW(6))", waitingFor, me);

        assertThat(withdraw()).hasStatus(204);

        assertThat(memberStatus(participating)).isEqualTo("LEFT");
        assertThat(jdbc.queryForList("SELECT type FROM attendance_event WHERE study_id = ? AND user_id = ?",
                String.class, participating, me)).containsExactly("EARLY_CANCEL");
        assertThat(jdbc.queryForObject("SELECT host_id FROM study WHERE id = ?", Long.class, hosting))
                .isEqualTo(other);
        assertThat(memberStatus(hosting)).isEqualTo("LEFT");
        assertThat(jdbc.queryForMap("SELECT status, cancel_reason FROM study WHERE id = ?", aloneHosting))
                .containsEntry("status", "CANCELLED").containsEntry("cancel_reason", "HOST_LEFT");
        assertThat(memberStatus(started)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_waiting WHERE user_id = ?", Integer.class, me))
                .isZero();
    }

    @Test
    @DisplayName("탈퇴 뒤 그 회원에게 가는 미발송 알림은 0건이고, 나중에 만들어진 알림도 발송 단계에서 취소된다")
    void noDeliveryAfterWithdrawal() {
        long pending = outbox(me, "EMAIL");
        assertThat(withdraw()).hasStatus(204);
        assertThat(status(pending)).isEqualTo("CANCELLED");

        long late = outbox(me, "PUSH");
        dispatch.dispatchDue();

        assertThat(status(late)).isEqualTo("CANCELLED");
        assertThat(push.sent()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE status = 'SENT'",
                Integer.class)).isZero();
    }

    private MvcTestResult withdraw() {
        return mvc.delete().uri("/api/v1/me").cookie(access, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .exchange();
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private long user(String nickname) {
        jdbc.update("INSERT INTO user (nickname, role, status, created_at) VALUES (?, 'USER', 'ACTIVE', NOW(6))",
                nickname);
        return jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class);
    }

    private long study(long hostId, Instant startAt) {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SEOUL', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, ?, 120, '2026-11-30', 4,
                    NULL, TRUE, 'ACTIVE', NOW(6), NOW(6))
                """, hostId, utc(startAt));
        long id = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        jdbc.update("INSERT INTO chat_room (study_id, status, created_at) VALUES (?, 'OPEN', NOW(6))", id);
        return id;
    }

    private void member(long studyId, long userId) {
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', NOW(6))",
                studyId, userId);
    }

    private String memberStatus(long studyId) {
        return jdbc.queryForObject("SELECT status FROM study_member WHERE study_id = ? AND user_id = ?",
                String.class, studyId, me);
    }

    private long outbox(long userId, String channel) {
        String key = "T-062:" + System.nanoTime();
        jdbc.update("INSERT INTO notification (user_id, type, study_id, title, body, dedupe_key, visible_at, "
                + "created_at) VALUES (?, 'CHECKIN_OPEN', NULL, '제목', '내용', ?, NOW(6), NOW(6))", userId, key);
        long notification = jdbc.queryForObject("SELECT MAX(id) FROM notification", Long.class);
        jdbc.update("INSERT INTO notification_outbox (notification_id, channel, dedupe_key, status, scheduled_at, "
                + "attempts, next_attempt_at, expires_at, created_at) VALUES (?, ?, ?, 'PENDING', NOW(6), 0, "
                + "NOW(6), DATE_ADD(NOW(6), INTERVAL 1 DAY), NOW(6))", notification, channel, key + ":" + channel);
        return jdbc.queryForObject("SELECT MAX(id) FROM notification_outbox", Long.class);
    }

    private String status(long outboxId) {
        return jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id = ?", String.class, outboxId);
    }
}
