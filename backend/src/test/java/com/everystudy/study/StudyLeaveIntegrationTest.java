package com.everystudy.study;

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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 나가기와 사전 조회 (PLAN T-018, PRD 4.4·4.6, API 4장). 스터디는 SQL 로 직접 넣는다. */
@Import(FakeSocialProvider.class)
class StudyLeaveIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private long hostId;
    private long memberId;
    private Cookie host;
    private Cookie member;
    private Cookie csrf;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        host = login(1001L);
        member = login(1002L);
        hostId = userIdOf(1001L);
        memberId = userIdOf(1002L);
        onboard(hostId, "주최자");
        onboard(memberId, "참여자");
        csrf = mvc.get().uri("/api/v1/me").cookie(member).exchange().getResponse().getCookie("XSRF-TOKEN");
    }

    @Nested
    @DisplayName("나가기")
    class Leave {

        @Test
        @DisplayName("시작 3시간 전보다 일찍 나가면 사전 취소: 200 EARLY, 행은 LEFT, EARLY_CANCEL 이벤트 1건")
        void earlyCancel() {
            long id = study(inHours(48), 4);
            addMember(id, memberId);

            MvcTestResult result = leave(id, member);

            assertThat(result).hasStatusOk().bodyJson().extractingPath("$.cancelType").isEqualTo("EARLY");
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT status, left_at, cancel_type FROM study_member WHERE study_id = ? AND user_id = ?",
                    id, memberId);
            assertThat(row).containsEntry("status", "LEFT").containsEntry("cancel_type", "EARLY");
            assertThat(row.get("left_at")).isNotNull();
            assertThat(events(id, memberId)).containsExactly("EARLY_CANCEL");
            assertThat(mvc.get().uri("/api/v1/studies/{id}", id).cookie(member).exchange())
                    .hasStatusOk().bodyJson().satisfies(json -> {
                        assertThat(json).extractingPath("$.myState").isEqualTo("NONE");
                        assertThat(json).extractingPath("$.memberCount").isEqualTo(1);
                    });
        }

        @Test
        @DisplayName("시작 3시간 이내에 나가면 직전 취소: 200 LATE, LATE_CANCEL 이벤트")
        void lateCancel() {
            long id = study(inHours(2), 4);
            addMember(id, memberId);

            assertThat(leave(id, member)).hasStatusOk().bodyJson().extractingPath("$.cancelType").isEqualTo("LATE");
            assertThat(events(id, memberId)).containsExactly("LATE_CANCEL");
        }

        @Test
        @DisplayName("나갔다가 다시 신청하고 또 나가도 된다. 취소 이벤트가 그만큼 쌓인다 (X-9)")
        void leaveRejoinLeave() {
            long id = study(inHours(48), 4);
            addMember(id, memberId);

            assertThat(leave(id, member)).hasStatusOk();
            assertThat(post("/api/v1/studies/{id}/members", id, member)).hasStatus(201);
            assertThat(leave(id, member)).hasStatusOk();

            assertThat(events(id, memberId)).containsExactly("EARLY_CANCEL", "EARLY_CANCEL");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_member WHERE study_id = ? AND user_id = ?",
                    Integer.class, id, memberId)).isEqualTo(1);
        }

        @Test
        @DisplayName("참여자가 아니면 403, 이미 나간 사람이 다시 호출해도 403 이고 이벤트는 늘지 않는다")
        void notMember() {
            long id = study(inHours(48), 4);

            assertCode(leave(id, member), 403, "FORBIDDEN");

            addMember(id, memberId);
            leave(id, member);
            assertCode(leave(id, member), 403, "FORBIDDEN");
            assertThat(events(id, memberId)).hasSize(1);
        }

        @Test
        @DisplayName("주최자의 나가기는 T-020 전까지 403 으로 거절한다 (X-21)")
        void hostIsRejectedForNow() {
            long id = study(inHours(48), 4);

            assertCode(leave(id, host), 403, "FORBIDDEN");
            assertThat(memberStatus(id, hostId)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("시작한 스터디는 409 SESSION_STARTED, 종료·파투된 스터디도 같다")
        void sessionStarted() {
            long started = study(now.minus(Duration.ofMinutes(1)), 4);
            addMember(started, memberId);
            long cancelled = study(inHours(48), 4);
            addMember(cancelled, memberId);
            jdbc.update("UPDATE study SET status = 'CANCELLED' WHERE id = ?", cancelled);

            assertCode(leave(started, member), 409, "SESSION_STARTED");
            assertCode(leave(cancelled, member), 409, "SESSION_STARTED");
            assertThat(memberStatus(started, memberId)).isEqualTo("ACTIVE");
            assertThat(events(started, memberId)).isEmpty();
        }

        @Test
        @DisplayName("없는 스터디는 404, 로그인하지 않으면 401")
        void notFoundAndUnauthenticated() {
            assertCode(leave(999_999L, member), 404, "NOT_FOUND");
            assertThat(mvc.delete().uri("/api/v1/studies/{id}/members/me", 1L)
                    .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())).hasStatus(401);
        }
    }

    @Nested
    @DisplayName("사전 조회")
    class Preview {

        @Test
        @DisplayName("사전 취소 구간이면 EARLY 와 penaltyApplies=false, 아무것도 바뀌지 않는다")
        void early() {
            long id = study(inHours(48), 4);
            addMember(id, memberId);

            assertThat(preview(id, member)).hasStatusOk().bodyJson().satisfies(json -> {
                assertThat(json).extractingPath("$.cancelType").isEqualTo("EARLY");
                assertThat(json).extractingPath("$.penaltyApplies").isEqualTo(false);
                assertThat(json).doesNotHavePath("$.hostOutcome");
            });
            assertThat(memberStatus(id, memberId)).isEqualTo("ACTIVE");
            assertThat(events(id, memberId)).isEmpty();
        }

        @Test
        @DisplayName("직전 취소 구간이면 LATE 와 penaltyApplies=true")
        void late() {
            long id = study(inHours(2), 4);
            addMember(id, memberId);

            assertThat(preview(id, member)).hasStatusOk().bodyJson().satisfies(json -> {
                assertThat(json).extractingPath("$.cancelType").isEqualTo("LATE");
                assertThat(json).extractingPath("$.penaltyApplies").isEqualTo(true);
            });
        }

        @Test
        @DisplayName("참여자가 아니거나 주최자거나 시작한 스터디면 나가기와 같은 오류다")
        void sameErrorsAsLeave() {
            long id = study(inHours(48), 4);
            assertCode(preview(id, member), 403, "FORBIDDEN");
            assertCode(preview(id, host), 403, "FORBIDDEN");

            long started = study(now.minus(Duration.ofMinutes(1)), 4);
            addMember(started, memberId);
            assertCode(preview(started, member), 409, "SESSION_STARTED");
        }
    }

    @Nested
    @DisplayName("자리가 나면 대기자 알림 (PRD 4.14, X-20)")
    class WaitingNotice {

        private long waiterId;
        private long restrictedId;

        @BeforeEach
        void waiters() {
            login(1003L);
            login(1004L);
            waiterId = userIdOf(1003L);
            restrictedId = userIdOf(1004L);
            onboard(waiterId, "대기자");
            onboard(restrictedId, "제한자");
        }

        @Test
        @DisplayName("정원이 찼던 스터디에서 나가면, 지금도 신청할 수 있는 대기자에게만 WAITING_OPENED 를 기록한다")
        void notifiesOnlyEligibleWaiters() {
            long id = fullStudy(inHours(48));
            wait(id, waiterId);
            wait(id, restrictedId);
            jdbc.update("""
                    INSERT INTO user_sanction (user_id, kind, source, reason, starts_at, ends_at)
                    VALUES (?, 'JOIN_RESTRICT', 'AUTO', '테스트', ?, ?)
                    """, restrictedId, utc(now.minus(Duration.ofDays(1))), utc(now.plus(Duration.ofDays(3))));

            assertThat(leave(id, member)).hasStatusOk();

            assertThat(noticeCount(waiterId)).isEqualTo(1);
            assertThat(noticeCount(restrictedId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_waiting WHERE study_id = ?", Integer.class, id))
                    .as("자격을 잃은 대기자의 행도 지우지 않는다").isEqualTo(2);
        }

        @Test
        @DisplayName("정원에 여유가 있던 스터디에서 나가면 새 자리가 아니므로 알리지 않는다")
        void noNoticeWhenSeatWasAlreadyFree() {
            long id = study(inHours(48), 4);
            addMember(id, memberId);
            wait(id, waiterId);

            assertThat(leave(id, member)).hasStatusOk();

            assertThat(noticeCount(waiterId)).isZero();
        }

        @Test
        @DisplayName("체크인이 열린 뒤에는 신청이 닫혀 있으므로 알리지 않는다 (X-16)")
        void noNoticeAfterCheckinOpens() {
            long id = fullStudy(now.plus(Duration.ofMinutes(5)));
            wait(id, waiterId);

            assertThat(leave(id, member)).hasStatusOk();

            assertThat(noticeCount(waiterId)).isZero();
        }

        private long fullStudy(Instant startAt) {
            long id = study(startAt, 2);
            addMember(id, memberId);
            return id;
        }

        private void wait(long studyId, long userId) {
            jdbc.update("INSERT INTO study_waiting (study_id, user_id, created_at) VALUES (?, ?, ?)",
                    studyId, userId, utc(now.minus(Duration.ofHours(1))));
        }

        private int noticeCount(long userId) {
            return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = 'WAITING_OPENED'",
                    Integer.class, userId);
        }
    }

    private MvcTestResult leave(long studyId, Cookie user) {
        return mvc.delete().uri("/api/v1/studies/{id}/members/me", studyId)
                .cookie(user, csrf).header("X-XSRF-TOKEN", csrf.getValue()).exchange();
    }

    private MvcTestResult post(String uri, long studyId, Cookie user) {
        return mvc.post().uri(uri, studyId).cookie(user, csrf).header("X-XSRF-TOKEN", csrf.getValue()).exchange();
    }

    private MvcTestResult preview(long studyId, Cookie user) {
        return mvc.get().uri("/api/v1/studies/{id}/leave-preview", studyId).cookie(user).exchange();
    }

    private List<String> events(long studyId, long userId) {
        return jdbc.queryForList(
                "SELECT type FROM attendance_event WHERE study_id = ? AND user_id = ? ORDER BY id", String.class,
                studyId, userId);
    }

    private static void assertCode(MvcTestResult result, int status, String code) {
        assertThat(result).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private Instant inHours(int hours) {
        return now.plus(Duration.ofHours(hours));
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
        jdbc.update("UPDATE user SET nickname = ?, job_code = 'IT_BACKEND', district_code = 'SUWON_JANGAN' WHERE id = ?",
                nickname, userId);
    }

    private String memberStatus(long studyId, long userId) {
        return jdbc.queryForObject("SELECT status FROM study_member WHERE study_id = ? AND user_id = ?", String.class,
                studyId, userId);
    }

    private long study(Instant startAt, int capacity) {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SUWON_JANGAN', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, ?, 120, ?, ?, NULL, TRUE, 'ACTIVE', ?, ?)
                """, hostId, utc(startAt), startAt.atZone(TimeProvider.KOREA).toLocalDate(), capacity,
                utc(now), utc(now));
        long id = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        addMember(id, hostId);
        return id;
    }

    private void addMember(long studyId, long userId) {
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', ?)",
                studyId, userId, utc(now));
    }
}
