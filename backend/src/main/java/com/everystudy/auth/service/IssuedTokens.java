package com.everystudy.auth.service;

import java.time.Duration;

/**
 * 로그인으로 발급한 토큰 한 쌍. 쿠키로만 내려보내고 로그나 응답 본문에 넣지 않는다.
 *
 * @param accessTtl  액세스 토큰 수명 (쿠키 Max-Age 로 쓴다)
 * @param refreshTtl 리프레시 토큰 수명
 */
public record IssuedTokens(String accessToken, Duration accessTtl, String refreshToken, Duration refreshTtl) {

    /** 토큰 값이 로그에 찍히지 않게 한다. */
    @Override
    public String toString() {
        return "IssuedTokens[accessTtl=" + accessTtl + ", refreshTtl=" + refreshTtl + "]";
    }
}
