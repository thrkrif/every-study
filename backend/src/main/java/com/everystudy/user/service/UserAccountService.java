package com.everystudy.user.service;

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

    /** 활동 중인 회원이면 현재 역할과 함께 돌려준다. 없거나 탈퇴했으면 빈 값이다. */
    @Transactional(readOnly = true)
    public Optional<ActiveUser> findActive(long userId) {
        return users.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(user -> new ActiveUser(user.getId(), user.getRole().name()));
    }
}
