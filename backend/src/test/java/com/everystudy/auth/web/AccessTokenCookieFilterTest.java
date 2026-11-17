package com.everystudy.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.common.security.LoginUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

class AccessTokenCookieFilterTest {

    private static final String VALID = "valid-token";

    /** 서명·만료 검증은 {@code AccessTokenDecoderConfigTest} 가 본다. 여기서는 검증 결과에 따른 동작만 본다. */
    private static final JwtDecoder DECODER = token -> {
        if (!VALID.equals(token)) {
            throw new BadJwtException("만료 또는 위조");
        }
        return jwt("42", "ADMIN");
    };
    private static final RequestMatcher IGNORED = request -> request.getRequestURI().startsWith("/api/v1/auth/");
    private static final AuthenticationEntryPoint ENTRY_POINT =
            (request, response, ex) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<Authentication> seenByNextFilter = new AtomicReference<>();
    private boolean nextFilterCalled;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void filter(JwtDecoder decoder, String path, String accessCookie) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (accessCookie != null) {
            request.setCookies(new Cookie("access_token", accessCookie));
        }
        new AccessTokenCookieFilter(decoder, IGNORED, ENTRY_POINT).doFilter(request, response,
                new MockFilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                        nextFilterCalled = true;
                        seenByNextFilter.set(SecurityContextHolder.getContext().getAuthentication());
                    }
                });
    }

    @Test
    @DisplayName("유효한 액세스 쿠키면 회원 ID 와 역할을 가진 로그인 회원으로 인증한다")
    void validCookieAuthenticates() throws Exception {
        filter(DECODER, "/api/v1/me", VALID);

        Authentication authentication = seenByNextFilter.get();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(new LoginUser(42L, "ADMIN"));
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("쿠키가 없으면 인증 없이 다음으로 넘긴다")
    void noCookiePassesThroughUnauthenticated() throws Exception {
        filter(DECODER, "/api/v1/me", null);

        assertThat(nextFilterCalled).isTrue();
        assertThat(seenByNextFilter.get()).isNull();
    }

    @Test
    @DisplayName("만료·위조된 쿠키면 401 로 끝내고 다음으로 넘기지 않는다")
    void invalidCookieStopsWith401() throws Exception {
        filter(DECODER, "/api/v1/me", "expired-or-forged");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(nextFilterCalled).isFalse();
    }

    @Test
    @DisplayName("토큰을 보지 않는 경로에서는 만료된 쿠키가 있어도 그대로 넘긴다")
    void ignoredPathDoesNotLookAtCookie() throws Exception {
        filter(DECODER, "/api/v1/auth/refresh", "expired-or-forged");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(nextFilterCalled).isTrue();
        assertThat(seenByNextFilter.get()).isNull();
    }

    @Test
    @DisplayName("회원 ID 가 숫자가 아니거나 역할이 없는 토큰은 401 이다")
    void malformedClaimsAreRejected() throws Exception {
        filter(token -> jwt("not-a-number", "USER"), "/api/v1/me", VALID);
        assertThat(response.getStatus()).isEqualTo(401);

        MockHttpServletResponse second = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.setCookies(new Cookie("access_token", VALID));
        new AccessTokenCookieFilter(token -> jwt("42", null), IGNORED, ENTRY_POINT)
                .doFilter(request, second, new MockFilterChain());
        assertThat(second.getStatus()).isEqualTo(401);
    }

    private static Jwt jwt(String subject, String role) {
        Jwt.Builder builder = Jwt.withTokenValue(VALID)
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(Instant.parse("2026-11-24T05:00:00Z"))
                .expiresAt(Instant.parse("2026-11-24T05:15:00Z"));
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.build();
    }
}
