package com.everystudy.auth.service;

import com.everystudy.user.service.SocialUser;
import com.everystudy.user.service.SocialUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소셜 로그인 성공 뒤의 처리: 회원을 찾거나 만들고 자체 토큰을 발급한다 (ADR-0002 결정 1·2). */
@Service
public class SocialLoginService {

    private final SocialUserService socialUsers;
    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenIssuer refreshTokens;
    private final TokenProperties properties;

    SocialLoginService(SocialUserService socialUsers, AccessTokenIssuer accessTokens,
            RefreshTokenIssuer refreshTokens, TokenProperties properties) {
        this.socialUsers = socialUsers;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.properties = properties;
    }

    /**
     * 회원 생성과 리프레시 토큰 저장은 한 트랜잭션이다 (토큰 저장이 실패하면 회원도 만들어지지 않는다).
     *
     * @param verifiedEmail 공급자가 확인한 이메일. 없으면 {@code null}
     */
    @Transactional
    public LoginResult login(String provider, String providerUserId, String verifiedEmail) {
        SocialUser user = socialUsers.findOrRegister(provider, providerUserId, verifiedEmail);

        IssuedTokens tokens = new IssuedTokens(
                accessTokens.issue(user.userId(), user.role()),
                properties.accessTtl(),
                refreshTokens.issueForLogin(user.userId()),
                properties.refreshTtl());
        return new LoginResult(tokens, user.onboarded());
    }
}
