package com.everystudy.study.service;

import com.everystudy.common.code.JobCode;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.domain.MemberStatus;
import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyMember;
import com.everystudy.study.repository.StudyMemberRepository;
import com.everystudy.study.repository.StudyRepository;
import com.everystudy.trust.service.AttendanceStandingService;
import com.everystudy.trust.service.SanctionService;
import com.everystudy.user.service.PublicUser;
import com.everystudy.user.service.UserAccountService;
import com.everystudy.user.service.UserDirectory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 참여 신청 (PRD 4.4, API 4장). 승인 단계 없이 검사를 통과하면 바로 확정한다. */
@Service
public class StudyJoinService {

    private final StudyRepository studies;
    private final StudyMemberRepository members;
    private final UserAccountService accounts;
    private final UserDirectory users;
    private final SanctionService sanctions;
    private final AttendanceStandingService standings;
    private final StudyProperties properties;
    private final StudyLockProperties lock;
    private final TimeProvider time;

    StudyJoinService(StudyRepository studies, StudyMemberRepository members, UserAccountService accounts,
            UserDirectory users, SanctionService sanctions, AttendanceStandingService standings,
            StudyProperties properties, StudyLockProperties lock, TimeProvider time) {
        this.studies = studies;
        this.members = members;
        this.accounts = accounts;
        this.users = users;
        this.sanctions = sanctions;
        this.standings = standings;
        this.properties = properties;
        this.lock = lock;
        this.time = time;
    }

    /**
     * {@code study} 행을 잠근 뒤 PRD 4.4 순서대로 검사하고 참여를 확정한다 (ADR-0004 결정 1·2). 나갔던 사용자는 같은 행을
     * 되살린다. 채팅방 입장 권한은 {@code ACTIVE} 참여 행으로 판단하므로 따로 만들 것이 없다.
     *
     * <p>온보딩 확인은 스터디 규칙이 아니라 신청자의 전제 조건이라 락을 잡기 전에 한다 (PLAN X-19).
     *
     * <p>격리 수준은 {@code READ COMMITTED} 다 (ADR-0004 결정 2). MySQL 기본값 {@code REPEATABLE READ} 에서는 첫 조회
     * (여기서는 락 전의 온보딩 확인) 때 읽기 시점이 고정되어, 락을 기다리는 동안 앞 트랜잭션이 커밋한 참여 행이 락을 잡은
     * 뒤에도 보이지 않는다. 그러면 정원을 넘겨 확정하거나 중복 신청이 유일 제약 오류로 끝난다.
     *
     * @throws BusinessException 온보딩 전이면 {@code ONBOARDING_REQUIRED}, 스터디가 없으면 {@code NOT_FOUND},
     *                           검사에 어긋나면 {@link StudyJoinRules} 의 오류
     * @throws org.springframework.dao.PessimisticLockingFailureException 락 대기 시간 초과 ({@code LOCK_TIMEOUT})
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void join(long studyId, long userId) {
        accounts.requireOnboarded(userId);
        Study study = studies.findByIdForUpdate(studyId, lock.timeout())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Instant now = time.now();
        Optional<StudyMember> existing = members.findByStudyIdAndUserId(studyId, userId);

        StudyJoinRules.check(new StudyJoinRules.JoinFacts(
                study.getStatus(), study.getStartAt(), study.getCapacity(),
                Math.toIntExact(members.countByStudyIdAndStatus(studyId, MemberStatus.ACTIVE)),
                sanctions.joinRestrictedUntil(userId, now).orElse(null),
                standings.standingOf(userId),
                study.getEntryCriteria(), study.getTargetJobCodes(), jobCategoryOf(userId),
                existing.filter(StudyMember::isActive).isPresent()), now, properties);

        existing.ifPresentOrElse(member -> member.rejoin(now),
                () -> members.save(StudyMember.join(studyId, userId, now)));
    }

    /** 온보딩을 마친 회원이므로 직군이 있다. 세부 직무면 그 대분류로 판단한다 (PRD 4.4). */
    private String jobCategoryOf(long userId) {
        PublicUser user = users.findPublic(List.of(userId)).get(userId);
        return JobCode.find(user.jobCode())
                .map(job -> job.category().name())
                .orElseThrow(() -> new IllegalStateException("온보딩을 마친 회원의 직군 코드가 목록에 없습니다"));
    }
}
