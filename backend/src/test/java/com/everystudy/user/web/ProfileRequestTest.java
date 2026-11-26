package com.everystudy.user.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProfileRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> invalidFields(ProfileRequest request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static ProfileRequest withNickname(String nickname) {
        return new ProfileRequest(nickname, "IT", "SEOUL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"준비생", "ab", "Study_Mate_01", "가나다라마바사아자차카타파하가나다라마바"})
    @DisplayName("2~20자의 한글·영문·숫자·밑줄 닉네임은 통과한다")
    void validNicknames(String nickname) {
        assertThat(invalidFields(withNickname(nickname))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "a", "공백 있음", "특수!문자", "ㄱㄴㄷ", "emoji😀", "가나다라마바사아자차카타파하가나다라마바사"})
    @DisplayName("1자, 21자 이상, 공백·특수문자·자모·이모지는 거절한다")
    void invalidNicknames(String nickname) {
        assertThat(invalidFields(withNickname(nickname))).containsExactly("nickname");
    }

    @Test
    @DisplayName("앞뒤 공백은 잘라 낸 뒤 검사한다")
    void stripsSurroundingWhitespace() {
        ProfileRequest request = withNickname("  준비생  ");

        assertThat(request.nickname()).isEqualTo("준비생");
        assertThat(invalidFields(request)).isEmpty();
        assertThat(invalidFields(withNickname("   "))).containsExactly("nickname");
    }

    @Test
    @DisplayName("직군은 대분류와 세부 직무 모두 받고, 모르는 코드는 거절한다")
    void jobCode() {
        assertThat(invalidFields(new ProfileRequest("준비생", "PUBLIC", "SEOUL"))).isEmpty();
        assertThat(invalidFields(new ProfileRequest("준비생", "IT_BACKEND", "SEOUL"))).isEmpty();
        assertThat(invalidFields(new ProfileRequest("준비생", "ANY", "SEOUL"))).containsExactly("jobCode");
        assertThat(invalidFields(new ProfileRequest("준비생", null, "SEOUL"))).containsExactly("jobCode");
    }

    @Test
    @DisplayName("지역은 시·도 코드만 받는다 (예전 구 코드는 거절)")
    void districtCode() {
        assertThat(invalidFields(new ProfileRequest("준비생", "IT", "GYEONGGI"))).isEmpty();
        assertThat(invalidFields(new ProfileRequest("준비생", "IT", "SUWON_JANGAN"))).containsExactly("districtCode");
        assertThat(invalidFields(new ProfileRequest("준비생", "IT", "GANGNAM"))).containsExactly("districtCode");
        assertThat(invalidFields(new ProfileRequest("준비생", "IT", null))).containsExactly("districtCode");
    }
}
