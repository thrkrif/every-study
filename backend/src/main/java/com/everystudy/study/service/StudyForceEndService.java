package com.everystudy.study.service;

import com.everystudy.common.audit.AuditAction;
import com.everystudy.common.audit.AuditLogger;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.notification.service.NotificationRecorder;
import com.everystudy.notification.service.NotificationRequest;
import com.everystudy.notification.service.NotificationType;
import com.everystudy.study.domain.CancelReason;
import com.everystudy.study.domain.MemberStatus;
import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyMember;
import com.everystudy.study.domain.StudyStatus;
import com.everystudy.study.domain.StudyWaiting;
import com.everystudy.study.event.StudyCancelled;
import com.everystudy.study.repository.StudyMemberRepository;
import com.everystudy.study.repository.StudyRepository;
import com.everystudy.study.repository.StudyWaitingRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 강제 종료 (PRD 4.11 조치 2, API 7장, PLAN T-054). 강제 종료는 <b>파투</b>로 처리한다(취소 사유 {@code OPERATOR}).
 * 참여자의 지표에는 영향이 없다: 참여자의 {@code VOIDED} 는 여기서 남기지 않고 체크인 마감 뒤의 판정 작업(T-031)이 파투
 * 스터디의 참여자마다 남긴다.
 *
 * <p>{@code study} 행을 잠근 안에서 파투하고, 대기 행을 지우고, 참여자·대기자에게 알린다. 예약해 둔 일정 알림 취소와 채팅방
 * 폐쇄는 {@link StudyCancelled} 를 받은 쪽이 같은 트랜잭션에서 한다(PLAN D-16). 이미 운영자가 강제 종료한 스터디는 다시
 * 불러도 아무것도 하지 않는다(멱등).
 */
@Service
public class StudyForceEndService {

    private static final Duration NOTICE_AFTER_START = Duration.ofDays(1);

    private final StudyRepository studies;
    private final StudyMemberRepository members;
    private final StudyWaitingRepository waiting;
    private final NotificationRecorder notifications;
    private final AuditLogger audit;
    private final StudyLockProperties lock;
    private final TimeProvider time;
    private final ApplicationEventPublisher eventPublisher;

    StudyForceEndService(StudyRepository studies, StudyMemberRepository members, StudyWaitingRepository waiting,
            NotificationRecorder notifications, AuditLogger audit, StudyLockProperties lock, TimeProvider time,
            ApplicationEventPublisher eventPublisher) {
        this.studies = studies;
        this.members = members;
        this.waiting = waiting;
        this.notifications = notifications;
        this.audit = audit;
        this.lock = lock;
        this.time = time;
        this.eventPublisher = eventPublisher;
    }

    /**
     * @param note 강제 종료 사유. 호출한 쪽이 비어 있지 않고 200자 이하임을 보장한다
     * @throws BusinessException 스터디가 없으면 {@code NOT_FOUND}, 이미 정상 종료됐거나 주최자가 포기한 파투면
     *                           {@code STUDY_CLOSED}
     * @throws org.springframework.dao.PessimisticLockingFailureException 락 대기 시간 초과 ({@code LOCK_TIMEOUT})
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void forceEnd(long operatorId, long studyId, String note) {
        Study study = studies.findByIdForUpdate(studyId, lock.timeout())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (study.getStatus() == StudyStatus.CANCELLED && study.getCancelReason() == CancelReason.OPERATOR) {
            return;
        }
        if (study.getStatus() != StudyStatus.ACTIVE) {
            throw new BusinessException(StudyErrorCode.STUDY_CLOSED);
        }
        Instant now = time.now();
        Set<Long> recipients = new LinkedHashSet<>();
        members.findByStudyIdAndStatusOrderByJoinedAtAscIdAsc(studyId, MemberStatus.ACTIVE)
                .stream().map(StudyMember::getUserId).forEach(recipients::add);
        waiting.findByStudyId(studyId).stream().map(StudyWaiting::getUserId).forEach(recipients::add);

        study.cancel(CancelReason.OPERATOR, now);
        waiting.deleteByStudyId(studyId);
        notifyCancelled(study, recipients, now);
        eventPublisher.publishEvent(new StudyCancelled(studyId, CancelReason.OPERATOR.name()));
        audit.record(operatorId, AuditAction.STUDY_FORCE_END, "STUDY", studyId,
                Map.of("note", note, "notified", recipients.size()));
    }

    /** 참여자와 대기자에게 알린다. 시작 전이면 시작 시각까지, 이미 시작했으면 하루 동안 유효하다. */
    private void notifyCancelled(Study study, Set<Long> recipients, Instant now) {
        Instant expiresAt = study.getStartAt().isAfter(now) ? study.getStartAt() : now.plus(NOTICE_AFTER_START);
        String title = "스터디가 운영자에 의해 종료되었어요";
        String body = "'" + WaitingNotifier.abbreviate(study.getTitle()) + "' 은(는) 운영 정책에 따라 종료되어 진행되지 않아요.";
        for (long userId : recipients) {
            notifications.record(NotificationRequest.allChannels(userId, NotificationType.STUDY_CANCELLED,
                    study.getId(), title, body, "STUDY_CANCELLED:" + study.getId() + ":" + userId, now, expiresAt));
        }
    }
}
