package com.everystudy.chat;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.auth.FakeSocialProvider;
import com.everystudy.chat.service.ChatMessageService;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.support.WebSocketIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 실시간 채팅: 연결·권한·전달 (PLAN T-047, API 6장, ADR-0002 결정 5). 임의 포트의 실제 서버에 STOMP 클라이언트로 붙는다.
 * 로그인은 가짜 소셜 공급자로 하고 받은 액세스 쿠키를 핸드셰이크에 실어 보낸다.
 */
@Import(FakeSocialProvider.class)
class ChatRealtimeIntegrationTest extends WebSocketIntegrationTestSupport {

    private static final String FRONTEND = "http://localhost:3000";

    @LocalServerPort
    int port;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ChatMessageService chat;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    SimpMessagingTemplate template;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private long hostId;
    private long memberId;
    private long outsiderId;
    private String hostToken;
    private String memberToken;
    private String outsiderToken;
    private long studyId;
    private long roomId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        hostToken = login(4001L);
        memberToken = login(4002L);
        outsiderToken = login(4003L);
        hostId = userIdOf(4001L);
        memberId = userIdOf(4002L);
        outsiderId = userIdOf(4003L);
        onboard(hostId, "주최자");
        onboard(memberId, "참여자");
        onboard(outsiderId, "외부인");
        studyId = study();
        member(hostId);
        member(memberId);
    }

    @Test
    @DisplayName("쿠키 없는 핸드셰이크는 거부된다")
    void noCookieRejected() {
        assertThat(mvc.get().uri("/ws").exchange()).hasStatus(401);
        assertThat(connectFailure(null, FRONTEND)).isNotNull();
    }

    @Test
    @DisplayName("허용 목록 밖의 Origin 은 쿠키가 있어도 거부되고, 프론트 Origin 은 연결된다")
    void originAllowList() throws Exception {
        assertThat(mvc.get().uri("/ws").cookie(new Cookie("access_token", memberToken))
                .header("Origin", "http://evil.example").exchange()).hasStatus(403);

        StompSession ok = connect(memberToken, FRONTEND);
        assertThat(ok.isConnected()).isTrue();
        ok.disconnect();
    }

    @Test
    @DisplayName("참여자가 보낸 메시지는 저장된 뒤 방 구독자에게 전달된다")
    void deliversAfterSave() throws Exception {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        StompSession hostSession = connect(hostToken, FRONTEND);
        hostSession.subscribe("/topic/studies/" + studyId + "/chat", handler(received));
        StompSession memberSession = connect(memberToken, FRONTEND);
        memberSession.subscribe("/topic/studies/" + studyId + "/chat", handler(new LinkedBlockingQueue<>()));
        Thread.sleep(300);

        memberSession.send("/app/studies/" + studyId + "/chat", Map.of("content", "  안녕하세요  "));

        Map<String, Object> event = received.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.get("type")).isEqualTo("MESSAGE");
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) event.get("message");
        assertThat(message.get("content")).isEqualTo("안녕하세요");
        assertThat(message.get("senderNickname")).isEqualTo("참여자");
        assertThat(jdbc.queryForObject("SELECT content FROM chat_message WHERE room_id = ?", String.class, roomId))
                .isEqualTo("안녕하세요");
        hostSession.disconnect();
        memberSession.disconnect();
    }

    @Test
    @DisplayName("비참여자의 구독과 전송은 STOMP ERROR 로 거부되고 저장되지 않는다")
    void outsiderRejected() throws Exception {
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSession session = connect(outsiderToken, FRONTEND, errors);
        session.subscribe("/topic/studies/" + studyId + "/chat", handler(new LinkedBlockingQueue<>()));

        assertThat(errors.poll(5, TimeUnit.SECONDS)).isEqualTo("FORBIDDEN");

        BlockingQueue<String> sendErrors = new LinkedBlockingQueue<>();
        StompSession second = connect(outsiderToken, FRONTEND, sendErrors);
        second.send("/app/studies/" + studyId + "/chat", Map.of("content", "몰래"));
        assertThat(sendErrors.poll(5, TimeUnit.SECONDS)).isEqualTo("FORBIDDEN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_message", Integer.class)).isZero();
    }

    @Test
    @DisplayName("닫힌 방에는 보낼 수 없고(CHAT_CLOSED), 다른 목적지로의 전송은 거부된다")
    void closedAndUnknownDestination() throws Exception {
        jdbc.update("UPDATE chat_room SET status = 'CLOSED', closed_at = ? WHERE id = ?", utc(now), roomId);
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSession session = connect(memberToken, FRONTEND, errors);
        session.send("/app/studies/" + studyId + "/chat", Map.of("content", "x"));
        assertThat(errors.poll(5, TimeUnit.SECONDS)).isEqualTo("CHAT_CLOSED");

        BlockingQueue<String> others = new LinkedBlockingQueue<>();
        StompSession second = connect(memberToken, FRONTEND, others);
        second.send("/topic/studies/" + studyId + "/chat", Map.of("content", "위조"));
        assertThat(others.poll(5, TimeUnit.SECONDS)).isEqualTo("FORBIDDEN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_message", Integer.class)).isZero();
    }

    @Test
    @DisplayName("내용이 비었거나 너무 길면 저장하지 않고 보낸 사람에게만 SEND_REJECTED 를 알린다")
    void invalidContent() throws Exception {
        BlockingQueue<Map<String, Object>> personal = new LinkedBlockingQueue<>();
        StompSession session = connect(memberToken, FRONTEND);
        session.subscribe("/user/queue/events", handler(personal));
        Thread.sleep(300);

        session.send("/app/studies/" + studyId + "/chat", Map.of("content", "   "));
        Map<String, Object> event = personal.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.get("type")).isEqualTo("SEND_REJECTED");
        assertThat(event.get("code")).isEqualTo("VALIDATION_FAILED");

        session.send("/app/studies/" + studyId + "/chat", Map.of("content", "가".repeat(1001)));
        assertThat(personal.poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_message", Integer.class)).isZero();
        session.disconnect();
    }

    @Test
    @DisplayName("저장 트랜잭션이 롤백되면 전달하지 않는다")
    void noDeliveryWhenNotCommitted() throws Exception {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        StompSession hostSession = connect(hostToken, FRONTEND);
        hostSession.subscribe("/topic/studies/" + studyId + "/chat", handler(received));
        Thread.sleep(300);

        tx.execute(status -> {
            chat.send(studyId, memberId, "롤백될 메시지");
            status.setRollbackOnly();
            return null;
        });

        assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_message", Integer.class)).isZero();
        hostSession.disconnect();
    }

    @Test
    @DisplayName("공지를 고치면 방 구독자에게 NOTICE 이벤트가 간다")
    void noticeBroadcast() throws Exception {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        StompSession session = connect(memberToken, FRONTEND);
        session.subscribe("/topic/studies/" + studyId + "/chat", handler(received));
        Thread.sleep(300);

        Cookie csrf = mvc.get().uri("/api/v1/me").cookie(new Cookie("access_token", hostToken)).exchange()
                .getResponse().getCookie("XSRF-TOKEN");
        assertThat(mvc.put().uri("/api/v1/studies/" + studyId + "/chat/notice")
                .cookie(new Cookie("access_token", hostToken), csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"모두 7시에 만나요\"}").exchange())
                .hasStatusOk();

        Map<String, Object> event = received.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.get("type")).isEqualTo("NOTICE");
        session.disconnect();
    }

    @Test
    @DisplayName("참여·나가기·승계는 시스템 메시지로 남고, 승계 시 새 주최자 안내가 1건이다")
    void systemMessages() {
        Cookie memberCookie = new Cookie("access_token", memberToken);
        Cookie csrf = csrfOf(memberCookie);
        long newcomer = outsiderId;
        Cookie outsiderCookie = new Cookie("access_token", outsiderToken);
        Cookie outsiderCsrf = csrfOf(outsiderCookie);

        assertThat(mvc.post().uri("/api/v1/studies/" + studyId + "/members").cookie(outsiderCookie, outsiderCsrf)
                .header("X-XSRF-TOKEN", outsiderCsrf.getValue()).exchange()).hasStatus(201);
        assertThat(systemMessages(studyId)).containsExactly("외부인님이 입장했어요");

        // 참여자가 나간다
        assertThat(mvc.delete().uri("/api/v1/studies/" + studyId + "/members/me").cookie(memberCookie, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).exchange()).hasStatusOk();
        assertThat(systemMessages(studyId)).containsExactly("외부인님이 입장했어요", "참여자님이 나갔어요");

        // 주최자가 시작 전에 나가면 남은 참여자(외부인)에게 승계된다
        Cookie hostCookie = new Cookie("access_token", hostToken);
        Cookie hostCsrf = csrfOf(hostCookie);
        assertThat(mvc.delete().uri("/api/v1/studies/" + studyId + "/members/me").cookie(hostCookie, hostCsrf)
                .header("X-XSRF-TOKEN", hostCsrf.getValue()).exchange()).hasStatusOk();
        assertThat(systemMessages(studyId)).containsExactly("외부인님이 입장했어요", "참여자님이 나갔어요",
                "주최자님이 나갔어요", "외부인님이 새 주최자가 됐어요");
        assertThat(newcomer).isEqualTo(outsiderId);
    }

    @Test
    @DisplayName("나가면 방 구독이 끊겨 이후 메시지를 받지 못하고, 본인에게 SUBSCRIPTION_ENDED 가 가며, 전송은 거부된다")
    void leavingEndsSubscription() throws Exception {
        BlockingQueue<Map<String, Object>> memberTopic = new LinkedBlockingQueue<>();
        BlockingQueue<Map<String, Object>> memberPersonal = new LinkedBlockingQueue<>();
        BlockingQueue<String> memberErrors = new LinkedBlockingQueue<>();
        StompSession memberSession = connect(memberToken, FRONTEND, memberErrors);
        memberSession.subscribe("/topic/studies/" + studyId + "/chat", handler(memberTopic));
        memberSession.subscribe("/user/queue/events", handler(memberPersonal));
        BlockingQueue<Map<String, Object>> hostTopic = new LinkedBlockingQueue<>();
        StompSession hostSession = connect(hostToken, FRONTEND);
        hostSession.subscribe("/topic/studies/" + studyId + "/chat", handler(hostTopic));
        Thread.sleep(400);

        Cookie memberCookie = new Cookie("access_token", memberToken);
        Cookie csrf = csrfOf(memberCookie);
        assertThat(mvc.delete().uri("/api/v1/studies/" + studyId + "/members/me").cookie(memberCookie, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).exchange()).hasStatusOk();

        Map<String, Object> ended = memberPersonal.poll(5, TimeUnit.SECONDS);
        assertThat(ended).isNotNull();
        assertThat(ended.get("type")).isEqualTo("SUBSCRIPTION_ENDED");
        assertThat(((Number) ended.get("studyId")).longValue()).isEqualTo(studyId);
        // 남은 사람은 나감 안내를 받는다
        Map<String, Object> notice = hostTopic.poll(5, TimeUnit.SECONDS);
        assertThat(notice).isNotNull();
        assertThat(notice.get("type")).isEqualTo("MESSAGE");

        // 나간 사람에게는 이후 메시지가 가지 않는다
        hostSession.send("/app/studies/" + studyId + "/chat", Map.of("content", "남은 사람끼리"));
        assertThat(hostTopic.poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(memberTopic.poll(1, TimeUnit.SECONDS)).isNull();

        // 나간 직후 전송은 거부된다
        memberSession.send("/app/studies/" + studyId + "/chat", Map.of("content", "나간 뒤"));
        assertThat(memberErrors.poll(5, TimeUnit.SECONDS)).isEqualTo("FORBIDDEN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_message WHERE kind = 'TEXT' AND content = '나간 뒤'",
                Integer.class)).isZero();
        hostSession.disconnect();
    }

    @Test
    @DisplayName("방이 닫히면 구독자에게 ROOM_CLOSED 가 가고 구독이 모두 끊겨 이후 메시지를 받지 못한다")
    void closingEndsAllSubscriptions() throws Exception {
        BlockingQueue<Map<String, Object>> hostTopic = new LinkedBlockingQueue<>();
        BlockingQueue<Map<String, Object>> memberTopic = new LinkedBlockingQueue<>();
        StompSession hostSession = connect(hostToken, FRONTEND);
        hostSession.subscribe("/topic/studies/" + studyId + "/chat", handler(hostTopic));
        StompSession memberSession = connect(memberToken, FRONTEND);
        memberSession.subscribe("/topic/studies/" + studyId + "/chat", handler(memberTopic));
        Thread.sleep(400);

        tx.executeWithoutResult(s -> publisher.publishEvent(new com.everystudy.study.event.StudyEnded(studyId)));

        assertThat(hostTopic.poll(5, TimeUnit.SECONDS)).containsEntry("type", "ROOM_CLOSED");
        assertThat(memberTopic.poll(5, TimeUnit.SECONDS)).containsEntry("type", "ROOM_CLOSED");

        template.convertAndSend("/topic/studies/" + studyId + "/chat", (Object) Map.of("type", "AFTER_CLOSE"));
        assertThat(hostTopic.poll(1, TimeUnit.SECONDS)).isNull();
        assertThat(memberTopic.poll(1, TimeUnit.SECONDS)).isNull();

        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSession again = connect(memberToken, FRONTEND, errors);
        again.send("/app/studies/" + studyId + "/chat", Map.of("content", "닫힌 뒤"));
        assertThat(errors.poll(5, TimeUnit.SECONDS)).isEqualTo("CHAT_CLOSED");
    }

    // ---- 도우미 ----

    private Cookie csrfOf(Cookie access) {
        return mvc.get().uri("/api/v1/me").cookie(access).exchange().getResponse().getCookie("XSRF-TOKEN");
    }

    private java.util.List<String> systemMessages(long studyId) {
        return jdbc.queryForList("""
                SELECT m.content FROM chat_message m JOIN chat_room r ON r.id = m.room_id
                WHERE r.study_id = ? AND m.kind = 'SYSTEM' ORDER BY m.id
                """, String.class, studyId);
    }

    private StompSession connect(String token, String origin) throws Exception {
        return connect(token, origin, new LinkedBlockingQueue<>());
    }

    private StompSession connect(String token, String origin, BlockingQueue<String> errors) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (token != null) {
            headers.add("Cookie", "access_token=" + token);
        }
        StompSessionHandlerAdapter adapter = new StompSessionHandlerAdapter() {
            @Override
            public void handleFrame(StompHeaders frameHeaders, Object payload) {
                String message = frameHeaders.getFirst("message");
                errors.add(message == null ? "" : message);
            }
        };
        return client.connectAsync("ws://localhost:" + port + "/ws", headers, new StompHeaders(), adapter)
                .get(5, TimeUnit.SECONDS);
    }

    private Throwable connectFailure(String token, String origin) {
        try {
            connect(token, origin).disconnect();
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private static StompFrameHandler handler(BlockingQueue<Map<String, Object>> sink) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                sink.add((Map<String, Object>) payload);
            }
        };
    }

    private String login(long kakaoId) {
        return FakeSocialProvider.login(mvc, "kakao", kakaoUser(kakaoId, null)).getCookie("access_token").getValue();
    }

    private long userIdOf(long kakaoId) {
        return jdbc.queryForObject("SELECT user_id FROM social_account WHERE provider_user_id = ?", Long.class,
                Long.toString(kakaoId));
    }

    private void onboard(long userId, String nickname) {
        jdbc.update("UPDATE user SET nickname = ?, job_code = 'IT_BACKEND', district_code = 'SUWON_JANGAN' WHERE id = ?",
                nickname, userId);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private long study() {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SUWON_JANGAN', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, ?, 120, ?, 6, NULL, TRUE, 'ACTIVE', ?, ?)
                """, hostId, utc(now.plus(Duration.ofDays(1))),
                now.plus(Duration.ofDays(1)).atZone(TimeProvider.KOREA).toLocalDate(), utc(now), utc(now));
        long id = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        jdbc.update("INSERT INTO chat_room (study_id, status, created_at) VALUES (?, 'OPEN', ?)", id, utc(now));
        roomId = jdbc.queryForObject("SELECT MAX(id) FROM chat_room", Long.class);
        return id;
    }

    private void member(long userId) {
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', ?)",
                studyId, userId, utc(now));
    }
}
