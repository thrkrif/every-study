package com.everystudy.auth.service;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** 액세스 토큰(JWT) 검증기. 발급({@link AccessTokenIssuer})과 같은 키·알고리즘을 쓴다. */
@Configuration(proxyBeanMethods = false)
class AccessTokenDecoderConfig {

    /**
     * 서명과 만료를 검사한다. 발급과 검증을 같은 서버가 하므로 시계 오차 허용(기본 60초)을 두지 않는다.
     * 만료 시각이 지나면 바로 거절한다.
     */
    @Bean
    JwtDecoder accessTokenDecoder(TokenProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(timestamps);
        return decoder;
    }
}
