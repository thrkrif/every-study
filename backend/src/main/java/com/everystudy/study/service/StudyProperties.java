package com.everystudy.study.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 스터디 정책 설정값 (ARCHITECTURE 4.9). 수치의 원본은 PRD 이고, PRD 에 없는 입력 단위는 PLAN D-21 에서 정했다.
 *
 * @param capacityMin              정원 하한, 주최자 포함 (PRD 4.2)
 * @param capacityMax              정원 상한, 주최자 포함 (PRD 4.2·4.14)
 * @param openableDays             개설 가능 범위. 오늘(KST)부터 며칠 뒤까지 고를 수 있나 (PRD 4.2)
 * @param startMinuteUnit          시작 시각의 분 단위 (10 이면 09:00, 09:10 …)
 * @param durationMinuteUnit       진행 시간의 분 단위
 * @param durationMinMinutes       진행 시간 최소값(분)
 * @param minAttendanceRateOptions 입장 기준 "최소 출석률"로 고를 수 있는 값(%). 기준 없음은 따로 고른다 (PRD 4.7)
 */
@Validated
@ConfigurationProperties("everystudy.study")
public record StudyProperties(
        @Min(2) @DefaultValue("2") int capacityMin,
        @Min(2) @DefaultValue("6") int capacityMax,
        @Min(0) @DefaultValue("14") int openableDays,
        @Min(1) @DefaultValue("10") int startMinuteUnit,
        @Min(1) @DefaultValue("10") int durationMinuteUnit,
        @Min(1) @DefaultValue("30") int durationMinMinutes,
        @NotEmpty @DefaultValue({"50", "60", "70", "80", "90", "100"}) List<Integer> minAttendanceRateOptions) {
}
