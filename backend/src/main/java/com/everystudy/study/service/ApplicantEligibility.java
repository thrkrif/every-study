package com.everystudy.study.service;

import com.everystudy.common.code.JobCode;
import com.everystudy.common.error.BusinessException;
import com.everystudy.study.domain.Study;
import com.everystudy.trust.service.AttendanceStandingService;
import com.everystudy.trust.service.SanctionService;
import com.everystudy.user.service.PublicUser;
import com.everystudy.user.service.UserDirectory;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 신청 자격 검사에 쓸 사실(제재·출석 지표·직군)을 모아 {@link StudyJoinRules#checkApplicant} 로 판단한다. 대기 등록과
 * 자리 알림 대상 판정이 같은 규칙을 쓰게 한다 (PRD 4.14, PLAN X-20).
 */
@Component
class ApplicantEligibility {

    private final SanctionService sanctions;
    private final AttendanceStandingService standings;
    private final UserDirectory users;

    ApplicantEligibility(SanctionService sanctions, AttendanceStandingService standings, UserDirectory users) {
        this.sanctions = sanctions;
        this.standings = standings;
        this.users = users;
    }

    /** @throws BusinessException 이용 제한 → 입장 기준 → 희망 직군 순으로 처음 어긋난 조건의 오류 */
    void verify(Study study, long userId, Instant now) {
        StudyJoinRules.checkApplicant(sanctions.joinRestrictedUntil(userId, now).orElse(null),
                standings.standingOf(userId), study.getEntryCriteria(), study.getTargetJobCodes(),
                jobCategoryOf(userId));
    }

    boolean isEligible(Study study, long userId, Instant now) {
        try {
            verify(study, userId, now);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    /** 온보딩을 마친 회원이므로 직군이 있다. 세부 직무면 그 대분류로 판단한다 (PRD 4.4). */
    String jobCategoryOf(long userId) {
        PublicUser user = users.findPublic(List.of(userId)).get(userId);
        return JobCode.find(user.jobCode())
                .map(job -> job.category().name())
                .orElseThrow(() -> new IllegalStateException("온보딩을 마친 회원의 직군 코드가 목록에 없습니다"));
    }
}
