package com.everystudy.common.ratelimit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 호출 제한 인터셉터를 API 경로에 건다. */
@Configuration
class RateLimitWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<RateLimiter> limiters;
    private final ObjectProvider<RateLimitProperties> settings;

    RateLimitWebConfig(ObjectProvider<RateLimiter> limiters, ObjectProvider<RateLimitProperties> settings) {
        this.limiters = limiters;
        this.settings = settings;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(limiters, settings)).addPathPatterns("/api/v1/**");
    }
}
