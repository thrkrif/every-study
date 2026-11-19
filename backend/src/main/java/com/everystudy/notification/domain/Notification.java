package com.everystudy.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 알림 센터의 한 건 (DATA-MODEL 7장, 화면 12). 일정 알림은 미리 만들어 두고 {@code visibleAt} 이 지나야 보인다 (PLAN X-10).
 * 내용은 중복 발송을 견디게 쓴다 (ADR-0005).
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** {@code NotificationType} 이름. 종류는 공개 서비스가 고르므로 엔티티는 문자열로 둔다. */
    @Column(nullable = false, length = 30)
    private String type;

    @Column(name = "study_id")
    private Long studyId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 300)
    private String body;

    @Column(name = "dedupe_key", nullable = false, length = 100, unique = true)
    private String dedupeKey;

    @Column(name = "visible_at", nullable = false)
    private Instant visibleAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public static Notification of(long userId, String type, Long studyId, String title, String body,
            String dedupeKey, Instant visibleAt, Instant now) {
        Notification notification = new Notification();
        notification.userId = userId;
        notification.type = type;
        notification.studyId = studyId;
        notification.title = title;
        notification.body = body;
        notification.dedupeKey = dedupeKey;
        notification.visibleAt = visibleAt;
        notification.createdAt = now;
        return notification;
    }

    /** 이미 읽었으면 처음 읽은 시각을 그대로 둔다. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public boolean isVisibleAt(Instant now) {
        return !visibleAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public Long getStudyId() {
        return studyId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public Instant getVisibleAt() {
        return visibleAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
