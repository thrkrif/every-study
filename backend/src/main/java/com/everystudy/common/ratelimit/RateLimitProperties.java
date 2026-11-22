package com.everystudy.common.ratelimit;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 호출 제한 설정값 (API 1장 {@code 429}·9장, PLAN D-5·T-064). 서버 1대의 메모리에서 센다(ADR-0005 단일 서버 가정).
 * 한도를 넘으면 창이 끝날 때까지 {@code 429 RATE_LIMITED} 이고, 시간 창은 이름에 있는 단위(분·시간)로 고정이다.
 *
 * @param checkinCodePerMinute 체크인 코드 입력, 회원당 분당 횟수
 * @param joinPerMinute 참여 신청, 회원당 분당 횟수
 * @param reportPerHour 신고, 회원당 시간당 횟수
 * @param chatSendPerMinute 채팅 메시지 전송, 회원당 분당 횟수 (REST·STOMP 공통)
 * @param publicReadPerMinute 비회원 스터디 목록·상세 조회, IP 당 분당 횟수
 */
@Validated
@ConfigurationProperties("everystudy.rate-limit")
public record RateLimitProperties(
        @Min(1) @DefaultValue("10") int checkinCodePerMinute,
        @Min(1) @DefaultValue("20") int joinPerMinute,
        @Min(1) @DefaultValue("20") int reportPerHour,
        @Min(1) @DefaultValue("60") int chatSendPerMinute,
        @Min(1) @DefaultValue("120") int publicReadPerMinute) {
}
