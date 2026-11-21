package com.everystudy.study.service;

import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.domain.MemberStatus;
import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyStatus;
import com.everystudy.study.domain.StudyWaiting;
import com.everystudy.study.repository.StudyMemberRepository;
import com.everystudy.study.repository.StudyRepository;
import com.everystudy.study.repository.StudyWaitingRepository;
import com.everystudy.common.code.JobCode;
import com.everystudy.trust.service.AttendanceStandingService;
import com.everystudy.trust.service.SanctionService;
import com.everystudy.user.service.PublicUser;
import com.everystudy.user.service.UserAccountService;
import com.everystudy.user.service.UserDirectory;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 대기 등록·취소 (PRD 4.14, API 4장). 정원이 차 있을 때만 대기할 수 있다. */
@Service
public class StudyWaitingService {

    private final StudyRepository studies;
    private final StudyMemberRepository members;
    private final StudyWaitingRepository waiting;
    private final UserAccountService accounts;
    private final UserDirectory users;
    private final SanctionService sanctions;
    private final AttendanceStandingService standings;
    private final StudyProperties properties;
    private final StudyLockProperties lock;
    private final TimeProvider time;

    StudyWaitingService(StudyRepository studies, StudyMemberRepository members, StudyWaitingRepository waiting,
            UserAccountService accounts, UserDirectory users, SanctionService sanctions,
            AttendanceStandingService standings, StudyProperties properties, StudyLockProperties lock, TimeProvider time) {
        this.studies = studies;
        this.members = members;
        this.waiting = waiting;
        this.accounts = accounts;
        this.users = users;
        this.sanctions = sanctions;
        this.standings = standings;
        this.properties = properties;
        this.lock = lock;
        this.time = time;
    }

    /**
     * {@code study} 행을 잠근 뒤 검사하고 대기를 등록한다. 정원과 대기가 같은 락 아래에서 바뀌므로 "찼는지"와 "중복인지"
     * 판단이 신청·나가기와 어긋나지 않는다. 격리 수준은 참여 신청과 같은 이유로 {@code READ COMMITTED} 다
     * ({@link StudyJoinService#join}). 검사 순서: 스터디 상태 → 이미 참여 → 정원 여유 →
     * 이용 제한 → 입장 기준 → 희망 직군 → 이미 대기. 대기자도 자리가 났을 때 신청할 수 있어야 하므로 참여 신청과 같은 자격
     * 검사를 거친다 (PLAN X-20).
     *
     * @throws BusinessException 온보딩 전이면 {@code ONBOARDING_REQUIRED}, 스터디가 없으면 {@code NOT_FOUND}, 그 밖에
     *                           {@code STUDY_NOT_RECRUITING}, {@code ALREADY_JOINED}, {@code STUDY_NOT_FULL},
     *                           {@code USER_RESTRICTED}, {@code ENTRY_CRITERIA_NOT_MET}, {@code JOB_NOT_ALLOWED},
     *                           {@code ALREADY_WAITING}
     * @throws org.springframework.dao.PessimisticLockingFailureException 락 대기 시간 초과 ({@code LOCK_TIMEOUT})
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void register(long studyId, long userId) {
        accounts.requireOnboarded(userId);
        Study study = studies.findByIdForUpdate(studyId, lock.timeout())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Instant now = time.now();

        if (study.getStatus() != StudyStatus.ACTIVE
                || !now.isBefore(StudyTimeline.checkinOpensAt(study.getStartAt(), properties))) {
            throw new BusinessException(StudyErrorCode.STUDY_NOT_RECRUITING);
        }
        if (members.findByStudyIdAndUserId(studyId, userId).filter(member -> member.isActive()).isPresent()) {
            throw new BusinessException(StudyErrorCode.ALREADY_JOINED);
        }
        if (members.countByStudyIdAndStatus(studyId, MemberStatus.ACTIVE) < study.getCapacity()) {
            throw new BusinessException(StudyErrorCode.STUDY_NOT_FULL);
        }
        StudyJoinRules.checkApplicant(sanctions.joinRestrictedUntil(userId, now).orElse(null),
                standings.standingOf(userId), study.getEntryCriteria(), study.getTargetJobCodes(),
                jobCategoryOf(userId));
        if (waiting.existsByStudyIdAndUserId(studyId, userId)) {
            throw new BusinessException(StudyErrorCode.ALREADY_WAITING);
        }
        waiting.save(StudyWaiting.of(studyId, userId, now));
    }

    /** 대기를 취소한다(행 삭제). 대기 중이 아니어도 오류 없이 끝난다. */
    @Transactional
    public void cancel(long studyId, long userId) {
        waiting.deleteByStudyIdAndUserId(studyId, userId);
    }

    /** 온보딩을 마친 회원이므로 직군이 있다. 세부 직무면 그 대분류로 판단한다 (PRD 4.4). */
    private String jobCategoryOf(long userId) {
        PublicUser user = users.findPublic(List.of(userId)).get(userId);
        return JobCode.find(user.jobCode())
                .map(job -> job.category().name())
                .orElseThrow(() -> new IllegalStateException("온보딩을 마친 회원의 직군 코드가 목록에 없습니다"));
    }
}
