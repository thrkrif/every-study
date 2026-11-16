package com.everystudy.user.domain;

import java.util.Locale;
import java.util.Optional;

/** 소셜 로그인 공급자 (PRD 4.1). */
public enum SocialProvider {
    KAKAO,
    GOOGLE;

    /** 대소문자를 가리지 않고 찾는다 (OAuth2 등록 ID 는 소문자다). */
    public static Optional<SocialProvider> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String upper = name.toUpperCase(Locale.ROOT);
        for (SocialProvider provider : values()) {
            if (provider.name().equals(upper)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }
}
