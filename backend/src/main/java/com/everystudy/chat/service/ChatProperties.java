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
 * @param notifyWindow 새 메시지 알림을 묶는 시간. 같은 방·같은 사람에게는 이 시간 창마다 한 건만 보낸다 (PLAN D-12)
 * @param notifyValid 새 메시지 알림의 유효 기간. 지나면 보내지 않는다 (PLAN D-18)
 */
@Validated
@ConfigurationProperties("everystudy.chat")
public record ChatProperties(
        @NotNull @DefaultValue("7d") Duration retention,
        @NotNull @DefaultValue("5m") Duration notifyWindow,
        @NotNull @DefaultValue("1h") Duration notifyValid) {
}
