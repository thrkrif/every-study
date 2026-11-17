package com.everystudy.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 리프레시 토큰 원문을 DB 에 저장할 해시(SHA-256, 16진수 64자)로 바꾼다. */
final class TokenHasher {

    private TokenHasher() {
    }

    static String sha256Hex(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없음", e);
        }
    }
}
