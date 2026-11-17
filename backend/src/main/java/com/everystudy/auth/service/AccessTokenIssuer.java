package com.everystudy.auth.service;

import com.everystudy.common.time.TimeProvider;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰(JWT, HS256)을 만든다 (ADR-0002 결정 2).
 * 클레임: {@code sub}=회원 ID, {@code role}=역할, {@code iat}, {@code exp}.
 */
@Component
class AccessTokenIssuer {

    static final String ROLE_CLAIM = "role";

    private final JwtEncoder encoder;
    private final TokenProperties properties;
    private final TimeProvider time;

    AccessTokenIssuer(TokenProperties properties, TimeProvider time) {
        SecretKey key = new SecretKeySpec(properties.signingKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        this.properties = properties;
        this.time = time;
    }

    String issue(long userId, String role) {
        Instant now = time.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(userId))
                .claim(ROLE_CLAIM, role)
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTtl()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
