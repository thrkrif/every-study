package com.everystudy.user.service;

/**
 * 다른 회원에게 보여도 되는 회원 정보 (PRD 4.1 프로필 공개 정보). 이메일 같은 개인정보는 없다.
 *
 * @param nickname 탈퇴했거나 온보딩 전이면 {@code null}. 화면은 "탈퇴한 사용자" 등으로 표시한다
 * @param jobCode  직군 대분류 또는 세부 직무 코드. 없으면 {@code null}
 */
public record PublicUser(long userId, String nickname, String jobCode) {
}
