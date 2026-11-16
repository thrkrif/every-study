package com.everystudy.user.service;

/**
 * 소셜 로그인으로 확인된 회원. 다른 모듈(auth)이 토큰을 발급할 때 쓴다.
 *
 * @param role      역할 이름 ({@code USER}, {@code ADMIN})
 * @param onboarded 온보딩(닉네임·직군·지역)을 마쳤는가
 */
public record SocialUser(long userId, String role, boolean onboarded) {
}
