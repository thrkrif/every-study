package com.everystudy.notification;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.everystudy.auth.FakeSocialProvider;
import com.everystudy.notification.service.NotificationChannel;
import com.everystudy.notification.service.NotificationRecorder;
import com.everystudy.notification.service.NotificationRequest;
import com.everystudy.notification.service.NotificationType;
import com.everystudy.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** 알림 기록과 알림 센터 (PLAN T-014, API 3장, ADR-0005). 아웃박스가 호출한 쪽 트랜잭션과 함께 커밋·롤백되는지 실제 DB 로 본다. */
@Import(FakeSocialProvider.class)
class NotificationIntegrationTest extends IntegrationTestSupport {

    private static final String CENTER = "/api/v1/me/notifications";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    NotificationRecorder recorder;

    @Autowired
    TransactionTemplate tx;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private long meId;
    private long otherId;
    private Cookie meCookie;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        meCookie = FakeSocialProvider.login(mvc, "kakao", kakaoUser(2001L, null)).getCookie("access_token");
        FakeSocialProvider.login(mvc, "kakao", kakaoUser(2002L, null));
        meId = userIdOf(2001L);
        otherId = userIdOf(2002L);
    }

    @Nested
    @DisplayName("기록")
    class Recording {

        @Test
        @DisplayName("호출한 트랜잭션이 커밋되면 알림 한 건과 채널별 PENDING 아웃박스가 남는다")
        void committedWithCaller() {
            Instant scheduled = now.plus(Duration.ofHours(2));

            Boolean created = tx.execute(status -> recorder.record(request(meId, "K:1", scheduled)));

            assertThat(created).isTrue();
            Map<String, Object> notification = jdbc.queryForMap(
                    "SELECT id, user_id, type, dedupe_key, visible_at, read_at FROM notification");
            assertThat(notification)
                    .containsEntry("user_id", meId)
                    .containsEntry("type", "CHECKIN_OPEN")
                    .containsEntry("dedupe_key", "K:1")
                    .containsEntry("read_at", null);
            List<Map<String, Object>> outbox = jdbc.queryForList(
                    "SELECT notification_id, channel, dedupe_key, status, attempts, "
                            + "scheduled_at = next_attempt_at AS first_try_on_schedule "
                            + "FROM notification_outbox ORDER BY channel");
            assertThat(outbox).extracting(row -> row.get("channel")).containsExactly("EMAIL", "PUSH");
            assertThat(outbox).extracting(row -> row.get("dedupe_key")).containsExactly("K:1:EMAIL", "K:1:PUSH");
            assertThat(outbox).allSatisfy(row -> {
                assertThat(row.get("notification_id")).isEqualTo(notification.get("id"));
                assertThat(row.get("status")).isEqualTo("PENDING");
                assertThat(((Number) row.get("attempts")).intValue()).isZero();
                assertThat(((Number) row.get("first_try_on_schedule")).intValue()).isEqualTo(1);
            });
        }

        @Test
        @DisplayName("호출한 트랜잭션이 롤백되면 알림도 아웃박스도 남지 않는다")
        void rolledBackWithCaller() {
            tx.executeWithoutResult(status -> {
                recorder.record(request(meId, "K:1", now));
                assertThat(count("notification_outbox")).isEqualTo(2);
                status.setRollbackOnly();
            });

            assertThat(count("notification")).isZero();
            assertThat(count("notification_outbox")).isZero();
        }

        @Test
        @DisplayName("같은 dedupe_key 로 두 번 기록하면 알림과 아웃박스가 한 벌만 남는다")
        void dedupes() {
            Boolean first = tx.execute(status -> recorder.record(request(meId, "K:1", now)));
            Boolean second = tx.execute(status -> recorder.record(request(meId, "K:1", now.plusSeconds(60))));

            assertThat(first).isTrue();
            assertThat(second).isFalse();
            assertThat(count("notification")).isEqualTo(1);
            assertThat(count("notification_outbox")).isEqualTo(2);
        }

        @Test
        @DisplayName("고른 채널만 아웃박스에 남는다")
        void onlyRequestedChannels() {
            tx.executeWithoutResult(status -> recorder.record(new NotificationRequest(meId,
                    NotificationType.WAITING_OPENED, null, "자리가 났어요", "먼저 신청한 사람이 참여해요", "K:2", now,
                    now.plusSeconds(3600), Set.of(NotificationChannel.PUSH))));

            assertThat(jdbc.queryForList("SELECT channel FROM notification_outbox", String.class))
                    .containsExactly("PUSH");
        }

        @Test
        @DisplayName("트랜잭션 밖에서 부르면 기록하지 않고 실패한다")
        void requiresCallerTransaction() {
            assertThatThrownBy(() -> recorder.record(request(meId, "K:1", now)))
                    .isInstanceOf(IllegalTransactionStateException.class);
            assertThat(count("notification")).isZero();
        }
    }

    @Nested
    @DisplayName("알림 센터")
    class Center {

        @Test
        @DisplayName("보이는 시각이 지난 내 알림만, 최근에 보인 순서로 준다 (예약 알림·남의 알림 제외)")
        void listsVisibleOwnNotifications() throws Exception {
            record(meId, "OLD", now.minus(Duration.ofHours(3)));
            record(meId, "NEW", now.minus(Duration.ofMinutes(5)));
            record(meId, "FUTURE", now.plus(Duration.ofHours(1)));
            record(otherId, "OTHER", now.minus(Duration.ofMinutes(1)));

            MvcTestResult result = mvc.get().uri(CENTER).cookie(meCookie).exchange();

            assertThat(result).hasStatusOk();
            assertThat(titles(result)).containsExactly("NEW", "OLD");
            assertThat(result).bodyJson().extractingPath("$.items[0].type").isEqualTo("CHECKIN_OPEN");
            assertThat(result).bodyJson().extractingPath("$.items[0].read").isEqualTo(false);
            assertThat(result).bodyJson().extractingPath("$.items[0].notifiedAt").isNotNull();
            assertThat(result).bodyJson().extractingPath("$.nextCursor").isNull();
        }

        @Test
        @DisplayName("커서로 다음 페이지를 이어서 준다")
        void pagesWithCursor() throws Exception {
            for (int i = 1; i <= 3; i++) {
                record(meId, "N" + i, now.minus(Duration.ofMinutes(10 - i)));
            }

            MvcTestResult first = mvc.get().uri(CENTER + "?size=2").cookie(meCookie).exchange();
            String cursor = JsonPath.read(body(first), "$.nextCursor");
            MvcTestResult second = mvc.get().uri(CENTER + "?size=2&cursor=" + cursor).cookie(meCookie).exchange();

            assertThat(titles(first)).containsExactly("N3", "N2");
            assertThat(cursor).isNotNull();
            assertThat(titles(second)).containsExactly("N1");
            assertThat(second).bodyJson().extractingPath("$.nextCursor").isNull();
        }

        @Test
        @DisplayName("망가진 커서는 400 VALIDATION_FAILED")
        void rejectsBrokenCursor() {
            MvcTestResult result = mvc.get().uri(CENTER + "?cursor=broken").cookie(meCookie).exchange();

            assertThat(result).hasStatus(400);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        }

        @Test
        @DisplayName("한 건 읽음은 204 이고 다시 불러도 204 다")
        void marksOneRead() throws Exception {
            long id = record(meId, "N", now.minusSeconds(60));
            Cookie csrf = csrf();

            assertThat(post(CENTER + "/" + id + "/read", csrf)).hasStatus(204);
            assertThat(post(CENTER + "/" + id + "/read", csrf)).hasStatus(204);

            MvcTestResult list = mvc.get().uri(CENTER).cookie(meCookie).exchange();
            assertThat(list).bodyJson().extractingPath("$.items[0].read").isEqualTo(true);
        }

        @Test
        @DisplayName("남의 알림, 아직 보이지 않는 알림, 없는 알림의 읽음은 404")
        void readOfInvisibleIsNotFound() {
            long others = record(otherId, "OTHER", now.minusSeconds(60));
            long future = record(meId, "FUTURE", now.plus(Duration.ofHours(1)));
            Cookie csrf = csrf();

            assertThat(post(CENTER + "/" + others + "/read", csrf)).hasStatus(404);
            assertThat(post(CENTER + "/" + future + "/read", csrf)).hasStatus(404);
            assertThat(post(CENTER + "/999999/read", csrf)).hasStatus(404);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE read_at IS NOT NULL",
                    Integer.class)).isZero();
        }

        @Test
        @DisplayName("모두 읽음은 보이는 내 알림만 읽음으로 바꾼다")
        void marksAllVisibleRead() {
            long visible = record(meId, "N", now.minusSeconds(60));
            long future = record(meId, "FUTURE", now.plus(Duration.ofHours(1)));
            long others = record(otherId, "OTHER", now.minusSeconds(60));

            assertThat(post(CENTER + "/read-all", csrf())).hasStatus(204);

            assertThat(readAt(visible)).isNotNull();
            assertThat(readAt(future)).isNull();
            assertThat(readAt(others)).isNull();
        }

        @Test
        @DisplayName("읽음 요청에 CSRF 헤더가 없으면 403 이고 바뀌지 않는다")
        void readRequiresCsrf() {
            long id = record(meId, "N", now.minusSeconds(60));

            MvcTestResult result = mvc.post().uri(CENTER + "/" + id + "/read").cookie(meCookie).exchange();

            assertThat(result).hasStatus(403);
            assertThat(readAt(id)).isNull();
        }

        @Test
        @DisplayName("로그인하지 않으면 401")
        void requiresLogin() {
            assertThat(mvc.get().uri(CENTER).exchange()).hasStatus(401);
        }
    }

    private NotificationRequest request(long userId, String dedupeKey, Instant scheduledAt) {
        return NotificationRequest.allChannels(userId, NotificationType.CHECKIN_OPEN, null, "체크인이 열렸어요",
                "장소에 도착하면 체크인해 주세요", dedupeKey, scheduledAt, scheduledAt.plus(Duration.ofHours(1)));
    }

    /** 제목을 dedupe 키로도 써서 한 건 기록하고 ID 를 돌려준다. */
    private long record(long userId, String title, Instant visibleAt) {
        tx.executeWithoutResult(status -> recorder.record(NotificationRequest.allChannels(userId,
                NotificationType.CHECKIN_OPEN, null, title, "본문", "T:" + userId + ":" + title, visibleAt,
                visibleAt.plus(Duration.ofHours(1)))));
        return jdbc.queryForObject("SELECT id FROM notification WHERE dedupe_key = ?", Long.class,
                "T:" + userId + ":" + title);
    }

    @Test
    @DisplayName("수신 설정은 처음에 모두 켜짐이고, 보낸 채널만 바뀌며, 저장된 값이 다시 조회된다")
    void notificationSettings() throws Exception {
        String uri = "/api/v1/me/notification-settings";
        MvcTestResult first = mvc.get().uri(uri).cookie(meCookie).exchange();
        assertThat(first).hasStatus(200);
        assertThat((Boolean) JsonPath.read(body(first), "$.push")).isTrue();
        assertThat((Boolean) JsonPath.read(body(first), "$.email")).isTrue();

        Cookie csrf = csrf();
        MvcTestResult changed = put(uri, "{\"email\": false}", csrf);
        assertThat(changed).hasStatus(200);
        assertThat((Boolean) JsonPath.read(body(changed), "$.push")).isTrue();
        assertThat((Boolean) JsonPath.read(body(changed), "$.email")).isFalse();

        put(uri, "{\"push\": false}", csrf);
        MvcTestResult again = mvc.get().uri(uri).cookie(meCookie).exchange();
        assertThat((Boolean) JsonPath.read(body(again), "$.push")).isFalse();
        assertThat((Boolean) JsonPath.read(body(again), "$.email")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_setting WHERE user_id = ?", Integer.class,
                meId)).isEqualTo(2);
    }

    @Test
    @DisplayName("수신 설정 변경에 CSRF 헤더가 없으면 403 이고 바뀌지 않는다")
    void notificationSettingsRequireCsrf() {
        MvcTestResult denied = mvc.put().uri("/api/v1/me/notification-settings").cookie(meCookie)
                .contentType("application/json").content("{\"push\": false}").exchange();
        assertThat(denied).hasStatus(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_setting", Integer.class)).isZero();
    }

    private MvcTestResult put(String uri, String json, Cookie csrf) {
        return mvc.put().uri(uri).cookie(meCookie, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json").content(json).exchange();
    }

    private MvcTestResult post(String uri, Cookie csrf) {
        return mvc.post().uri(uri).cookie(meCookie, csrf).header("X-XSRF-TOKEN", csrf.getValue()).exchange();
    }

    private Cookie csrf() {
        return mvc.get().uri("/api/v1/me").cookie(meCookie).exchange().getResponse().getCookie("XSRF-TOKEN");
    }

    private List<String> titles(MvcTestResult result) throws UnsupportedEncodingException {
        return JsonPath.read(body(result), "$.items[*].title");
    }

    private static String body(MvcTestResult result) throws UnsupportedEncodingException {
        return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private Object readAt(long id) {
        return jdbc.queryForObject("SELECT read_at FROM notification WHERE id = ?", Object.class, id);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private long userIdOf(long kakaoId) {
        return jdbc.queryForObject("SELECT user_id FROM social_account WHERE provider_user_id = ?", Long.class,
                Long.toString(kakaoId));
    }
}
