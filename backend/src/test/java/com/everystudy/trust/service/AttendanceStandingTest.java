package com.everystudy.trust.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttendanceStandingTest {

    private static final int NEWCOMER_BELOW = 3;

    @Test
    @DisplayName("집계 대상 세션이 기준 미만이면 출석률이 있어도 신규다")
    void fewSessionsIsNewcomer() {
        AttendanceStanding standing = AttendanceStanding.of(2, new BigDecimal("100.00"), NEWCOMER_BELOW);

        assertThat(standing.newcomer()).isTrue();
        assertThat(standing.rate()).isNull();
    }

    @Test
    @DisplayName("집계 대상 세션이 기준 이상이면 출석률로 판단한다")
    void enoughSessionsIsRated() {
        AttendanceStanding standing = AttendanceStanding.of(3, new BigDecimal("66.67"), NEWCOMER_BELOW);

        assertThat(standing.newcomer()).isFalse();
        assertThat(standing.rate()).isEqualByComparingTo("66.67");
    }

    @Test
    @DisplayName("출석률이 비어 있으면 세션 수와 관계없이 신규로 본다")
    void missingRateIsNewcomer() {
        assertThat(AttendanceStanding.of(10, null, NEWCOMER_BELOW)).isEqualTo(AttendanceStanding.NEWCOMER);
    }
}
