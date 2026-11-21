package com.everystudy.study.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.notification.service.NotificationRecorder;
import com.everystudy.notification.service.NotificationRequest;
import com.everystudy.notification.service.NotificationType;
import com.everystudy.study.domain.CancelType;
import com.everystudy.study.domain.MemberStatus;
import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyMember;
import com.everystudy.study.domain.StudyWaiting;
import com.everystudy.study.repository.StudyMemberRepository;
import com.everystudy.study.repository.StudyRepository;
import com.everystudy.study.repository.StudyWaitingRepository;
import com.everystudy.trust.service.AttendanceEventRecorder;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참여자 나가기와 사전 조회 (PRD 4.4·4.6, API 4장). 주최자의 나가기(승계·파투·감점)는 T-020 에서 더한다. 그때까지
 * 주최자가 부르면 {@code FORBIDDEN} 이다 (PLAN X-21).
 */
@Service
public class StudyLeaveService {

    /** 나가기 결과. */
    public record Left(CancelType cancelType) {
    }

    /**
     * 나가기 사전 조회 (API 4장). {@code hostOutcome} 은 주최자의 나가기가 생기는 T-020 에서 채우고, 그전에는 항상 비어 있다.
     *
     * @param penaltyApplies 지표(출석률)에 영향이 가는가: 직전 취소만 해당한다 (PRD 4.6)
     */
    public record Preview(CancelType cancelType, @JsonInclude(JsonInclude.Include.NON_NULL) String hostOutcome,
            boolean penaltyApplies) {
    }

    private final StudyRepository studies;
    private final StudyMemberRepository members;
    private final StudyWaitingRepository waiting;
    private final AttendanceEventRecorder events;
    private final NotificationRecorder notifications;
    private final ApplicantEligibility eligibility;
    private final StudyProperties properties;
    private final StudyLockProperties lock;
    private final TimeProvider time;

    StudyLeaveService(StudyRepository studies, StudyMemberRepository members, StudyWaitingRepository waiting,
            AttendanceEventRecorder events, NotificationRecorder notifications, ApplicantEligibility eligibility,
            StudyProperties properties, StudyLockProperties lock, TimeProvider time) {
        this.studies = studies;
        this.members = members;
        this.waiting = waiting;
        this.events = events;
        this.notifications = notifications;
        this.eligibility = eligibility;
        this.properties = properties;
        this.lock = lock;
        this.time = time;
    }

    /**
     * {@code study} 행을 잠근 뒤 {@code LEFT} 로 바꾸고, 사전/직전 취소를 {@code attendance_event} 에 남긴다. 정원이 찼던
     * 스터디에서 나가 자리가 나면 대기자에게 알림을 같은 트랜잭션에서 아웃박스에 기록한다. 격리 수준은 참여 신청과 같은
     * 이유로 {@code READ COMMITTED} 다 ({@link StudyJoinService#join}).
     *
     * @throws BusinessException 스터디가 없으면 {@code NOT_FOUND}, 주최자이거나 참여자가 아니면 {@code FORBIDDEN},
     *                           시작했거나 종료·파투면 {@code SESSION_STARTED}
     * @throws org.springframework.dao.PessimisticLockingFailureException 락 대기 시간 초과 ({@code LOCK_TIMEOUT})
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Left leave(long studyId, long userId) {
        Study study = studies.findByIdForUpdate(studyId, lock.timeout())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Instant now = time.now();
        StudyMember member = requireLeavingMember(study, userId, now);
        CancelType type = StudyLeaveRules.cancelType(study.getStartAt(), now, properties);

        long activeBefore = members.countByStudyIdAndStatus(studyId, MemberStatus.ACTIVE);
        member.leave(now, type);
        if (type == CancelType.EARLY) {
            events.recordEarlyCancel(studyId, userId, now);
        } else {
            events.recordLateCancel(studyId, userId, now);
        }
        if (activeBefore >= study.getCapacity()) {
            notifyWaiters(study, userId, now);
        }
        return new Left(type);
    }

    /** 나가기와 같은 검사를 하고 어떻게 기록될지만 알려 준다. 아무것도 바꾸지 않는다. */
    @Transactional(readOnly = true)
    public Preview preview(long studyId, long userId) {
        Study study = studies.findById(studyId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Instant now = time.now();
        requireLeavingMember(study, userId, now);
        CancelType type = StudyLeaveRules.cancelType(study.getStartAt(), now, properties);
        return new Preview(type, null, type == CancelType.LATE);
    }

    private StudyMember requireLeavingMember(Study study, long userId, Instant now) {
        if (study.getHostId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        StudyMember member = members.findByStudyIdAndUserId(study.getId(), userId)
                .filter(StudyMember::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.FORBIDDEN));
        StudyLeaveRules.requireLeavable(study.getStatus(), study.getStartAt(), now);
        return member;
    }

    /**
     * 자리가 새로 났으니 대기자에게 알린다 (PRD 4.14). 알림 시점에 신청 자격을 다시 검사해 지금도 신청할 수 있는 사람에게만
     * 보낸다 (PLAN X-20). 체크인이 열린 뒤에는 신청이 닫혀 있어 보내지 않는다 (X-16). 자격을 잃은 대기자의 행은 지우지 않는다.
     */
    private void notifyWaiters(Study study, long leftUserId, Instant now) {
        Instant applicationsCloseAt = StudyTimeline.checkinOpensAt(study.getStartAt(), properties);
        if (!now.isBefore(applicationsCloseAt)) {
            return;
        }
        String title = "참여하려던 스터디에 자리가 났어요";
        String body = "'" + abbreviate(study.getTitle()) + "' 에 자리가 생겼어요. 먼저 신청하는 분이 참여해요.";
        for (StudyWaiting waiter : waiting.findByStudyId(study.getId())) {
            long waiterId = waiter.getUserId();
            if (!eligibility.isEligible(study, waiterId, now)) {
                continue;
            }
            // 같은 사람이 나갔다 다시 들어와 또 나가도 새 자리이므로 나간 시각을 키에 넣는다.
            String key = "WAITING_OPENED:" + study.getId() + ":" + waiterId + ":" + leftUserId + ":"
                    + now.toEpochMilli();
            notifications.record(NotificationRequest.allChannels(waiterId, NotificationType.WAITING_OPENED,
                    study.getId(), title, body, key, now, applicationsCloseAt));
        }
    }

    private static String abbreviate(String title) {
        return title.length() <= 60 ? title : title.substring(0, 59) + "…";
    }
}
