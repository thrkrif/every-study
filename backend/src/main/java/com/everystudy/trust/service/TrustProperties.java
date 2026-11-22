package com.everystudy.trust.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 신뢰 지표·제재 정책 설정값 (ARCHITECTURE 4.9). 값의 원본은 PRD 다. 제재 단계(PRD 4.8)는 T-035a 가 더했다.
 *
 * @param newcomerBelowSessions 집계 대상 세션이 이 값보다 적으면 신규다 (PRD 4.7 신규 배지)
 * @param hostLeaveWindowMinutes 시작 몇 분 전부터 주최자가 나가면 감점인가 (PRD 4.13B 3시간). 그보다 일찍은 감점 없음
 * @param hostLeavePenaltyPoints 주최자 이탈 감점 (PRD 4.13B -1점)
 * @param recentSessions 출석률을 계산할 최근 세션 수 (PRD 4.7 최근 10회). 분모에 드는 세션만 센다
 * @param hostCountOnlyBelow 주최 횟수가 이 값보다 적으면 비율 대신 건수만 보여 준다 (PRD 4.13D 5회 미만)
 * @param sanctionShortWindow 제한·경고를 판정하는 최근 세션 수 (PRD 4.8 최근 5회)
 * @param sanctionLongWindow 정지를 판정하는 최근 세션 수 (PRD 4.8 최근 10회)
 * @param lateAsNoShow 지각 몇 회를 노쇼 1회로 치는가. 지각만 이만큼 쌓이면 경고다 (PRD 4.8 환산 노쇼, 3회)
 * @param restrictConvertedNoShows 짧은 창의 환산 노쇼가 이 값 이상이면 참여 신청 제한 (PRD 4.8, 2회)
 * @param suspendConvertedNoShows 긴 창의 환산 노쇼가 이 값 이상이면 이용 정지 (PRD 4.8, 4회)
 * @param joinRestrictDays 참여 신청 제한 기간, 일 (PRD 4.8, 7일)
 * @param suspendDays 이용 정지 기간, 일 (PRD 4.8, 14일)
 * @param hostPenaltyWindowDays 주최자 감점을 합산하는 기간, 일 (PRD 4.13B 90일 롤링)
 * @param hostWarnPoints 합계가 이 값 이상이면 주최자 경고 (PRD 4.13C 2점)
 * @param hostRestrictPoints 합계가 이 값 이상이면 개설 제한 (PRD 4.13C 3점)
 * @param hostRestrictDays 개설 제한 기간, 일 (PRD 4.13C 7일)
 * @param hostReviewPoints 합계가 이 값 이상이면 긴 개설 제한과 운영자 검토 (PRD 4.13C 5점)
 * @param hostLongRestrictDays 운영자 검토 단계의 개설 제한 기간, 일 (PRD 4.13C 30일)
 * @param noticeValidDays 제재 알림의 유효 기간, 일. 알림 종류별 기준(PLAN D-18)이 정해지면 그쪽을 따른다
 */
@Validated
@ConfigurationProperties("everystudy.trust")
public record TrustProperties(
        @Min(1) @DefaultValue("3") int newcomerBelowSessions,
        @Min(1) @DefaultValue("180") int hostLeaveWindowMinutes,
        @Min(1) @DefaultValue("1") int hostLeavePenaltyPoints,
        @Min(1) @DefaultValue("10") int recentSessions,
        @Min(1) @DefaultValue("5") int hostCountOnlyBelow,
        @Min(1) @DefaultValue("5") int sanctionShortWindow,
        @Min(1) @DefaultValue("10") int sanctionLongWindow,
        @Min(1) @DefaultValue("3") int lateAsNoShow,
        @Min(1) @DefaultValue("2") int restrictConvertedNoShows,
        @Min(1) @DefaultValue("4") int suspendConvertedNoShows,
        @Min(1) @DefaultValue("7") int joinRestrictDays,
        @Min(1) @DefaultValue("14") int suspendDays,
        @Min(1) @DefaultValue("7") int noticeValidDays,
        @Min(1) @DefaultValue("90") int hostPenaltyWindowDays,
        @Min(1) @DefaultValue("2") int hostWarnPoints,
        @Min(1) @DefaultValue("3") int hostRestrictPoints,
        @Min(1) @DefaultValue("7") int hostRestrictDays,
        @Min(1) @DefaultValue("5") int hostReviewPoints,
        @Min(1) @DefaultValue("30") int hostLongRestrictDays) {
}
