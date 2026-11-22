package com.everystudy.chat;

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

/** 신고 접수와 운영자 신고 큐 (PLAN T-053, D-4). */
@Import(FakeSocialProvider.class)
@TestPropertySource(properties = "everystudy.admin.emails=admin@example.com")
class ReportIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private Cookie admin;
    private Cookie reporter;
    private Cookie outsider;
    private Cookie csrf;
    private long adminId;
    private long reporterId;
    private long outsiderId;
    private long studyId;
    private long roomId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        admin = FakeSocialProvider.login(mvc, "kakao", kakaoUser(7001L, "admin@example.com")).getCookie("access_token");
        reporter = FakeSocialProvider.login(mvc, "kakao", kakaoUser(7002L, null)).getCookie("access_token");
        outsider = FakeSocialProvider.login(mvc, "kakao", kakaoUser(7003L, null)).getCookie("access_token");
        csrf = mvc.get().uri("/api/v1/me").cookie(admin).exchange().getResponse().getCookie("XSRF-TOKEN");
        adminId = idOf("kakao", 7001L);
        reporterId = idOf("kakao", 7002L);
        outsiderId = idOf("kakao", 7003L);
        jdbc.update("UPDATE user SET nickname = '신고자' WHERE id = ?", reporterId);
        jdbc.update("UPDATE user SET nickname = '외부인' WHERE id = ?", outsiderId);
        studyId = study();
        member(reporterId);
    }

    @Test
    @DisplayName("스터디·사용자·메시지를 신고하면 201 이고, 같은 대상에 열린 신고가 있으면 409 ALREADY_REPORTED 다")
    void fileAndDuplicate() {
        assertThat(report(reporter, "STUDY", studyId, "SPAM_AD", null)).hasStatus(201);
        assertCode(report(reporter, "STUDY", studyId, "ABUSE", null), 409, "ALREADY_REPORTED");
        assertThat(report(reporter, "USER", outsiderId, "HARASSMENT", "불쾌한 말")).hasStatus(201);
        long message = message(outsiderId, "TEXT", null);
        member(outsiderId);
        assertThat(report(reporter, "MESSAGE", message, "ABUSE", null)).hasStatus(201);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report", Integer.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("사유 코드 밖의 값·기타인데 상세 없음·길이 초과·자기 신고는 400, 없는 대상은 404")
    void validation() {
        assertCode(report(reporter, "STUDY", studyId, "NOPE", null), 400, "VALIDATION_FAILED");
        assertCode(report(reporter, "STUDY", studyId, "OTHER", " "), 400, "VALIDATION_FAILED");
        assertCode(report(reporter, "STUDY", studyId, "ABUSE", "가".repeat(501)), 400, "VALIDATION_FAILED");
        assertCode(report(reporter, "USER", reporterId, "ABUSE", null), 400, "VALIDATION_FAILED");
        assertThat(report(reporter, "STUDY", 999999L, "ABUSE", null)).hasStatus(404);
        assertThat(report(reporter, "USER", 999999L, "ABUSE", null)).hasStatus(404);
        assertThat(report(reporter, "MESSAGE", 999999L, "ABUSE", null)).hasStatus(404);
        assertThat(report(reporter, "STUDY", studyId, "OTHER", "상세")).hasStatus(201);
    }

    @Test
    @DisplayName("메시지 신고는 그 방의 참여자만 할 수 있고, 내 메시지·시스템 메시지·지워진 메시지는 신고할 수 없다")
    void messageRules() {
        member(adminId);
        long text = message(adminId, "TEXT", null);
        long mine = message(reporterId, "TEXT", null);
        long system = message(null, "SYSTEM", null);
        long deleted = message(adminId, "TEXT", "2026-11-20 00:00:00");

        assertThat(report(outsider, "MESSAGE", text, "ABUSE", null)).hasStatus(403);
        assertCode(report(reporter, "MESSAGE", mine, "ABUSE", null), 400, "VALIDATION_FAILED");
        assertThat(report(reporter, "MESSAGE", system, "ABUSE", null)).hasStatus(404);
        assertThat(report(reporter, "MESSAGE", deleted, "ABUSE", null)).hasStatus(404);
        assertThat(report(reporter, "MESSAGE", text, "ABUSE", null)).hasStatus(201);
    }

    @Test
    @DisplayName("신고 큐는 기본이 열린 신고이고 대상 요약이 붙는다. 반려·처리 완료는 사유 필수, 한 번만, 감사 로그가 남는다")
    void queueAndHandle() throws Exception {
        report(reporter, "STUDY", studyId, "SPAM_AD", null);
        report(reporter, "USER", outsiderId, "ABUSE", null);

        MvcTestResult queue = mvc.get().uri("/api/v1/admin/reports").cookie(admin).exchange();
        assertThat(queue).hasStatus(200);
        assertThat((List<String>) JsonPath.read(body(queue), "$.items[*].targetSummary"))
                .containsExactly("외부인", "모각코");
        assertThat((List<String>) JsonPath.read(body(queue), "$.items[*].reporterNickname"))
                .containsExactly("신고자", "신고자");
        int first = JsonPath.read(body(queue), "$.items[0].id");
        int second = JsonPath.read(body(queue), "$.items[1].id");

        assertThat(handle(first, "dismiss", " ")).hasStatus(400);
        MvcTestResult dismissed = handle(first, "dismiss", "근거 없음");
        assertThat(dismissed).hasStatus(200);
        assertThat((String) JsonPath.read(body(dismissed), "$.status")).isEqualTo("DISMISSED");
        assertCode(handle(first, "resolve", "다시"), 409, "REPORT_ALREADY_HANDLED");
        assertThat(handle(second, "resolve", "경고 조치함")).hasStatus(200);
        assertThat(handle(999999, "resolve", "없음")).hasStatus(404);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'REPORT_DISMISS'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'REPORT_RESOLVE'",
                Integer.class)).isEqualTo(1);
        assertThat((List<Integer>) JsonPath.read(body(mvc.get().uri("/api/v1/admin/reports").cookie(admin)
                .exchange()), "$.items[*].id")).isEmpty();
        assertThat((List<Integer>) JsonPath.read(body(mvc.get().uri("/api/v1/admin/reports?status=all")
                .cookie(admin).exchange()), "$.items[*].id")).containsExactly(second, first);
        assertThat(mvc.get().uri("/api/v1/admin/reports?status=nope").cookie(admin).exchange()).hasStatus(400);
        assertThat(mvc.get().uri("/api/v1/admin/reports").cookie(reporter).exchange()).hasStatus(403);
    }

    private MvcTestResult report(Cookie user, String type, long targetId, String reason, String detail) {
        String json = "{\"targetType\":\"" + type + "\",\"targetId\":" + targetId + ",\"reason\":\"" + reason + "\""
                + (detail == null ? "" : ",\"detail\":\"" + detail + "\"") + "}";
        return mvc.post().uri("/api/v1/reports").cookie(user, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json").content(json).exchange();
    }

    private MvcTestResult handle(long id, String action, String note) {
        return mvc.post().uri("/api/v1/admin/reports/" + id + "/" + action).cookie(admin, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
                .content("{\"note\":\"" + note + "\"}").exchange();
    }

    private static void assertCode(MvcTestResult result, int status, String code) {
        assertThat(result).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static String body(MvcTestResult result) throws UnsupportedEncodingException {
        return result.getResponse().getContentAsString();
    }

    private long idOf(String provider, long providerUserId) {
        return jdbc.queryForObject("SELECT user_id FROM social_account WHERE provider_user_id = ?", Long.class,
                Long.toString(providerUserId));
    }

    private long study() {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SUWON_JANGAN', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, '2026-11-30 03:00:00', 120, '2026-11-30', 6,
                    NULL, TRUE, 'ACTIVE', NOW(6), NOW(6))
                """, reporterId);
        long id = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        jdbc.update("INSERT INTO chat_room (study_id, status, created_at) VALUES (?, 'OPEN', NOW(6))", id);
        roomId = jdbc.queryForObject("SELECT MAX(id) FROM chat_room", Long.class);
        return id;
    }

    private void member(long userId) {
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', NOW(6))",
                studyId, userId);
    }

    private long message(Long senderId, String kind, String deletedAt) {
        jdbc.update("INSERT INTO chat_message (room_id, sender_id, kind, content, deleted_at, created_at) "
                + "VALUES (?, ?, ?, '내용', ?, NOW(6))", roomId, senderId, kind, deletedAt);
        return jdbc.queryForObject("SELECT MAX(id) FROM chat_message", Long.class);
    }
}
