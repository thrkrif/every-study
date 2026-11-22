package com.everystudy.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 채널별 발송 건 (DATA-MODEL 7장, ADR-0005 트랜잭션 아웃박스). 알림과 같은 트랜잭션에서 {@code PENDING} 으로 만든다.
 * 발송·재시도·만료 처리는 워커(PLAN T-038)가 한다.
 */
@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    /** {@code NotificationChannel} 이름. */
    @Column(nullable = false, length = 10)
    private String channel;

    @Column(name = "dedupe_key", nullable = false, length = 100, unique = true)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private OutboxStatus status;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_error", length = 300)
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationOutbox() {
    }

    /** 첫 시도는 예정 시각에 한다. */
    public static NotificationOutbox pending(long notificationId, String channel, String dedupeKey,
            Instant scheduledAt, Instant expiresAt, Instant now) {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.notificationId = notificationId;
        outbox.channel = channel;
        outbox.dedupeKey = dedupeKey;
        outbox.status = OutboxStatus.PENDING;
        outbox.scheduledAt = scheduledAt;
        outbox.attempts = 0;
        outbox.nextAttemptAt = scheduledAt;
        outbox.expiresAt = expiresAt;
        outbox.createdAt = now;
        return outbox;
    }

    /** 발송했다. */
    public void markSent(Instant now) {
        this.status = OutboxStatus.SENT;
        this.sentAt = now;
        this.lastError = null;
    }

    /** 유효 기한이 지나 보내지 않고 취소한다 (ARCHITECTURE 4.6). */
    public void cancelExpired(String reason) {
        this.status = OutboxStatus.CANCELLED;
        this.lastError = truncate(reason);
    }

    /** 수신 설정이 꺼져 있어 보내지 않는다. 설정을 다시 켜도 이미 건너뛴 건은 보내지 않는다. */
    public void skipByPreference() {
        this.status = OutboxStatus.SKIPPED;
        this.lastError = null;
    }

    /** 실패를 기록하고 {@code retryAt} 에 다시 시도한다. */
    public void retryLater(String error, Instant retryAt) {
        this.attempts++;
        this.lastError = truncate(error);
        this.nextAttemptAt = retryAt;
    }

    /** 더 시도하지 않는다. */
    public void fail(String error) {
        this.attempts++;
        this.status = OutboxStatus.FAILED;
        this.lastError = truncate(error);
    }

    private static String truncate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 299) + "…";
    }

    public Long getId() {
        return id;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public String getChannel() {
        return channel;
    }

    public OutboxStatus getStatus() {
        return status;
    }
}
