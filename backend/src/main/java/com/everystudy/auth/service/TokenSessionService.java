package com.everystudy.auth.service;

import com.everystudy.auth.domain.RefreshToken;
import com.everystudy.auth.repository.RefreshTokenRepository;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.user.service.ActiveUser;
import com.everystudy.user.service.UserAccountService;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 뒤의 토큰 수명 관리: 갱신(회전)과 로그아웃 (ADR-0002 결정 2·6). */
@Service
public class TokenSessionService {

    private static final Logger log = LoggerFactory.getLogger(TokenSessionService.class);

    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final AccessTokenIssuer accessTokens;
    private final UserAccountService users;
    private final TokenProperties properties;
    private final TimeProvider time;

    TokenSessionService(RefreshTokenRepository refreshTokens, RefreshTokenIssuer refreshTokenIssuer,
            AccessTokenIssuer accessTokens, UserAccountService users, TokenProperties properties,
            TimeProvider time) {
        this.refreshTokens = refreshTokens;
        this.refreshTokenIssuer = refreshTokenIssuer;
        this.accessTokens = accessTokens;
        this.users = users;
        this.properties = properties;
        this.time = time;
    }

    /**
     * 리프레시 토큰을 새 토큰 한 쌍으로 바꾼다. 쓴 토큰은 다시 쓸 수 없다.
     *
     * <p>이미 쓴 토큰이 다시 오면 탈취로 보고 같은 계열을 모두 폐기한다. 거절을 예외가 아니라 빈 값으로 돌려주는
     * 이유는, 예외를 던지면 트랜잭션이 되돌려져 그 폐기가 저장되지 않기 때문이다.
     *
     * @param refreshToken 쿠키로 받은 원문. 없으면 {@code null}
     * @return 새 토큰. 토큰이 없거나, 모르는 값이거나, 만료·폐기·재사용이거나, 회원이 탈퇴했으면 빈 값
     */
    @Transactional
    public Optional<IssuedTokens> refresh(String refreshToken) {
        Optional<RefreshToken> found = find(refreshToken);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken current = found.get();
        Instant now = time.now();

        if (current.isUsed() && !current.isRevoked()) {
            int revoked = refreshTokens.revokeFamily(current.getFamilyId(), now);
            log.warn("리프레시 토큰 재사용 감지. 계열을 폐기함: userId={}, familyId={}, revoked={}",
                    current.getUserId(), current.getFamilyId(), revoked);
            return Optional.empty();
        }
        if (!current.isUsableAt(now)) {
            return Optional.empty();
        }

        Optional<ActiveUser> user = users.findActive(current.getUserId());
        if (user.isEmpty()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            return Optional.empty();
        }

        return Optional.of(new IssuedTokens(
                accessTokens.issue(user.get().userId(), user.get().role()),
                properties.accessTtl(),
                refreshTokenIssuer.rotate(current),
                properties.refreshTtl()));
    }

    /**
     * 이 토큰이 속한 계열을 모두 폐기한다 (이 로그인만 끝낸다. 다른 기기의 로그인은 다른 계열이라 남는다).
     * 토큰이 없거나 모르는 값이면 아무 일도 하지 않는다.
     */
    @Transactional
    public void logout(String refreshToken) {
        find(refreshToken).ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), time.now()));
    }

    private Optional<RefreshToken> find(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokens.findByTokenHash(TokenHasher.sha256Hex(refreshToken));
    }
}
