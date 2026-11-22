package com.everystudy.trust.service;

import com.everystudy.common.audit.AuditAction;
import com.everystudy.common.audit.AuditLogger;
import com.everystudy.notification.service.NotificationRecorder;
import com.everystudy.notification.service.NotificationRequest;
import com.everystudy.notification.service.NotificationType;
import com.everystudy.trust.domain.HostPenaltyEvent;
import com.everystudy.trust.domain.HostPenaltyReason;
import com.everystudy.trust.domain.SanctionKind;
import com.everystudy.trust.domain.UserSanction;
import com.everystudy.trust.repository.HostPenaltyEventRepository;
import com.everystudy.trust.repository.UserSanctionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final UserSanctionRepository sanctions;
    private final NotificationRecorder notifications;
    private final TrustProperties properties;

    HostPenaltyRecorder(HostPenaltyEventRepository events, AuditLogger audit, UserSanctionRepository sanctions,
            NotificationRecorder notifications, TrustProperties properties) {
        this.events = events;
        this.audit = audit;
        this.sanctions = sanctions;
        this.notifications = notifications;
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
        judge(studyId, hostId, occurredAt);
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
        judge(studyId, hostId, occurredAt);
        return true;
    }

    /**
     * 방금 부과한 감점까지 90일 롤링 합계로 주최자 단계를 판정한다 (PRD 4.13C). 감점마다 한 번만 부르므로(같은 스터디·사유는
     * 한 번만 부과) 같은 감점으로 제재가 늘지 않는다. 이미 더 늦게 끝나는 개설 제한이 있으면 같은 제한을 다시 걸지 않는다.
     */
    private void judge(long studyId, long hostId, Instant now) {
        Instant since = now.minus(Duration.ofDays(properties.hostPenaltyWindowDays()));
        long total = events.sumPointsSince(hostId, since);
        if (total >= properties.hostRestrictPoints()) {
            boolean review = total >= properties.hostReviewPoints();
            int days = review ? properties.hostLongRestrictDays() : properties.hostRestrictDays();
            restrict(studyId, hostId, now, total, days, review);
        } else if (total >= properties.hostWarnPoints()) {
            notifications.record(NotificationRequest.allChannels(hostId, NotificationType.HOST_WARNING, studyId,
                    "주최자 경고를 받았어요",
                    "최근 " + properties.hostPenaltyWindowDays() + "일 동안 주최 감점이 " + total
                            + "점이에요. 감점이 더 쌓이면 새 스터디 개설이 제한돼요.",
                    "HOST_WARNING:" + studyId + ":" + hostId, now, validUntil(now)));
        }
    }

    private void restrict(long studyId, long hostId, Instant now, long total, int days, boolean review) {
        Instant endsAt = now.plus(Duration.ofDays(days));
        Optional<Instant> current = sanctions.findLatestActiveEnd(hostId, Set.of(SanctionKind.HOST_RESTRICT), now);
        if (current.isPresent() && !current.get().isBefore(endsAt)) {
            return;
        }
        String reason = "최근 " + properties.hostPenaltyWindowDays() + "일 주최 감점 합계 " + total + "점"
                + (review ? ", 운영자 검토" : "");
        UserSanction sanction = sanctions.save(UserSanction.autoHostRestrict(hostId, reason, now, endsAt, review));
        audit.record(null, AuditAction.SANCTION_GRANT, "USER", hostId,
                Map.of("kind", SanctionKind.HOST_RESTRICT.name(), "source", "AUTO", "studyId", studyId, "days", days,
                        "totalPoints", total, "reviewRequired", review, "endsAt", endsAt.toString(),
                        "sanctionId", sanction.getId()));
        notifications.record(NotificationRequest.allChannels(hostId, NotificationType.SANCTION_APPLIED, studyId,
                "새 스터디 개설이 제한됐어요",
                days + "일간 새 스터디를 개설할 수 없어요. 이미 만든 스터디는 그대로 유지돼요.",
                "SANCTION_APPLIED:HOST:" + studyId + ":" + hostId, now, validUntil(now)));
    }

    private Instant validUntil(Instant now) {
        return now.plus(Duration.ofDays(properties.noticeValidDays()));
    }
}
