package com.everystudy.admin;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.auth.FakeSocialProvider;
import com.everystudy.support.IntegrationTestSupport;
import com.everystudy.trust.service.SanctionService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
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

/** 운영자의 메시지 삭제·이용 정지·제재 조회 (PLAN T-055, PRD 4.11). */
@Import(FakeSocialProvider.class)
@TestPropertySource(properties = "everystudy.admin.emails=admin@example.com")
class ModerationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SanctionService sanctionService;

    private Cookie admin;
    private Cookie normal;
    private Cookie csrf;
    private long adminId;
    private long target;
    private long roomId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        admin = FakeSocialProvider.login(mvc, "kakao", kakaoUser(9001L, "admin@example.com")).getCookie("access_token");
        normal = FakeSocialProvider.login(mvc, "kakao", kakaoUser(9002L, null)).getCookie("access_token");
        csrf = mvc.get().uri("/api/v1/me").cookie(admin).exchange().getResponse().getCookie("XSRF-TOKEN");
        adminId = jdbc.queryForObject("SELECT id FROM user WHERE role = 'ADMIN'", Long.class);
        target = jdbc.queryForObject("SELECT user_id FROM social_account WHERE provider_user_id = '9002'", Long.class);
        jdbc.update("INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name, "
                + "place_address, latitude, longitude, start_at, duration_min, session_date, capacity, "
                + "min_attendance_rate, allow_newcomer, status, created_at, updated_at) VALUES (?, 'OFFLINE', '모각코', "
                + "'', 'DEVELOPMENT', 'SUWON_JANGAN', '카페', '수원', 37.3, 127.01, '2026-11-30 03:00:00', 120, "
                + "'2026-11-30', 4, NULL, TRUE, 'ACTIVE', NOW(6), NOW(6))", adminId);
        long study = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        jdbc.update("INSERT INTO chat_room (study_id, status, created_at) VALUES (?, 'OPEN', NOW(6))", study);
        roomId = jdbc.queryForObject("SELECT MAX(id) FROM chat_room", Long.class);
    }

    @Test
    @DisplayName("메시지를 지우면 내용이 비고 지운 시각이 남으며, 다시 지워도 같고 감사 로그에 내용이 없다. 시스템 메시지는 404")
    void deletesMessage() {
        long message = message(target, "TEXT", "지울 내용");
        long system = message(null, "SYSTEM", "입장");

        assertThat(delete("/api/v1/admin/chat-messages/" + message, "욕설")).hasStatus(204);
        assertThat(delete("/api/v1/admin/chat-messages/" + message, "다시")).hasStatus(204);

        assertThat(jdbc.queryForMap("SELECT content, deleted_at IS NOT NULL AS gone FROM chat_message WHERE id = ?",
                message)).containsEntry("content", "");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'MESSAGE_DELETE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'MESSAGE_DELETE' "
                + "AND detail LIKE '%지울 내용%'", Integer.class)).isZero();
        assertThat(delete("/api/v1/admin/chat-messages/" + system, "x")).hasStatus(404);
        assertThat(delete("/api/v1/admin/chat-messages/999999", "x")).hasStatus(404);
        assertThat(delete("/api/v1/admin/chat-messages/" + message, " ")).hasStatus(400);
    }

    @Test
    @DisplayName("이용 정지를 걸면 신청·개설이 막히고 알림이 가며, 해제하면 바로 풀린다. 두 번 해제해도 같다")
    void suspendAndRevoke() throws Exception {
        MvcTestResult granted = post("/api/v1/admin/users/" + target + "/suspensions", "{\"days\":7,\"note\":\"반복 위반\"}");
        assertThat(granted).hasStatus(200);
        int sanctionId = JsonPath.read(body(granted), "$.id");
        assertThat((String) JsonPath.read(body(granted), "$.source")).isEqualTo("OPERATOR");

        Instant now = Instant.now();
        assertThat(sanctionService.joinRestrictedUntil(target, now)).isPresent();
        assertThat(sanctionService.hostRestrictedUntil(target, now)).isPresent();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'SANCTION_APPLIED' "
                + "AND user_id = ?", Integer.class, target)).isEqualTo(1);

        assertThat(delete("/api/v1/admin/users/" + target + "/suspensions/" + sanctionId, "오해")).hasStatus(204);
        assertThat(delete("/api/v1/admin/users/" + target + "/suspensions/" + sanctionId, "다시")).hasStatus(204);

        assertThat(sanctionService.joinRestrictedUntil(target, Instant.now())).isEmpty();
        assertThat(sanctionService.hostRestrictedUntil(target, Instant.now())).isEmpty();
        assertThat(count("GRANT")).isEqualTo(1);
        assertThat(count("REVOKE")).isEqualTo(1);
        // 다른 회원의 제재 번호로는 해제할 수 없다
        assertThat(delete("/api/v1/admin/users/" + adminId + "/suspensions/" + sanctionId, "x")).hasStatus(404);
    }

    @Test
    @DisplayName("정지 입력 검사: 기간 범위·사유·없는 회원, 일반 회원은 403")
    void suspendValidation() {
        String uri = "/api/v1/admin/users/" + target + "/suspensions";
        assertThat(post(uri, "{\"days\":0,\"note\":\"x\"}")).hasStatus(400);
        assertThat(post(uri, "{\"days\":366,\"note\":\"x\"}")).hasStatus(400);
        assertThat(post(uri, "{\"days\":3,\"note\":\" \"}")).hasStatus(400);
        assertThat(post("/api/v1/admin/users/999999/suspensions", "{\"days\":3,\"note\":\"x\"}")).hasStatus(404);
        assertThat(mvc.post().uri(uri).cookie(normal, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json").content("{\"days\":3,\"note\":\"x\"}").exchange()).hasStatus(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_sanction", Integer.class)).isZero();
    }

    @Test
    @DisplayName("제재 목록은 회원·유효 여부·운영자 검토 대상으로 거르고, 자동 제재도 해제할 수 있다")
    void listsAndRevokesAuto() throws Exception {
        jdbc.update("INSERT INTO user_sanction (user_id, kind, source, reason, starts_at, ends_at, review_required) "
                + "VALUES (?, 'HOST_RESTRICT', 'AUTO', '검토', DATE_SUB(NOW(6), INTERVAL 1 DAY), "
                + "DATE_ADD(NOW(6), INTERVAL 20 DAY), TRUE)", target);
        jdbc.update("INSERT INTO user_sanction (user_id, kind, source, reason, starts_at, ends_at) VALUES "
                + "(?, 'JOIN_RESTRICT', 'AUTO', '지난 제한', DATE_SUB(NOW(6), INTERVAL 20 DAY), "
                + "DATE_SUB(NOW(6), INTERVAL 13 DAY))", target);
        jdbc.update("INSERT INTO user_sanction (user_id, kind, source, reason, starts_at, ends_at) VALUES "
                + "(?, 'JOIN_RESTRICT', 'AUTO', '남의 제한', NOW(6), DATE_ADD(NOW(6), INTERVAL 3 DAY))", adminId);

        assertThat(reasons("/api/v1/admin/sanctions?userId=" + target)).containsExactly("지난 제한", "검토");
        assertThat(reasons("/api/v1/admin/sanctions?userId=" + target + "&activeOnly=true")).containsExactly("검토");
        assertThat(reasons("/api/v1/admin/sanctions?reviewRequired=true")).containsExactly("검토");
        assertThat(reasons("/api/v1/admin/sanctions")).hasSize(3);
        assertThat(mvc.get().uri("/api/v1/admin/sanctions").cookie(normal).exchange()).hasStatus(403);

        long review = jdbc.queryForObject("SELECT id FROM user_sanction WHERE reason = '검토'", Long.class);
        assertThat(delete("/api/v1/admin/sanctions/" + review, "검토 끝")).hasStatus(204);
        assertThat(reasons("/api/v1/admin/sanctions?userId=" + target + "&activeOnly=true")).isEmpty();
    }

    private List<String> reasons(String uri) throws UnsupportedEncodingException {
        return JsonPath.read(body(mvc.get().uri(uri).cookie(admin).exchange()), "$.items[*].reason");
    }

    private int count(String kind) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'SANCTION_" + kind + "'",
                Integer.class);
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).cookie(admin, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json").content(json).exchange();
    }

    private MvcTestResult delete(String uri, String note) {
        return mvc.delete().uri(uri).cookie(admin, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json").content("{\"note\":\"" + note + "\"}").exchange();
    }

    private static String body(MvcTestResult result) throws UnsupportedEncodingException {
        return result.getResponse().getContentAsString();
    }

    private long message(Long senderId, String kind, String content) {
        jdbc.update("INSERT INTO chat_message (room_id, sender_id, kind, content, created_at) VALUES (?, ?, ?, ?, NOW(6))",
                roomId, senderId, kind, content);
        return jdbc.queryForObject("SELECT MAX(id) FROM chat_message", Long.class);
    }
}
