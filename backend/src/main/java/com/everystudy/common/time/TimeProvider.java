package com.everystudy.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 현재 시각을 얻는 한 곳. {@code Instant.now()} 나 {@code LocalDate.now()} 를 직접 부르지 않고 이 클래스를 쓴다.
 * 테스트에서는 고정 {@link Clock} 을 넣어 시간 경계를 검증한다.
 */
@Component
public class TimeProvider {

    /** 날짜 경계(세션 날짜, 자정 종료)의 기준 시간대 (ARCHITECTURE 4.7). */
    public static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public TimeProvider(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate todayInKorea() {
        return LocalDate.ofInstant(clock.instant(), KOREA);
    }
}
