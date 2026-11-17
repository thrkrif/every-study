package com.everystudy.auth.web;

import com.everystudy.auth.service.AccessTokenIssuer;
import com.everystudy.common.security.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

/**
 * 쿠키의 액세스 토큰으로 요청을 인증한다 (ADR-0002 결정 2). {@code Authorization} 헤더는 보지 않는다.
 *
 * <ul>
 *   <li>쿠키가 없으면 비로그인 요청으로 넘긴다 (권한 검사가 뒤에서 판단한다).</li>
 *   <li>쿠키가 있는데 만료됐거나 서명이 맞지 않으면 {@code 401} 로 끝낸다. 클라이언트는 갱신 뒤 다시 요청한다.</li>
 *   <li>토큰 없이 동작해야 하는 경로(갱신, 로그아웃, 소셜 로그인 등)에서는 쿠키를 보지 않는다.
 *       만료된 액세스 쿠키가 갱신 자체를 막으면 안 되기 때문이다.</li>
 * </ul>
 *
 * <p>Spring Security 의 리소스 서버 설정({@code oauth2ResourceServer})을 쓰지 않는 이유: 그 설정은 토큰이 실린
 * 요청의 CSRF 검사를 끈다. 헤더로 보내는 토큰에는 맞지만, 브라우저가 자동으로 붙이는 쿠키에는 CSRF 검사가 필요하다.
 */
class AccessTokenCookieFilter extends OncePerRequestFilter {

    private final JwtDecoder decoder;
    private final RequestMatcher tokenIgnoredPaths;
    private final AuthenticationEntryPoint entryPoint;

    AccessTokenCookieFilter(JwtDecoder decoder, RequestMatcher tokenIgnoredPaths, AuthenticationEntryPoint entryPoint) {
        this.decoder = decoder;
        this.tokenIgnoredPaths = tokenIgnoredPaths;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie cookie = WebUtils.getCookie(request, AuthCookies.ACCESS);
        if (cookie == null || tokenIgnoredPaths.matches(request)) {
            chain.doFilter(request, response);
            return;
        }

        Authentication authentication;
        try {
            authentication = authenticate(cookie.getValue());
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new BadCredentialsException("액세스 토큰이 유효하지 않음", ex));
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    private Authentication authenticate(String token) {
        Jwt jwt = decoder.decode(token);
        String role = jwt.getClaimAsString(AccessTokenIssuer.ROLE_CLAIM);
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("역할 클레임이 없음");
        }
        LoginUser user = new LoginUser(Long.parseLong(jwt.getSubject()), role);
        return UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
