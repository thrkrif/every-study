package com.everystudy.common.scheduling;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 스케줄러 공통 설정 (ARCHITECTURE 4.7). 정책 수치가 아닌 기술 파라미터다.
 *
 * @param enabled    {@code false} 면 {@code @Scheduled} 작업을 돌리지 않는다 (통합 테스트는 직접 호출한다)
 * @param batchLimit 작업 한 번이 한 번에 처리하는 최대 건수. 남은 건은 다음 실행이 처리한다 (멱등)
 */
@Validated
@ConfigurationProperties("everystudy.scheduler")
public record SchedulerProperties(
        @DefaultValue("true") boolean enabled,
        @Min(1) @DefaultValue("100") int batchLimit) {
}
