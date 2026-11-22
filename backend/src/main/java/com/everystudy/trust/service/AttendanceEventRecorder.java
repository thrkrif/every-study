package com.everystudy.trust.service;

import com.everystudy.trust.domain.AttendanceEvent;
import com.everystudy.trust.domain.AttendanceEventType;
import com.everystudy.trust.repository.AttendanceEventRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 모듈이 출석 이벤트를 남기는 곳 (ARCHITECTURE 3장, DATA-MODEL 5장). 호출한 쪽 트랜잭션 안에서 기록하므로 나가기 같은
 * 비즈니스 변경과 함께 커밋되거나 롤백된다. 트랜잭션 밖에서 부르면 실패한다. 출석률에 영향을 주는 이벤트는 같은 트랜잭션에서
 * 그 사용자의 집계({@link TrustSummaryService})도 갱신한다. 노쇼 후보는 지표에 반영되지 않아 갱신하지 않는다.
 */
@Service
public class AttendanceEventRecorder {

    private final AttendanceEventRepository events;
    private final TrustSummaryService summaries;

    AttendanceEventRecorder(AttendanceEventRepository events, TrustSummaryService summaries) {
        this.events = events;
        this.summaries = summaries;
    }

    /**
     * 세션 무효 또는 파투: 진행되지 않은 세션이라 이 사람의 지표에 영향이 없다 (PRD 4.6·4.7). 같은 (스터디, 사용자)에 이미
     * 있으면 남기지 않는다(판정 작업의 재실행, PLAN X-9).
     *
     * @return 새로 남겼으면 {@code true}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordVoided(long studyId, long userId, Instant occurredAt) {
        boolean recorded = recordVerdictOnce(studyId, userId, AttendanceEventType.VOIDED, occurredAt);
        if (recorded) {
            summaries.refresh(userId);
        }
        return recorded;
    }

    /**
     * 노쇼 후보: 주최자는 체크인했는데 이 참여자는 하지 않았다 (PRD 4.6). 이미 있으면 남기지 않는다.
     *
     * @return 새로 남겼으면 {@code true}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordNoShowCandidate(long studyId, long userId, Instant occurredAt) {
        return recordVerdictOnce(studyId, userId, AttendanceEventType.NO_SHOW_CANDIDATE, occurredAt);
    }

    private boolean recordVerdictOnce(long studyId, long userId, AttendanceEventType type, Instant occurredAt) {
        if (events.existsByStudyIdAndUserIdAndType(studyId, userId, type)) {
            return false;
        }
        events.save(AttendanceEvent.of(studyId, userId, type, occurredAt));
        return true;
    }

    /** 사전 취소(시작 3시간 전까지): 출석률 분모에서 제외된다 (PRD 4.6·4.7). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEarlyCancel(long studyId, long userId, Instant occurredAt) {
        events.save(AttendanceEvent.of(studyId, userId, AttendanceEventType.EARLY_CANCEL, occurredAt));
        summaries.refresh(userId);
    }

    /** 직전 취소(시작 3시간 이내): 노쇼보다 가벼운 감점으로 분모에 들어간다 (PRD 4.6·4.7). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordLateCancel(long studyId, long userId, Instant occurredAt) {
        events.save(AttendanceEvent.of(studyId, userId, AttendanceEventType.LATE_CANCEL, occurredAt));
        summaries.refresh(userId);
    }

    /** 출석: 시작 10분 이내 체크인 (PRD 4.6). 판정 이벤트는 한 번만 남는다 (PLAN X-9). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAttended(long studyId, long userId, Instant occurredAt) {
        events.save(AttendanceEvent.of(studyId, userId, AttendanceEventType.ATTENDED, occurredAt));
        summaries.refresh(userId);
    }

    /** 지각: 시작 10분 초과 ~ 30분 이내 체크인 (PRD 4.6). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordLate(long studyId, long userId, Instant occurredAt) {
        events.save(AttendanceEvent.of(studyId, userId, AttendanceEventType.LATE, occurredAt));
        summaries.refresh(userId);
    }
}
