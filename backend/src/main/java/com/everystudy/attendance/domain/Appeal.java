package com.everystudy.attendance.domain;

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

/** 이의 신청 한 건 (DATA-MODEL 5장, PRD 4.9). 판정(출석 이벤트)당 1건이다. */
@Entity
@Table(name = "appeal")
public class Appeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attendance_event_id", nullable = false)
    private Long attendanceEventId;

    @Column(name = "study_id", nullable = false)
    private Long studyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "host_opinion", length = 500)
    private String hostOpinion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private AppealStatus status;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected Appeal() {
    }

    public static Appeal open(long attendanceEventId, long studyId, long userId, String reason, Instant dueAt,
            Instant now) {
        Appeal appeal = new Appeal();
        appeal.attendanceEventId = attendanceEventId;
        appeal.studyId = studyId;
        appeal.userId = userId;
        appeal.reason = reason;
        appeal.status = AppealStatus.OPEN;
        appeal.dueAt = dueAt;
        appeal.createdAt = now;
        return appeal;
    }

    /** 주최자의 1차 의견. 결정 전에는 다시 보내면 덮어쓴다. */
    public void writeHostOpinion(String opinion) {
        this.hostOpinion = opinion;
    }

    public boolean isOpen() {
        return status == AppealStatus.OPEN;
    }

    public Long getId() {
        return id;
    }

    public Long getAttendanceEventId() {
        return attendanceEventId;
    }

    public Long getStudyId() {
        return studyId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getReason() {
        return reason;
    }

    public String getHostOpinion() {
        return hostOpinion;
    }

    public AppealStatus getStatus() {
        return status;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
