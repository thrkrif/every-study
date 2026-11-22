package com.everystudy.admin;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.auth.FakeSocialProvider;
import com.everystudy.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 운영자 권한과 감사 로그 조회 (PLAN T-052, D-10). 운영자 이메일은 설정 목록으로 지정한다. */
@Import(FakeSocialProvider.class)
@TestPropertySource(properties = "everystudy.admin.emails=Admin@Example.com, other-admin@example.com")
class AdminAccessIntegrationTest extends IntegrationTestSupport {

    private static final String LOGS = "/api/v1/admin/audit-logs";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private Cookie admin;
    private Cookie user;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        admin = login(4001L, "admin@example.com");
        user = login(4002L, "user@example.com");
    }

    @Test
    @DisplayName("설정 목록의 이메일(대소문자 무관)로 로그인한 회원만 ADMIN 이 된다")
    void onlyListedEmailBecomesAdmin() {
        assertThat(jdbc.queryForList("SELECT role FROM user ORDER BY id", String.class))
                .containsExactly("ADMIN", "USER");
    }

    @Test
    @DisplayName("비회원은 401, 일반 회원은 403, 운영자는 200")
    void accessByRole() {
        assertThat(mvc.get().uri(LOGS).exchange()).hasStatus(401);
        assertThat(mvc.get().uri(LOGS).cookie(user).exchange()).hasStatus(403);
        assertThat(mvc.get().uri(LOGS).cookie(admin).exchange()).hasStatus(200);
    }

    @Test
    @DisplayName("감사 로그는 최근 것부터 커서로 넘기고, 행위로 거를 수 있다")
    void pagesAndFilters() throws Exception {
        log("HOST_PENALTY", "STUDY", 1);
        log("SANCTION_GRANT", "USER", 2);
        log("HOST_PENALTY", "STUDY", 3);

        MvcTestResult first = mvc.get().uri(LOGS + "?size=2").cookie(admin).exchange();
        assertThat(ids(first)).containsExactly(3, 2);
        String cursor = JsonPath.read(body(first), "$.nextCursor");

        MvcTestResult second = mvc.get().uri(LOGS + "?size=2&cursor=" + cursor).cookie(admin).exchange();
        assertThat(ids(second)).containsExactly(1);
        assertThat((Object) JsonPath.read(body(second), "$.nextCursor")).isNull();

        MvcTestResult filtered = mvc.get().uri(LOGS + "?action=HOST_PENALTY").cookie(admin).exchange();
        assertThat(ids(filtered)).containsExactly(3, 1);
        assertThat(mvc.get().uri(LOGS + "?action=NOPE").cookie(admin).exchange()).hasStatus(400);
        assertThat(mvc.get().uri(LOGS + "?cursor=!!!").cookie(admin).exchange()).hasStatus(400);
    }

    private void log(String action, String targetType, long targetId) {
        jdbc.update("INSERT INTO audit_log (actor_id, action, target_type, target_id, detail, created_at) "
                + "VALUES (NULL, ?, ?, ?, JSON_OBJECT('k', 'v'), NOW(6))", action, targetType, targetId);
    }

    private List<Integer> ids(MvcTestResult result) throws UnsupportedEncodingException {
        List<Integer> targets = JsonPath.read(body(result), "$.items[*].targetId");
        return targets;
    }

    private Cookie login(long kakaoId, String email) {
        return FakeSocialProvider.login(mvc, "kakao", kakaoUser(kakaoId, email)).getCookie("access_token");
    }

    private static String body(MvcTestResult result) throws UnsupportedEncodingException {
        return result.getResponse().getContentAsString();
    }
}
