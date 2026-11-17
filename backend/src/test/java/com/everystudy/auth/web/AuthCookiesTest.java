package com.everystudy.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.auth.service.IssuedTokens;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCookiesTest {

    private static final IssuedTokens TOKENS =
            new IssuedTokens("access-value", Duration.ofMinutes(15), "refresh-value", Duration.ofDays(14));

    private static MockHttpServletResponse write(AuthWebProperties properties) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AuthCookies(properties).write(response, TOKENS);
        return response;
    }

    @Test
    @DisplayName("액세스 쿠키는 HttpOnly, Secure, SameSite=Lax 이고 전체 경로에 15분 동안 유효하다")
    void accessCookieAttributes() {
        MockCookie cookie = (MockCookie) write(new AuthWebProperties("http://localhost:3000", true, null))
                .getCookie("access_token");

        assertThat(cookie.getValue()).isEqualTo("access-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(15 * 60);
        assertThat(cookie.getDomain()).isNull();
    }

    @Test
    @DisplayName("리프레시 쿠키는 인증 경로로만 보내고 14일 동안 유효하다")
    void refreshCookieAttributes() {
        MockCookie cookie = (MockCookie) write(new AuthWebProperties("http://localhost:3000", true, null))
                .getCookie("refresh_token");

        assertThat(cookie.getValue()).isEqualTo("refresh-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(14 * 24 * 60 * 60);
    }

    @Test
    @DisplayName("Domain 을 설정하면 두 쿠키에 모두 붙는다")
    void domainIsAppliedWhenConfigured() {
        MockHttpServletResponse response = write(new AuthWebProperties("https://app.example.com", true, "example.com"));

        assertThat(response.getCookies()).extracting(Cookie::getDomain).containsOnly("example.com");
    }

    @Test
    @DisplayName("Secure 는 설정으로만 끌 수 있고, 꺼도 HttpOnly 는 남는다")
    void secureCanBeRelaxedOnlyByConfiguration() {
        MockHttpServletResponse response = write(new AuthWebProperties("http://localhost:3000", false, null));

        assertThat(response.getCookies()).allSatisfy(cookie -> {
            assertThat(cookie.getSecure()).isFalse();
            assertThat(cookie.isHttpOnly()).isTrue();
        });
    }

    @Test
    @DisplayName("지울 때는 쓸 때와 같은 이름·경로·도메인에 수명 0 으로 보낸다")
    void clearExpiresBothCookiesWithSameScope() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AuthCookies(new AuthWebProperties("https://app.example.com", true, "example.com")).clear(response);

        MockCookie access = (MockCookie) response.getCookie("access_token");
        MockCookie refresh = (MockCookie) response.getCookie("refresh_token");
        assertThat(access.getPath()).isEqualTo("/");
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth");
        assertThat(response.getCookies()).allSatisfy(cookie -> {
            assertThat(cookie.getValue()).isEmpty();
            assertThat(cookie.getMaxAge()).isZero();
            assertThat(cookie.getDomain()).isEqualTo("example.com");
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getSecure()).isTrue();
        });
    }
}
