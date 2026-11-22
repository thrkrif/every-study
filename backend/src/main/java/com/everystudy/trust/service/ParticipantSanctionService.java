package com.everystudy.trust.service;

import com.everystudy.common.audit.AuditAction;
import com.everystudy.common.audit.AuditLogger;
import com.everystudy.notification.service.NotificationRecorder;
import com.everystudy.notification.service.NotificationRequest;
import com.everystudy.notification.service.NotificationType;
import com.everystudy.trust.domain.SanctionKind;
import com.everystudy.trust.domain.UserSanction;
import com.everystudy.trust.repository.AttendanceEventRepository;
import com.everystudy.trust.repository.UserSanctionRepository;
import com.everystudy.trust.service.SanctionJudgement.Decision;
import com.everystudy.trust.service.SanctionJudgement.Trigger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참여자 제재를 판정해 부여하는 공개 서비스 (PRD 4.8, ARCHITECTURE 3장). 노쇼 확정과 지각 기록 때 그 트랜잭션 안에서 부른다.
 * 제재 행·감사 로그·알림이 출석 이벤트와 함께 커밋되거나 함께 롤백된다. 이의 인정 정정(T-056)도 이 서비스를 재사용한다.
 *
 * <p>같은 이벤트로 다시 불러도 제재가 늘지 않는다. 제한은 해제되지 않은 자동 제한이 있으면 다시 걸지 않고, 정지는 유효한
 * 정지가 있으면 다시 걸지 않으며, 알림은 (종류, 스터디, 사용자) 키로 한 번만 만든다.
 */
@Service
public class ParticipantSanctionService {

    private static final Set<SanctionKind> SUSPEND_ONLY = Set.of(SanctionKind.SUSPEND);

    private final AttendanceEventRepository events;
    private final UserSanctionRepository sanctions;
    private final NotificationRecorder notifications;
    private final AuditLogger audit;
    private final TrustProperties properties;

    ParticipantSanctionService(AttendanceEventRepository events, UserSanctionRepository sanctions,
            NotificationRecorder notifications, AuditLogger audit, TrustProperties properties) {
        this.events = events;
        this.sanctions = sanctions;
        this.notifications = notifications;
        this.audit = audit;
        this.properties = properties;
    }

