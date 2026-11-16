package com.everystudy.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenHasherTest {

    @Test
    @DisplayName("SHA-256 해시를 16진수 64자로 만든다")
    void hashesToSha256Hex() {
        // SHA-256 표준 테스트 벡터 ("abc")
        assertThat(TokenHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("값이 다르면 해시도 다르다")
    void differentInputsGiveDifferentHashes() {
        assertThat(TokenHasher.sha256Hex("a")).isNotEqualTo(TokenHasher.sha256Hex("b"));
    }
}
