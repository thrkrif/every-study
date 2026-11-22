package com.everystudy.chat.service;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 채팅 정책 설정값 (ARCHITECTURE 4.9). 값의 원본은 PRD 다.
 *
 * @param retention 방을 닫은 뒤 메시지를 보관하는 기간 (PRD 4.14, 7일). 지나면 영구 삭제한다
 */
@Validated
@ConfigurationProperties("everystudy.chat")
public record ChatProperties(@NotNull @DefaultValue("7d") Duration retention) {
}
