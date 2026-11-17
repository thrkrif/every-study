package com.everystudy.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-12-08T05:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-24T05:00:00Z");

    private static RefreshToken token() {
        return RefreshToken.startFamily(42L, "family-1", "hash-1", EXPIRES_AT);
    }

    @Test
    @DisplayName("회전하면 쓴 것으로 표시되고, 다음 토큰은 같은 회원·같은 계열이다")
    void rotateMarksUsedAndKeepsFamily() {
        RefreshToken current = token();
        Instant nextExpiresAt = NOW.plusSeconds(60);

        RefreshToken next = current.rotate("hash-2", nextExpiresAt, NOW);

        assertThat(current.isUsed()).isTrue();
        assertThat(current.isUsableAt(NOW)).isFalse();
        assertThat(next.getUserId()).isEqualTo(42L);
        assertThat(next.getFamilyId()).isEqualTo("family-1");
        assertThat(next.getExpiresAt()).isEqualTo(nextExpiresAt);
        assertThat(next.isUsableAt(NOW)).isTrue();
    }

    @Test
    @DisplayName("이미 쓴 토큰은 다시 회전할 수 없다")
    void usedTokenCannotRotateAgain() {
        RefreshToken current = token();
        current.rotate("hash-2", EXPIRES_AT, NOW);

        assertThatThrownBy(() -> current.rotate("hash-3", EXPIRES_AT, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("만료 시각 직전까지 쓸 수 있고, 만료 시각이 되면 쓸 수 없다")
    void expiresExactlyAtExpiry() {
        RefreshToken token = token();

        assertThat(token.isUsableAt(EXPIRES_AT.minusNanos(1_000))).isTrue();
        assertThat(token.isExpiredAt(EXPIRES_AT)).isTrue();
        assertThat(token.isUsableAt(EXPIRES_AT)).isFalse();
        assertThatThrownBy(() -> token.rotate("hash-2", EXPIRES_AT.plusSeconds(60), EXPIRES_AT))
                .isInstanceOf(IllegalStateException.class);
    }
}
