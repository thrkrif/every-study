package com.everystudy.user.service;

import java.time.Instant;

/**
 * 공개 프로필의 회원 정보 (PRD 4.1 프로필 공개 정보). 이메일 같은 개인정보는 없다.
 *
 * @param nickname     탈퇴한 회원이면 {@code null}. 화면은 "탈퇴한 사용자"로 표시한다
 * @param jobCode      탈퇴한 회원이면 {@code null}
 * @param districtCode 탈퇴한 회원이면 {@code null}
 */
public record PublicProfile(long userId, String nickname, String jobCode, String districtCode, Instant joinedAt) {
}
