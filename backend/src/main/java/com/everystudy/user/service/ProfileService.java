package com.everystudy.user.service;

import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.user.domain.User;
import com.everystudy.user.domain.UserStatus;
import com.everystudy.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 정보 조회와 온보딩·프로필 수정 (PRD 4.1, API 3장). */
@Service
public class ProfileService {

    private static final String NICKNAME_UNIQUE_KEY = "uk_user_nickname";

    private final UserRepository users;

    public ProfileService(UserRepository users) {
        this.users = users;
    }

    /** @throws BusinessException 회원이 없거나 탈퇴했으면 {@code UNAUTHENTICATED} (토큰은 아직 살아 있는 경우) */
    @Transactional(readOnly = true)
    public MyProfile me(long userId) {
        return toProfile(activeUser(userId));
    }

    /**
     * 닉네임·직군·지역을 정한다. 값의 형식은 웹 계층이 검사했다고 본다.
     *
     * @throws BusinessException 다른 회원이 같은 닉네임(영문 대소문자 무시)을 쓰면 {@code NICKNAME_TAKEN}
     */
    @Transactional
    public MyProfile updateProfile(long userId, String nickname, String jobCode, String districtCode) {
        User user = activeUser(userId);
        if (users.existsByNicknameAndIdNot(nickname, userId)) {
            throw new BusinessException(UserErrorCode.NICKNAME_TAKEN);
        }
        user.updateProfile(nickname, jobCode, districtCode);
        try {
            // 위 검사와 저장 사이에 같은 닉네임이 먼저 저장되면 유니크 제약이 막는다.
            users.flush();
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(NICKNAME_UNIQUE_KEY)) {
                throw new BusinessException(UserErrorCode.NICKNAME_TAKEN);
            }
            throw ex;
        }
        return toProfile(user);
    }

    private User activeUser(long userId) {
        return users.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));
    }

    private static MyProfile toProfile(User user) {
        return new MyProfile(user.getId(), user.getNickname(), user.getJobCode(), user.getDistrictCode(),
                user.getRole().name(), user.isOnboarded(), user.getCreatedAt());
    }
}
