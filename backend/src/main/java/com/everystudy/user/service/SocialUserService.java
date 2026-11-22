package com.everystudy.user.service;

import com.everystudy.common.time.TimeProvider;
import com.everystudy.user.domain.SocialAccount;
import com.everystudy.user.domain.SocialProvider;
import com.everystudy.user.domain.User;
import com.everystudy.user.domain.UserRole;
import com.everystudy.user.repository.SocialAccountRepository;
import com.everystudy.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소셜 계정으로 회원을 찾거나 새로 만든다 (PRD 4.1, ADR-0002 결정 1). */
@Service
public class SocialUserService {

    private final UserRepository users;
    private final SocialAccountRepository socialAccounts;
    private final TimeProvider time;
    private final AdminProperties admins;

    public SocialUserService(UserRepository users, SocialAccountRepository socialAccounts, TimeProvider time,
            AdminProperties admins) {
        this.users = users;
        this.socialAccounts = socialAccounts;
        this.time = time;
        this.admins = admins;
    }

    /**
     * 같은 {@code (provider, providerUserId)} 는 항상 같은 회원이다. 처음이면 온보딩 전 상태의 회원을 만든다.
     *
     * @param provider       공급자 이름 ({@code kakao}, {@code google}. 대소문자 무관)
     * @param providerUserId 공급자가 준 사용자 식별값
     * @param verifiedEmail  공급자가 확인한 이메일. 없으면 {@code null}
     * @throws IllegalArgumentException 지원하지 않는 공급자
     */
    @Transactional
    public SocialUser findOrRegister(String provider, String providerUserId, String verifiedEmail) {
        SocialProvider socialProvider = SocialProvider.find(provider)
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 소셜 공급자: " + provider));

        User user = socialAccounts.findByProviderAndProviderUserId(socialProvider, providerUserId)
                .map(account -> users.findById(account.getUserId()).orElseThrow())
                .orElseGet(() -> register(socialProvider, providerUserId, verifiedEmail));
        user.refreshEmail(verifiedEmail);
        if (user.getRole() != UserRole.ADMIN && admins.isAdminEmail(user.getEmail())) {
            user.grantAdmin();
        }

        return new SocialUser(user.getId(), user.getRole().name(), user.isOnboarded());
    }

    private User register(SocialProvider provider, String providerUserId, String verifiedEmail) {
        User user = users.save(User.registerBySocialLogin(verifiedEmail, time.now()));
        socialAccounts.save(new SocialAccount(user.getId(), provider, providerUserId));
        return user;
    }
}
