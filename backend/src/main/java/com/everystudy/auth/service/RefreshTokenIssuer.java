package com.everystudy.auth.service;

import com.everystudy.auth.domain.RefreshToken;
import com.everystudy.auth.repository.RefreshTokenRepository;
import com.everystudy.common.time.TimeProvider;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰을 만든다 (ADR-0002 결정 2). 값은 의미 없는 랜덤 256비트이고, DB 에는 해시만 저장한다.
 * 원문은 이 메서드의 반환값으로만 존재한다.
 */
@Component
class RefreshTokenIssuer {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final RefreshTokenRepository refreshTokens;
    private final TokenProperties properties;
    private final TimeProvider time;

    RefreshTokenIssuer(RefreshTokenRepository refreshTokens, TokenProperties properties, TimeProvider time) {
        this.refreshTokens = refreshTokens;
        this.properties = properties;
        this.time = time;
    }

    /** 로그인 한 번에 새 계열을 시작한다. 호출한 쪽의 트랜잭션 안에서 저장된다. */
    String issueForLogin(long userId) {
        String token = randomToken();
        refreshTokens.save(RefreshToken.startFamily(
                userId,
                UUID.randomUUID().toString(),
                TokenHasher.sha256Hex(token),
                time.now().plus(properties.refreshTtl())));
        return token;
    }

    /**
     * 쓴 토큰을 같은 계열의 새 토큰으로 바꾼다. 새 토큰의 수명은 지금부터 다시 센다 (ADR-0002 확정 값).
     * 호출한 쪽의 트랜잭션 안에서 저장된다.
     */
    String rotate(RefreshToken current) {
        String token = randomToken();
        Instant now = time.now();
        refreshTokens.save(current.rotate(TokenHasher.sha256Hex(token), now.plus(properties.refreshTtl()), now));
        return token;
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
