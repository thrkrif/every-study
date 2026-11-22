package com.everystudy.common.ratelimit;

import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.time.TimeProvider;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 메모리 고정 시간 창 호출 제한 (PLAN T-064). (대상 이름, 키)마다 시간 창 안의 횟수를 세고 한도를 넘으면
 * {@code 429 RATE_LIMITED} 를 던진다. 서버를 늘리면 공유 저장소로 옮긴다. 창이 지난 항목은 맵이 커졌을 때 쓸어 낸다.
 */
@Component
public class RateLimiter {

    /** 맵이 이 크기를 넘으면 지난 창의 항목을 지운다. */
    private static final int SWEEP_ABOVE = 10_000;

    private record Window(long number, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final TimeProvider time;

    RateLimiter(TimeProvider time) {
        this.time = time;
    }

    /**
     * 한 번 호출했다고 세고, 한도를 넘었으면 거부한다.
     *
     * @param bucket 제한 대상 이름 (예: {@code report})
     * @param key 같은 대상 안에서 나누는 키 (회원 ID 또는 IP)
     * @throws RateLimitedException 한도를 넘음. {@code retryAfterSeconds} 는 다음 창까지 남은 초
     */
    public void check(String bucket, String key, int limit, Duration window) {
        long windowMillis = window.toMillis();
        long nowMillis = time.now().toEpochMilli();
        long number = nowMillis / windowMillis;
        if (windows.size() > SWEEP_ABOVE) {
            windows.values().removeIf(w -> w.number() < number);
        }
        Window current = windows.merge(bucket + ":" + key, new Window(number, 1),
                (old, fresh) -> old.number() == number ? new Window(number, old.count() + 1) : fresh);
        if (current.count() > limit) {
            long retryAfter = Math.max(1, (((number + 1) * windowMillis) - nowMillis + 999) / 1000);
            throw new RateLimitedException(retryAfter);
        }
    }

    /** 한도를 넘었다. 응답에 {@code Retry-After} 를 실을 수 있게 남은 초를 갖는다. */
    public static final class RateLimitedException extends BusinessException {

        private final long retryAfterSeconds;

        RateLimitedException(long retryAfterSeconds) {
            super(CommonErrorCode.RATE_LIMITED);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
