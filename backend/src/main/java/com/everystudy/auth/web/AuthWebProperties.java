package com.everystudy.auth.web;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 로그인 뒤 돌아갈 프론트 주소와 쿠키 속성 (ADR-0002 결정 4·확정 값).
 *
 * @param frontendUrl  프론트 오리진 (예: {@code http://localhost:3000}). 끝에 {@code /} 를 붙이지 않는다
 * @param cookieSecure 쿠키의 {@code Secure} 속성. HTTPS 가 아닌 로컬에서만 끈다
 * @param cookieDomain 쿠키의 {@code Domain}. 비우면 응답한 호스트에만 묶인다 (배포 도메인이 정해지면 설정)
 */
@Validated
@ConfigurationProperties("everystudy.auth.web")
public record AuthWebProperties(
        @NotBlank String frontendUrl,
        @DefaultValue("true") boolean cookieSecure,
        String cookieDomain) {

    /** 온보딩을 마친 회원이 로그인 뒤 가는 곳. */
    String homeUrl() {
        return frontendUrl + "/";
    }

    /** 온보딩 전 회원이 로그인 뒤 가는 곳 (PRD 5장 화면 2). */
    String onboardingUrl() {
        return frontendUrl + "/onboarding";
    }

    String loginUrl() {
        return frontendUrl + "/login";
    }

    /** 소셜 로그인이 실패했을 때 가는 곳. 화면은 {@code error} 값으로 안내 문구를 정한다. */
    String loginFailedUrl() {
        return loginUrl() + "?error=social_login_failed";
    }
}
