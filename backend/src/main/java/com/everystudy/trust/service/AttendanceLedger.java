package com.everystudy.trust.service;

import com.everystudy.trust.domain.AttendanceEvent;
import com.everystudy.trust.domain.AttendanceEventType;
import com.everystudy.trust.repository.AttendanceEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 모듈(출석의 이의 신청)이 출석 이벤트를 읽는 곳 (ARCHITECTURE 3장). 이벤트는 추가만 하므로 여기서는 읽기만 한다.
 */
@Service
public class AttendanceLedger {

    /** 이의를 신청할 수 있는 판정 종류: 노쇼 후보와 지각 (PRD 4.9). */
    private static final Set<AttendanceEventType> APPEALABLE =
            Set.of(AttendanceEventType.NO_SHOW_CANDIDATE, AttendanceEventType.LATE);

    /**
     * 이벤트 한 건. 엔티티를 내보내지 않으려고 값만 담는다.
     *
     * @param type       {@link AttendanceEventType} 이름
     * @param appealable 이의를 낼 수 있는 종류의 판정인가 (기한·중복은 호출한 쪽이 본다)
     */
    public record Verdict(long id, long studyId, long userId, String type, Instant occurredAt, boolean appealable) {

        static Verdict of(AttendanceEvent e) {
            return new Verdict(e.getId(), e.getStudyId(), e.getUserId(), e.getType().name(), e.getOccurredAt(),
                    APPEALABLE.contains(e.getType()));
        }
    }

    private final AttendanceEventRepository events;

    AttendanceLedger(AttendanceEventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Optional<Verdict> find(long eventId) {
        return events.findById(eventId).map(Verdict::of);
    }

    /** 이 판정이 (스터디, 사용자)의 현재 판정인가. 정정이나 확정으로 뒤따르는 이벤트가 있으면 아니다. */
    @Transactional(readOnly = true)
    public boolean isCurrent(long eventId, long studyId, long userId) {
        return events.findFirstByStudyIdAndUserIdOrderByOccurredAtDescIdDesc(studyId, userId)
                .map(latest -> latest.getId() == eventId)
                .orElse(false);
    }

    /** 사용자의 스터디별 현재 판정, 최근 것부터 {@code limit} 건. {@code beforeId} 보다 id 가 작은 것만 본다. */
    @Transactional(readOnly = true)
    public List<Verdict> currentVerdicts(long userId, long beforeId, int limit) {
        return events.findCurrentVerdicts(userId, beforeId, PageRequest.of(0, limit)).stream()
                .map(Verdict::of)
                .toList();
    }
}
