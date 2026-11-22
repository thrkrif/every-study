package com.everystudy.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.ratelimit.RateLimiter.RateLimitedException;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock("2026-11-23T03:00:10Z");
    private final RateLimiter limiter = new RateLimiter(new TimeProvider(clock));

    @Test
    @DisplayName("한도 직전까지는 허용하고 한도를 넘는 호출부터 429 와 남은 초를 알린다")
    void allowsUpToLimitThenRejects() {
        for (int i = 0; i < 3; i++) {
            limiter.check("join", "u1", 3, Duration.ofMinutes(1));
        }

        assertThatThrownBy(() -> limiter.check("join", "u1", 3, Duration.ofMinutes(1)))
                .isInstanceOf(RateLimitedException.class)
                .satisfies(e -> {
                    RateLimitedException limited = (RateLimitedException) e;
                    assertThat(limited.errorCode()).isEqualTo(CommonErrorCode.RATE_LIMITED);
                    assertThat(limited.retryAfterSeconds()).isEqualTo(50);
                });
    }

    @Test
    @DisplayName("대상 이름과 키가 다르면 따로 센다")
    void bucketsAndKeysAreIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.check("join", "u1", 3, Duration.ofMinutes(1));
        }

        assertThatCode(() -> limiter.check("join", "u2", 3, Duration.ofMinutes(1))).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("report", "u1", 3, Duration.ofMinutes(1))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다음 시간 창이 되면 다시 센다")
    void resetsInNextWindow() {
        for (int i = 0; i < 3; i++) {
            limiter.check("join", "u1", 3, Duration.ofMinutes(1));
        }
        clock.set("2026-11-23T03:01:00Z");

        assertThatCode(() -> limiter.check("join", "u1", 3, Duration.ofMinutes(1))).doesNotThrowAnyException();
    }
}
