package com.everystudy.trust.service;

import com.everystudy.trust.domain.AttendanceEvent;
import com.everystudy.trust.domain.AttendanceEventType;
import com.everystudy.trust.repository.AttendanceEventRepository;
import com.everystudy.trust.repository.UserTrustSummaryRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출석 이벤트로 {@code user_trust_summary} 를 만든다 (PRD 4.7, ARCHITECTURE 4.5). 원본은 이벤트이고 집계는 언제든 다시
 * 만들 수 있는 캐시다. 이벤트가 쌓일 때의 갱신({@link #refresh})과 전체 재계산({@link #recalculateAll})은 같은 계산을
 * 쓰므로 결과가 같다.
 */
@Service
public class TrustSummaryService {

    private final AttendanceEventRepository events;
    private final UserTrustSummaryRepository summaries;
    private final TrustProperties properties;
    private final Clock clock;

    TrustSummaryService(AttendanceEventRepository events, UserTrustSummaryRepository summaries,
            TrustProperties properties, Clock clock) {
        this.events = events;
        this.summaries = summaries;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 한 사용자의 집계를 이벤트로 다시 만든다. 이벤트를 남기는 트랜잭션 안에서 불러 이벤트와 집계가 함께 커밋되거나 함께
     * 롤백된다. 이의 인정 정정 같은 이벤트를 새로 남기는 쪽도 이 메서드를 부른다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refresh(long userId) {
        recalculate(userId);
    }

    /**
     * 모든 사용자의 집계를 이벤트로 다시 만든다. 정책(최근 N회, 신규 기준)이 바뀌었을 때 쓴다.
     *
     * @return 다시 계산한 사용자 수
     */
    @Transactional
    public int recalculateAll() {
        List<Long> userIds = events.findAllUserIds();
        userIds.forEach(this::recalculate);
        return userIds.size();
    }

    private void recalculate(long userId) {
        AttendanceTally tally = AttendanceTally.of(currentVerdictsNewestFirst(userId), properties.recentSessions(),
                properties.newcomerBelowSessions());
        summaries.upsert(userId, tally.counted(), tally.attended(), tally.late(), tally.noShow(),
                tally.lateCancel(), tally.rate(), clock.instant());
    }

    /** 세션(스터디)마다 가장 최근 이벤트 하나가 현재 판정이다 (DATA-MODEL 5장). 세션은 그 판정의 발생 시각 순이다. */
    private List<AttendanceEventType> currentVerdictsNewestFirst(long userId) {
        Set<Long> seenStudies = new HashSet<>();
        List<AttendanceEventType> verdicts = new ArrayList<>();
        for (AttendanceEvent event : events.findByUserIdOrderByOccurredAtDescIdDesc(userId)) {
            if (seenStudies.add(event.getStudyId())) {
                verdicts.add(event.getType());
            }
        }
        return verdicts;
    }
}
