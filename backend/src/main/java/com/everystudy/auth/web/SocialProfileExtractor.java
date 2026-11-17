package com.everystudy.auth.web;

import java.util.Map;

/**
 * 공급자마다 다른 사용자 정보 응답에서 식별값과 이메일을 뽑는다 (PLAN D-9).
 *
 * <ul>
 *   <li>카카오: {@code id}, {@code kakao_account.email} (선택 동의. {@code is_email_valid}·{@code is_email_verified} 가 모두 참일 때만)</li>
 *   <li>구글: {@code sub}, {@code email} ({@code email_verified} 가 참일 때만)</li>
 * </ul>
 * 확인되지 않은 이메일은 남의 주소일 수 있어 저장하지 않는다.
 */
final class SocialProfileExtractor {

    private SocialProfileExtractor() {
    }

    /** @throws IllegalArgumentException 지원하지 않는 공급자이거나 식별값이 없음 */
    static SocialProfile extract(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "kakao" -> new SocialProfile(registrationId, requireId(attributes, "id"), kakaoEmail(attributes));
            case "google" -> new SocialProfile(registrationId, requireId(attributes, "sub"), googleEmail(attributes));
            default -> throw new IllegalArgumentException("지원하지 않는 소셜 공급자: " + registrationId);
        };
    }

    private static String requireId(Map<String, Object> attributes, String key) {
        Object id = attributes.get(key);
        if (id == null || id.toString().isBlank()) {
            throw new IllegalArgumentException("소셜 사용자 식별값(" + key + ")이 없음");
        }
        return id.toString();
    }

    private static String kakaoEmail(Map<String, Object> attributes) {
        if (!(attributes.get("kakao_account") instanceof Map<?, ?> account)) {
            return null;
        }
        boolean usable = isTrue(account.get("is_email_valid")) && isTrue(account.get("is_email_verified"));
        return usable ? email(account.get("email")) : null;
    }

    private static String googleEmail(Map<String, Object> attributes) {
        return isTrue(attributes.get("email_verified")) ? email(attributes.get("email")) : null;
    }

    private static String email(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equals(value);
    }
}