    /** 노쇼가 확정됐다. 이 이벤트가 이미 저장된 상태에서 부른다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void judgeAfterNoShow(long userId, long studyId, Instant now) {
        judge(Trigger.NO_SHOW, userId, studyId, now);
    }

    /** 지각이 기록됐다. 이 이벤트가 이미 저장된 상태에서 부른다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void judgeAfterLate(long userId, long studyId, Instant now) {
        judge(Trigger.LATE, userId, studyId, now);
    }

    /**
     * 이의 인정으로 판정이 바뀐 뒤, 그 판정 때문에 걸린 자동 제재가 아직 근거가 있는지 다시 본다 (PLAN T-056). 정정된 판정이
     * 난 시각 이후에 시작된 자동 제한·정지 중, 정정을 반영한 기록으로는 그 단계에 이르지 않는 것을 해제한다(제한 먼저, 그다음
     * 정지). 다른 근거가 남아 있으면 그대로 둔다. 해제마다 감사 로그를 남긴다.
     *
     * @param correctedEventAt 정정된 판정이 난 시각
     * @param operatorId 결정한 운영자
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reassessAfterCorrection(long userId, Instant correctedEventAt, long operatorId, Instant now) {
        List<SessionVerdict> verdicts = SessionVerdict.currentOf(events.findByUserIdOrderByOccurredAtDescIdDesc(userId));
        revokeUnsupported(userId, SanctionKind.JOIN_RESTRICT, Decision.JOIN_RESTRICT, verdicts, null,
                correctedEventAt, operatorId, now);
        Instant restrictStartedAt = sanctions.findLatestAutoStart(userId, SanctionKind.JOIN_RESTRICT).orElse(null);
        revokeUnsupported(userId, SanctionKind.SUSPEND, Decision.SUSPEND, verdicts, restrictStartedAt,
                correctedEventAt, operatorId, now);
    }

    private void revokeUnsupported(long userId, SanctionKind kind, Decision required, List<SessionVerdict> verdicts,
            Instant restrictStartedAt, Instant correctedEventAt, long operatorId, Instant now) {
        Decision supported = SanctionJudgement.decide(Trigger.LATE, verdicts, restrictStartedAt, properties);
        if (supported.ordinal() >= required.ordinal()) {
            return;
        }
        for (UserSanction sanction : sanctions.findActiveAuto(userId, Set.of(kind), now)) {
            if (sanction.getStartsAt().isBefore(correctedEventAt)) {
                continue;
            }
            sanction.revoke(now);
            audit.record(operatorId, AuditAction.SANCTION_REVOKE, "USER", userId,
                    Map.of("kind", kind.name(), "sanctionId", sanction.getId(), "reason", "이의 인정으로 판정이 정정됨"));
        }
    }

    private void judge(Trigger trigger, long userId, long studyId, Instant now) {
        List<SessionVerdict> verdicts = SessionVerdict.currentOf(events.findByUserIdOrderByOccurredAtDescIdDesc(userId));
        Instant restrictStartedAt = sanctions.findLatestAutoStart(userId, SanctionKind.JOIN_RESTRICT).orElse(null);
        Decision decision = SanctionJudgement.decide(trigger, verdicts, restrictStartedAt, properties);
        switch (decision) {
            case WARN -> warn(trigger, userId, studyId, now);
            case JOIN_RESTRICT -> grant(SanctionKind.JOIN_RESTRICT, userId, studyId, now);
            case SUSPEND -> {
                // 정지 기간이 남아 있으면 같은 정지를 다시 걸지 않는다.
                if (sanctions.findLatestActiveEnd(userId, SUSPEND_ONLY, now).isEmpty()) {
                    grant(SanctionKind.SUSPEND, userId, studyId, now);
                }
            }
            case NONE -> {
            }
        }
    }

    private void warn(Trigger trigger, long userId, long studyId, Instant now) {
        String title = trigger == Trigger.NO_SHOW ? "노쇼가 기록됐어요" : "지각이 쌓이고 있어요";
        String body = trigger == Trigger.NO_SHOW
                ? "출석 기록이 없는 모임이 노쇼로 확정됐어요. 노쇼가 더해지면 새 스터디 참여 신청이 제한될 수 있어요."
                : "최근 모임에서 지각이 " + properties.lateAsNoShow()
                        + "회 이상이에요. 지각이 더 쌓이면 노쇼로 환산돼 제재를 받을 수 있어요.";
        notifications.record(NotificationRequest.allChannels(userId, NotificationType.SANCTION_WARNING, studyId, title,
                body, "SANCTION_WARNING:" + studyId + ":" + userId, now, validUntil(now)));
    }

    private void grant(SanctionKind kind, long userId, long studyId, Instant now) {
        boolean suspend = kind == SanctionKind.SUSPEND;
        int days = suspend ? properties.suspendDays() : properties.joinRestrictDays();
        Instant endsAt = now.plus(Duration.ofDays(days));
        String reason = suspend ? "노쇼·지각 재발 또는 최근 10회 환산 노쇼 누적" : "최근 5회 환산 노쇼 누적";
        UserSanction sanction = sanctions.save(UserSanction.auto(userId, kind, reason, now, endsAt));
        audit.record(null, AuditAction.SANCTION_GRANT, "USER", userId,
                Map.of("kind", kind.name(), "source", "AUTO", "studyId", studyId, "days", days,
                        "endsAt", endsAt.toString(), "sanctionId", sanction.getId()));
        String title = suspend ? "이용이 정지됐어요" : "새 스터디 참여 신청이 제한됐어요";
        String body = suspend
                ? days + "일간 이용이 정지돼요. 정지가 끝나면 다시 이용할 수 있어요."
                : days + "일간 새 스터디에 참여 신청할 수 없어요. 이미 참여 중인 스터디는 그대로 유지돼요.";
        notifications.record(NotificationRequest.allChannels(userId, NotificationType.SANCTION_APPLIED, studyId, title,
                body, "SANCTION_APPLIED:" + studyId + ":" + userId, now, validUntil(now)));
    }

    private Instant validUntil(Instant now) {
        return now.plus(Duration.ofDays(properties.noticeValidDays()));
    }
}
