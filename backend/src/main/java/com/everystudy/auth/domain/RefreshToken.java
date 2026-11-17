package com.everystudy.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 리프레시 토큰 한 장 (DATA-MODEL 3장, ADR-0002 결정 2). 원문은 저장하지 않고 해시만 둔다.
 * 로그인 한 번이 계열({@code familyId}) 하나를 만들고, 회전으로 생긴 토큰은 같은 계열에 속한다.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {
    }

    private RefreshToken(Long userId, String familyId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /** 로그인으로 새 계열의 첫 토큰을 만든다. */
    public static RefreshToken startFamily(Long userId, String familyId, String tokenHash, Instant expiresAt) {
        return new RefreshToken(userId, familyId, tokenHash, expiresAt);
    }

    public Long getUserId() {
        return userId;
    }

    public String getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
