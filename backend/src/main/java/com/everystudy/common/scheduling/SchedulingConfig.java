package com.everystudy.common.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @Scheduled} 를 켠다. {@code everystudy.scheduler.enabled=false} 면 켜지 않는다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "everystudy.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {
}
