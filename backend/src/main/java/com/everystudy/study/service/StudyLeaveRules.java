package com.everystudy.study.service;

import com.everystudy.common.error.BusinessException;
import com.everystudy.study.domain.CancelType;
import com.everystudy.study.domain.StudyStatus;
import java.time.Instant;

/** 나가기 규칙 (PRD 4.4·4.6). DB·Spring 에 의존하지 않는다. */
final class StudyLeaveRules {

    private StudyLeaveRules() {
    }

    /**
     * 사전/직전 취소 판정. 시작 {@code lateCancelWithinMinutes} 분 전 시각까지는 사전, 그 뒤는 직전이다 (PRD 4.6). 경계 시각
     * 자체는 사전 취소다("3시간 전까지").
     */
    static CancelType cancelType(Instant startAt, Instant now, StudyProperties properties) {
        Instant lateFrom = startAt.minusSeconds(properties.lateCancelWithinMinutes() * 60L);
        return now.isAfter(lateFrom) ? CancelType.LATE : CancelType.EARLY;
    }

    /**
     * 시작 전의 진행 중인 스터디에서만 나갈 수 있다. 시작 이후의 이탈은 체크인 여부에 따라 출석·노쇼로 판정되므로(M3) 나가기로
     * 기록을 피할 수 없게 한다 (PLAN X-21). 종료·파투된 스터디도 같다.
     *
     * @throws BusinessException {@code SESSION_STARTED}
     */
    static void requireLeavable(StudyStatus status, Instant startAt, Instant now) {
        if (status != StudyStatus.ACTIVE || !now.isBefore(startAt)) {
            throw new BusinessException(StudyErrorCode.SESSION_STARTED);
        }
    }
}
