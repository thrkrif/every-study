package com.everystudy.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** 테스트용 고정 시계. 시간에 의존하는 코드는 실제 시각 대신 이 시계를 주입받아 검증한다. */
public final class TestClocks {

    private TestClocks() {
    }

    /** @param utcInstant ISO-8601 UTC 시각 (예: {@code 2026-11-24T15:00:00Z}) */
    public static Clock fixedAt(String utcInstant) {
        return Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC);
    }
}
