package com.everystudy.study.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.random.RandomSource;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.notification.service.NotificationRecorder;
import com.everystudy.notification.service.NotificationRequest;
import com.everystudy.notification.service.NotificationType;
import com.everystudy.study.domain.CancelReason;
import com.everystudy.study.domain.CancelType;
import com.everystudy.study.domain.HostTransfer;
import com.everystudy.study.domain.MemberStatus;
import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyMember;
import com.everystudy.study.domain.StudyStatus;
import com.everystudy.study.domain.StudyWaiting;
import com.everystudy.study.event.HostChanged;
import com.everystudy.study.event.MemberLeft;
import com.everystudy.study.event.StudyCancelled;
import com.everystudy.study.repository.HostTransferRepository;
import com.everystudy.study.repository.StudyMemberRepository;
import com.everystudy.study.repository.StudyRepository;
import com.everystudy.study.repository.StudyWaitingRepository;
import com.everystudy.trust.service.AttendanceEventRecorder;
import com.everystudy.trust.service.HostPenaltyRecorder;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 나가기와 사전 조회 (PRD 4.4·4.6·4.13, API 4장). 참여자의 나가기는 참여 취소이고, 주최자의 나가기는 주최 포기라
 * 승계·파투·감점 규칙을 적용한다 (T-020, ADR-0004 결정 4). 둘 다 {@code study} 행을 잠근 안에서 처리한다.
 */
@Service
public class StudyLeaveService {

    /** 주최자의 나가기 결과 (API 4장 {@code outcome}). */
    public enum HostOutcome {
        /** 남은 참여자 중 한 명이 새 주최자가 됐다. */
        TRANSFERRED,
        /** 주최자만 남아 있어 스터디가 파투됐다. */
        CANCELLED,
        /** 시작한 뒤라 승계 없이 나갔다. 세션은 그대로 진행된다 (PLAN X-13). */
        LEFT_AFTER_START
    }

