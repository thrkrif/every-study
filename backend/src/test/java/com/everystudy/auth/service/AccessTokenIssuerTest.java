package com.everystudy.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.common.time.TimeProvider;
import com.everystudy.support.TestClocks;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccessTokenIssuerTest {

    // 테스트에서만 쓰는 지어낸 키다.
    private static final String KEY = "unit-test-signing-key-0123456789abcdef";
    private static final String NOW = "2026-11-24T05:00:00Z";

    private final AccessTokenIssuer issuer = new AccessTokenIssuer(
            new TokenProperties(KEY, Duration.ofMinutes(15), Duration.ofDays(14)),
            new TimeProvider(TestClocks.fixedAt(NOW)));

    @Test
    @DisplayName("회원 ID 는 sub, 역할은 role 클레임에 담는다")
    void putsUserIdAndRoleInClaims() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(issuer.issue(42L, "USER")).getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getStringClaim("role")).isEqualTo("USER");
    }

    @Test
    @DisplayName("만료는 발급 시각에서 설정한 수명(15분) 뒤다")
    void expiresAfterConfiguredTtl() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(issuer.issue(42L, "USER")).getJWTClaimsSet();

        assertThat(claims.getIssueTime()).isEqualTo(Date.from(Instant.parse(NOW)));
        assertThat(claims.getExpirationTime()).isEqualTo(Date.from(Instant.parse("2026-11-24T05:15:00Z")));
    }

    @Test
    @DisplayName("HS256 으로 서명하고, 같은 키로만 검증된다")
    void signsWithHs256() throws Exception {
        SignedJWT jwt = SignedJWT.parse(issuer.issue(42L, "USER"));

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        assertThat(jwt.verify(new MACVerifier(KEY.getBytes(StandardCharsets.UTF_8)))).isTrue();
        assertThat(jwt.verify(new MACVerifier("another-signing-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8))))
                .isFalse();
    }

    @Test
    @DisplayName("이메일 같은 개인정보는 토큰에 넣지 않는다")
    void containsOnlyExpectedClaims() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(issuer.issue(42L, "USER")).getJWTClaimsSet();

        assertThat(claims.getClaims().keySet()).containsExactlyInAnyOrder("sub", "role", "iat", "exp");
    }
}
