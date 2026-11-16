package com.everystudy.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TimeConfig {

    /** 모든 시간 로직이 주입받는 시계. 저장·비교는 UTC 로 한다 (ARCHITECTURE 5장). */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
