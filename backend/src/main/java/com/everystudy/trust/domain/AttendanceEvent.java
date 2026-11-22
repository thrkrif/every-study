package com.everystudy.trust.domain;

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
 * 출석 원본 이벤트 (DATA-MODEL 5장). 추가만 하고 고치거나 지우지 않는다. 생성 컬럼 {@code verdict_key} 는 DB 가 채우므로
 * 매핑하지 않는다 (PLAN X-9).
 */
@Entity
@Table(name = "attendance_event")
public class AttendanceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false)
    private Long studyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AttendanceEventType type;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(length = 200)
    private String note;

    protected AttendanceEvent() {
    }

    public static AttendanceEvent of(long studyId, long userId, AttendanceEventType type, Instant occurredAt) {
        AttendanceEvent event = new AttendanceEvent();
        event.studyId = studyId;
        event.userId = userId;
        event.type = type;
        event.occurredAt = occurredAt;
        return event;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getStudyId() {
        return studyId;
    }

    public AttendanceEventType getType() {
        return type;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
