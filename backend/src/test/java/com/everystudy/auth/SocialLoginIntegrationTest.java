package com.everystudy.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.support.IntegrationTestSupport;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
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
 * 소셜 로그인 흐름 전체(인가 요청 → 콜백 → 회원 조회·생성 → 쿠키 발급 → 프론트로 이동)를 실제 보안 필터와 DB 로 검증한다.
 * 카카오·구글에는 접속하지 않는다. 토큰 교환과 사용자 정보 조회만 가짜로 바꾼다 (ARCHITECTURE 8.3).
 */
class SocialLoginIntegrationTest extends IntegrationTestSupport {

    private static final String FRONTEND = "http://localhost:3000";
    // 가짜 공급자가 내주는 소셜 액세스 토큰. 어디에도 저장되면 안 된다.
    private static final String SOCIAL_ACCESS_TOKEN = "fake-social-access-token";
    private static final String EMAIL = "tester@example.com";

    /** 가짜 공급자가 다음 로그인에서 돌려줄 사용자 정보. */
    static final AtomicReference<Map<String, Object>> NEXT_USER_INFO = new AtomicReference<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeSocialProviderConfig {

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
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OAuth2AuthorizedClientService authorizedClients;

    @Value("${everystudy.auth.token.signing-key}")
    String signingKey;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM social_account");
        jdbc.update("DELETE FROM user");
    }

    @Test
    @DisplayName("로그인을 시작하면 공급자의 인가 주소로 보낸다")
    void startRedirectsToProvider() {
        assertThat(mvc.get().uri("/oauth2/authorization/kakao"))
                .hasStatus(302)
                .redirectedUrl().startsWith("https://kauth.kakao.com/oauth/authorize?")
                .contains("client_id=test-kakao-client", "scope=account_email",
                        "redirect_uri=http://localhost/login/oauth2/code/kakao");
        assertThat(mvc.get().uri("/oauth2/authorization/google"))
                .hasStatus(302)
                .redirectedUrl().startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
                .contains("scope=email");
    }

    @Test
    @DisplayName("처음 로그인하면 회원과 소셜 계정을 만들고 온보딩 화면으로 보낸다")
    void firstLoginRegistersUserAndRedirectsToOnboarding() {
        MockHttpServletResponse response = login("kakao", kakaoUser(1001L, EMAIL));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/onboarding");
        assertThat(jdbc.queryForMap("SELECT nickname, email, job_code, district_code, role, status FROM user"))
                .containsEntry("nickname", null)
                .containsEntry("email", EMAIL)
                .containsEntry("job_code", null)
                .containsEntry("district_code", null)
                .containsEntry("role", "USER")
                .containsEntry("status", "ACTIVE");
        assertThat(jdbc.queryForMap("SELECT provider, provider_user_id FROM social_account"))
                .containsEntry("provider", "KAKAO")
                .containsEntry("provider_user_id", "1001");
    }

    @Test
    @DisplayName("같은 소셜 계정으로 다시 로그인하면 같은 회원이다")
    void sameSocialAccountIsSameUser() throws Exception {
        String first = subjectOf(login("kakao", kakaoUser(1001L, EMAIL)));
        String second = subjectOf(login("kakao", kakaoUser(1001L, EMAIL)));

        assertThat(second).isEqualTo(first);
        assertThat(count("user")).isEqualTo(1);
        assertThat(count("social_account")).isEqualTo(1);
    }

    @Test
    @DisplayName("공급자가 다르면 식별값이 같아도 다른 회원이다")
    void sameIdOnDifferentProviderIsDifferentUser() throws Exception {
        String kakao = subjectOf(login("kakao", kakaoUser(1001L, null)));
        String google = subjectOf(login("google", googleUser("1001", null)));

        assertThat(google).isNotEqualTo(kakao);
        assertThat(count("user")).isEqualTo(2);
    }

