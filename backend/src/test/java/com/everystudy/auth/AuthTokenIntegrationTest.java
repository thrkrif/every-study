package com.everystudy.auth;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.common.security.LoginUser;
import com.everystudy.support.IntegrationTestSupport;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 뒤의 인증 동작을 실제 보안 필터와 DB 로 검증한다 (PLAN T-005, ADR-0002 결정 2·3·4·6):
 * 액세스 쿠키 인증, 갱신(회전·재사용 탐지), 로그아웃, CSRF, {@code Origin}.
 */
@Import(FakeSocialProvider.class)
class AuthTokenIntegrationTest extends IntegrationTestSupport {

    private static final String FRONTEND = "http://localhost:3000";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String PROBE = "/api/v1/test-probe";

    /**
     * 로그인이 필요한 API 의 대역. 아직 보호할 실제 API 가 없어 테스트에서만 둔다.
     * 테스트 클래스 경로도 컴포넌트 스캔 대상이라 따로 등록하지 않아도 빈이 된다.
     */
    @RestController
    static class ProbeController {

        @GetMapping(PROBE)
        Map<String, Object> read(@AuthenticationPrincipal LoginUser user) {
            return Map.of("userId", user.userId(), "role", user.role());
        }

        @PostMapping(PROBE)
        Map<String, Object> change(@AuthenticationPrincipal LoginUser user) {
            return Map.of("userId", user.userId());
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${everystudy.auth.token.signing-key}")
    String signingKey;

    /** 로그인한 브라우저가 들고 있는 쿠키. */
    private Cookie access;
    private Cookie refresh;
    private Cookie csrf;

    @BeforeEach
    void loginAsNewUser() {
        cleanDatabase(jdbc);

        MockHttpServletResponse login = FakeSocialProvider.login(mvc, "kakao", kakaoUser(1001L, null));
        access = login.getCookie("access_token");
        refresh = login.getCookie("refresh_token");
        csrf = fetchCsrfCookie();
    }

    @Nested
    @DisplayName("액세스 토큰 인증")
    class AccessTokenAuthentication {

        @Test
        @DisplayName("유효한 액세스 쿠키면 그 회원으로 처리한다")
        void validCookieIsAuthenticated() {
            assertThat(mvc.get().uri(PROBE).cookie(access))
                    .hasStatusOk()
                    .bodyJson()
                    .isLenientlyEqualTo("{\"userId\": " + userId() + ", \"role\": \"USER\"}");
        }

        @Test
        @DisplayName("쿠키 없이 보호된 API 를 부르면 401 UNAUTHENTICATED 다")
        void missingCookieIs401() {
            MvcTestResult result = mvc.get().uri(PROBE).exchange();

            assertUnauthenticated(result);
            assertThat(result).bodyJson().extractingPath("$.traceId").asString().isNotBlank();
            assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo(PROBE);
        }

        @Test
        @DisplayName("만료된 액세스 토큰은 401 UNAUTHENTICATED 다")
        void expiredTokenIs401() throws Exception {
            Cookie expired = new Cookie("access_token", jwt(signingKey, Instant.now().minusSeconds(1)));

            assertUnauthenticated(mvc.get().uri(PROBE).cookie(expired).exchange());
        }

        @Test
        @DisplayName("다른 키로 서명한 토큰은 401 이다")
        void forgedTokenIs401() throws Exception {
            Cookie forged = new Cookie("access_token",
                    jwt("another-signing-key-0123456789abcdefgh", Instant.now().plusSeconds(600)));

            assertUnauthenticated(mvc.get().uri(PROBE).cookie(forged).exchange());
        }

        @Test
        @DisplayName("Authorization 헤더의 토큰은 받지 않는다")
        void authorizationHeaderIsIgnored() {
            assertUnauthenticated(mvc.get().uri(PROBE)
                    .header("Authorization", "Bearer " + access.getValue())
                    .exchange());
        }

        @Test
        @DisplayName("API 문서는 로그인 없이, 만료된 쿠키가 있어도 열린다")
        void apiDocsStayOpen() throws Exception {
            Cookie expired = new Cookie("access_token", jwt(signingKey, Instant.now().minusSeconds(1)));

            assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk();
            assertThat(mvc.get().uri("/v3/api-docs").cookie(expired)).hasStatusOk();
        }
    }

    @Nested
    @DisplayName("갱신")
    class Refresh {

        @Test
        @DisplayName("새 토큰 한 쌍을 쿠키로 내려주고, 쓴 토큰은 쓴 것으로 표시한다")
        void rotatesTokens() {
            Instant before = Instant.now();

            MockHttpServletResponse response = refresh(refresh).getResponse();

            assertThat(response.getStatus()).isEqualTo(204);
            MockCookie newAccess = (MockCookie) response.getCookie("access_token");
            MockCookie newRefresh = (MockCookie) response.getCookie("refresh_token");
            assertThat(newRefresh.getValue()).isNotEqualTo(refresh.getValue());
            assertThat(List.of(newAccess, newRefresh)).allSatisfy(cookie -> {
                assertThat(cookie.isHttpOnly()).isTrue();
                assertThat(cookie.getSecure()).isTrue();
                assertThat(cookie.getSameSite()).isEqualTo("Lax");
            });
            assertThat(newAccess.getPath()).isEqualTo("/");
            assertThat(newAccess.getMaxAge()).isEqualTo((int) Duration.ofMinutes(15).toSeconds());
            assertThat(newRefresh.getPath()).isEqualTo("/api/v1/auth");
            assertThat(newRefresh.getMaxAge()).isEqualTo((int) Duration.ofDays(14).toSeconds());

            // 계열 하나에 쓴 토큰 1장과 새 토큰 1장.
            assertThat(count("refresh_token")).isEqualTo(2);
            assertThat(count("refresh_token WHERE used_at IS NOT NULL")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT family_id) FROM refresh_token", Integer.class))
                    .isEqualTo(1);
            // 새 토큰의 수명은 회전한 시각부터 다시 14일이다.
            Instant expiresAt = jdbc.queryForObject(
                    "SELECT expires_at FROM refresh_token WHERE used_at IS NULL", LocalDateTime.class)
                    .toInstant(ZoneOffset.UTC);
            assertThat(expiresAt).isBetween(
                    before.plus(Duration.ofDays(14)).minusSeconds(5), Instant.now().plus(Duration.ofDays(14)));

            assertThat(mvc.get().uri(PROBE).cookie(newAccess)).hasStatusOk();
            assertThat(refresh(newRefresh)).hasStatus(204);
        }

        @Test
        @DisplayName("액세스 토큰이 만료된 상태에서도 갱신된다")
        void worksWithExpiredAccessToken() throws Exception {
            Cookie expired = new Cookie("access_token", jwt(signingKey, Instant.now().minusSeconds(1)));

            assertThat(mvc.post().uri(REFRESH).cookie(expired, refresh, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                    .hasStatus(204);
        }

        @Test
        @DisplayName("이미 쓴 토큰이 다시 오면 401 이고, 같은 계열의 새 토큰까지 모두 폐기한다")
        void reuseRevokesWholeFamily() {
            Cookie rotated = refresh(refresh).getResponse().getCookie("refresh_token");

            MvcTestResult reused = refresh(refresh);

            assertUnauthenticated(reused);
            assertCookiesCleared(reused.getResponse());
            assertThat(count("refresh_token WHERE revoked_at IS NULL")).isZero();
            // 정상 사용자가 받은 새 토큰도 더는 쓸 수 없다 (다시 로그인해야 한다).
            assertUnauthenticated(refresh(rotated));
        }

        @Test
        @DisplayName("재사용으로 폐기되는 것은 그 계열뿐이다. 다른 로그인의 토큰은 남는다")
        void reuseDoesNotTouchOtherFamilies() {
            Cookie otherLogin = FakeSocialProvider.login(mvc, "kakao", kakaoUser(1001L, null))
                    .getCookie("refresh_token");
            refresh(refresh);
            refresh(refresh);

            assertThat(refresh(otherLogin)).hasStatus(204);
        }

        @Test
        @DisplayName("만료된 리프레시 토큰은 401 이고 쿠키를 지운다")
        void expiredRefreshTokenIs401() {
            jdbc.update("UPDATE refresh_token SET expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND");

            MvcTestResult result = refresh(refresh);

            assertUnauthenticated(result);
            assertCookiesCleared(result.getResponse());
            assertThat(count("refresh_token")).isEqualTo(1);
        }

        @Test
        @DisplayName("리프레시 쿠키가 없거나 모르는 값이면 401 이다")
        void missingOrUnknownTokenIs401() {
            assertUnauthenticated(mvc.post().uri(REFRESH)
                    .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).exchange());
            assertUnauthenticated(refresh(new Cookie("refresh_token", "unknown-token-value")));
        }

        @Test
        @DisplayName("탈퇴한 회원의 토큰은 401 이고 계열을 폐기한다")
        void withdrawnUserIs401() {
            jdbc.update("UPDATE user SET status = 'WITHDRAWN'");

            assertUnauthenticated(refresh(refresh));
            assertThat(count("refresh_token WHERE revoked_at IS NULL")).isZero();
        }

        @Test
        @DisplayName("갱신한 액세스 토큰은 그 시점의 역할을 담는다")
        void newAccessTokenCarriesCurrentRole() {
            jdbc.update("UPDATE user SET role = 'ADMIN'");

            Cookie newAccess = refresh(refresh).getResponse().getCookie("access_token");

            assertThat(mvc.get().uri(PROBE).cookie(newAccess))
                    .bodyJson().extractingPath("$.role").isEqualTo("ADMIN");
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("리프레시 토큰의 계열을 폐기하고 토큰 쿠키와 CSRF 쿠키를 지운다")
        void revokesFamilyAndClearsCookies() {
            MvcTestResult result = logout(access, refresh);

            assertThat(result).hasStatus(204);
            assertCookiesCleared(result.getResponse());
            assertThat(result.getResponse().getCookie("XSRF-TOKEN").getMaxAge()).isZero();
            assertThat(count("refresh_token WHERE revoked_at IS NULL")).isZero();

            // 지워지기 전의 리프레시 토큰을 들고 있어도 더는 갱신되지 않는다.
            assertUnauthenticated(refresh(refresh));
        }

        @Test
        @DisplayName("회전된 계열은 지금 토큰과 예전 토큰을 모두 폐기한다")
        void revokesAllTokensOfRotatedFamily() {
            Cookie rotated = refresh(refresh).getResponse().getCookie("refresh_token");

            logout(rotated);

            assertThat(count("refresh_token")).isEqualTo(2);
            assertThat(count("refresh_token WHERE revoked_at IS NULL")).isZero();
        }

        @Test
        @DisplayName("다른 기기의 로그인(다른 계열)은 남는다")
        void keepsOtherLogins() {
            Cookie otherLogin = FakeSocialProvider.login(mvc, "kakao", kakaoUser(1001L, null))
                    .getCookie("refresh_token");

            logout(refresh);

            assertThat(refresh(otherLogin)).hasStatus(204);
        }

        @Test
        @DisplayName("액세스 토큰이 만료됐어도 로그아웃된다")
        void worksWithExpiredAccessToken() throws Exception {
            Cookie expired = new Cookie("access_token", jwt(signingKey, Instant.now().minusSeconds(1)));

            assertThat(logout(expired, refresh)).hasStatus(204);
            assertThat(count("refresh_token WHERE revoked_at IS NULL")).isZero();
        }

        @Test
        @DisplayName("쿠키가 없거나 이미 로그아웃했어도 204 다")
        void isIdempotent() {
            assertThat(logout(refresh)).hasStatus(204);
            assertThat(logout(refresh)).hasStatus(204);
            assertThat(logout()).hasStatus(204);
        }
    }

    @Nested
    @DisplayName("CSRF")
    class Csrf {

        @Test
        @DisplayName("CSRF 쿠키는 JS 가 읽을 수 있고(HttpOnly 아님) Secure, SameSite=Lax 다")
        void csrfCookieAttributes() {
            assertThat(csrf.getValue()).isNotBlank();
            assertThat(csrf.isHttpOnly()).isFalse();
            assertThat(csrf.getSecure()).isTrue();
            assertThat(csrf.getAttribute("SameSite")).isEqualTo("Lax");
            assertThat(csrf.getPath()).isEqualTo("/");
            assertThat(csrf.getDomain()).isNull();
        }

        @Test
        @DisplayName("CSRF 쿠키가 없는 요청이면 응답에 쿠키를 내려주고, 이미 있으면 다시 내려주지 않는다")
        void cookieIsIssuedWhenMissing() {
            // 로그인하지 않은 첫 방문(401 응답)에서도 받는다.
            assertThat(mvc.get().uri(PROBE).exchange().getResponse().getCookie("XSRF-TOKEN")).isNotNull();
            assertThat(mvc.get().uri(PROBE).cookie(access, csrf).exchange().getResponse().getCookie("XSRF-TOKEN"))
                    .isNull();
        }

        @Test
        @DisplayName("헤더 없이 거절된 응답에도 쿠키가 실려, 그 값으로 다시 보내면 통과한다")
        void rejectedResponseCarriesCookieForRetry() {
            MvcTestResult rejected = mvc.post().uri(PROBE).cookie(access).exchange();
            Cookie issued = rejected.getResponse().getCookie("XSRF-TOKEN");

            assertCsrfInvalid(rejected);
            assertThat(mvc.post().uri(PROBE).cookie(access, issued).header("X-XSRF-TOKEN", issued.getValue()))
                    .hasStatusOk();
        }

        @Test
        @DisplayName("쿠키 값과 같은 헤더를 보내면 상태 변경 요청이 통과한다")
        void matchingHeaderPasses() {
            assertThat(mvc.post().uri(PROBE).cookie(access, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                    .hasStatusOk();
        }

        @Test
        @DisplayName("헤더가 없으면 403 CSRF_INVALID 다")
        void missingHeaderIs403() {
            MvcTestResult result = mvc.post().uri(PROBE).cookie(access, csrf).exchange();

            assertThat(result).hasStatus(403);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("CSRF_INVALID");
            assertThat(result).bodyJson().extractingPath("$.traceId").asString().isNotBlank();
        }

        @Test
        @DisplayName("헤더가 쿠키와 다르면 403 CSRF_INVALID 다")
        void mismatchedHeaderIs403() {
            assertCsrfInvalid(mvc.post().uri(PROBE).cookie(access, csrf)
                    .header("X-XSRF-TOKEN", "another-value").exchange());
        }

        @Test
        @DisplayName("쿠키 없이 헤더만 보내면 403 CSRF_INVALID 다")
        void headerWithoutCookieIs403() {
            assertCsrfInvalid(mvc.post().uri(PROBE).cookie(access)
                    .header("X-XSRF-TOKEN", csrf.getValue()).exchange());
        }

        @Test
        @DisplayName("갱신과 로그아웃도 CSRF 검사를 받고, 거절되면 토큰을 건드리지 않는다")
        void refreshAndLogoutRequireCsrf() {
            assertCsrfInvalid(mvc.post().uri(REFRESH).cookie(refresh, csrf).exchange());
            assertCsrfInvalid(mvc.post().uri(LOGOUT).cookie(refresh, csrf).exchange());

            assertThat(count("refresh_token WHERE used_at IS NULL AND revoked_at IS NULL")).isEqualTo(1);
        }

        @Test
        @DisplayName("조회(GET)는 CSRF 헤더 없이 통과한다")
        void safeMethodNeedsNoCsrf() {
            assertThat(mvc.get().uri(PROBE).cookie(access)).hasStatusOk();
        }

        @Test
        @DisplayName("로그인하면 이전 CSRF 토큰은 버려진다 (지워지거나 새 값으로 바뀐다)")
        void loginDiscardsPreviousCsrfToken() {
            MvcTestResult start = mvc.get().uri("/oauth2/authorization/kakao").cookie(csrf).exchange();
            MockHttpServletResponse login = mvc.get().uri("/login/oauth2/code/kakao")
                    .param("code", "fake-authorization-code")
                    .param("state", FakeSocialProvider.stateOf(start))
                    .session((MockHttpSession) start.getRequest().getSession(false))
                    .cookie(csrf)
                    .exchange().getResponse();

            List<Cookie> csrfCookies = List.of(login.getCookies()).stream()
                    .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                    .toList();
            assertThat(csrfCookies).isNotEmpty();
            assertThat(csrfCookies.getLast().getValue()).isNotEqualTo(csrf.getValue());
        }
    }

    @Nested
    @DisplayName("Origin 과 CORS")
    class Origin {

        @Test
        @DisplayName("프론트 오리진의 요청은 통과하고, 쿠키를 포함한 응답을 그 오리진에만 허용한다")
        void frontendOriginIsAllowed() {
            MvcTestResult result = mvc.post().uri(PROBE)
                    .header("Origin", FRONTEND)
                    .cookie(access, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo(FRONTEND);
            assertThat(result.getResponse().getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        }

        @Test
        @DisplayName("허용하지 않은 오리진의 요청은 쿠키와 CSRF 토큰이 맞아도 403 이고 처리되지 않는다")
        void otherOriginIsRejected() {
            MvcTestResult result = mvc.post().uri(REFRESH)
                    .header("Origin", "https://evil.example.com")
                    .cookie(refresh, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                    .exchange();

            assertThat(result).hasStatus(403);
            assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
            assertThat(count("refresh_token WHERE used_at IS NOT NULL")).isZero();
        }

        @Test
        @DisplayName("조회(GET)도 허용하지 않은 오리진이면 403 이다")
        void otherOriginIsRejectedForGet() {
            assertThat(mvc.get().uri(PROBE).header("Origin", "https://evil.example.com").cookie(access))
                    .hasStatus(403);
        }

        @Test
        @DisplayName("사전 요청(preflight)은 프론트 오리진에만, CSRF 헤더를 허용한다고 답한다")
        void preflight() {
            MvcTestResult allowed = mvc.options().uri(PROBE)
                    .header("Origin", FRONTEND)
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "x-xsrf-token, content-type")
                    .exchange();
            MvcTestResult denied = mvc.options().uri(PROBE)
                    .header("Origin", "https://evil.example.com")
                    .header("Access-Control-Request-Method", "POST")
                    .exchange();

            assertThat(allowed).hasStatusOk();
            assertThat(allowed.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo(FRONTEND);
            assertThat(allowed.getResponse().getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
            assertThat(allowed.getResponse().getHeader("Access-Control-Allow-Headers")).containsIgnoringCase("x-xsrf-token");
            assertThat(denied).hasStatus(403);
        }
    }

    /** 브라우저가 하는 것처럼, CSRF 쿠키 없이 보낸 조회 요청의 응답에서 쿠키를 받는다. */
    private Cookie fetchCsrfCookie() {
        return mvc.get().uri(PROBE).cookie(access).exchange().getResponse().getCookie("XSRF-TOKEN");
    }

    private MvcTestResult refresh(Cookie refreshToken) {
        return mvc.post().uri(REFRESH)
                .cookie(refreshToken, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .exchange();
    }

    private MvcTestResult logout(Cookie... cookies) {
        Cookie[] all = new Cookie[cookies.length + 1];
        System.arraycopy(cookies, 0, all, 0, cookies.length);
        all[cookies.length] = csrf;
        return mvc.post().uri(LOGOUT).cookie(all).header("X-XSRF-TOKEN", csrf.getValue()).exchange();
    }

    private static void assertUnauthenticated(MvcTestResult result) {
        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    private static void assertCsrfInvalid(MvcTestResult result) {
        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("CSRF_INVALID");
    }

    /** 토큰 쿠키 둘 다 쓸 때와 같은 경로에 수명 0 으로 내려왔는가. */
    private static void assertCookiesCleared(MockHttpServletResponse response) {
        Cookie clearedAccess = response.getCookie("access_token");
        Cookie clearedRefresh = response.getCookie("refresh_token");
        assertThat(clearedAccess.getMaxAge()).isZero();
        assertThat(clearedAccess.getPath()).isEqualTo("/");
        assertThat(clearedRefresh.getMaxAge()).isZero();
        assertThat(clearedRefresh.getPath()).isEqualTo("/api/v1/auth");
    }

    private long userId() {
        return jdbc.queryForObject("SELECT id FROM user", Long.class);
    }

    private int count(String tableAndCondition) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tableAndCondition, Integer.class);
    }

    /** 주어진 키로 서명한 액세스 토큰. 만료·위조 사례를 만들 때 쓴다. */
    private String jwt(String key, Instant expiresAt) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .subject(Long.toString(userId()))
                        .claim("role", "USER")
                        .issueTime(Date.from(expiresAt.minus(Duration.ofMinutes(15))))
                        .expirationTime(Date.from(expiresAt))
                        .build());
        jwt.sign(new MACSigner(key.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
