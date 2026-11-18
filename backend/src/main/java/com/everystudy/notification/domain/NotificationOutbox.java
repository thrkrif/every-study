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

    public Long getId() {
        return id;
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
