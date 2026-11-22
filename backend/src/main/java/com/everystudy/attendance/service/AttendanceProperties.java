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
 * @param codeMaxFailures       한 사람이 한 스터디에서 일회용 코드를 틀릴 수 있는 횟수. 이만큼 틀리면 그 뒤로는 맞아도 거부한다
 *                              (PLAN D-7·X-28)
 * @param appealWindowHours     노쇼 후보 통지 뒤 이의를 받는 시간 (PRD 4.9). 통지의 유효 기한에 쓴다 (PLAN X-30)
 * @param appealRejectLimit     이 횟수만큼 기각되면 이의를 제한한다 (PRD 4.9 악용 방지, PLAN D-11)
 * @param appealRejectWindowDays 기각 횟수를 세는 최근 기간, 일
 * @param appealRestrictDays    제한 기간, 일. 마지막 기각 결정부터 센다
 * @param appealResultNoticeDays 이의 결과 알림의 유효 기간, 일 (PLAN D-18 즉시 알림의 고정 기간)
 */
@Validated
@ConfigurationProperties("everystudy.attendance")
public record AttendanceProperties(
        @Min(1) @DefaultValue("150") int checkinRadiusMeters,
        @Min(0) @DefaultValue("10") int attendedWithinMinutes,
        @Min(1) @DefaultValue("5") int codeMaxFailures,
        @Min(1) @DefaultValue("48") int appealWindowHours,
        @Min(1) @DefaultValue("7") int appealResultNoticeDays,
        @Min(1) @DefaultValue("3") int appealRejectLimit,
        @Min(1) @DefaultValue("90") int appealRejectWindowDays,
        @Min(1) @DefaultValue("30") int appealRestrictDays) {
}
