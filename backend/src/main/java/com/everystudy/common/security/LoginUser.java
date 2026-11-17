package com.everystudy.common.security;

/**
 * 요청을 보낸 로그인 회원. 액세스 토큰이 유효한 요청의 인증 주체(principal)다.
 * 컨트롤러는 {@code @AuthenticationPrincipal LoginUser} 로 받는다.
 *
 * @param userId 회원 ID
 * @param role   역할 이름 ({@code USER}, {@code ADMIN}). 토큰을 발급한 시점의 값이다
 */
public record LoginUser(long userId, String role) {
}
