package com.everystudy.study.service;

import com.everystudy.study.domain.MemberStatus;
import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyMember;
import com.everystudy.study.domain.StudyStatus;
import com.everystudy.study.repository.StudyMemberRepository;
import com.everystudy.study.repository.StudyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션(모임 당일)에 관한 일을 하는 다른 모듈(출석)이 스터디를 읽는 곳 (ARCHITECTURE 3장). 체크인과 자동 판정은 참여·나가기와
 * 같은 {@code study} 행 락 안에서 처리해(PLAN X-15, ADR-0004 결정 1) 마감 직전 체크인과 판정이 겹쳐도 한 번에 하나씩
 * 순서대로 실행된다. 락을 잡는 호출자는 격리 수준을 {@code READ COMMITTED} 로 둔다 ({@link StudyJoinService#join}).
 */
@Service
public class StudySessions {

    /**
     * 세션 하나의 사실. 엔티티를 내보내지 않으려고 값만 담는다.
     *
     * @param active          진행 중({@code ACTIVE})인가. 종료·파투면 {@code false}
     * @param hostId          지금의 주최자
     * @param activeMember    물어본 사람이 {@code ACTIVE} 참여자인가 (주최자도 참여자다)
     * @param checkinOpensAt  체크인 시작 시각 (PRD 4.6, 시작 10분 전)
     * @param checkinClosesAt 체크인 마감 시각 (PRD 4.6, 시작 30분 뒤)
     */
    public record Session(long studyId, boolean active, long hostId, Instant startAt, double latitude,
            double longitude, boolean activeMember, Instant checkinOpensAt, Instant checkinClosesAt) {
    }

    private final StudyRepository studies;
    private final StudyMemberRepository members;
    private final StudyProperties properties;
    private final StudyLockProperties lock;

    StudySessions(StudyRepository studies, StudyMemberRepository members, StudyProperties properties,
            StudyLockProperties lock) {
        this.studies = studies;
        this.members = members;
        this.properties = properties;
        this.lock = lock;
    }

    /**
     * {@code study} 행을 잠그고 세션 사실을 돌려준다. 호출한 쪽 트랜잭션이 끝날 때까지 락이 유지된다. 이 뒤에 하는 모든 검사는
     * 락을 잡은 다음의 값으로 해야 한다.
     *
     * @return 스터디가 없으면 빈 값
     * @throws org.springframework.dao.PessimisticLockingFailureException 락 대기 시간 초과 ({@code LOCK_TIMEOUT})
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Session> lock(long studyId, long userId) {
        return studies.findByIdForUpdate(studyId, lock.timeout()).map(study -> session(study, userId));
    }

    /** 락 없이 읽는다. 조회용이다. */
    @Transactional(readOnly = true)
    public Optional<Session> read(long studyId, long userId) {
        return studies.findById(studyId).map(study -> session(study, userId));
    }

    /** 지금 {@code ACTIVE} 인 참여자 ID (주최자 포함, 참여 순서). */
    @Transactional(readOnly = true)
    public List<Long> activeMemberIds(long studyId) {
        return members.findByStudyIdAndStatusOrderByJoinedAtAscIdAsc(studyId, MemberStatus.ACTIVE).stream()
                .map(StudyMember::getUserId)
                .toList();
    }

    private Session session(Study study, long userId) {
        boolean activeMember = members.findByStudyIdAndUserId(study.getId(), userId)
                .filter(StudyMember::isActive)
                .isPresent();
        Study.Place place = study.getPlace();
        return new Session(study.getId(), study.getStatus() == StudyStatus.ACTIVE, study.getHostId(),
                study.getStartAt(), place.latitude().doubleValue(), place.longitude().doubleValue(), activeMember,
                StudyTimeline.checkinOpensAt(study.getStartAt(), properties),
                StudyTimeline.checkinClosesAt(study.getStartAt(), properties));
    }
}
