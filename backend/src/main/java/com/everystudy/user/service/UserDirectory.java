package com.everystudy.user.service;

import com.everystudy.user.domain.User;
import com.everystudy.user.domain.UserStatus;
import com.everystudy.user.repository.UserRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 모듈이 화면에 보일 회원 정보를 읽는 곳 (스터디 목록의 주최자, 참여자 목록 등). */
@Service
public class UserDirectory {

    private final UserRepository users;

    public UserDirectory(UserRepository users) {
        this.users = users;
    }

    /**
     * 공개 정보를 한 번에 읽는다. 없는 ID 는 결과에 없다. 탈퇴한 회원은 닉네임·직군을 비워 돌려준다
     * (다른 사람 기록에는 "탈퇴한 사용자"로 남는다. PRD 4.1).
     */
    @Transactional(readOnly = true)
    public Map<Long, PublicUser> findPublic(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(userIds).stream()
                .map(UserDirectory::toPublic)
                .collect(Collectors.toMap(PublicUser::userId, Function.identity()));
    }

    /**
     * 공개 프로필 한 건. 없는 회원과 온보딩을 마치지 않은 회원은 비어 있다 (프로필은 온보딩을 마친 회원만 갖는다).
     * 탈퇴한 회원은 닉네임·직군·지역을 비워 돌려준다.
     */
    @Transactional(readOnly = true)
    public Optional<PublicProfile> findProfile(long userId) {
        return users.findById(userId)
                .filter(user -> user.isOnboarded() || user.getStatus() == UserStatus.WITHDRAWN)
                .map(user -> {
                    boolean withdrawn = user.getStatus() == UserStatus.WITHDRAWN;
                    return new PublicProfile(user.getId(), withdrawn ? null : user.getNickname(),
                            withdrawn ? null : user.getJobCode(), withdrawn ? null : user.getDistrictCode(),
                            user.getCreatedAt());
                });
    }

    private static PublicUser toPublic(User user) {
        boolean withdrawn = user.getStatus() == UserStatus.WITHDRAWN;
        return new PublicUser(user.getId(), withdrawn ? null : user.getNickname(), withdrawn ? null : user.getJobCode());
    }
}
