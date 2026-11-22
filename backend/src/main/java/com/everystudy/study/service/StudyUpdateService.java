package com.everystudy.study.service;

import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.error.FieldViolation;
import com.everystudy.common.error.InvalidInputException;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.domain.MemberStatus;
import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyMember;
import com.everystudy.study.domain.StudyStatus;
import com.everystudy.study.repository.StudyMemberRepository;
import com.everystudy.study.repository.StudyRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스터디 수정과 정원 변경 (PRD 4.2·4.14, API 4장, PLAN T-022). 둘 다 멤버 구성과 맞물리므로 {@code study} 행을 잠근 안에서
 * 처리한다 (ADR-0004 결정 1). 격리 수준은 참여 신청과 같은 이유로 {@code READ COMMITTED} 다 ({@link StudyJoinService#join}).
 *
 * <p>수정할 수 있는 사람은 지금의 주최자({@code study.host_id} 이고 {@code ACTIVE})뿐이다. 시작 뒤에 나간 주최자는
 * {@code host_id} 가 남아 있어도 수정할 수 없다 (PLAN X-13).
 */
@Service
public class StudyUpdateService {

    /**
     * 수정할 값. {@code null} 인 항목은 바꾸지 않는다. 일정·장소 항목({@code startAt}, {@code durationMin}, {@code place})은
     * 하나라도 있으면 일정 수정으로 본다.
     */
    public record Changes(String title, String description, Instant startAt, Integer durationMin, Study.Place place) {

        boolean touchesSchedule() {
            return startAt != null || durationMin != null || place != null;
        }
    }

    private final StudyRepository studies;
    private final StudyMemberRepository members;
    private final WaitingNotifier waitingNotifier;
    private final StudyProperties properties;
    private final StudyLockProperties lock;
    private final TimeProvider time;

    StudyUpdateService(StudyRepository studies, StudyMemberRepository members, WaitingNotifier waitingNotifier,
            StudyProperties properties, StudyLockProperties lock, TimeProvider time) {
        this.studies = studies;
        this.members = members;
        this.waitingNotifier = waitingNotifier;
        this.properties = properties;
        this.lock = lock;
        this.time = time;
    }

    /**
     * 제목·소개는 진행 중이면 언제든 고치고, 일정·장소는 주최자 말고 {@code ACTIVE} 참여자가 없고 시작 전일 때만 고친다.
     * 일정을 바꾸면 시작 시각의 KST 날짜({@code session_date})도 함께 바꾼다. 바꾸지 않는 일정 항목은 지금 값을 쓰고, 합친
     * 일정이 개설 규칙을 지키는지 다시 검사한다.
     *
     * @throws BusinessException 스터디가 없으면 {@code NOT_FOUND}, 주최자가 아니면 {@code FORBIDDEN}, 종료·파투면
     *                           {@code STUDY_CLOSED}, 일정을 못 고치면 {@code SCHEDULE_LOCKED}
     * @throws InvalidInputException 일정 값이 개설 규칙에 어긋남
     * @throws org.springframework.dao.PessimisticLockingFailureException 락 대기 시간 초과 ({@code LOCK_TIMEOUT})
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void update(long studyId, long userId, Changes changes) {
        Study study = lockedHostStudy(studyId, userId);
        Instant now = time.now();

        if (changes.touchesSchedule()) {
            long others = members.countByStudyIdAndStatus(studyId, MemberStatus.ACTIVE) - 1;
            if (!StudyTimeline.isScheduleEditable(study.getStatus(), study.getStartAt(), Math.toIntExact(others), now)) {
                throw new BusinessException(StudyErrorCode.SCHEDULE_LOCKED);
            }
            Instant startAt = changes.startAt() != null ? changes.startAt() : study.getStartAt();
            int durationMin = changes.durationMin() != null ? changes.durationMin() : study.getDurationMin();
            List<FieldViolation> violations = StudyCreationRules.checkSchedule(startAt, durationMin, now, properties);
            if (!violations.isEmpty()) {
                throw new InvalidInputException(violations);
            }
            study.reschedule(startAt, durationMin, StudyCreationRules.sessionDate(startAt),
                    changes.place() != null ? changes.place() : study.getPlace(), now);
        }
        if (changes.title() != null || changes.description() != null) {
            study.editText(changes.title() != null ? changes.title() : study.getTitle(),
                    changes.description() != null ? changes.description() : study.getDescription(), now);
        }
    }

    /**
     * 정원을 바꾼다. 범위는 설정값({@code capacityMin}~{@code capacityMax}), 현재 {@code ACTIVE} 참여자 수(주최자 포함)
     * 미만으로는 줄일 수 없다. 정원이 찼던 스터디에서 자리가 생기게 늘리면 자격이 남은 대기자에게 알린다
     * ({@link WaitingNotifier}, PLAN X-20).
     *
     * @throws BusinessException 없으면 {@code NOT_FOUND}, 주최자가 아니면 {@code FORBIDDEN}, 종료·파투면
     *                           {@code STUDY_CLOSED}, 참여자 수보다 줄이면 {@code CAPACITY_BELOW_MEMBERS}
     * @throws InvalidInputException 범위를 벗어남
     * @throws org.springframework.dao.PessimisticLockingFailureException 락 대기 시간 초과 ({@code LOCK_TIMEOUT})
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void changeCapacity(long studyId, long userId, int capacity) {
        Study study = lockedHostStudy(studyId, userId);
        if (!StudyCreationRules.capacityInRange(capacity, properties)) {
            throw new InvalidInputException(List.of(new FieldViolation("capacity",
                    "정원은 " + properties.capacityMin() + "~" + properties.capacityMax() + "명이어야 합니다")));
        }
        long active = members.countByStudyIdAndStatus(studyId, MemberStatus.ACTIVE);
        if (capacity < active) {
            throw new BusinessException(StudyErrorCode.CAPACITY_BELOW_MEMBERS);
        }
        Instant now = time.now();
        boolean wasFull = study.getCapacity() <= active;
        study.changeCapacity(capacity, now);
        if (wasFull && capacity > active) {
            waitingNotifier.notifySeatOpened(study, "CAPACITY", now);
        }
    }

    /** 스터디 행을 잠그고 지금의 주최자인지, 진행 중인지 확인한다. 순서: 없음 404 → 주최자 아님 403 → 종료·파투 409. */
    private Study lockedHostStudy(long studyId, long userId) {
        Study study = studies.findByIdForUpdate(studyId, lock.timeout())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        boolean activeHost = study.getHostId().equals(userId)
                && members.findByStudyIdAndUserId(studyId, userId).filter(StudyMember::isActive).isPresent();
        if (!activeHost) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (study.getStatus() != StudyStatus.ACTIVE) {
            throw new BusinessException(StudyErrorCode.STUDY_CLOSED);
        }
        return study;
    }
}
