package com.everystudy.study.service;

import com.everystudy.study.domain.StudyStatus;
import com.everystudy.study.repository.HostedCount;
import com.everystudy.study.repository.StudyRepository;
import com.everystudy.trust.service.TrustMetricsService;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 목록·상세·참여자·공개 프로필이 함께 쓰는 출석 지표와 주최 지표 (API 3장). 지표의 계산은 {@code trust} 가 하고, 이곳은
 * 주최 횟수(스터디)를 더해 응답 모양으로 만든다.
 */
@Component
class PersonMetrics {

    private final TrustMetricsService trust;
    private final StudyRepository studies;

    PersonMetrics(TrustMetricsService trust, StudyRepository studies) {
        this.trust = trust;
        this.studies = studies;
    }

    /** 모든 요청 ID 가 결과에 있다. 출석 기록이 없으면 신규다. */
    @Transactional(readOnly = true)
    Map<Long, StudyViews.Trust> trustOf(Collection<Long> userIds) {
        return trust.attendanceOf(userIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> StudyViews.Trust.of(entry.getValue())));
    }

    /**
     * 모든 요청 ID 가 결과에 있다. 주최 횟수는 지금 주최자이면서 끝난 스터디와, 시작 직전에 나가 승계한 스터디의 합이다.
     * 승계한 스터디는 더는 그 사람이 주최자가 아니므로 둘은 겹치지 않는다. 파투·진행 중인 스터디는 세지 않는다.
     */
    @Transactional(readOnly = true)
    Map<Long, StudyViews.HostStats> hostStatsOf(Collection<Long> hostIds) {
        Set<Long> ids = Set.copyOf(hostIds);
        Map<Long, Integer> ended = new HashMap<>();
        if (!ids.isEmpty()) {
            for (HostedCount row : studies.countByHostIdsAndStatus(ids, StudyStatus.ENDED)) {
                ended.put(row.hostId(), Math.toIntExact(row.count()));
            }
        }
        Map<Long, Integer> left = trust.leftWithinWindowCountOf(ids);
        Map<Long, StudyViews.HostStats> result = new HashMap<>();
        for (Long id : ids) {
            int leftCount = left.getOrDefault(id, 0);
            int hosted = ended.getOrDefault(id, 0) + leftCount;
            result.put(id, new StudyViews.HostStats(hosted, leftCount, trust.showCountOnly(hosted)));
        }
        return result;
    }
}
