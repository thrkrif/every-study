package com.everystudy.auth.web;

import com.everystudy.auth.service.IssuedTokens;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 토큰 쿠키를 쓴다 (ADR-0002 결정 2·확정 값). 둘 다 {@code HttpOnly; Secure; SameSite=Lax} 다.
 * 리프레시 쿠키는 인증 엔드포인트({@value #REFRESH_PATH})로만 보낸다. 갱신과 로그아웃이 이 값을 읽는다.
 */
@Component
class AuthCookies {

    static final String ACCESS = "access_token";
    static final String REFRESH = "refresh_token";
    static final String ACCESS_PATH = "/";
    static final String REFRESH_PATH = "/api/v1/auth";

    private final AuthWebProperties properties;

    AuthCookies(AuthWebProperties properties) {
        this.properties = properties;
    }

    void write(HttpServletResponse response, IssuedTokens tokens) {
        add(response, ACCESS, tokens.accessToken(), ACCESS_PATH, tokens.accessTtl());
        add(response, REFRESH, tokens.refreshToken(), REFRESH_PATH, tokens.refreshTtl());
    }

    private void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge);
        if (properties.cookieDomain() != null && !properties.cookieDomain().isBlank()) {
            cookie.domain(properties.cookieDomain());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }
}
