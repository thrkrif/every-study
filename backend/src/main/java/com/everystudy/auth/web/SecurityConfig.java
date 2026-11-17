package com.everystudy.auth.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 보안 필터 구성 (ADR-0002).
 *
 * <p>지금은 소셜 로그인과 토큰 쿠키 발급까지다 (PLAN T-004). 쿠키의 액세스 토큰으로 요청을 인증하는 것,
 * 경로별 권한, CSRF(SPA 방식)·CORS·{@code Origin} 검사, 갱신·로그아웃은 T-005 에서 더한다.
 * 그때까지 보호할 API 가 없어 모든 요청을 허용한다.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SocialLoginSuccessHandler successHandler,
            SocialLoginFailureHandler failureHandler,
            AuthWebProperties properties) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                // 로그인 상태는 쿠키의 토큰이 나타낸다. 서버 세션에 인증을 저장하지 않는다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                // 로그아웃은 /api/v1/auth/logout 으로 직접 만든다 (T-005). 기본 /logout 은 쓰지 않는다.
                .logout(AbstractHttpConfigurer::disable)
                .oauth2Login(oauth2 -> oauth2
                        // 로그인 화면은 프론트에 있다. 지정하지 않으면 Spring Security 기본 로그인 화면이 생긴다.
                        .loginPage(properties.loginUrl())
                        .authorizedClientRepository(new NoOpAuthorizedClientRepository())
                        .successHandler(successHandler)
                        .failureHandler(failureHandler));
        return http.build();
    }
}
