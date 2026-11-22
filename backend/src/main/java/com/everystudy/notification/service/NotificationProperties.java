package com.everystudy.notification.service;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 알림 발송 워커 설정 (ARCHITECTURE 4.6·4.9). 정책 수치가 아닌 기술 파라미터라 PRD 에 값이 없다.
 *
 * @param maxAttempts    한 건을 보내 보는 최대 횟수. 이만큼 실패하면 {@code FAILED} 로 남긴다
 * @param retryBaseDelay 첫 실패 뒤 기다리는 시간. 실패할 때마다 두 배로 늘어난다 (지수 백오프)
 */
@Validated
@ConfigurationProperties("everystudy.notification")
public record NotificationProperties(
        @Min(1) @DefaultValue("5") int maxAttempts,
        @NotNull @DefaultValue("PT1M") Duration retryBaseDelay) {

    /** {@code attempts} 번째 실패 뒤 다음 시도까지 기다리는 시간: 기본값은 1, 2, 4, 8분. */
    Duration retryDelayAfter(int attempts) {
        return retryBaseDelay.multipliedBy(1L << Math.min(attempts - 1, 10));
    }
}
