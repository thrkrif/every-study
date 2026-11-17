package com.everystudy.user;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.auth.FakeSocialProvider;
import com.everystudy.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 내 정보 조회와 온보딩·프로필 수정 (PLAN T-006, API 3장). 닉네임 중복은 실제 DB 의 유니크 제약·정렬 규칙으로 본다. */
@Import(FakeSocialProvider.class)
class ProfileIntegrationTest extends IntegrationTestSupport {

    private static final String ME = "/api/v1/me";
    private static final String PROFILE = "/api/v1/me/profile";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private Cookie access;
    private Cookie csrf;

    @BeforeEach
    void loginAsNewUser() {
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM social_account");
        jdbc.update("DELETE FROM user");
        access = loginAccessCookie(1001L);
        csrf = mvc.get().uri(ME).cookie(access).exchange().getResponse().getCookie("XSRF-TOKEN");
    }

    @Test
    @DisplayName("온보딩 전에는 닉네임·직군·지역이 비어 있고 onboarded 가 false 다")
    void meBeforeOnboarding() {
        assertThat(mvc.get().uri(ME).cookie(access))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"nickname": null, "jobCode": null, "districtCode": null,
                         "role": "USER", "onboarded": false}
                        """);
        assertThat(mvc.get().uri(ME).cookie(access)).bodyJson().extractingPath("$.joinedAt").asString().isNotBlank();
    }

    @Test
    @DisplayName("닉네임·직군·지역을 저장하면 onboarded 가 true 가 되고, 다음 로그인은 홈으로 간다")
    void onboardingCompletes() {
        MvcTestResult result = putProfile(access, "준비생", "IT_BACKEND", "SUWON_YEONGTONG");

        assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"nickname": "준비생", "jobCode": "IT_BACKEND", "districtCode": "SUWON_YEONGTONG", "onboarded": true}
                """);
        assertThat(jdbc.queryForMap("SELECT nickname, job_code, district_code FROM user"))
                .containsEntry("nickname", "준비생")
                .containsEntry("job_code", "IT_BACKEND")
                .containsEntry("district_code", "SUWON_YEONGTONG");
        assertThat(mvc.get().uri(ME).cookie(access)).bodyJson().extractingPath("$.onboarded").isEqualTo(true);

        MockHttpServletResponse login = FakeSocialProvider.login(mvc, "kakao", kakaoUser(1001L, null));
        assertThat(login.getRedirectedUrl()).isEqualTo("http://localhost:3000/");
    }

    @Test
    @DisplayName("직군은 세부 직무 없이 대분류만 골라도 된다")
    void categoryOnlyJob() {
        assertThat(putProfile(access, "준비생", "PUBLIC", "SUWON_JANGAN"))
                .hasStatusOk().bodyJson().extractingPath("$.jobCode").isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("다른 회원이 쓰는 닉네임은 409 NICKNAME_TAKEN 이다 (영문 대소문자를 구분하지 않는다)")
    void duplicateNicknameIsRejected() {
        Cookie other = loginAccessCookie(2002L);
        assertThat(putProfile(other, "StudyMate", "IT", "SUWON_JANGAN")).hasStatusOk();

        MvcTestResult sameCase = putProfile(access, "StudyMate", "IT", "SUWON_PALDAL");
        MvcTestResult otherCase = putProfile(access, "studymate", "IT", "SUWON_PALDAL");

        assertThat(sameCase).hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("NICKNAME_TAKEN");
        assertThat(otherCase).hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("NICKNAME_TAKEN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE nickname IS NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("내 닉네임을 그대로 두고 다른 항목만 바꿀 수 있다")
    void keepOwnNickname() {
        putProfile(access, "준비생", "IT", "SUWON_JANGAN");

        assertThat(putProfile(access, "준비생", "DESIGN", "SUWON_GWONSEON"))
                .hasStatusOk().bodyJson().extractingPath("$.jobCode").isEqualTo("DESIGN");
    }

    @Test
    @DisplayName("형식이 틀리면 400 VALIDATION_FAILED 이고 어느 필드인지 알려 준다")
    void invalidInputIsRejected() {
        MvcTestResult result = putProfile(access, "특수!문자", "UNKNOWN", "SUWON");

        assertThat(result).hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("nickname", "jobCode", "districtCode");
        assertThat(jdbc.queryForObject("SELECT nickname FROM user", String.class)).isNull();
    }

    @Test
    @DisplayName("로그인하지 않으면 401, CSRF 헤더가 없으면 403 CSRF_INVALID 다")
    void requiresLoginAndCsrf() {
        assertThat(mvc.get().uri(ME)).hasStatus(401);
        assertThat(mvc.put().uri(PROFILE).cookie(access, csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("준비생", "IT", "SUWON_JANGAN")))
                .hasStatus(403).bodyJson().extractingPath("$.code").isEqualTo("CSRF_INVALID");
    }

    @Test
    @DisplayName("탈퇴한 회원은 액세스 토큰이 남아 있어도 401 이다")
    void withdrawnUserIs401() {
        jdbc.update("UPDATE user SET status = 'WITHDRAWN'");

        assertThat(mvc.get().uri(ME).cookie(access))
                .hasStatus(401).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
        assertThat(putProfile(access, "준비생", "IT", "SUWON_JANGAN")).hasStatus(401);
    }

    private Cookie loginAccessCookie(long kakaoId) {
        return FakeSocialProvider.login(mvc, "kakao", kakaoUser(kakaoId, null)).getCookie("access_token");
    }

    private MvcTestResult putProfile(Cookie accessCookie, String nickname, String jobCode, String districtCode) {
        return mvc.put().uri(PROFILE)
                .cookie(accessCookie, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(nickname, jobCode, districtCode))
                .exchange();
    }

    private static String body(String nickname, String jobCode, String districtCode) {
        return toJson(Map.of("nickname", nickname, "jobCode", jobCode, "districtCode", districtCode));
    }

    private static String toJson(Map<String, String> fields) {
        StringBuilder json = new StringBuilder("{");
        fields.forEach((key, value) -> json.append(json.length() > 1 ? "," : "")
                .append('"').append(key).append("\":\"").append(value).append('"'));
        return json.append('}').toString();
    }
}
