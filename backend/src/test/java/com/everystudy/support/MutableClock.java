package com.everystudy.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 테스트가 시각을 옮길 수 있는 시계. 통합 테스트에서 {@code @Primary} 시계 빈으로 넣어 쓴다. */
public final class MutableClock extends Clock {

    private volatile Instant instant;

    public MutableClock(String utcInstant) {
        this.instant = Instant.parse(utcInstant);
    }

    public void set(String utcInstant) {
        this.instant = Instant.parse(utcInstant);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
