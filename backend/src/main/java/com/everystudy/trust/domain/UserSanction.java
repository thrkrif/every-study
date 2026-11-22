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
 * 제재 한 건 (DATA-MODEL 6장). {@code startsAt <= now < endsAt} 이고 해제되지 않았으면 유효하다.
 * 자동 제재는 {@code ParticipantSanctionService}(T-035a)가 건다. 운영자 조치는 T-055 에서 더한다.
 */
@Entity
@Table(name = "user_sanction")
public class UserSanction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SanctionKind kind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private SanctionSource source;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_by")
    private Long createdBy;

    protected UserSanction() {
    }

    public static UserSanction auto(long userId, SanctionKind kind, String reason, Instant startsAt, Instant endsAt) {
        UserSanction sanction = new UserSanction();
        sanction.userId = userId;
        sanction.kind = kind;
        sanction.source = SanctionSource.AUTO;
        sanction.reason = reason;
        sanction.startsAt = startsAt;
        sanction.endsAt = endsAt;
        return sanction;
    }

    public Long getId() {
        return id;
    }

    public Instant getEndsAt() {
        return endsAt;
    }
}
