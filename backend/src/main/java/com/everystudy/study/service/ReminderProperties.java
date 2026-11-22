package com.everystudy.study.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 일정 알림 시점 설정값 (PRD 4.5, PLAN T-040). 체크인 오픈 시점은 {@link StudyProperties} 를 따른다.
 *
 * @param dayBeforeHours 하루 전 알림 = 시작 몇 시간 전 (PRD 4.5 "전날")
 * @param hourBeforeMinutes 1시간 전 알림 = 시작 몇 분 전
 */
@Validated
@ConfigurationProperties("everystudy.study.reminder")
public record ReminderProperties(
        @Min(2) @DefaultValue("24") int dayBeforeHours,
        @Min(1) @DefaultValue("60") int hourBeforeMinutes) {
}
