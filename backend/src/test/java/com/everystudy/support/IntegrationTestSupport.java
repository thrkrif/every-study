package com.everystudy.support;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 통합 테스트의 공통 부모. 통합 테스트는 이 클래스를 상속한다 ({@code @SpringBootTest} 를 직접 붙이지 않는다).
 *
 * <ul>
 *   <li>{@code integration} 태그가 붙어 {@code ./gradlew integrationTest} 에서만 실행된다.</li>
 *   <li>MySQL 컨테이너는 테스트 JVM 에서 한 번만 띄워 모든 통합 테스트가 함께 쓴다 (ARCHITECTURE 8.1).
 *       JUnit 의 {@code @Container} 로 관리하면 테스트 클래스마다 재시작되어, 캐시된 Spring 컨텍스트가
 *       죽은 컨테이너를 가리키게 되므로 쓰지 않는다. 컨테이너는 JVM 종료 시 Testcontainers 가 정리한다.</li>
 * </ul>
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    // 로컬 compose.yaml 의 MySQL 과 같은 버전을 쓴다.
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    static {
        MYSQL.start();
    }

    /**
     * 모든 테이블을 비운다 (Flyway 이력 제외). 통합 테스트는 컨테이너 하나를 함께 쓰므로 각 테스트가 시작할 때 부른다.
     * 외래 키 순서를 따지지 않도록 같은 연결에서 검사를 잠시 끈다.
     */
    protected static void cleanDatabase(JdbcTemplate jdbc) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                List<String> tables = new ArrayList<>();
                try (ResultSet rs = statement.executeQuery(
                        "SELECT table_name FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'")) {
                    while (rs.next()) {
                        tables.add(rs.getString(1));
                    }
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                try {
                    for (String table : tables) {
                        statement.execute("TRUNCATE TABLE `" + table + "`");
                    }
                } finally {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
            return null;
        });
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