    @Test
    @DisplayName("온보딩을 마친 회원은 홈으로 보낸다")
    void onboardedUserGoesHome() {
        login("google", googleUser("g-1", EMAIL));
        jdbc.update("UPDATE user SET nickname = '준비생', job_code = 'BACKEND', district_code = 'GANGNAM'");

        MockHttpServletResponse response = login("google", googleUser("g-1", EMAIL));

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/");
    }

    @Test
    @DisplayName("카카오에서 이메일 제공에 동의하지 않아도 이메일 없이 가입된다")
    void kakaoWithoutEmailRegistersWithNullEmail() {
        MockHttpServletResponse response = login("kakao", kakaoUser(1001L, null));

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/onboarding");
        assertThat(jdbc.queryForObject("SELECT email FROM user", String.class)).isNull();
    }

    @Test
    @DisplayName("토큰 쿠키는 HttpOnly, Secure, SameSite=Lax 이고 리프레시는 인증 경로로만 보낸다")
    void tokenCookieAttributes() {
        MockHttpServletResponse response = login("kakao", kakaoUser(1001L, EMAIL));

        MockCookie access = (MockCookie) response.getCookie("access_token");
        MockCookie refresh = (MockCookie) response.getCookie("refresh_token");
        assertThat(List.of(access, refresh)).allSatisfy(cookie -> {
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getSecure()).isTrue();
            assertThat(cookie.getSameSite()).isEqualTo("Lax");
            assertThat(cookie.getDomain()).isNull();
        });
        assertThat(access.getPath()).isEqualTo("/");
        assertThat(access.getMaxAge()).isEqualTo((int) Duration.ofMinutes(15).toSeconds());
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth");
        assertThat(refresh.getMaxAge()).isEqualTo((int) Duration.ofDays(14).toSeconds());
    }

    @Test
    @DisplayName("액세스 쿠키는 우리 키로 서명한 15분짜리 JWT 이고 회원 ID 와 역할을 담는다")
    void accessCookieIsOurJwt() throws Exception {
        MockHttpServletResponse response = login("kakao", kakaoUser(1001L, EMAIL));

        SignedJWT jwt = SignedJWT.parse(response.getCookie("access_token").getValue());
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(jwt.verify(new MACVerifier(signingKey.getBytes(StandardCharsets.UTF_8)))).isTrue();
        assertThat(claims.getSubject()).isEqualTo(jdbc.queryForObject("SELECT id FROM user", String.class));
        assertThat(claims.getStringClaim("role")).isEqualTo("USER");
        assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("리프레시 토큰은 해시만 저장하고 14일 뒤 만료된다")
    void refreshTokenIsStoredAsHashOnly() throws Exception {
        Instant before = Instant.now();
        MockHttpServletResponse response = login("kakao", kakaoUser(1001L, EMAIL));

        String raw = response.getCookie("refresh_token").getValue();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT user_id, family_id, token_hash, expires_at, used_at, revoked_at FROM refresh_token");
        assertThat(raw).hasSizeGreaterThanOrEqualTo(43);
        assertThat(row.get("token_hash")).isEqualTo(sha256Hex(raw)).isNotEqualTo(raw);
        assertThat((String) row.get("family_id")).hasSize(36);
        assertThat(row.get("used_at")).isNull();
        assertThat(row.get("revoked_at")).isNull();
        // DATETIME 에는 UTC 시각이 저장된다 (ARCHITECTURE 5장). JVM 시간대의 영향을 받지 않게 값 그대로 읽는다.
        Instant expiresAt = jdbc.queryForObject("SELECT expires_at FROM refresh_token", LocalDateTime.class)
                .toInstant(ZoneOffset.UTC);
        assertThat(expiresAt).isBetween(
                before.plus(Duration.ofDays(14)).minusSeconds(5), Instant.now().plus(Duration.ofDays(14)));
    }

    @Test
    @DisplayName("로그인할 때마다 새 리프레시 계열이 생긴다")
    void eachLoginStartsNewFamily() {
        login("kakao", kakaoUser(1001L, EMAIL));
        login("kakao", kakaoUser(1001L, EMAIL));

        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT family_id) FROM refresh_token", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("소셜 토큰은 쿠키에도 서버에도 남기지 않는다")
    void socialTokenIsNotKept() {
        MockHttpServletResponse response = login("kakao", kakaoUser(1001L, EMAIL));

        assertThat(response.getCookies()).extracting(Cookie::getValue).doesNotContain(SOCIAL_ACCESS_TOKEN);
        assertThat((Object) authorizedClients.loadAuthorizedClient("kakao", "1001")).isNull();
    }

    @Test
    @DisplayName("로그인이 끝나면 인가 요청에 쓴 서버 세션을 없앤다")
    void handshakeSessionIsInvalidated() {
        MvcTestResult start = mvc.get().uri("/oauth2/authorization/kakao").exchange();
        MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);
        NEXT_USER_INFO.set(kakaoUser(1001L, EMAIL));

        callback("kakao", session, stateOf(start));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("사용자가 동의를 취소하면 쿠키 없이 로그인 화면으로 보낸다")
    void deniedConsentRedirectsToLoginWithoutCookies() {
        MvcTestResult start = mvc.get().uri("/oauth2/authorization/kakao").exchange();
        MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);

        MockHttpServletResponse response = mvc.get().uri("/login/oauth2/code/kakao")
                .param("error", "access_denied")
                .param("state", stateOf(start))
                .session(session)
                .exchange().getResponse();

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/login?error=social_login_failed");
        assertThat(response.getCookie("access_token")).isNull();
        assertThat(response.getCookie("refresh_token")).isNull();
        assertThat(count("user")).isZero();
    }

    @Test
    @DisplayName("state 가 맞지 않는 콜백은 회원을 만들지 않고 로그인 화면으로 보낸다")
    void forgedStateIsRejected() {
        MvcTestResult start = mvc.get().uri("/oauth2/authorization/kakao").exchange();
        MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);
        NEXT_USER_INFO.set(kakaoUser(1001L, EMAIL));

        MockHttpServletResponse response = callback("kakao", session, "forged-state");

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/login?error=social_login_failed");
        assertThat(response.getCookie("access_token")).isNull();
        assertThat(count("user")).isZero();
    }

    /** 인가 요청부터 콜백까지 한 번의 로그인을 수행하고 콜백 응답을 돌려준다. */
    private MockHttpServletResponse login(String registrationId, Map<String, Object> userInfo) {
        MvcTestResult start = mvc.get().uri("/oauth2/authorization/" + registrationId).exchange();
        NEXT_USER_INFO.set(userInfo);
        return callback(registrationId, (MockHttpSession) start.getRequest().getSession(false), stateOf(start));
    }

    private MockHttpServletResponse callback(String registrationId, MockHttpSession session, String state) {
        return mvc.get().uri("/login/oauth2/code/" + registrationId)
                .param("code", "fake-authorization-code")
                .param("state", state)
                .session(session)
                .exchange().getResponse();
    }

    private static String stateOf(MvcTestResult start) {
        // 주소에 들어간 state 는 URL 인코딩돼 있다 (끝의 '=' 가 %3D).
        String encoded = UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl())
                .build().getQueryParams().getFirst("state");
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private static String subjectOf(MockHttpServletResponse response) throws Exception {
        return SignedJWT.parse(response.getCookie("access_token").getValue()).getJWTClaimsSet().getSubject();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static Map<String, Object> kakaoUser(long id, String email) {
        return email == null
                ? Map.of("id", id)
                : Map.of("id", id, "kakao_account",
                        Map.of("email", email, "is_email_valid", true, "is_email_verified", true));
    }

    private static Map<String, Object> googleUser(String sub, String email) {
        return email == null
                ? Map.of("sub", sub)
                : Map.of("sub", sub, "email", email, "email_verified", true);
    }

    private static String sha256Hex(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
