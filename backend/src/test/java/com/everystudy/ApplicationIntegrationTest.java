package com.everystudy;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.support.IntegrationTestSupport;
import java.time.Clock;
import java.time.ZoneOffset;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class ApplicationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment environment;

    @Autowired
    Clock clock;

    @Autowired
    MockMvcTester mvc;

    @Test
    @DisplayName("Flyway 가 실제 MySQL 에 마이그레이션을 모두 적용한다")
    void flywayAppliesAllMigrations() {
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    @DisplayName("JPA 는 스키마를 만들지 않고 검증만 한다")
    void jpaOnlyValidatesSchema() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    @DisplayName("데이터베이스 기본 문자셋은 utf8mb4 다")
    void databaseCharsetIsUtf8mb4() {
        String charset = jdbc.queryForObject("SELECT @@character_set_database", String.class);

        assertThat(charset).isEqualTo("utf8mb4");
    }

    @Test
    @DisplayName("DB 세션 시간대는 UTC 다")
    void databaseSessionTimeZoneIsUtc() {
        String offset = jdbc.queryForObject("SELECT TIME_FORMAT(TIMEDIFF(NOW(), UTC_TIMESTAMP()), '%H:%i')", String.class);

        assertThat(offset).isEqualTo("00:00");
    }

    @Test
    @DisplayName("주입되는 Clock 은 UTC 기준이다")
    void clockIsUtc() {
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("OpenAPI 문서가 생성된다")
    void openApiDocumentIsGenerated() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.openapi").asString().startsWith("3.");
    }

    @Test
    @DisplayName("Swagger UI 화면이 열린다")
    void swaggerUiIsServed() {
        assertThat(mvc.get().uri("/swagger-ui/index.html"))
                .hasStatusOk()
                .bodyText().contains("Swagger UI");
    }
}
