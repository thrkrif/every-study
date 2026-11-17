package com.everystudy.study.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 스터디 정책 설정값 (ARCHITECTURE 4.9). 값의 원본은 PRD 다.
 *
 * @param capacityMin  정원 하한, 주최자 포함 (PRD 4.2)
 * @param capacityMax  정원 상한, 주최자 포함 (PRD 4.2·4.14)
 * @param openableDays 개설 가능 범위. 오늘부터 며칠 뒤까지 고를 수 있나 (PRD 4.2)
 */
@Validated
@ConfigurationProperties("everystudy.study")
public record StudyProperties(
        @Min(2) @DefaultValue("2") int capacityMin,
        @Min(2) @DefaultValue("6") int capacityMax,
        @Min(0) @DefaultValue("14") int openableDays) {
}
