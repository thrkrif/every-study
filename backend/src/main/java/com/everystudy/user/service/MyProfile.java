package com.everystudy.user.service;

import java.time.Instant;

/**
 * 내 정보 (API 3장 {@code GET /me}). 온보딩 전이면 닉네임·직군·지역이 {@code null} 이다.
 *
 * @param jobCode   직군 대분류 또는 세부 직무 코드
 * @param onboarded 닉네임·직군·지역을 모두 정했는가
 * @param joinedAt  가입 시각 (프로필의 가입일)
 */
public record MyProfile(
        long id,
        String nickname,
        String jobCode,
        String districtCode,
        String role,
        boolean onboarded,
        Instant joinedAt) {
}
