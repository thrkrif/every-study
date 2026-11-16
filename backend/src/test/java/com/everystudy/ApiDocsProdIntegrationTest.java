package com.everystudy;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@ActiveProfiles("prod")
class ApiDocsProdIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvcTester mvc;

    @Test
    @DisplayName("운영(prod)에서는 OpenAPI 문서를 열지 않는다")
    void openApiDocumentIsNotExposed() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(404);
    }

    @Test
    @DisplayName("운영(prod)에서는 Swagger UI 를 열지 않는다")
    void swaggerUiIsNotExposed() {
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatus(404);
        assertThat(mvc.get().uri("/swagger-ui.html")).hasStatus(404);
    }
}
