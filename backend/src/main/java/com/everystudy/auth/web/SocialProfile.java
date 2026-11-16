package com.everystudy.auth.web;

/**
 * 공급자 응답에서 우리가 쓰는 값만 뽑은 것.
 *
 * @param provider       OAuth2 등록 ID ({@code kakao}, {@code google})
 * @param providerUserId 공급자의 사용자 식별값
 * @param verifiedEmail  공급자가 확인한 이메일. 없거나 확인되지 않았으면 {@code null}
 */
record SocialProfile(String provider, String providerUserId, String verifiedEmail) {

    /** 이메일이 로그에 찍히지 않게 한다. */
    @Override
    public String toString() {
        return "SocialProfile[provider=" + provider + "]";
    }
}