    /**
     * 나가기 결과. 참여자는 {@code cancelType} 만, 주최자는 {@code outcome} 만 담긴다(없는 쪽은 응답에서 생략).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Left(CancelType cancelType, HostOutcome outcome) {
    }

    /**
     * 나가기 사전 조회 (API 4장). 참여자는 {@code cancelType}, 주최자는 {@code hostOutcome} 이 담긴다(없는 쪽은 생략).
     *
     * @param penaltyApplies 참여자는 지표(출석률)에 영향이 가는가(직전 취소만, PRD 4.6), 주최자는 주최자 감점이 붙는가
     *                       (PRD 4.13B)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Preview(CancelType cancelType, HostOutcome hostOutcome, boolean penaltyApplies) {
    }

    private final StudyRepository studies;
    private final StudyMemberRepository members;
    private final StudyWaitingRepository waiting;
    private final HostTransferRepository transfers;
    private final AttendanceEventRecorder events;
    private final HostPenaltyRecorder penalties;
    private final NotificationRecorder notifications;
    private final WaitingNotifier waitingNotifier;
    private final StudyProperties properties;
    private final StudyLockProperties lock;
    private final TimeProvider time;
    private final RandomSource random;
    private final ApplicationEventPublisher eventPublisher;

    StudyLeaveService(StudyRepository studies, StudyMemberRepository members, StudyWaitingRepository waiting,
            HostTransferRepository transfers, AttendanceEventRecorder events, HostPenaltyRecorder penalties,
            NotificationRecorder notifications, WaitingNotifier waitingNotifier, StudyProperties properties,
            StudyLockProperties lock, TimeProvider time, RandomSource random,
            ApplicationEventPublisher eventPublisher) {
        this.studies = studies;
        this.members = members;
        this.waiting = waiting;
        this.transfers = transfers;
        this.events = events;
        this.penalties = penalties;
        this.notifications = notifications;
        this.waitingNotifier = waitingNotifier;
        this.properties = properties;
        this.lock = lock;
        this.time = time;
        this.random = random;
        this.eventPublisher = eventPublisher;
    }

    /**
     * {@code study} 행을 잠근 뒤 {@code LEFT} 로 바꾼다. 참여자는 사전/직전 취소를 {@code attendance_event} 에 남기고,
     * 주최자는 승계·파투·시작 후 나가기 중 하나로 처리한다({@link #leaveAsHost}). 정원이 찼던 스터디에서 나가 자리가 나면
     * 대기자에게 알림을 같은 트랜잭션에서 아웃박스에 기록한다. 격리 수준은 참여 신청과 같은 이유로 {@code READ COMMITTED}
     * 다 ({@link StudyJoinService#join}).
     *
     * @throws BusinessException 스터디가 없으면 {@code NOT_FOUND}, 참여 중이 아니면 {@code FORBIDDEN}, 참여자인데 시작했거나
     *                           종료·파투면(주최자는 종료·파투면) {@code SESSION_STARTED}
     * @throws org.springframework.dao.PessimisticLockingFailureException 락 대기 시간 초과 ({@code LOCK_TIMEOUT})
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Left leave(long studyId, long userId) {
        Study study = studies.findByIdForUpdate(studyId, lock.timeout())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Instant now = time.now();
        if (study.getHostId().equals(userId)) {
            return leaveAsHost(study, userId, now);
        }
        StudyMember member = requireLeavingMember(study, userId, now);
        CancelType type = StudyLeaveRules.cancelType(study.getStartAt(), now, properties);

        long activeBefore = members.countByStudyIdAndStatus(studyId, MemberStatus.ACTIVE);
        member.leave(now, type);
        eventPublisher.publishEvent(new MemberLeft(studyId, userId));
        if (type == CancelType.EARLY) {
            events.recordEarlyCancel(studyId, userId, now);
        } else {
            events.recordLateCancel(studyId, userId, now);
        }
        if (activeBefore >= study.getCapacity()) {
            waitingNotifier.notifySeatOpened(study, String.valueOf(userId), now);
        }
        return new Left(type, null);
    }

    /**
     * 주최자의 나가기 = 주최 포기 (PRD 4.13A). 락 안에서 남은 참여자 집합이 고정되므로 참여자 나가기와 겹쳐도 승계 대상은 한
     * 명으로만 정해진다 (ADR-0004 결정 4).
     *
     * <ul>
     *   <li>시작 후: 승계하지 않는다. {@code study_member} 는 {@code LEFT}, {@code host_id}·체크인·출석은 그대로 둔다
     *       (PLAN X-13).</li>
     *   <li>시작 전, 남은 참여자 없음: 파투. 참여자가 없으니 지표 영향·감점이 없다. 대기 행은 알리고 지운다.</li>
     *   <li>시작 전, 남은 참여자 있음: 무작위 1명 승계. 시작 3시간 이내면 주최자 감점 (PRD 4.13B).</li>
     * </ul>
     */
    private Left leaveAsHost(Study study, long hostId, Instant now) {
        StudyMember hostMember = members.findByStudyIdAndUserId(study.getId(), hostId)
                .filter(StudyMember::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.FORBIDDEN));
        requireOngoing(study);
        long activeBefore = members.countByStudyIdAndStatus(study.getId(), MemberStatus.ACTIVE);

        if (!now.isBefore(study.getStartAt())) {
            hostMember.leaveAsHost(now);
            eventPublisher.publishEvent(new MemberLeft(study.getId(), hostId));
            return new Left(null, HostOutcome.LEFT_AFTER_START);
        }

        List<StudyMember> others = otherActiveMembers(study.getId(), hostId);
        hostMember.leaveAsHost(now);
        eventPublisher.publishEvent(new MemberLeft(study.getId(), hostId));
        if (others.isEmpty()) {
            study.cancel(CancelReason.HOST_LEFT, now);
            notifyWaitersCancelled(study, now);
            waiting.deleteByStudyId(study.getId());
            eventPublisher.publishEvent(new StudyCancelled(study.getId(), CancelReason.HOST_LEFT.name()));
            return new Left(null, HostOutcome.CANCELLED);
        }

