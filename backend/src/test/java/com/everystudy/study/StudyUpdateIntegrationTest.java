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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 스터디 수정과 정원 변경 (PLAN T-022, PRD 4.2·4.14, API 4장). 스터디는 SQL 로 직접 넣는다. */
@Import(FakeSocialProvider.class)
class StudyUpdateIntegrationTest extends IntegrationTestSupport {

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
    @DisplayName("수정 PATCH (PRD 4.2)")
    class Patch {

        @Test
        @DisplayName("참여자가 있어도 제목·소개는 고칠 수 있고, 고친 상세를 돌려준다. 일정 입력란은 잠겨 있다")
        void textIsAlwaysEditable() {
            long id = study(inHours(48), 4);
            addMember(id, memberId);

            MvcTestResult result = patch(id, host, "{\"title\": \"  새 제목  \", \"description\": \"새 소개\"}");

            assertThat(result).hasStatusOk().bodyJson().satisfies(json -> {
                assertThat(json).extractingPath("$.title").isEqualTo("새 제목");
                assertThat(json).extractingPath("$.description").isEqualTo("새 소개");
                assertThat(json).extractingPath("$.scheduleEditable").isEqualTo(false);
            });
        }

        @Test
        @DisplayName("보내지 않은 항목은 그대로 둔다")
        void omittedFieldsAreKept() {
            long id = study(inHours(48), 4);

            assertThat(patch(id, host, "{\"description\": \"소개만\"}")).hasStatusOk();

            assertThat(jdbc.queryForObject("SELECT title FROM study WHERE id = ?", String.class, id))
                    .isEqualTo("모각코");
        }

        @Test
        @DisplayName("주최자 혼자면 일정·장소를 고치고 session_date 도 KST 날짜로 다시 계산한다")
        void scheduleEditableWhenAlone() {
            long id = study(inHours(48), 4);
            Instant start = nextMinuteUnit(now.plus(Duration.ofDays(5)));

            MvcTestResult result = patch(id, host, """
                    {"startAt": "%s", "durationMin": 90,
                     "place": {"name": "새 장소", "address": "서울 강남구", "latitude": 37.5, "longitude": 127.03}}
                    """.formatted(start));

            assertThat(result).hasStatusOk().bodyJson().satisfies(json -> {
                assertThat(json).extractingPath("$.durationMin").isEqualTo(90);
                assertThat(json).extractingPath("$.place.name").isEqualTo("새 장소");
                assertThat(json).extractingPath("$.scheduleEditable").isEqualTo(true);
            });
            assertThat(jdbc.queryForObject("SELECT session_date FROM study WHERE id = ?", java.sql.Date.class, id)
                    .toLocalDate()).isEqualTo(start.atZone(TimeProvider.KOREA).toLocalDate());
        }

        @Test
        @DisplayName("참여자가 있으면 일정·장소는 409 SCHEDULE_LOCKED 이고, 같이 보낸 제목도 바뀌지 않는다")
        void scheduleLockedWithMembers() {
            long id = study(inHours(48), 4);
            addMember(id, memberId);

            assertCode(patch(id, host, "{\"title\": \"바꿀제목\", \"durationMin\": 60}"), 409, "SCHEDULE_LOCKED");

            assertThat(jdbc.queryForObject("SELECT title FROM study WHERE id = ?", String.class, id))
                    .isEqualTo("모각코");
        }

        @Test
        @DisplayName("나간 참여자는 세지 않는다: 혼자가 되면 다시 일정을 고칠 수 있다")
        void leftMembersDoNotLockSchedule() {
            long id = study(inHours(48), 4);
            addMember(id, memberId);
            jdbc.update("UPDATE study_member SET status = 'LEFT' WHERE study_id = ? AND user_id = ?", id, memberId);

            assertThat(patch(id, host, "{\"durationMin\": 60}")).hasStatusOk();
        }

