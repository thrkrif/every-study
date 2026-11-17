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

    /**
     * 이 토큰을 쓴 것으로 표시하고 같은 계열의 다음 토큰을 만든다 (회전).
     * 쓴 토큰이 다시 오면 탈취로 보고 계열 전체를 폐기한다 (ADR-0002 결정 2).
     */
    public RefreshToken rotate(String nextTokenHash, Instant nextExpiresAt, Instant now) {
        if (!isUsableAt(now)) {
            throw new IllegalStateException("쓸 수 없는 리프레시 토큰은 회전할 수 없음");
        }
        this.usedAt = now;
        return new RefreshToken(userId, familyId, nextTokenHash, nextExpiresAt);
    }

    /** 이미 회전에 쓰였는가. */
    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** 만료 시각이 되면 그 순간부터 쓸 수 없다. */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** 한 번도 쓰이지 않았고, 폐기되지 않았고, 만료 전인가. */
    public boolean isUsableAt(Instant now) {
        return !isUsed() && !isRevoked() && !isExpiredAt(now);
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