        long newHostId = others.get(random.nextInt(others.size())).getUserId();
        study.transferHostTo(newHostId, now);
        transfers.save(HostTransfer.of(study.getId(), hostId, newHostId, now));
        eventPublisher.publishEvent(new HostChanged(study.getId(), hostId, newHostId));
        if (penalties.isLeaveWithinPenaltyWindow(study.getStartAt(), now)) {
            penalties.recordLeftWithinWindow(study.getId(), hostId, now);
        }
        notifyNewHost(study, newHostId, now);
        if (activeBefore >= study.getCapacity()) {
            waitingNotifier.notifySeatOpened(study, String.valueOf(hostId), now);
        }
        return new Left(null, HostOutcome.TRANSFERRED);
    }

    /** 나가기와 같은 검사를 하고 어떻게 기록될지만 알려 준다. 아무것도 바꾸지 않는다. */
    @Transactional(readOnly = true)
    public Preview preview(long studyId, long userId) {
        Study study = studies.findById(studyId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Instant now = time.now();
        if (study.getHostId().equals(userId)) {
            return previewAsHost(study, userId, now);
        }
        requireLeavingMember(study, userId, now);
        CancelType type = StudyLeaveRules.cancelType(study.getStartAt(), now, properties);
        return new Preview(type, null, type == CancelType.LATE);
    }

    private Preview previewAsHost(Study study, long hostId, Instant now) {
        members.findByStudyIdAndUserId(study.getId(), hostId)
                .filter(StudyMember::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.FORBIDDEN));
        requireOngoing(study);
        if (!now.isBefore(study.getStartAt())) {
            return new Preview(null, HostOutcome.LEFT_AFTER_START, false);
        }
        if (otherActiveMembers(study.getId(), hostId).isEmpty()) {
            return new Preview(null, HostOutcome.CANCELLED, false);
        }
        return new Preview(null, HostOutcome.TRANSFERRED,
                penalties.isLeaveWithinPenaltyWindow(study.getStartAt(), now));
    }

    /** 남은 참여자: 주최자를 뺀 {@code ACTIVE} 참여자 (참여 순서). */
    private List<StudyMember> otherActiveMembers(long studyId, long hostId) {
        return members.findByStudyIdAndStatusOrderByJoinedAtAscIdAsc(studyId, MemberStatus.ACTIVE).stream()
                .filter(member -> !member.getUserId().equals(hostId))
                .toList();
    }

    /** 종료·파투된 스터디의 나가기는 막는다. 시작 뒤라도 진행 중이면(주최자) 나갈 수 있다. */
    private static void requireOngoing(Study study) {
        if (study.getStatus() != StudyStatus.ACTIVE) {
            throw new BusinessException(StudyErrorCode.SESSION_STARTED);
        }
    }

    private StudyMember requireLeavingMember(Study study, long userId, Instant now) {
        StudyMember member = members.findByStudyIdAndUserId(study.getId(), userId)
                .filter(StudyMember::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.FORBIDDEN));
        StudyLeaveRules.requireLeavable(study.getStatus(), study.getStartAt(), now);
        return member;
    }

    /** 새 주최자에게 알린다. 수락·거절 없이 자동 배정이라 알림만 보낸다 (PRD 4.13A). 일정이 끝나면 의미가 없다. */
    private void notifyNewHost(Study study, long newHostId, Instant now) {
        Instant endsAt = study.getStartAt().plusSeconds(study.getDurationMin() * 60L);
        String key = "HOST_ASSIGNED:" + study.getId() + ":" + newHostId + ":" + now.toEpochMilli();
        notifications.record(NotificationRequest.allChannels(newHostId, NotificationType.HOST_ASSIGNED,
                study.getId(), "새 주최자가 되었어요",
                "'" + abbreviate(study.getTitle()) + "' 의 주최자가 나가 새 주최자로 배정되었어요. 일정과 장소는 그대로예요.",
                key, now, endsAt));
    }

    /** 파투를 대기자에게 알린다. 참여자는 주최자뿐이었으므로 알릴 사람은 대기자뿐이다. 시작 시각이 지나면 의미가 없다. */
    private void notifyWaitersCancelled(Study study, Instant now) {
        String title = "대기하던 스터디가 취소되었어요";
        String body = "'" + abbreviate(study.getTitle()) + "' 은(는) 주최자가 나가 취소되었어요.";
        for (StudyWaiting waiter : waiting.findByStudyId(study.getId())) {
            String key = "STUDY_CANCELLED:" + study.getId() + ":" + waiter.getUserId();
            notifications.record(NotificationRequest.allChannels(waiter.getUserId(), NotificationType.STUDY_CANCELLED,
                    study.getId(), title, body, key, now, study.getStartAt()));
        }
    }

    private static String abbreviate(String title) {
        return WaitingNotifier.abbreviate(title);
    }
}
