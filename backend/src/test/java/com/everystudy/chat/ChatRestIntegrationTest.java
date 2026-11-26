package com.everystudy.chat;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.auth.FakeSocialProvider;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 채팅 REST와 차단 (PLAN T-046, API 6장·7장). 스터디·방·메시지는 SQL 로 직접 넣는다. */
@Import(FakeSocialProvider.class)
class ChatRestIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private long hostId;
    private long memberId;
    private long otherId;
    private long outsiderId;
    private Cookie host;
    private Cookie member;
    private Cookie other;
    private Cookie outsider;
    private Cookie csrf;
    private long studyId;
    private long roomId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        host = login(3001L);
        member = login(3002L);
        other = login(3003L);
        outsider = login(3004L);
        hostId = userIdOf(3001L);
        memberId = userIdOf(3002L);
        otherId = userIdOf(3003L);
        outsiderId = userIdOf(3004L);
        onboard(hostId, "주최자");
        onboard(memberId, "참여자");
        onboard(otherId, "다른참여자");
        onboard(outsiderId, "외부인");
        csrf = mvc.get().uri("/api/v1/me").cookie(member).exchange().getResponse().getCookie("XSRF-TOKEN");
        studyId = study();
        member(studyId, hostId, "ACTIVE");
        member(studyId, memberId, "ACTIVE");
        member(studyId, otherId, "ACTIVE");
    }

    @Test
    @DisplayName("참여자는 방 상태·공지·읽지 않음 수를 본다")
    void room() {
        long a = message(hostId, "TEXT", "첫째");
        message(hostId, "TEXT", "둘째");

        assertThat(get("/api/v1/studies/" + studyId + "/chat", member)).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.status").isEqualTo("OPEN");
            assertThat(json).extractingPath("$.notice").isNull();
            assertThat(json).extractingPath("$.unreadCount").isEqualTo(2);
        });
        assertThat(a).isPositive();
    }

    @Test
    @DisplayName("비참여자·나간 사용자는 403, 없는 스터디는 404, 로그인하지 않으면 401")
    void permissions() {
        Cookie leftCookie = login(3005L);
        long left = userIdOf(3005L);
        onboard(left, "나간사람");
        member(studyId, left, "LEFT");
        String chat = "/api/v1/studies/" + studyId + "/chat";

        assertCode(get(chat, outsider), 403, "FORBIDDEN");
        assertCode(get(chat, leftCookie), 403, "FORBIDDEN");
        assertCode(get(chat + "/messages", outsider), 403, "FORBIDDEN");
        assertCode(get(chat + "/messages", leftCookie), 403, "FORBIDDEN");
        assertCode(send("PUT", chat + "/read", outsider, "{\"lastReadMessageId\":1}"), 403, "FORBIDDEN");
        assertCode(get("/api/v1/studies/999999/chat", member), 404, "NOT_FOUND");
        assertThat(mvc.get().uri(chat).exchange()).hasStatus(401);
    }

    @Test
    @DisplayName("공지는 주최자만 고정·수정하고, 비주최자와 시작 후 나간 주최자는 403")
    void notice() {
        String url = "/api/v1/studies/" + studyId + "/chat/notice";

        assertCode(send("PUT", url, member, "{\"content\":\"공지\"}"), 403, "FORBIDDEN");
        assertCode(send("PUT", url, outsider, "{\"content\":\"공지\"}"), 403, "FORBIDDEN");
        assertCode(send("PUT", url, host, "{\"content\":\"   \"}"), 400, "VALIDATION_FAILED");
        assertCode(send("PUT", url, host, "{\"content\":\"" + "가".repeat(501) + "\"}"), 400, "VALIDATION_FAILED");

        assertThat(send("PUT", url, host, "{\"content\":\"  첫 공지  \"}")).hasStatusOk().bodyJson()
                .extractingPath("$.content").isEqualTo("첫 공지");
        assertThat(send("PUT", url, host, "{\"content\":\"고친 공지\"}")).hasStatusOk();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_notice", Integer.class)).isEqualTo(1);
        assertThat(get("/api/v1/studies/" + studyId + "/chat", member)).hasStatusOk().bodyJson()
                .extractingPath("$.notice.content").isEqualTo("고친 공지");

        // 주최자가 시작 후 나가면 host_id 는 남지만 참여자가 아니다 (PLAN X-13)
        jdbc.update("UPDATE study_member SET status = 'LEFT' WHERE study_id = ? AND user_id = ?", studyId, hostId);
        assertCode(send("PUT", url, host, "{\"content\":\"나간 뒤 공지\"}"), 403, "FORBIDDEN");
    }

    @Test
    @DisplayName("읽지 않음 수는 읽음 위치 뒤의 메시지에서 내 것·차단한 사람의 것·지운 것을 뺀 수이고, 읽음은 되감기지 않는다")
    void unreadCount() {
        long first = message(hostId, "TEXT", "1");
        message(memberId, "TEXT", "내가 쓴 것");
        message(otherId, "TEXT", "차단할 사람 것");
        message(null, "SYSTEM", "시스템 안내");
        long deleted = message(hostId, "TEXT", "지워질 것");
        jdbc.update("UPDATE chat_message SET deleted_at = ?, content = '' WHERE id = ?", utc(now), deleted);
        String chat = "/api/v1/studies/" + studyId + "/chat";

        assertThat(unread(chat, member)).isEqualTo(3); // 첫째, 다른참여자, 시스템 (내 것·지운 것 제외)
        send("PUT", "/api/v1/blocks/" + otherId, member, null);
        assertThat(unread(chat, member)).isEqualTo(2);

        assertThat(send("PUT", chat + "/read", member, "{\"lastReadMessageId\":" + first + "}")).hasStatus(204);
        assertThat(unread(chat, member)).isEqualTo(1);

        send("PUT", chat + "/read", member, "{\"lastReadMessageId\":1}");
        assertThat(unread(chat, member)).isEqualTo(1);

        // 마지막 메시지 번호보다 큰 값은 마지막으로 맞춘다
        send("PUT", chat + "/read", member, "{\"lastReadMessageId\":999999}");
        assertThat(unread(chat, member)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_read_message_id FROM chat_read_state WHERE user_id = ?",
                Long.class, memberId)).isEqualTo(deleted);
        assertCode(send("PUT", chat + "/read", member, "{\"lastReadMessageId\":-1}"), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("이전 대화는 최근 것부터 커서로 나뉘고, 차단한 사람과 지운 메시지는 내용 없이 표시만 된다")
    void history() {
        long a = message(hostId, "TEXT", "A");
        long b = message(otherId, "TEXT", "B(차단됨)");
        long c = message(null, "SYSTEM", "C 시스템");
        long d = message(hostId, "TEXT", "D(지워짐)");
        long e = message(memberId, "TEXT", "E");
        jdbc.update("UPDATE chat_message SET deleted_at = ?, content = '' WHERE id = ?", utc(now), d);
        send("PUT", "/api/v1/blocks/" + otherId, member, null);
        String url = "/api/v1/studies/" + studyId + "/chat/messages";

        assertThat(get(url + "?size=2", member)).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items.length()").isEqualTo(2);
            assertThat(json).extractingPath("$.items[0].id").isEqualTo((int) e);
            assertThat(json).extractingPath("$.items[0].content").isEqualTo("E");
            assertThat(json).extractingPath("$.items[0].senderNickname").isEqualTo("참여자");
            assertThat(json).extractingPath("$.items[1].id").isEqualTo((int) d);
            assertThat(json).extractingPath("$.items[1].deleted").isEqualTo(true);
            assertThat(json).extractingPath("$.items[1].content").isNull();
            assertThat(json).extractingPath("$.nextCursor").isEqualTo(String.valueOf(d));
        });
        assertThat(get(url + "?size=3&before=" + d, member)).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items.length()").isEqualTo(3);
            assertThat(json).extractingPath("$.items[0].kind").isEqualTo("SYSTEM");
            assertThat(json).extractingPath("$.items[0].senderId").isNull();
            assertThat(json).extractingPath("$.items[1].id").isEqualTo((int) b);
            assertThat(json).extractingPath("$.items[1].hidden").isEqualTo(true);
            assertThat(json).extractingPath("$.items[1].content").isNull();
            assertThat(json).extractingPath("$.items[2].content").isEqualTo("A");
            assertThat(json).extractingPath("$.nextCursor").isNull();
        });
        // 차단하지 않은 사람에게는 같은 메시지가 그대로 보인다
        assertThat(get(url + "?before=" + c, host)).hasStatusOk().bodyJson().satisfies(json ->
                assertThat(json).extractingPath("$.items[0].content").isEqualTo("B(차단됨)"));
        assertThat(a).isPositive();
        assertCode(get(url + "?size=0", member), 400, "VALIDATION_FAILED");
        assertCode(get(url + "?before=0", member), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("닫힌 방은 상태만 볼 수 있고 이전 대화·공지·읽음은 409 CHAT_CLOSED")
    void closedRoom() {
        message(hostId, "TEXT", "x");
        jdbc.update("UPDATE chat_room SET status = 'CLOSED', closed_at = ? WHERE id = ?", utc(now), roomId);
        String chat = "/api/v1/studies/" + studyId + "/chat";

        assertThat(get(chat, member)).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
        assertCode(get(chat + "/messages", member), 409, "CHAT_CLOSED");
        assertCode(send("PUT", chat + "/notice", host, "{\"content\":\"공지\"}"), 409, "CHAT_CLOSED");
        assertCode(send("PUT", chat + "/read", member, "{\"lastReadMessageId\":1}"), 409, "CHAT_CLOSED");
    }

    @Test
    @DisplayName("차단·해제는 멱등이고, 자기 자신은 400, 없는 사용자는 404")
    void blocks() {
        String url = "/api/v1/blocks/" + otherId;

        assertThat(send("PUT", url, member, null)).hasStatus(204);
        assertThat(send("PUT", url, member, null)).hasStatus(204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM block WHERE blocker_id = ?", Integer.class, memberId))
                .isEqualTo(1);

        assertThat(send("DELETE", url, member, null)).hasStatus(204);
        assertThat(send("DELETE", url, member, null)).hasStatus(204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM block", Integer.class)).isZero();

        assertCode(send("PUT", "/api/v1/blocks/" + memberId, member, null), 400, "VALIDATION_FAILED");
        assertCode(send("PUT", "/api/v1/blocks/999999", member, null), 404, "NOT_FOUND");
    }

    private long unread(String chat, Cookie user) {
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                bodyOf(get(chat, user)), "$.unreadCount")).longValue();
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult get(String uri, Cookie user) {
        return mvc.get().uri(uri).cookie(user).exchange();
    }

    private MvcTestResult send(String method, String uri, Cookie user, String json) {
        var request = switch (method) {
            case "PUT" -> mvc.put();
            case "DELETE" -> mvc.delete();
            default -> mvc.post();
        };
        var builder = request.uri(uri).cookie(user, csrf).header("X-XSRF-TOKEN", csrf.getValue());
        if (json != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return builder.exchange();
    }

    private static void assertCode(MvcTestResult result, int status, String code) {
        assertThat(result).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Cookie login(long kakaoId) {
        return FakeSocialProvider.login(mvc, "kakao", kakaoUser(kakaoId, null)).getCookie("access_token");
    }

    private long userIdOf(long kakaoId) {
        return jdbc.queryForObject("SELECT user_id FROM social_account WHERE provider_user_id = ?", Long.class,
                Long.toString(kakaoId));
    }

    private void onboard(long userId, String nickname) {
        jdbc.update("UPDATE user SET nickname = ?, job_code = 'IT_BACKEND', district_code = 'SEOUL' WHERE id = ?",
                nickname, userId);
    }

    private long study() {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SEOUL', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, ?, 120, ?, 6, NULL, TRUE, 'ACTIVE', ?, ?)
                """, hostId, utc(now.plus(Duration.ofDays(1))),
                now.plus(Duration.ofDays(1)).atZone(TimeProvider.KOREA).toLocalDate(), utc(now), utc(now));
        long id = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        jdbc.update("INSERT INTO chat_room (study_id, status, created_at) VALUES (?, 'OPEN', ?)", id, utc(now));
        roomId = jdbc.queryForObject("SELECT MAX(id) FROM chat_room", Long.class);
        return id;
    }

    private void member(long studyId, long userId, String status) {
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, ?, ?)", studyId,
                userId, status, utc(now));
    }

    private long message(Long senderId, String kind, String content) {
        jdbc.update("INSERT INTO chat_message (room_id, sender_id, kind, content, created_at) VALUES (?, ?, ?, ?, ?)",
                roomId, senderId, kind, content, utc(now));
        return jdbc.queryForObject("SELECT MAX(id) FROM chat_message", Long.class);
    }
}