        @Test
        @DisplayName("이미 시작한 스터디의 일정은 혼자여도 SCHEDULE_LOCKED")
        void scheduleLockedAfterStart() {
            long id = study(now.minus(Duration.ofMinutes(5)), 4);

            assertCode(patch(id, host, "{\"durationMin\": 60}"), 409, "SCHEDULE_LOCKED");
        }

        @Test
        @DisplayName("일정 값은 개설과 같은 규칙으로 검사한다: 과거 시각·진행 시간 단위·합친 값이 자정을 넘는 경우 400")
        void scheduleRules() {
            long id = study(inHours(48), 4);

            assertThat(patch(id, host, "{\"startAt\": \"%s\"}".formatted(now.minus(Duration.ofDays(1)))))
                    .hasStatus(400).bodyJson().extractingPath("$.errors[*].field").asArray().contains("startAt");
            assertThat(patch(id, host, "{\"durationMin\": 35}"))
                    .hasStatus(400).bodyJson().extractingPath("$.errors[*].field").asArray().contains("durationMin");
            assertThat(patch(id, host, "{\"title\": \"가\"}"))
                    .hasStatus(400).bodyJson().extractingPath("$.errors[*].field").asArray().contains("title");
        }

        @Test
        @DisplayName("주최자가 아니면 403, 시작 뒤에 나간 주최자도 403, 종료·파투는 409 STUDY_CLOSED, 없으면 404")
        void permissions() {
            long id = study(inHours(48), 4);
            addMember(id, memberId);
            assertCode(patch(id, member, "{\"title\": \"바꿀제목\"}"), 403, "FORBIDDEN");

            long left = study(inHours(48), 4);
            jdbc.update("UPDATE study_member SET status = 'LEFT' WHERE study_id = ? AND user_id = ?", left, hostId);
            assertCode(patch(left, host, "{\"title\": \"바꿀제목\"}"), 403, "FORBIDDEN");

            long ended = study(inHours(48), 4);
            jdbc.update("UPDATE study SET status = 'CANCELLED' WHERE id = ?", ended);
            assertCode(patch(ended, host, "{\"title\": \"바꿀제목\"}"), 409, "STUDY_CLOSED");

            assertCode(patch(999_999L, host, "{\"title\": \"바꿀제목\"}"), 404, "NOT_FOUND");
        }

        @Test
        @DisplayName("승계된 새 주최자가 수정할 수 있고 전 주최자는 못 한다")
        void afterSuccession() {
            long id = study(inHours(48), 4);
            addMember(id, memberId);
            assertThat(mvc.delete().uri("/api/v1/studies/{id}/members/me", id)
                    .cookie(host, csrf).header("X-XSRF-TOKEN", csrf.getValue()).exchange()).hasStatusOk();

            assertThat(patch(id, member, "{\"title\": \"새주최자제목\"}")).hasStatusOk();
            assertCode(patch(id, host, "{\"title\": \"전주최자제목\"}"), 403, "FORBIDDEN");
        }
    }

    @Nested
    @DisplayName("정원 변경 PUT capacity (PRD 4.14)")
    class Capacity {

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
        @DisplayName("증액·감소하고 고친 상세를 돌려준다. 참여자 수와 같게까지 줄일 수 있다")
        void changes() {
            long id = study(inHours(48), 4);
            addMember(id, memberId);

            assertThat(put(id, host, 6)).hasStatusOk().bodyJson().extractingPath("$.capacity").isEqualTo(6);
            assertThat(put(id, host, 2)).hasStatusOk().bodyJson().extractingPath("$.capacity").isEqualTo(2);
        }

        @Test
        @DisplayName("현재 참여자 수보다 적게는 409 CAPACITY_BELOW_MEMBERS, 설정 범위 밖은 400")
        void limits() {
            long id = study(inHours(48), 6);
            addMember(id, memberId);
            addMember(id, waiterId);

            assertCode(put(id, host, 2), 409, "CAPACITY_BELOW_MEMBERS");
            assertThat(put(id, host, 7)).hasStatus(400).bodyJson().extractingPath("$.errors[*].field").asArray()
                    .contains("capacity");
            assertThat(put(id, host, 1)).hasStatus(400);
            assertThat(jdbc.queryForObject("SELECT capacity FROM study WHERE id = ?", Integer.class, id)).isEqualTo(6);
        }

