package com.everystudy.trust.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 주최자 이탈 감점 기준 시각 (PRD 4.13B). DB 없이 경계만 본다. */
class HostPenaltyRecorderTest {

    private static final Instant START = Instant.parse("2026-11-25T06:00:00Z");

    private final HostPenaltyRecorder recorder =
            new HostPenaltyRecorder(null, null, new TrustProperties(3, 180, 1));

    @Test
    @DisplayName("시작 3시간 전 정각까지는 감점이 아니다")
    void exactlyThreeHoursBeforeIsNotPenalized() {
        assertThat(recorder.isLeaveWithinPenaltyWindow(START, START.minus(Duration.ofHours(3)))).isFalse();
        assertThat(recorder.isLeaveWithinPenaltyWindow(START, START.minus(Duration.ofHours(3)).minusSeconds(1)))
                .isFalse();
    }

    @Test
    @DisplayName("3시간 전 정각을 1초라도 넘기면 감점이다")
    void justInsideWindowIsPenalized() {
        assertThat(recorder.isLeaveWithinPenaltyWindow(START, START.minus(Duration.ofHours(3)).plusSeconds(1)))
                .isTrue();
        assertThat(recorder.isLeaveWithinPenaltyWindow(START, START.minusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("기준 시간은 설정값을 따른다")
    void followsConfiguredWindow() {
        HostPenaltyRecorder narrow = new HostPenaltyRecorder(null, null, new TrustProperties(3, 60, 1));

        assertThat(narrow.isLeaveWithinPenaltyWindow(START, START.minus(Duration.ofMinutes(61)))).isFalse();
        assertThat(narrow.isLeaveWithinPenaltyWindow(START, START.minus(Duration.ofMinutes(59)))).isTrue();
    }
}
