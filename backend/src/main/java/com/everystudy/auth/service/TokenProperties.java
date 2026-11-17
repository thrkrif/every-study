package com.everystudy.auth.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 토큰 설정 (ADR-0002 확정 값).
 *
 * @param signingKey 액세스 JWT 서명 키(HS256). 환경변수로만 주입하고, 32자 미만이면 기동하지 않는다
 * @param accessTtl  액세스 토큰 수명
 * @param refreshTtl 리프레시 토큰 수명
 */
@Validated
@ConfigurationProperties("everystudy.auth.token")
public record TokenProperties(
        @NotBlank @Size(min = 32, message = "서명 키는 32자 이상이어야 합니다") String signingKey,
        @NotNull @DefaultValue("15m") Duration accessTtl,
        @NotNull @DefaultValue("14d") Duration refreshTtl) {

    /** 발급과 검증이 함께 쓰는 HS256 키. */
    SecretKey secretKey() {
        return new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** 키가 로그나 actuator 출력에 찍히지 않게 한다. */
    @Override
    public String toString() {
        return "TokenProperties[accessTtl=" + accessTtl + ", refreshTtl=" + refreshTtl + "]";
    }
}
