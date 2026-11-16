package com.everystudy.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
