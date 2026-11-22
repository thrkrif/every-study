package com.everystudy.trust.service;

import com.everystudy.trust.domain.HostPenaltyReason;
import com.everystudy.trust.domain.UserTrustSummary;
import com.everystudy.trust.repository.HostPenaltyCount;
import com.everystudy.trust.repository.HostPenaltyEventRepository;
import com.everystudy.trust.repository.UserTrustSummaryRepository;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 모듈이 화면에 보일 출석 지표와 주최자 이탈 건수를 읽는 곳 (PRD 4.7, 4.13D). 목록처럼 여러 명이면 한 번에 읽는다. */
@Service
public class TrustMetricsService {

    private final UserTrustSummaryRepository summaries;
    private final HostPenaltyEventRepository penalties;
    private final TrustProperties properties;

    TrustMetricsService(UserTrustSummaryRepository summaries, HostPenaltyEventRepository penalties,
            TrustProperties properties) {
        this.summaries = summaries;
        this.penalties = penalties;
        this.properties = properties;
    }

    /** 출석 지표. 집계 행이 없는 사용자(출석 기록 없음)는 신규로 채워 돌려준다. 모든 요청 ID 가 결과에 있다. */
    @Transactional(readOnly = true)
    public Map<Long, AttendanceMetrics> attendanceOf(Collection<Long> userIds) {
        Set<Long> ids = Set.copyOf(userIds);
        Map<Long, AttendanceMetrics> result = new HashMap<>();
        ids.forEach(id -> result.put(id, AttendanceMetrics.NEWCOMER));
        for (UserTrustSummary summary : summaries.findAllById(ids)) {
            result.put(summary.getUserId(), toMetrics(summary));
        }
        return result;
    }

    /** 시작 직전(감점 기준 안)에 나가 다른 사람에게 승계한 횟수 (PRD 4.13B). 모든 요청 ID 가 결과에 있다. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> leftWithinWindowCountOf(Collection<Long> hostIds) {
        Set<Long> ids = Set.copyOf(hostIds);
        Map<Long, Integer> result = new HashMap<>();
        ids.forEach(id -> result.put(id, 0));
        if (!ids.isEmpty()) {
            for (HostPenaltyCount row : penalties.countByHostIdsAndReason(ids, HostPenaltyReason.LEFT_WITHIN_WINDOW)) {
                result.put(row.hostId(), Math.toIntExact(row.count()));
            }
        }
        return result;
    }

    /** 주최 횟수가 적으면 비율 대신 건수만 보여 준다 (PRD 4.13D). */
    public boolean showCountOnly(int hostedCount) {
        return hostedCount < properties.hostCountOnlyBelow();
    }

    private AttendanceMetrics toMetrics(UserTrustSummary summary) {
        AttendanceStanding standing = AttendanceStanding.of(summary.getCounted(), summary.getRate(),
                properties.newcomerBelowSessions());
        Integer rate = standing.newcomer() ? null : standing.rate().setScale(0, RoundingMode.DOWN).intValueExact();
        return new AttendanceMetrics(rate, summary.getCounted(), summary.getNoShow(), summary.getLate(),
                summary.getLateCancel(), standing.newcomer());
    }
}
