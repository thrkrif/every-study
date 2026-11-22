package com.everystudy.auth.web;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 보안 필터 구성 (ADR-0002).
 *
 * <ul>
 *   <li><b>인증</b>: 쿠키의 액세스 토큰 ({@link AccessTokenCookieFilter}). 서버 세션에 인증을 저장하지 않는다.</li>
 *   <li><b>권한</b>: 기본은 로그인 필요다. 비회원에게 여는 경로는 여기에 하나씩 적는다 (빠뜨리면 {@code 401} 이 나는 쪽이 안전하다).</li>
 *   <li><b>CSRF</b>: 이중 제출 쿠키. 상태 변경 요청은 {@code XSRF-TOKEN} 쿠키 값을 {@code X-XSRF-TOKEN} 헤더로 보내야 한다.</li>
 *   <li><b>CORS</b>: 프론트 오리진 하나만 허용하고 쿠키를 포함한다. 다른 {@code Origin} 의 요청은 {@code 403} 으로 거절된다.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    /**
     * 액세스 토큰 없이 동작하는 경로. 누구에게나 열려 있고, 액세스 쿠키가 실려 와도 보지 않는다.
     * 로그인한 회원에게 다른 응답을 주는 공개 API(예: 스터디 상세)는 여기가 아니라 권한 규칙에만 추가한다.
     */
    private static final String[] TOKEN_IGNORED_PATHS = {
            "/oauth2/**",
            "/login/oauth2/**",
            AuthController.BASE_PATH + "/**",
            // 코드 목록과 클라이언트용 설정값 (API 4장)
            "/api/v1/meta/**",
            // API 문서. 운영에서는 springdoc 을 꺼서 404 다 (PLAN D-19).
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error",
    };

    /** 비회원에게도 여는 조회 경로 (API 권한 표의 "공개"). 하나씩 적는다. */
    private static final String[] PUBLIC_READ_PATHS = {
            "/api/v1/studies",
            "/api/v1/studies/{id}",
            "/api/v1/users/{id}",
    };

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SocialLoginSuccessHandler successHandler,
            SocialLoginFailureHandler failureHandler,
            SecurityProblemHandler problemHandler,
            CsrfTokenRepository csrfTokenRepository,
            JwtDecoder accessTokenDecoder,
            AuthWebProperties properties) throws Exception {
        RequestMatcher tokenIgnoredPaths = tokenIgnoredPaths();
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(tokenIgnoredPaths).permitAll()
                        // 공개 API. 로그인했으면 액세스 쿠키를 읽어 그 회원에게 맞춘 값(myState 등)을 준다.
                        .requestMatchers(HttpMethod.GET, PUBLIC_READ_PATHS).permitAll()
                        .anyRequest().authenticated())
                // 로그인 상태는 쿠키의 토큰이 나타낸다. 서버 세션에 인증을 저장하지 않는다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                // 로그아웃은 AuthController 가 한다. 기본 /logout 은 쓰지 않는다.
                .logout(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                // spa(): 쿠키의 값을 그대로 헤더로 보내는 방식을 받고, CSRF 쿠키가 없는 요청이면 응답에 새 쿠키를 내려준다
                // (조회 요청 포함). 저장소는 쿠키 속성을 맞춘 것으로 바꾼다.
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .addFilterBefore(
                        new AccessTokenCookieFilter(accessTokenDecoder, tokenIgnoredPaths, problemHandler),
                        AuthorizationFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        // 로그인 화면은 프론트에 있다. 지정하지 않으면 Spring Security 기본 로그인 화면이 생긴다.
                        .loginPage(properties.loginUrl())
                        .authorizedClientRepository(new NoOpAuthorizedClientRepository())
                        .successHandler(successHandler)
                        .failureHandler(failureHandler));
        return http.build();
    }

    /**
     * CSRF 토큰 쿠키({@code XSRF-TOKEN}). 프론트의 JS 가 읽어 헤더로 보내야 하므로 {@code HttpOnly} 가 아니다.
     * 프론트와 API 의 호스트가 다르면(app.*, api.*) JS 가 읽을 수 있게 토큰 쿠키와 같은 {@code Domain} 을 준다.
     */
    @Bean
    CsrfTokenRepository csrfTokenRepository(AuthWebProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> {
            cookie.secure(properties.cookieSecure()).sameSite("Lax");
            if (properties.cookieDomain() != null && !properties.cookieDomain().isBlank()) {
                cookie.domain(properties.cookieDomain());
            }
        });
        return repository;
    }

    /** 프론트 오리진 하나만 허용한다 (ADR-0002 결정 4). 쿠키를 주고받으므로 와일드카드는 쓸 수 없다. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AuthWebProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(properties.frontendUrl()));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        cors.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, "X-XSRF-TOKEN"));
        cors.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    private static RequestMatcher tokenIgnoredPaths() {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(List.of(TOKEN_IGNORED_PATHS).stream()
                .map(paths::matcher)
                .map(RequestMatcher.class::cast)
                .toList());
    }
}