        @Test
        @DisplayName("정원이 찼던 스터디를 늘리면 지금도 신청할 수 있는 대기자에게만 WAITING_OPENED 를 기록한다")
        void increaseNotifiesEligibleWaiters() {
            long id = study(inHours(48), 2);
            addMember(id, memberId);
            wait(id, waiterId);
            wait(id, restrictedId);
            jdbc.update("""
                    INSERT INTO user_sanction (user_id, kind, source, reason, starts_at, ends_at)
                    VALUES (?, 'JOIN_RESTRICT', 'AUTO', '테스트', ?, ?)
                    """, restrictedId, utc(now.minus(Duration.ofDays(1))), utc(now.plus(Duration.ofDays(3))));

            assertThat(put(id, host, 3)).hasStatusOk();

            assertThat(noticeCount(waiterId)).isEqualTo(1);
            assertThat(noticeCount(restrictedId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_waiting WHERE study_id = ?", Integer.class, id))
                    .as("대기 행은 지우지 않는다").isEqualTo(2);
        }

        @Test
        @DisplayName("원래 자리가 남아 있던 스터디를 늘리거나, 줄이거나, 체크인이 열린 뒤에는 알리지 않는다")
        void noNoticeWhenNotNewSeat() {
            long roomy = study(inHours(48), 4);
            wait(roomy, waiterId);
            assertThat(put(roomy, host, 5)).hasStatusOk();
            assertThat(put(roomy, host, 3)).hasStatusOk();

            long checkinOpen = study(now.plus(Duration.ofMinutes(5)), 2);
            addMember(checkinOpen, memberId);
            wait(checkinOpen, waiterId);
            assertThat(put(checkinOpen, host, 3)).hasStatusOk();

            assertThat(noticeCount(waiterId)).isZero();
        }

        @Test
        @DisplayName("주최자가 아니면 403, 종료·파투는 409 STUDY_CLOSED, 본문이 없으면 400")
        void permissions() {
            long id = study(inHours(48), 4);
            addMember(id, memberId);
            assertCode(put(id, member, 5), 403, "FORBIDDEN");

            jdbc.update("UPDATE study SET status = 'ENDED' WHERE id = ?", id);
            assertCode(put(id, host, 5), 409, "STUDY_CLOSED");

            long open = study(inHours(48), 4);
            assertThat(mvc.put().uri("/api/v1/studies/{id}/capacity", open).cookie(host, csrf)
                    .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content("{}")
                    .exchange()).hasStatus(400);
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

    private MvcTestResult patch(long studyId, Cookie user, String json) {
        return mvc.patch().uri("/api/v1/studies/{id}", studyId).cookie(user, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(json)
                .exchange();
    }

    private MvcTestResult put(long studyId, Cookie user, int capacity) {
        return mvc.put().uri("/api/v1/studies/{id}/capacity", studyId).cookie(user, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"capacity\": " + capacity + "}").exchange();
    }

    private static void assertCode(MvcTestResult result, int status, String code) {
        assertThat(result).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    /** 시작 시각 단위(10분)에 맞게 정각으로 맞춘다. 일정 검사가 현재 시작 시각의 단위도 보기 때문이다. */
    private Instant inHours(int hours) {
        return now.plus(Duration.ofHours(hours)).truncatedTo(ChronoUnit.HOURS);
    }

    /** 시작 시각 단위(10분)에 맞추어 분 이하를 0 으로 맞춘 시각 (KST 와 UTC 의 분 오프셋이 같다). */
    private static Instant nextMinuteUnit(Instant instant) {
        return instant.truncatedTo(ChronoUnit.HOURS);
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
