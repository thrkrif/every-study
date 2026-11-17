package com.everystudy.user.service;

/**
 * 활동 중인 회원의 인증용 요약.
 *
 * @param role 역할 이름 ({@code USER}, {@code ADMIN})
 */
public record ActiveUser(long userId, String role) {
}
