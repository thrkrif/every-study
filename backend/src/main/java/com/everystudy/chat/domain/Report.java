package com.everystudy.chat.domain;

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

/** 신고 한 건 (DATA-MODEL 9장, PRD 4.11). 운영자가 한 번만 반려 또는 처리 완료한다. */
@Entity
@Table(name = "report")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "target_type", nullable = false, length = 10)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private ReportStatus status;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handle_note", length = 300)
    private String handleNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "handled_at")
    private Instant handledAt;

    protected Report() {
    }

    public static Report open(long reporterId, ReportTargetType targetType, long targetId, ReportReason reason,
            String detail, Instant now) {
        Report report = new Report();
        report.reporterId = reporterId;
        report.targetType = targetType;
        report.targetId = targetId;
        report.reason = reason;
        report.detail = detail;
        report.status = ReportStatus.OPEN;
        report.createdAt = now;
        return report;
    }

    /** 운영자가 반려하거나 처리 완료한다. 열린 신고만 호출한다. */
    public void handle(boolean resolved, long operatorId, String note, Instant now) {
        this.status = resolved ? ReportStatus.RESOLVED : ReportStatus.DISMISSED;
        this.handledBy = operatorId;
        this.handleNote = note;
        this.handledAt = now;
    }

    public boolean isOpen() {
        return status == ReportStatus.OPEN;
    }

    public Long getId() {
        return id;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public ReportTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public Long getHandledBy() {
        return handledBy;
    }

    public String getHandleNote() {
        return handleNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getHandledAt() {
        return handledAt;
    }
}
