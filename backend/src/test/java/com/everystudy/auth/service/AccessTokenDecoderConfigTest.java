package com.everystudy.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.everystudy.common.time.TimeProvider;
import com.everystudy.support.TestClocks;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class AccessTokenDecoderConfigTest {

    // 테스트에서만 쓰는 지어낸 키다.
    private static final TokenProperties PROPERTIES = new TokenProperties(
            "unit-test-signing-key-0123456789abcdef", Duration.ofMinutes(15), Duration.ofDays(14));
    private static final String ISSUED_AT = "2026-11-24T05:00:00Z";

    private final String token =
            new AccessTokenIssuer(PROPERTIES, new TimeProvider(TestClocks.fixedAt(ISSUED_AT))).issue(42L, "USER");

    private static JwtDecoder decoderAt(String now) {
        return new AccessTokenDecoderConfig().accessTokenDecoder(PROPERTIES, TestClocks.fixedAt(now));
    }

    @Test
    @DisplayName("우리가 발급한 토큰에서 회원 ID 와 역할을 읽는다")
    void readsSubjectAndRole() {
        Jwt jwt = decoderAt(ISSUED_AT).decode(token);

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString(AccessTokenIssuer.ROLE_CLAIM)).isEqualTo("USER");
    }

    @Test
    @DisplayName("만료 1초 전까지 받아들이고, 만료 시각이 지나면 오차 허용 없이 거절한다")
    void rejectsRightAfterExpiry() {
        assertThat(decoderAt("2026-11-24T05:14:59Z").decode(token)).isNotNull();
        assertThatThrownBy(() -> decoderAt("2026-11-24T05:15:01Z").decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 거절한다")
    void rejectsTokenSignedWithAnotherKey() {
        TokenProperties other = new TokenProperties(
                "another-signing-key-0123456789abcdefgh", Duration.ofMinutes(15), Duration.ofDays(14));
        String forged = new AccessTokenIssuer(other, new TimeProvider(TestClocks.fixedAt(ISSUED_AT))).issue(42L, "ADMIN");

        assertThatThrownBy(() -> decoderAt(ISSUED_AT).decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("JWT 가 아닌 값은 거절한다")
    void rejectsGarbage() {
        assertThatThrownBy(() -> decoderAt(ISSUED_AT).decode("not-a-jwt")).isInstanceOf(JwtException.class);
    }
}
