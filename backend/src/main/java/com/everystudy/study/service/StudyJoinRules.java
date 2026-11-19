package com.everystudy.study.service;

import com.everystudy.common.error.BusinessException;
import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyStatus;
import com.everystudy.trust.service.AttendanceStanding;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * 참여 신청 검사 (PRD 4.4). DB·Spring 에 의존하지 않는다. 검사 순서가 곧 오류의 우선순위다:
 * 스터디 상태 → 정원 → 이용 제한 → 입장 기준 → 희망 직군 → 중복 신청. 처음 어긋난 것 하나만 알린다.
 */
final class StudyJoinRules {

    private StudyJoinRules() {
    }

    /**
     * 검사에 쓰는 사실. 모두 {@code study} 행 락을 잡은 뒤에 읽은 값이다 (ADR-0004 결정 2).
     *
     * @param memberCount     주최자를 포함한 {@code ACTIVE} 참여자 수
     * @param restrictedUntil 참여 신청을 막는 제재가 풀리는 시각. 막혀 있지 않으면 {@code null}
     * @param myJobCategory   신청자 직군의 대분류 코드
     * @param alreadyMember   신청자가 이미 {@code ACTIVE} 참여자다
     */
    record JoinFacts(StudyStatus status, Instant startAt, int capacity, int memberCount, Instant restrictedUntil,
            AttendanceStanding standing, Study.EntryCriteria entryCriteria, Set<String> targetJobCodes,
            String myJobCategory, boolean alreadyMember) {
    }

    /** @throws BusinessException 처음 어긋난 조건의 오류. {@code USER_RESTRICTED} 는 {@code until} 을 함께 담는다 */
    static void check(JoinFacts facts, Instant now, StudyProperties properties) {
        if (facts.status() != StudyStatus.ACTIVE
                || !now.isBefore(StudyTimeline.checkinOpensAt(facts.startAt(), properties))) {
            throw new BusinessException(StudyErrorCode.STUDY_NOT_RECRUITING);
        }
        if (facts.memberCount() >= facts.capacity()) {
            throw new BusinessException(StudyErrorCode.STUDY_FULL);
        }
        if (facts.restrictedUntil() != null) {
            throw new BusinessException(StudyErrorCode.USER_RESTRICTED, null, Map.of("until", facts.restrictedUntil()));
        }
        if (!meetsEntryCriteria(facts.standing(), facts.entryCriteria())) {
            throw new BusinessException(StudyErrorCode.ENTRY_CRITERIA_NOT_MET);
        }
        if (!facts.targetJobCodes().isEmpty() && !facts.targetJobCodes().contains(facts.myJobCategory())) {
            throw new BusinessException(StudyErrorCode.JOB_NOT_ALLOWED);
        }
        if (facts.alreadyMember()) {
            throw new BusinessException(StudyErrorCode.ALREADY_JOINED);
        }
    }

    /** 신규는 신규 허용 여부만, 신규가 아니면 최소 출석률(있을 때만)로 판단한다 (PRD 4.7). */
    private static boolean meetsEntryCriteria(AttendanceStanding standing, Study.EntryCriteria criteria) {
        if (standing.newcomer()) {
            return criteria.allowNewcomer();
        }
        Integer minimum = criteria.minAttendanceRate();
        return minimum == null || standing.rate().compareTo(BigDecimal.valueOf(minimum)) >= 0;
    }
}
