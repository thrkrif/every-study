package com.everystudy.trust.service;

import com.everystudy.trust.domain.AttendanceEvent;
import com.everystudy.trust.domain.AttendanceEventType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 세션(스터디) 하나의 현재 판정과 그 판정이 난 시각 (DATA-MODEL 5장). */
record SessionVerdict(AttendanceEventType type, Instant occurredAt) {

    /**
     * 세션마다 가장 최근 이벤트 하나가 현재 판정이다.
     *
     * @param eventsNewestFirst 한 사용자의 이벤트, 최근 것부터
     * @return 세션마다 하나씩, 최근 것부터
     */
    static List<SessionVerdict> currentOf(List<AttendanceEvent> eventsNewestFirst) {
        Set<Long> seenStudies = new HashSet<>();
        List<SessionVerdict> verdicts = new ArrayList<>();
        for (AttendanceEvent event : eventsNewestFirst) {
            if (seenStudies.add(event.getStudyId())) {
                verdicts.add(new SessionVerdict(event.getType(), event.getOccurredAt()));
            }
        }
        return verdicts;
    }
}
