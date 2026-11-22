package com.everystudy.user.service;

import com.everystudy.common.time.TimeProvider;
import com.everystudy.user.domain.User;
import com.everystudy.user.domain.UserStatus;
import com.everystudy.user.repository.SocialAccountRepository;
import com.everystudy.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴의 계정 부분 (PRD 4.1, DATA-MODEL 3장, PLAN T-062). 개인정보 컬럼을 비우고 소셜 계정 연결을 지운다. 행과
 * {@code id} 는 남아 다른 사람의 기록(채팅·출석 집계)에 "탈퇴한 사용자"로 보인다. 진행 중 스터디·알림·토큰 정리는 이것을
 * 부르는 쪽({@code auth} 의 탈퇴 서비스)이 같은 트랜잭션에서 한다.
 */
@Service
public class UserWithdrawal {

    private final UserRepository users;
    private final SocialAccountRepository socialAccounts;
    private final TimeProvider time;

    UserWithdrawal(UserRepository users, SocialAccountRepository socialAccounts, TimeProvider time) {
        this.users = users;
        this.socialAccounts = socialAccounts;
        this.time = time;
    }

    /**
     * 회원을 탈퇴 처리한다. 이미 탈퇴했으면 아무것도 하지 않는다(멱등).
     *
     * @return 새로 탈퇴 처리했으면 {@code true}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean withdraw(long userId) {
        User user = users.findById(userId).filter(found -> found.getStatus() == UserStatus.ACTIVE).orElse(null);
        if (user == null) {
            return false;
        }
        user.withdraw(time.now());
        socialAccounts.deleteByUserId(userId);
        return true;
    }
}
