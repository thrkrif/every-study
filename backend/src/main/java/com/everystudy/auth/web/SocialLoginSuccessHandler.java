package com.everystudy.auth.web;

import com.everystudy.auth.service.LoginResult;
import com.everystudy.auth.service.SocialLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인 성공 시 자체 토큰을 쿠키로 내려주고 프론트로 돌려보낸다 (ADR-0002 결정 1, API 명세 2장).
 * 온보딩을 마치지 않은 회원(신규 포함)은 온보딩 화면으로 보낸다.
 */
@Component
class SocialLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(SocialLoginSuccessHandler.class);

    private final SocialLoginService loginService;
    private final AuthCookies cookies;
    private final AuthWebProperties properties;

    SocialLoginSuccessHandler(SocialLoginService loginService, AuthCookies cookies, AuthWebProperties properties) {
        this.loginService = loginService;
        this.cookies = cookies;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String target;
        try {
            SocialProfile profile = SocialProfileExtractor.extract(
                    token.getAuthorizedClientRegistrationId(), token.getPrincipal().getAttributes());
            LoginResult result = loginService.login(
                    profile.provider(), profile.providerUserId(), profile.verifiedEmail());
            cookies.write(response, result.tokens());
            target = result.onboarded() ? properties.homeUrl() : properties.onboardingUrl();
        } catch (IllegalArgumentException e) {
            // 공급자 응답에 식별값이 없는 경우. 응답 내용은 개인정보라 로그에 남기지 않는다.
            log.warn("소셜 로그인 응답을 처리하지 못함: {}", e.getMessage());
            target = properties.loginFailedUrl();
        }
        endHandshakeSession(request);
        response.sendRedirect(target);
    }

    /** 세션은 OAuth2 인가 요청(state)을 콜백까지 들고 있는 용도뿐이다. 로그인 상태는 쿠키의 토큰이 나타낸다. */
    static void endHandshakeSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
