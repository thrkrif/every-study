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
 * 비즈니스 변경과 함께 커밋되거나 롤백된다. 트랜잭션 밖에서 부르면 실패한다.
 */
@Service
public class AttendanceEventRecorder {

    private final AttendanceEventRepository events;

    AttendanceEventRecorder(AttendanceEventRepository events) {
        this.events = events;
    }

    /** 사전 취소(시작 3시간 전까지): 출석률 분모에서 제외된다 (PRD 4.6·4.7). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEarlyCancel(long studyId, long userId, Instant occurredAt) {
        events.save(AttendanceEvent.of(studyId, userId, AttendanceEventType.EARLY_CANCEL, occurredAt));
    }

    /** 직전 취소(시작 3시간 이내): 노쇼보다 가벼운 감점으로 분모에 들어간다 (PRD 4.6·4.7). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordLateCancel(long studyId, long userId, Instant occurredAt) {
        events.save(AttendanceEvent.of(studyId, userId, AttendanceEventType.LATE_CANCEL, occurredAt));
    }

    /** 출석: 시작 10분 이내 체크인 (PRD 4.6). 판정 이벤트는 한 번만 남는다 (PLAN X-9). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAttended(long studyId, long userId, Instant occurredAt) {
        events.save(AttendanceEvent.of(studyId, userId, AttendanceEventType.ATTENDED, occurredAt));
    }

    /** 지각: 시작 10분 초과 ~ 30분 이내 체크인 (PRD 4.6). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordLate(long studyId, long userId, Instant occurredAt) {
        events.save(AttendanceEvent.of(studyId, userId, AttendanceEventType.LATE, occurredAt));
    }
}
