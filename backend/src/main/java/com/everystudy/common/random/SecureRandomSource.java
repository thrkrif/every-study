package com.everystudy.common.random;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** 운영에서 쓰는 {@link RandomSource}. 추첨이 예측되지 않도록 {@link SecureRandom} 을 쓴다. */
@Component
class SecureRandomSource implements RandomSource {

    private final SecureRandom random = new SecureRandom();

    @Override
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }
}
