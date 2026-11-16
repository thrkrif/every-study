package com.everystudy.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.validation.annotation.Validated;

/**
 * 정책 설정값을 담는 방식(ARCHITECTURE 4.9)을 예시 속성으로 고정한다:
 * {@code everystudy.<모듈>} 접두사의 불변 record 에 값을 묶고, 잘못된 값이면 기동이 실패한다.
 */
class ConfigurationPropertiesTest {

    @Validated
    @ConfigurationProperties("everystudy.sample")
    record SampleProperties(@Min(1) int limit) {
    }

    @EnableConfigurationProperties(SampleProperties.class)
    static class SampleConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(SampleConfig.class);

    @Test
    @DisplayName("설정값이 타입 있는 속성 객체에 묶인다")
    void bindsValueToTypedProperties() {
        runner.withPropertyValues("everystudy.sample.limit=3").run(context ->
                assertThat(context.getBean(SampleProperties.class).limit()).isEqualTo(3));
    }

    @Test
    @DisplayName("검증에 어긋난 설정값이면 기동이 실패한다")
    void failsToStartOnInvalidValue() {
        runner.withPropertyValues("everystudy.sample.limit=0").run(context ->
                assertThat(context).hasFailed());
    }
}
