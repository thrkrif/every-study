package com.everystudy.study.repository;

import java.time.Instant;
import java.util.List;

/** 목록 한 줄. {@code memberCount} 는 주최자를 포함한 {@code ACTIVE} 참여자 수다. */
public record StudySummaryRow(
        long id,
        long hostId,
        String title,
        String topicCode,
        String districtCode,
        Instant startAt,
        int durationMin,
        int capacity,
        int memberCount,
        Integer minAttendanceRate,
        boolean allowNewcomer,
        List<String> targetJobCodes) {
}
