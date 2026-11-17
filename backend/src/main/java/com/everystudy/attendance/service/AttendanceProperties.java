package com.everystudy.attendance.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 출석 정책 설정값 (ARCHITECTURE 4.9). 값의 원본은 PRD 다. 체크인 시간창 등은 체크인 작업에서 더한다.
 *
 * @param checkinRadiusMeters 체크인을 받는 스터디 장소 반경, 미터 (PRD 4.6)
 */
@Validated
@ConfigurationProperties("everystudy.attendance")
public record AttendanceProperties(
        @Min(1) @DefaultValue("150") int checkinRadiusMeters) {
}
