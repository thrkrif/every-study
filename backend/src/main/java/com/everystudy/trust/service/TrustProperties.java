package com.everystudy.trust.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 신뢰 지표·제재 정책 설정값 (ARCHITECTURE 4.9). 값의 원본은 PRD 다. 제재 단계는 제재 작업(T-035a)에서 더한다.
 *
 * @param newcomerBelowSessions 집계 대상 세션이 이 값보다 적으면 신규다 (PRD 4.7 신규 배지)
 * @param hostLeaveWindowMinutes 시작 몇 분 전부터 주최자가 나가면 감점인가 (PRD 4.13B 3시간). 그보다 일찍은 감점 없음
 * @param hostLeavePenaltyPoints 주최자 이탈 감점 (PRD 4.13B -1점)
 * @param recentSessions 출석률을 계산할 최근 세션 수 (PRD 4.7 최근 10회). 분모에 드는 세션만 센다
 */
@Validated
@ConfigurationProperties("everystudy.trust")
public record TrustProperties(
        @Min(1) @DefaultValue("3") int newcomerBelowSessions,
        @Min(1) @DefaultValue("180") int hostLeaveWindowMinutes,
        @Min(1) @DefaultValue("1") int hostLeavePenaltyPoints,
        @Min(1) @DefaultValue("10") int recentSessions) {
}
