package com.everystudy.trust.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 신뢰 지표·제재 정책 설정값 (ARCHITECTURE 4.9). 값의 원본은 PRD 다. 최근 N회·제재 단계는 집계·제재 작업(M3)에서 더한다.
 *
 * @param newcomerBelowSessions 집계 대상 세션이 이 값보다 적으면 신규다 (PRD 4.7 신규 배지)
 */
@Validated
@ConfigurationProperties("everystudy.trust")
public record TrustProperties(
        @Min(1) @DefaultValue("3") int newcomerBelowSessions) {
}
