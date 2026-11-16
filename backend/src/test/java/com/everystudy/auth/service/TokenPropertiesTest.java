package com.everystudy.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class TokenPropertiesTest {

    private static final String KEY = "unit-test-signing-key-0123456789abcdef";

    @EnableConfigurationProperties(TokenProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    @DisplayName("수명을 적지 않으면 액세스 15분, 리프레시 14일이다")
    void defaultsToConfirmedLifetimes() {
        runner.withPropertyValues("everystudy.auth.token.signing-key=" + KEY).run(context -> {
            TokenProperties properties = context.getBean(TokenProperties.class);
            assertThat(properties.accessTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.refreshTtl()).isEqualTo(Duration.ofDays(14));
        });
    }

    @Test
    @DisplayName("서명 키가 32자보다 짧으면 기동이 실패한다")
    void failsToStartWithShortKey() {
        runner.withPropertyValues("everystudy.auth.token.signing-key=too-short").run(context ->
                assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("서명 키가 없으면 기동이 실패한다")
    void failsToStartWithoutKey() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("문자열로 바꿔도 서명 키가 드러나지 않는다")
    void toStringHidesKey() {
        assertThat(new TokenProperties(KEY, Duration.ofMinutes(15), Duration.ofDays(14)).toString())
                .doesNotContain(KEY);
    }
}
