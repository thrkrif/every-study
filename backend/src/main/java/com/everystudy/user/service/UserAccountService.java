package com.everystudy.user.service;

import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.user.domain.User;
import com.everystudy.user.domain.UserStatus;
import com.everystudy.user.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 계정 상태 조회. */
@Service
public class UserAccountService {

    private final UserRepository users;

    public UserAccountService(UserRepository users) {
        this.users = users;
    }

    /**
     * 온보딩을 마친 활동 회원인지 확인한다. 스터디 개설처럼 프로필이 필요한 기능이 먼저 부른다.
     *
     * @throws BusinessException 없거나 탈퇴했으면 {@code UNAUTHENTICATED}, 온보딩 전이면 {@code ONBOARDING_REQUIRED}
     */
    @Transactional(readOnly = true)
    public void requireOnboarded(long userId) {
        User user = users.findById(userId)
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));
        if (!user.isOnboarded()) {
            throw new BusinessException(UserErrorCode.ONBOARDING_REQUIRED);
        }
    }

    /** 활동 중인 회원이면 현재 역할과 함께 돌려준다. 없거나 탈퇴했으면 빈 값이다. */
    @Transactional(readOnly = true)
    public Optional<ActiveUser> findActive(long userId) {
        return users.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(user -> new ActiveUser(user.getId(), user.getRole().name()));
    }
}
