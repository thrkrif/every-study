package com.everystudy.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.everystudy.common.error.BusinessException;
import com.everystudy.study.domain.CancelType;
import com.everystudy.study.domain.StudyStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StudyLeaveRulesTest {

    private static final StudyProperties PROPERTIES =
            new StudyProperties(2, 6, 14, 10, 10, 30, List.of(50, 60, 70, 80, 90, 100), 10, 30, 180);
    private static final Instant START = Instant.parse("2026-11-20T10:00:00Z");

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "2026-11-19T10:00:00Z, EARLY",
            "2026-11-20T06:59:59Z, EARLY",
            "2026-11-20T07:00:00Z, EARLY",
            "2026-11-20T07:00:01Z, LATE",
            "2026-11-20T09:59:59Z, LATE"})
    @DisplayName("시작 3시간 전까지는 사전 취소, 그 뒤부터 시작 전까지는 직전 취소다 (PRD 4.6)")
    void cancelType(String now, CancelType expected) {
        assertThat(StudyLeaveRules.cancelType(START, Instant.parse(now), PROPERTIES)).isEqualTo(expected);
    }

    @Test
    @DisplayName("진행 중이고 시작 전이면 나갈 수 있다")
    void leavableBeforeStart() {
        StudyLeaveRules.requireLeavable(StudyStatus.ACTIVE, START, Instant.parse("2026-11-20T09:59:59Z"));
    }

    @ParameterizedTest(name = "{0}, {1} → SESSION_STARTED")
    @CsvSource({
            "ACTIVE, 2026-11-20T10:00:00Z",
            "ACTIVE, 2026-11-20T11:00:00Z",
            "ENDED, 2026-11-19T10:00:00Z",
            "CANCELLED, 2026-11-19T10:00:00Z"})
    @DisplayName("시작했거나 종료·파투된 스터디는 나갈 수 없다")
    void notLeavable(StudyStatus status, String now) {
        assertThatThrownBy(() -> StudyLeaveRules.requireLeavable(status, START, Instant.parse(now)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(StudyErrorCode.SESSION_STARTED);
    }
}
