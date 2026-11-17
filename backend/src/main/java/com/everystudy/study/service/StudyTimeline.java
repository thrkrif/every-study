package com.everystudy.study.service;

import com.everystudy.study.domain.StudyStatus;
import java.time.Instant;

/**
 * 스터디의 시각에서 나오는 파생 값 (PRD 4.6·4.14, ARCHITECTURE 4.2). 저장하지 않고 매번 계산한다. DB·Spring 에 의존하지 않는다.
 */
final class StudyTimeline {

    private StudyTimeline() {
    }

    /** 체크인 오픈. 신청과 대기도 이때 닫힌다. */
    static Instant checkinOpensAt(Instant startAt, StudyProperties properties) {
        return startAt.minusSeconds(properties.checkinOpensBeforeMinutes() * 60L);
    }

    static Instant checkinClosesAt(Instant startAt, StudyProperties properties) {
        return startAt.plusSeconds(properties.checkinClosesAfterMinutes() * 60L);
    }

    /** 모집 중 = 진행 중이고, 체크인 오픈 전이고, 정원에 여유가 있다. 체크인 오픈 순간부터는 마감이다. */
    static boolean isRecruiting(StudyStatus status, Instant startAt, int memberCount, int capacity, Instant now,
            StudyProperties properties) {
        return status == StudyStatus.ACTIVE
                && now.isBefore(checkinOpensAt(startAt, properties))
                && memberCount < capacity;
    }

    /**
     * 일정·장소를 고칠 수 있나 (PRD 4.2). 진행 중이고 시작 전이며 주최자 말고 참여자가 없을 때만이다.
     *
     * @param otherMemberCount 주최자를 뺀 {@code ACTIVE} 참여자 수
     */
    static boolean isScheduleEditable(StudyStatus status, Instant startAt, int otherMemberCount, Instant now) {
        return status == StudyStatus.ACTIVE && now.isBefore(startAt) && otherMemberCount == 0;
    }
}
