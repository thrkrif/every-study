package com.everystudy.auth.service;

import com.everystudy.auth.repository.RefreshTokenRepository;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.notification.service.ScheduledNotificationCanceller;
import com.everystudy.study.service.StudyWithdrawalCleanup;
import com.everystudy.user.service.UserWithdrawal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 (PRD 4.1, ADR-0002 결정 6, API 3장 {@code DELETE /me}, PLAN T-062). 한 트랜잭션에서 차례로 한다: 진행 중인
 * 스터디에 나가기 규칙 적용(참여 취소·주최자 승계) → 미발송 알림 취소 → 개인정보 삭제와 소셜 계정 연결 삭제 → 리프레시
 * 토큰 모두 폐기. 하나라도 실패하면 모두 되돌아가 반쯤 탈퇴한 회원이 남지 않는다. 같은 회원이 다시 불러도 결과가 같다.
 *
 * <p>모듈 의존이 순환하지 않도록 {@code user} 가 아니라 {@code study}·{@code notification}·{@code user} 를 모두 아래로 보는
 * {@code auth} 에 둔다 (ARCHITECTURE 3장).
 */
@Service
public class WithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

    private final StudyWithdrawalCleanup studies;
    private final ScheduledNotificationCanceller notifications;
    private final UserWithdrawal accounts;
    private final RefreshTokenRepository refreshTokens;
    private final TimeProvider time;

    WithdrawalService(StudyWithdrawalCleanup studies, ScheduledNotificationCanceller notifications,
            UserWithdrawal accounts, RefreshTokenRepository refreshTokens, TimeProvider time) {
        this.studies = studies;
        this.notifications = notifications;
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.time = time;
    }

    /** 스터디 나가기와 같은 격리 수준이다 (잠금 안에서 최신 값을 본다). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdraw(long userId) {
        int studiesLeft = studies.leaveAll(userId);
        notifications.cancelAllPendingOfUser(userId);
        boolean done = accounts.withdraw(userId);
        refreshTokens.revokeAllOfUser(userId, time.now());
        log.info("회원 탈퇴 처리: 새로 탈퇴={}, 정리한 스터디={}", done, studiesLeft);
    }
}
