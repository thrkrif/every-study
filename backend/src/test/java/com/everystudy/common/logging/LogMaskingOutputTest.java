package com.everystudy.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * 실제 로그 설정(logback-spring.xml)을 거쳐 콘솔에 찍힌 결과를 캡처해 검사한다.
 * DB 없이 로그 설정만 올리려고 빈 설정 클래스로 애플리케이션을 띄운다.
 */
@ExtendWith(OutputCaptureExtension.class)
class LogMaskingOutputTest {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern JWT = Pattern.compile("eyJ[\\w-]+\\.[\\w-]+\\.[\\w-]*");
    private static final Pattern COORDINATE = Pattern.compile("\\d{1,3}\\.\\d{4,}");

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfig {
    }

    @Test
    @DisplayName("콘솔 로그에 토큰·이메일·좌표가 한 건도 남지 않는다")
    void consoleLogContainsNoSensitiveValues(CapturedOutput output) {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(EmptyConfig.class)
                .web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off")) {
            Logger log = LoggerFactory.getLogger(LogMaskingOutputTest.class);

            log.info("marker-email 가입 {}", "someone@example.com");
            log.info("marker-token access_token={}", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl");
            log.info("marker-coordinate lat={}, lng={}", 37.263573, 127.028601);
            log.error("marker-exception", new IllegalStateException("메일 someone@example.com 발송 실패"));
        }

        String console = output.getOut();
        // 로그가 실제로 찍혔는지 먼저 확인한다 (아무것도 안 찍혀서 통과하는 것을 막는다).
        assertThat(console).contains("marker-email", "marker-token", "marker-coordinate", "marker-exception");
        assertThat(EMAIL.matcher(console).results().count()).as("이메일").isZero();
        assertThat(JWT.matcher(console).results().count()).as("토큰").isZero();
        assertThat(COORDINATE.matcher(console).results().count()).as("좌표").isZero();
    }
}
