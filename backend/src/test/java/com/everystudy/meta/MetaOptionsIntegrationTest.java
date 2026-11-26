package com.everystudy.meta;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** {@code GET /meta/options} 가 공개 경로이고 설정 파일의 값을 내려주는지 (API 4장). */
class MetaOptionsIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvcTester mvc;

    @Test
    @DisplayName("로그인 없이, 만료·위조된 액세스 쿠키가 있어도 열리고 설정 파일의 값을 내려준다")
    void publicAndReadsSettings() {
        assertThat(mvc.get().uri("/api/v1/meta/options"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"study": {"capacityMin": 2, "capacityMax": 6, "openableDays": 14},
                         "checkin": {"radiusMeters": 150}}
                        """);
        assertThat(mvc.get().uri("/api/v1/meta/options").cookie(new Cookie("access_token", "not-a-jwt")))
                .hasStatusOk()
                .bodyJson().extractingPath("$.regions[*].code").asArray()
                .hasSize(16).startsWith("SEOUL", "GYEONGGI", "INCHEON");
    }
}
