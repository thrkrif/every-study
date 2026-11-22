package com.everystudy.user.repository;

import com.everystudy.user.domain.SocialAccount;
import com.everystudy.user.domain.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    /** 회원의 소셜 계정 연결을 모두 지운다 (탈퇴, PRD 4.1). 같은 소셜 계정으로 다시 로그인하면 새 회원이다. */
    void deleteByUserId(Long userId);
}
