package com.everystudy.auth;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 통합 테스트용 가짜 소셜 공급자. 카카오·구글에는 접속하지 않고, 토큰 교환과 사용자 정보 조회만 가짜로 바꾼다
 * (ARCHITECTURE 8.3). 쓰는 테스트는 {@code @Import(FakeSocialProvider.class)} 를 붙인다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeSocialProvider {

    /** 가짜 공급자가 내주는 소셜 액세스 토큰. 어디에도 저장되면 안 된다. */
    public static final String SOCIAL_ACCESS_TOKEN = "fake-social-access-token";

    /** 가짜 공급자가 다음 로그인에서 돌려줄 사용자 정보. */
    private static final AtomicReference<Map<String, Object>> NEXT_USER_INFO = new AtomicReference<>();

    @Bean
    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> fakeTokenResponseClient() {
        return request -> OAuth2AccessTokenResponse.withToken(SOCIAL_ACCESS_TOKEN)
                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                .expiresIn(60)
                .build();
    }

    @Bean
    OAuth2UserService<OAuth2UserRequest, OAuth2User> fakeUserService() {
        return request -> new DefaultOAuth2User(
                AuthorityUtils.createAuthorityList("OAUTH2_USER"),
                NEXT_USER_INFO.get(),
                request.getClientRegistration().getProviderDetails().getUserInfoEndpoint()
                        .getUserNameAttributeName());
    }

    /** 인가 요청부터 콜백까지 한 번의 로그인을 수행하고 콜백 응답(토큰 쿠키 포함)을 돌려준다. */
    public static MockHttpServletResponse login(MockMvcTester mvc, String registrationId, Map<String, Object> userInfo) {
        MvcTestResult start = mvc.get().uri("/oauth2/authorization/" + registrationId).exchange();
        return callback(mvc, registrationId, (MockHttpSession) start.getRequest().getSession(false),
                stateOf(start), userInfo);
    }

    /** 공급자가 돌려보낸 콜백 한 건. */
    public static MockHttpServletResponse callback(MockMvcTester mvc, String registrationId, MockHttpSession session,
            String state, Map<String, Object> userInfo) {
        NEXT_USER_INFO.set(userInfo);
        return mvc.get().uri("/login/oauth2/code/" + registrationId)
                .param("code", "fake-authorization-code")
                .param("state", state)
                .session(session)
                .exchange().getResponse();
    }

    public static String stateOf(MvcTestResult start) {
        // 주소에 들어간 state 는 URL 인코딩돼 있다 (끝의 '=' 가 %3D).
        String encoded = UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl())
                .build().getQueryParams().getFirst("state");
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    public static Map<String, Object> kakaoUser(long id, String email) {
        return email == null
                ? Map.of("id", id)
                : Map.of("id", id, "kakao_account",
                        Map.of("email", email, "is_email_valid", true, "is_email_verified", true));
    }

    public static Map<String, Object> googleUser(String sub, String email) {
        return email == null
                ? Map.of("sub", sub)
                : Map.of("sub", sub, "email", email, "email_verified", true);
    }
}
