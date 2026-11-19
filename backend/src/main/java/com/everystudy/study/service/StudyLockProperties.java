package com.everystudy.study.service;

import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code study} 행 락 설정 (ADR-0004 결정 5). 정책 수치가 아닌 기술 파라미터라 기본값은 ARCHITECTURE 4.2 가 원본이다.
 *
 * @param timeout 락 대기 시간. 넘으면 {@code 409 LOCK_TIMEOUT}. MySQL 은 초 단위로만 받으므로 초 단위로 적는다
 */
@Validated
@ConfigurationProperties("everystudy.study.lock")
public record StudyLockProperties(
        @DurationMin(seconds = 1) @DefaultValue("3s") Duration timeout) {
}
