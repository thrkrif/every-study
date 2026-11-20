package com.everystudy.trust.service;

import com.everystudy.trust.repository.UserTrustSummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 모듈이 입장 기준 검사를 위해 내 출석 지표를 읽는 곳 (PRD 4.7). */
@Service
public class AttendanceStandingService {

    private final UserTrustSummaryRepository summaries;
    private final TrustProperties properties;

    AttendanceStandingService(UserTrustSummaryRepository summaries, TrustProperties properties) {
        this.summaries = summaries;
        this.properties = properties;
    }

    /** 집계 행이 없으면(아직 출석 기록이 없으면) 신규다. */
    @Transactional(readOnly = true)
    public AttendanceStanding standingOf(long userId) {
        return summaries.findById(userId)
                .map(summary -> AttendanceStanding.of(summary.getCounted(), summary.getRate(),
                        properties.newcomerBelowSessions()))
                .orElse(AttendanceStanding.NEWCOMER);
    }
}
