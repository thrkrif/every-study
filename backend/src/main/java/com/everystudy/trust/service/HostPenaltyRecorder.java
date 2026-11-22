package com.everystudy.trust.service;

import com.everystudy.common.audit.AuditAction;
import com.everystudy.common.audit.AuditLogger;
import com.everystudy.trust.domain.HostPenaltyEvent;
import com.everystudy.trust.domain.HostPenaltyReason;
import com.everystudy.trust.repository.HostPenaltyEventRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주최자 감점을 기록하는 공개 서비스 (PRD 4.13B, ARCHITECTURE 3장). 호출한 쪽 트랜잭션 안에서 감점 이벤트와 감사 로그를
 * 함께 남기므로, 승계 같은 본 변경이 롤백되면 감점도 남지 않는다. 트랜잭션 밖에서 부르면 실패한다.
 */
@Service
public class HostPenaltyRecorder {

    private final HostPenaltyEventRepository events;
    private final AuditLogger audit;
    private final TrustProperties properties;

    HostPenaltyRecorder(HostPenaltyEventRepository events, AuditLogger audit, TrustProperties properties) {
        this.events = events;
        this.audit = audit;
        this.properties = properties;
    }

    /**
     * 주최자가 이 시각에 나가면 감점인가 (PRD 4.13B). 시작 {@code hostLeaveWindowMinutes} 분 전 시각보다 <b>엄격히 뒤</b>면
     * 감점이고, 정확히 그 시각은 아직 아니다("3시간 전까지"는 사전, 참여자 취소 판정과 같은 경계). 영향받는 참여자가 있는지는
     * 호출한 쪽이 따진다.
     */
    public boolean isLeaveWithinPenaltyWindow(Instant startAt, Instant now) {
        return now.isAfter(startAt.minusSeconds(properties.hostLeaveWindowMinutes() * 60L));
    }

    /**
     * 시작 직전 이탈 감점을 기록한다 (사유 {@code LEFT_WITHIN_WINDOW}). 감점 이벤트와 감사 로그를 함께 남기고, 같은
     * (스터디, 주최자, 사유)는 한 번만 부과한다.
     *
     * @return 새로 부과했으면 {@code true}, 이미 있어 건너뛰었으면 {@code false}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordLeftWithinWindow(long studyId, long hostId, Instant occurredAt) {
        if (events.existsByStudyIdAndHostIdAndReason(studyId, hostId, HostPenaltyReason.LEFT_WITHIN_WINDOW)) {
            return false;
        }
        int points = properties.hostLeavePenaltyPoints();
        events.save(HostPenaltyEvent.of(hostId, studyId, points, HostPenaltyReason.LEFT_WITHIN_WINDOW, occurredAt));
        audit.record(null, AuditAction.HOST_PENALTY, "STUDY", studyId,
                Map.of("hostId", hostId, "points", points, "reason", HostPenaltyReason.LEFT_WITHIN_WINDOW.name()));
        return true;
    }

    /**
     * 주최자 미도착 감점을 기록한다 (사유 {@code ABSENT}, PRD 4.13B). 영향받는 참여자가 있는지는 호출한 쪽이 따진다. 같은
     * (스터디, 주최자, 사유)는 한 번만 부과한다.
     *
     * @return 새로 부과했으면 {@code true}, 이미 있어 건너뛰었으면 {@code false}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordAbsent(long studyId, long hostId, Instant occurredAt) {
        if (events.existsByStudyIdAndHostIdAndReason(studyId, hostId, HostPenaltyReason.ABSENT)) {
            return false;
        }
        int points = properties.hostLeavePenaltyPoints();
        events.save(HostPenaltyEvent.of(hostId, studyId, points, HostPenaltyReason.ABSENT, occurredAt));
        audit.record(null, AuditAction.HOST_PENALTY, "STUDY", studyId,
                Map.of("hostId", hostId, "points", points, "reason", HostPenaltyReason.ABSENT.name()));
        return true;
    }
}
