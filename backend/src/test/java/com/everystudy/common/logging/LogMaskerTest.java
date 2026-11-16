package com.everystudy.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LogMaskerTest {

    // 테스트 값은 모두 지어낸 것이다 (실제 토큰·개인정보를 쓰지 않는다).
    private static final String FAKE_JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl";

    @Test
    @DisplayName("이메일 주소를 가린다")
    void masksEmail() {
        String masked = LogMasker.mask("가입 완료 user.name+tag@example.co.kr 처리");

        assertThat(masked).isEqualTo("가입 완료 ***@*** 처리");
    }

    @Test
    @DisplayName("JWT 모양의 값을 가린다")
    void masksJwt() {
        String masked = LogMasker.mask("cookie=" + FAKE_JWT + " ok");

        assertThat(masked).doesNotContain(FAKE_JWT).doesNotContain("eyJ").endsWith(" ok");
    }

    @Test
    @DisplayName("Bearer 토큰을 가린다")
    void masksBearerToken() {
        String masked = LogMasker.mask("Authorization: Bearer abc.DEF-123_xyz");

        assertThat(masked).doesNotContain("abc.DEF-123_xyz");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "refresh_token=opaque-value-123",
        "refreshToken: opaque-value-123",
        "{\"accessToken\":\"opaque-value-123\"}",
        "client_secret=opaque-value-123",
        "password=opaque-value-123",
    })
    @DisplayName("토큰·비밀값으로 보이는 키의 값을 가린다")
    void masksSecretLikeKeyValues(String line) {
        assertThat(LogMasker.mask(line)).doesNotContain("opaque-value-123");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "lat=37.263573, lng=127.028601",
        "latitude: 37.263573 longitude: 127.028601",
        "{\"lat\":37.263573,\"lng\":127.028601}",
        "위치 (37.263573, 127.028601) 에서 체크인",
    })
    @DisplayName("위도·경도 값을 가린다")
    void masksCoordinates(String line) {
        assertThat(LogMasker.mask(line)).doesNotContain("37.263573").doesNotContain("127.028601");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "스터디 42 참여 확정 (정원 6)",
        "o.s.s.web.csrf.CsrfTokenRepository : 초기화 완료",
        "거리 12.5m, 정확도 30.0m",
    })
    @DisplayName("민감하지 않은 내용은 그대로 둔다")
    void keepsOrdinaryText(String line) {
        assertThat(LogMasker.mask(line)).isEqualTo(line);
    }
}
