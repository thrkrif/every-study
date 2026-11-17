package com.everystudy.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialProfileExtractorTest {

    // 값은 모두 지어낸 것이다.
    private static final String EMAIL = "tester@example.com";

    @Test
    @DisplayName("카카오는 id 와 확인된 이메일을 뽑는다")
    void extractsKakaoIdAndVerifiedEmail() {
        Map<String, Object> attributes = Map.of(
                "id", 1234567890L,
                "kakao_account", Map.of("email", EMAIL, "is_email_valid", true, "is_email_verified", true));

        SocialProfile profile = SocialProfileExtractor.extract("kakao", attributes);

        assertThat(profile.provider()).isEqualTo("kakao");
        assertThat(profile.providerUserId()).isEqualTo("1234567890");
        assertThat(profile.verifiedEmail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("카카오에서 이메일 제공에 동의하지 않으면 이메일은 없다")
    void kakaoWithoutEmailConsentHasNoEmail() {
        assertThat(SocialProfileExtractor.extract("kakao", Map.of("id", 1L)).verifiedEmail()).isNull();
        assertThat(SocialProfileExtractor.extract("kakao", Map.of("id", 1L, "kakao_account", Map.of()))
                .verifiedEmail()).isNull();
    }

    @Test
    @DisplayName("카카오 이메일이 유효하지 않거나 인증되지 않았으면 저장하지 않는다")
    void kakaoUnverifiedEmailIsDropped() {
        Map<String, Object> invalid = Map.of("id", 1L,
                "kakao_account", Map.of("email", EMAIL, "is_email_valid", false, "is_email_verified", true));
        Map<String, Object> unverified = Map.of("id", 1L,
                "kakao_account", Map.of("email", EMAIL, "is_email_valid", true, "is_email_verified", false));

        assertThat(SocialProfileExtractor.extract("kakao", invalid).verifiedEmail()).isNull();
        assertThat(SocialProfileExtractor.extract("kakao", unverified).verifiedEmail()).isNull();
    }

    @Test
    @DisplayName("구글은 sub 와 확인된 이메일을 뽑는다")
    void extractsGoogleSubAndVerifiedEmail() {
        SocialProfile profile = SocialProfileExtractor.extract(
                "google", Map.of("sub", "g-100", "email", EMAIL, "email_verified", true));

        assertThat(profile.providerUserId()).isEqualTo("g-100");
        assertThat(profile.verifiedEmail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("구글 이메일이 인증되지 않았으면 저장하지 않는다")
    void googleUnverifiedEmailIsDropped() {
        assertThat(SocialProfileExtractor.extract(
                "google", Map.of("sub", "g-100", "email", EMAIL, "email_verified", false)).verifiedEmail()).isNull();
        assertThat(SocialProfileExtractor.extract(
                "google", Map.of("sub", "g-100", "email", EMAIL)).verifiedEmail()).isNull();
    }

    @Test
    @DisplayName("식별값이 없으면 거부한다")
    void rejectsMissingId() {
        assertThatThrownBy(() -> SocialProfileExtractor.extract("kakao", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SocialProfileExtractor.extract("google", Map.of("sub", " ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("지원하지 않는 공급자는 거부한다")
    void rejectsUnknownProvider() {
        assertThatThrownBy(() -> SocialProfileExtractor.extract("naver", Map.of("id", "1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("문자열로 바꿔도 이메일이 드러나지 않는다")
    void toStringHidesEmail() {
        assertThat(new SocialProfile("kakao", "1", EMAIL).toString()).doesNotContain(EMAIL);
    }
}
