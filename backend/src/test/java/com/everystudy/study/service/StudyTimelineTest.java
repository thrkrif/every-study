package com.everystudy.study.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.study.domain.StudyStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StudyTimelineTest {

    private static final StudyProperties PROPERTIES =
            new StudyProperties(2, 6, 14, 10, 10, 30, List.of(50, 60, 70, 80, 90, 100), 10, 30);
    private static final Instant START = Instant.parse("2026-11-20T10:00:00Z");

    @Test
    @DisplayName("체크인은 시작 10분 전에 열리고 30분 뒤에 닫힌다")
    void checkinWindow() {
        assertThat(StudyTimeline.checkinOpensAt(START, PROPERTIES)).isEqualTo(Instant.parse("2026-11-20T09:50:00Z"));
        assertThat(StudyTimeline.checkinClosesAt(START, PROPERTIES)).isEqualTo(Instant.parse("2026-11-20T10:30:00Z"));
    }

    @ParameterizedTest(name = "{0}, 참여 {1}/정원 4, {2} → 모집 중 {3}")
    @CsvSource({
            "ACTIVE, 3, 2026-11-20T09:49:59Z, true",
            "ACTIVE, 3, 2026-11-20T09:50:00Z, false",
            "ACTIVE, 4, 2026-11-20T09:00:00Z, false",
            "ENDED, 1, 2026-11-20T09:00:00Z, false",
            "CANCELLED, 1, 2026-11-20T09:00:00Z, false"})
    @DisplayName("모집 중은 진행 중 + 체크인 오픈 전 + 정원 여유. 체크인 오픈 순간부터 마감이다")
    void recruiting(StudyStatus status, int memberCount, String now, boolean expected) {
        assertThat(StudyTimeline.isRecruiting(status, START, memberCount, 4, Instant.parse(now), PROPERTIES))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}, 다른 참여자 {1}, {2} → 수정 가능 {3}")
    @CsvSource({
            "ACTIVE, 0, 2026-11-20T09:59:59Z, true",
            "ACTIVE, 1, 2026-11-20T09:00:00Z, false",
            "ACTIVE, 0, 2026-11-20T10:00:00Z, false",
            "ENDED, 0, 2026-11-20T09:00:00Z, false"})
    @DisplayName("일정·장소는 진행 중이고 시작 전이며 주최자 혼자일 때만 고칠 수 있다")
    void scheduleEditable(StudyStatus status, int others, String now, boolean expected) {
        assertThat(StudyTimeline.isScheduleEditable(status, START, others, Instant.parse(now))).isEqualTo(expected);
    }
}
