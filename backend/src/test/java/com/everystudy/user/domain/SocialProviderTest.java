package com.everystudy.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialProviderTest {

    @Test
    @DisplayName("공급자 이름은 대소문자를 가리지 않고 찾는다")
    void findsIgnoringCase() {
        assertThat(SocialProvider.find("kakao")).contains(SocialProvider.KAKAO);
        assertThat(SocialProvider.find("GOOGLE")).contains(SocialProvider.GOOGLE);
    }

    @Test
    @DisplayName("모르는 이름이나 null 은 빈 값이다")
    void unknownIsEmpty() {
        assertThat(SocialProvider.find("naver")).isEmpty();
        assertThat(SocialProvider.find(null)).isEmpty();
    }
}
