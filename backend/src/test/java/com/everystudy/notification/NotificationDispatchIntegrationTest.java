package com.everystudy.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.notification.service.ChannelSender.ChannelSendException;
import com.everystudy.notification.service.NotificationChannel;
import com.everystudy.notification.service.NotificationDispatchService;
import com.everystudy.support.IntegrationTestSupport;
import com.everystudy.support.MutableClock;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 알림 발송 워커 (PLAN T-038, ARCHITECTURE 4.6). 고정 시계와 가짜 채널로 발송·재시도·만료를 실제 DB 로 본다. 푸시 채널만
 * 가짜 구현이 있고 이메일 채널은 구현이 없다 (T-041 이전의 실제 상태).
 */
@Import(NotificationDispatchIntegrationTest.Config.class)
class NotificationDispatchIntegrationTest extends IntegrationTestSupport {

    private static final String T0 = "2026-11-23T03:00:00Z";

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(T0);
        }

        @Bean
        FakeChannelSender pushSender() {
            return new FakeChannelSender(NotificationChannel.PUSH);
        }
    }

    @Autowired
    Clock clock;

    @Autowired
    FakeChannelSender push;

    @Autowired
    NotificationDispatchService dispatch;

    @Autowired
    JdbcTemplate jdbc;

    private long userId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        at(T0);
        push.reset();
        jdbc.update("INSERT INTO user (role, status, created_at) VALUES ('USER', 'ACTIVE', ?)",
                utc(Instant.parse(T0)));
        userId = jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class);
    }

    @Test
    @DisplayName("시각이 된 건은 보내고 SENT 로 바꾼다. 다시 돌려도 재발송 0건")
    void sendsOnce() {
        long id = outbox("PUSH", "2026-11-23T03:00:00Z", "2026-11-23T05:00:00Z");

        assertThat(dispatch.dispatchDue()).isEqualTo(1);
        assertThat(dispatch.dispatchDue()).isZero();

        assertThat(push.sent()).hasSize(1);
        assertThat(push.sent().get(0).userId()).isEqualTo(userId);
        assertThat(push.sent().get(0).title()).isEqualTo("제목");
        Map<String, Object> row = row(id);
        assertThat(row).containsEntry("status", "SENT");
        assertThat(row.get("sent_at")).isNotNull();
        assertThat(row.get("last_error")).isNull();
    }

    @Test
    @DisplayName("예정 시각 전의 건은 보내지 않고, 그 시각이 되면 보낸다")
    void notBeforeScheduled() {
        long id = outbox("PUSH", "2026-11-23T03:00:01Z", "2026-11-23T05:00:00Z");

        assertThat(dispatch.dispatchDue()).isZero();
        assertThat(status(id)).isEqualTo("PENDING");

        at("2026-11-23T03:00:01Z");
        assertThat(dispatch.dispatchDue()).isEqualTo(1);
        assertThat(status(id)).isEqualTo("SENT");
    }

    @Test
    @DisplayName("유효 기한이 지난 건은 보내지 않고 CANCELLED, 기한 정각까지는 보낸다")
    void expiry() {
        long expired = outbox("PUSH", "2026-11-23T02:00:00Z", "2026-11-23T02:59:59Z");
        long onTime = outbox("PUSH", "2026-11-23T02:00:00Z", "2026-11-23T03:00:00Z");

        assertThat(dispatch.dispatchDue()).isEqualTo(2);

        assertThat(status(expired)).isEqualTo("CANCELLED");
        assertThat(row(expired).get("last_error")).isNotNull();
        assertThat(status(onTime)).isEqualTo("SENT");
        assertThat(push.sent()).hasSize(1);
    }

    @Test
    @DisplayName("실패하면 백오프(1·2·4·8분)로 다시 시도하고, 다섯 번째 실패에서 FAILED 로 남긴다")
    void retriesThenFails() {
        long id = outbox("PUSH", "2026-11-23T03:00:00Z", "2026-11-23T09:00:00Z");
        push.failNext(5, true);

        String[] attemptAt = {T0, "2026-11-23T03:01:00Z", "2026-11-23T03:03:00Z", "2026-11-23T03:07:00Z",
                "2026-11-23T03:15:00Z"};
        String[] nextAt = {"2026-11-23T03:01:00Z", "2026-11-23T03:03:00Z", "2026-11-23T03:07:00Z",
                "2026-11-23T03:15:00Z"};
        for (int i = 0; i < 4; i++) {
            at(attemptAt[i]);
            assertThat(dispatch.dispatchDue()).isEqualTo(1);
            Map<String, Object> row = row(id);
            assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", i + 1);
            assertThat(row.get("next_attempt_at")).isEqualTo(utc(Instant.parse(nextAt[i])));
            assertThat(row.get("last_error")).isEqualTo("테스트: 발송 실패");
            // 다음 시각 전에는 다시 시도하지 않는다
            assertThat(dispatch.dispatchDue()).isZero();
        }

        at(attemptAt[4]);
        assertThat(dispatch.dispatchDue()).isEqualTo(1);
        assertThat(row(id)).containsEntry("status", "FAILED").containsEntry("attempts", 5);
        at("2026-11-23T04:00:00Z");
        assertThat(dispatch.dispatchDue()).isZero();
        assertThat(push.sent()).isEmpty();
    }

    @Test
    @DisplayName("한두 번 실패한 뒤 성공하면 SENT 이고 시도 횟수가 남는다")
    void succeedsAfterRetry() {
        long id = outbox("PUSH", "2026-11-23T03:00:00Z", "2026-11-23T09:00:00Z");
        push.failNext(1, true);

        assertThat(dispatch.dispatchDue()).isEqualTo(1);
        at("2026-11-23T03:01:00Z");
        assertThat(dispatch.dispatchDue()).isEqualTo(1);

        assertThat(row(id)).containsEntry("status", "SENT").containsEntry("attempts", 1);
        assertThat(push.sent()).hasSize(1);
    }

    @Test
    @DisplayName("다시 해도 소용없는 실패(retryable=false)는 바로 FAILED")
    void nonRetryableFailsAtOnce() {
        long id = outbox("PUSH", "2026-11-23T03:00:00Z", "2026-11-23T09:00:00Z");
        push.failNext(1, false);

        assertThat(dispatch.dispatchDue()).isEqualTo(1);

        assertThat(row(id)).containsEntry("status", "FAILED").containsEntry("attempts", 1);
    }

    @Test
    @DisplayName("채널 구현이 예기치 못한 예외를 던져도 건을 잃지 않고 다음 건을 계속 처리한다")
    void unexpectedErrorIsolated() {
        long first = outbox("PUSH", "2026-11-23T02:00:00Z", "2026-11-23T09:00:00Z");
        long second = outbox("PUSH", "2026-11-23T02:30:00Z", "2026-11-23T09:00:00Z");
        push.throwNext(new IllegalStateException("연락처 user@example.com 포함"));

        assertThat(dispatch.dispatchDue()).isEqualTo(2);

        assertThat(row(first)).containsEntry("status", "PENDING").containsEntry("attempts", 1);
        assertThat((String) row(first).get("last_error")).contains("IllegalStateException")
                .doesNotContain("example.com");
        assertThat(status(second)).isEqualTo("SENT");
    }

    @Test
    @DisplayName("보낼 구현이 없는 채널의 건은 건드리지 않고, 기한이 지나면 취소한다")
    void channelWithoutSender() {
        long id = outbox("EMAIL", "2026-11-23T03:00:00Z", "2026-11-23T05:00:00Z");

        assertThat(dispatch.dispatchDue()).isZero();
        assertThat(row(id)).containsEntry("status", "PENDING").containsEntry("attempts", 0);

        at("2026-11-23T05:00:01Z");
        assertThat(dispatch.dispatchDue()).isEqualTo(1);
        assertThat(status(id)).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("이미 SENT·FAILED·CANCELLED 인 건은 읽지 않는다")
    void settledRowsIgnored() {
        outbox("PUSH", "2026-11-23T02:00:00Z", "2026-11-23T09:00:00Z");
        jdbc.update("UPDATE notification_outbox SET status = 'FAILED'");

        assertThat(dispatch.dispatchDue()).isZero();
        assertThat(push.sent()).isEmpty();
    }

    private long outbox(String channel, String nextAttemptAt, String expiresAt) {
        String key = "T-038:" + System.nanoTime();
        jdbc.update("""
                INSERT INTO notification (user_id, type, study_id, title, body, dedupe_key, visible_at, created_at)
                VALUES (?, 'CHECKIN_OPEN', NULL, '제목', '내용', ?, ?, ?)
                """, userId, key, utc(Instant.parse(T0)), utc(Instant.parse(T0)));
        long notificationId = jdbc.queryForObject("SELECT MAX(id) FROM notification", Long.class);
        jdbc.update("""
                INSERT INTO notification_outbox (notification_id, channel, dedupe_key, status, scheduled_at, attempts,
                    next_attempt_at, expires_at, created_at)
                VALUES (?, ?, ?, 'PENDING', ?, 0, ?, ?, ?)
                """, notificationId, channel, key + ":" + channel, utc(Instant.parse(nextAttemptAt)),
                utc(Instant.parse(nextAttemptAt)), utc(Instant.parse(expiresAt)), utc(Instant.parse(T0)));
        return jdbc.queryForObject("SELECT MAX(id) FROM notification_outbox", Long.class);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT * FROM notification_outbox WHERE id = ?", id);
    }

    private String status(long id) {
        return (String) row(id).get("status");
    }

    private void at(String utcInstant) {
        ((MutableClock) clock).set(utcInstant);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
