package com.everystudy.attendance.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 출석 정책 설정값 (ARCHITECTURE 4.9). 값의 원본은 PRD 다. 체크인 가능 시간(시작 10분 전~30분 뒤)은 스터디 설정
 * ({@code everystudy.study.checkin-*}) 이 갖는다.
 *
 * @param checkinRadiusMeters   체크인을 받는 스터디 장소 반경, 미터 (PRD 4.6)
 * @param attendedWithinMinutes 시작 후 몇 분까지의 체크인이 출석인가. 그 뒤 마감까지는 지각 (PRD 4.6)
 */
@Validated
@ConfigurationProperties("everystudy.attendance")
public record AttendanceProperties(
        @Min(1) @DefaultValue("150") int checkinRadiusMeters,
        @Min(0) @DefaultValue("10") int attendedWithinMinutes) {
}
