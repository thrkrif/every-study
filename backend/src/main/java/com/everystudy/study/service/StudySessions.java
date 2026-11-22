package com.everystudy.study.service;

import com.everystudy.study.domain.MemberStatus;
import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyMember;
import com.everystudy.study.domain.StudyStatus;
import com.everystudy.study.repository.StudyMemberRepository;
import com.everystudy.study.repository.StudyRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
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

    /**
     * 출석 판정 대상 하나의 사실 (PLAN T-031).
     *
     * @param cancelled       파투({@code CANCELLED})인가. 주최자 포기·운영자 강제 종료 모두 해당한다
     * @param activeMemberIds 지금 {@code ACTIVE} 인 참여자 (주최자가 아직 참여자면 포함)
     */
    public record JudgementTarget(long studyId, String title, boolean cancelled, long hostId,
            List<Long> activeMemberIds) {
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

    /** 스터디 제목. 없는 스터디는 빠진다. 이의·출석 기록 목록이 스터디 이름을 붙일 때 쓴다. */
    @Transactional(readOnly = true)
    public Map<Long, String> titles(Collection<Long> studyIds) {
        return studies.findAllById(studyIds).stream().collect(Collectors.toMap(Study::getId, Study::getTitle));
    }

    /** 지금 {@code ACTIVE} 인 참여자 ID (주최자 포함, 참여 순서). */
    @Transactional(readOnly = true)
    public List<Long> activeMemberIds(long studyId) {
        return members.findByStudyIdAndStatusOrderByJoinedAtAscIdAsc(studyId, MemberStatus.ACTIVE).stream()
                .map(StudyMember::getUserId)
                .toList();
    }

    /**
     * 판정할 때가 된 스터디 id: 아직 판정하지 않았고 체크인 마감 시각이 {@code now} 보다 앞선다. 오래된 것부터 {@code limit} 건.
     */
    @Transactional(readOnly = true)
    public List<Long> findJudgementDueIds(Instant now, int limit) {
        Instant startedBefore = now.minusSeconds(properties.checkinClosesAfterMinutes() * 60L);
        return studies.findUnjudgedIdsStartedBefore(startedBefore, PageRequest.of(0, limit));
    }

    /**
     * 판정 대상을 잠그고 읽는다. 락을 잡은 뒤 조건(판정 전, 체크인 마감 경과)을 다시 확인해 아니면 빈 값이다. 호출한 쪽
     * 트랜잭션이 끝날 때까지 락이 유지되어 체크인과 직렬화된다 (PLAN X-15). 격리 수준은 {@code READ COMMITTED} 로 둔다.
     *
     * @throws org.springframework.dao.PessimisticLockingFailureException 락 대기 시간 초과
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<JudgementTarget> lockForJudgement(long studyId, Instant now) {
        return studies.findByIdForUpdate(studyId, lock.timeout())
                .filter(study -> study.getJudgedAt() == null)
                .filter(study -> now.isAfter(StudyTimeline.checkinClosesAt(study.getStartAt(), properties)))
                .map(study -> new JudgementTarget(study.getId(), study.getTitle(),
                        study.getStatus() == StudyStatus.CANCELLED, study.getHostId(),
                        activeMemberIds(study.getId())));
    }

    /** {@link #lockForJudgement} 로 잠근 스터디의 판정을 끝낸다. {@code voided} 면 주최자 미도착 무효로도 표시한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markJudged(long studyId, boolean voided, Instant now) {
        studies.findById(studyId).orElseThrow().markJudged(voided, now);
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
