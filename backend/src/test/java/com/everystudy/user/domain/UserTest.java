package com.everystudy.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-11-24T05:00:00Z");

    @Test
    @DisplayName("소셜 로그인으로 처음 만든 회원은 온보딩 전의 일반 회원이다")
    void newSocialUserIsActiveUserBeforeOnboarding() {
        User user = User.registerBySocialLogin(null, NOW);

        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isOnboarded()).isFalse();
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        assertThat(user.getEmail()).isNull();
    }

    @Test
    @DisplayName("공급자가 이메일을 주지 않으면 기존 이메일을 지우지 않는다")
    void keepsEmailWhenProviderGivesNone() {
        User user = User.registerBySocialLogin("first@example.com", NOW);

        user.refreshEmail(null);
        assertThat(user.getEmail()).isEqualTo("first@example.com");

        user.refreshEmail("second@example.com");
        assertThat(user.getEmail()).isEqualTo("second@example.com");
    }
}
