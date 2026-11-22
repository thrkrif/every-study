package com.everystudy.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.everystudy.attendance.domain.CheckinResult;
import com.everystudy.common.error.BusinessException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 체크인 시간창·판정·반경 경계 (PRD 4.6). 고정 시각으로 각 경계의 직전·직후를 본다. */
class CheckinRulesTest {

    private static final Instant START = Instant.parse("2026-11-25T05:00:00Z");
    private static final Instant OPENS = START.minus(Duration.ofMinutes(10));
    private static final Instant CLOSES = START.plus(Duration.ofMinutes(30));

    private static void assertClosed(Instant now) {
        assertThatThrownBy(() -> CheckinRules.requireWindowOpen(now, OPENS, CLOSES))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode().name()).isEqualTo("CHECKIN_WINDOW_CLOSED"));
    }

    @Test
    @DisplayName("체크인은 시작 10분 전 정각부터 열린다: 1초 전은 닫힘, 정각은 열림")
    void opensAtTenMinutesBefore() {
        assertClosed(OPENS.minusSeconds(1));
        CheckinRules.requireWindowOpen(OPENS, OPENS, CLOSES);
        CheckinRules.requireWindowOpen(OPENS.plusSeconds(1), OPENS, CLOSES);
    }

    @Test
    @DisplayName("체크인은 시작 30분 뒤 정각까지 받는다: 정각은 열림, 1초 뒤는 닫힘")
    void closesAtThirtyMinutesAfter() {
        CheckinRules.requireWindowOpen(CLOSES.minusSeconds(1), OPENS, CLOSES);
        CheckinRules.requireWindowOpen(CLOSES, OPENS, CLOSES);
        assertClosed(CLOSES.plusSeconds(1));
    }

    @Test
    @DisplayName("시작 10분 뒤 정각까지는 출석, 1초라도 넘으면 지각이다. 시작 전 체크인은 출석")
    void attendedUntilTenMinutesAfter() {
        assertThat(CheckinRules.resultAt(START.minus(Duration.ofMinutes(10)), START, 10))
                .isEqualTo(CheckinResult.ATTENDED);
        assertThat(CheckinRules.resultAt(START, START, 10)).isEqualTo(CheckinResult.ATTENDED);
        Instant tenMinutes = START.plus(Duration.ofMinutes(10));
        assertThat(CheckinRules.resultAt(tenMinutes.minusSeconds(1), START, 10)).isEqualTo(CheckinResult.ATTENDED);
        assertThat(CheckinRules.resultAt(tenMinutes, START, 10)).isEqualTo(CheckinResult.ATTENDED);
        assertThat(CheckinRules.resultAt(tenMinutes.plusSeconds(1), START, 10)).isEqualTo(CheckinResult.LATE);
        assertThat(CheckinRules.resultAt(CLOSES, START, 10)).isEqualTo(CheckinResult.LATE);
    }

    @Test
    @DisplayName("출석 기준 분은 설정값을 따른다")
    void followsConfiguredMinutes() {
        Instant fiveMinutes = START.plus(Duration.ofMinutes(5));
        assertThat(CheckinRules.resultAt(fiveMinutes, START, 5)).isEqualTo(CheckinResult.ATTENDED);
        assertThat(CheckinRules.resultAt(fiveMinutes.plusSeconds(1), START, 5)).isEqualTo(CheckinResult.LATE);
    }

    @Test
    @DisplayName("Haversine 거리: 같은 지점은 0, 위도 0.001도는 약 111m")
    void haversine() {
        assertThat(CheckinRules.distanceMeters(37.3, 127.01, 37.3, 127.01)).isZero();
        assertThat(CheckinRules.distanceMeters(37.300, 127.01, 37.301, 127.01)).isBetween(110.0, 112.5);
        // 경도 1도의 길이는 위도에 따라 줄어든다 (서울 부근 약 88.8km)
        assertThat(CheckinRules.distanceMeters(37.5, 127.0, 37.5, 128.0)).isBetween(88_000.0, 89_500.0);
    }

    @Test
    @DisplayName("반경 150m: 안쪽과 경계는 통과하고 반경을 넘으면 CHECKIN_OUT_OF_RANGE")
    void radiusBoundary() {
        // 위도 0.001도 = 약 111.2m (반경 안), 0.0014도 = 약 155.7m (반경 밖)
        assertThat(CheckinRules.requireWithinRadius(37.301, 127.01, 37.3, 127.01, 150)).isBetween(111, 112);
        assertThat(CheckinRules.requireWithinRadius(37.3, 127.01, 37.3, 127.01, 150)).isZero();
        assertThatThrownBy(() -> CheckinRules.requireWithinRadius(37.3014, 127.01, 37.3, 127.01, 150))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode().name()).isEqualTo("CHECKIN_OUT_OF_RANGE"));
        // 반올림한 거리가 반경과 같으면 안이다: 반경 112m 에서 111.2m(→111)는 통과, 반경 111m 에서도 통과, 110m 는 밖
        assertThat(CheckinRules.requireWithinRadius(37.301, 127.01, 37.3, 127.01, 111)).isEqualTo(111);
        assertThatThrownBy(() -> CheckinRules.requireWithinRadius(37.301, 127.01, 37.3, 127.01, 110))
                .isInstanceOf(BusinessException.class);
    }
}
