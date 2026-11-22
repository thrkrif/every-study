package com.everystudy.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 감사 로그 한 줄 (DATA-MODEL 9장). 만든 뒤에는 바꾸지 않는다. 그래서 setter 가 없다. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 행위자. 시스템이 한 행위면 {@code null}. */
    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private long targetId;

    /** 변경 전후 값 등. 없으면 {@code null}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    static AuditLog of(Long actorId, AuditAction action, String targetType, long targetId,
            Map<String, Object> detail, Instant now) {
        AuditLog log = new AuditLog();
        log.actorId = actorId;
        log.action = action.name();
        log.targetType = targetType;
        log.targetId = targetId;
        log.detail = detail;
        log.createdAt = now;
        return log;
    }

    public Long getId() {
        return id;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public long getTargetId() {
        return targetId;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
