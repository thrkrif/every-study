package com.everystudy.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/** 소셜 로그인 실패(사용자가 동의 취소, state 불일치, 공급자 오류) 시 쿠키 없이 프론트 로그인 화면으로 보낸다. */
@Component
class SocialLoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(SocialLoginFailureHandler.class);

    private final AuthWebProperties properties;

    SocialLoginFailureHandler(AuthWebProperties properties) {
        this.properties = properties;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        // 오류 코드만 남긴다. 설명(description)에는 공급자가 준 값이 섞일 수 있다.
        String code = exception instanceof OAuth2AuthenticationException oauth
                ? oauth.getError().getErrorCode()
                : exception.getClass().getSimpleName();
        log.warn("소셜 로그인 실패: {}", code);

        SocialLoginSuccessHandler.endHandshakeSession(request);
        response.sendRedirect(properties.loginFailedUrl());
    }
}
