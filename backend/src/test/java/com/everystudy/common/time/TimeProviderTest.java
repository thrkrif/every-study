package com.everystudy.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.support.TestClocks;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeProviderTest {

    @Test
    @DisplayName("현재 시각은 주입된 Clock 의 시각이다")
    void nowComesFromInjectedClock() {
        TimeProvider time = new TimeProvider(TestClocks.fixedAt("2026-11-24T05:00:00Z"));

        assertThat(time.now()).isEqualTo(Instant.parse("2026-11-24T05:00:00Z"));
    }

    @ParameterizedTest(name = "UTC {0} → KST {1}")
    @CsvSource({
        "2026-11-24T14:59:59Z, 2026-11-24",
        "2026-11-24T15:00:00Z, 2026-11-25",
    })
    @DisplayName("오늘 날짜는 한국 시간 자정을 경계로 바뀐다")
    void todayChangesAtKoreanMidnight(String utcInstant, LocalDate expected) {
        TimeProvider time = new TimeProvider(TestClocks.fixedAt(utcInstant));

        assertThat(time.todayInKorea()).isEqualTo(expected);
    }
}
