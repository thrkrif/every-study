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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 내 스터디 조회 (PLAN T-023, PRD 4.10, API 3장). 스터디는 SQL 로 직접 넣는다. */
@Import(FakeSocialProvider.class)
class MyStudyIntegrationTest extends IntegrationTestSupport {

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

    @Test
    @DisplayName("role 을 생략하면 주최·참여 모두, 시작이 빠른 순이고 내 역할·참여자 수가 담긴다")
    void upcomingAllRoles() {
        long later = study(hostId, inHours(72), 4, "ACTIVE");
        long mine = study(memberId, inHours(24), 4, "ACTIVE");
        addMember(mine, hostId);
        addMember(later, memberId);
        study(memberId, inHours(5), 4, "ACTIVE");   // 내가 참여하지 않은 스터디

        assertThat(list(host, "")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items[*].id").asArray().containsExactly((int) mine, (int) later);
            assertThat(json).extractingPath("$.items[0].role").isEqualTo("MEMBER");
            assertThat(json).extractingPath("$.items[1].role").isEqualTo("HOST");
            assertThat(json).extractingPath("$.items[0].memberCount").isEqualTo(2);
            assertThat(json).extractingPath("$.items[0].status").isEqualTo("ACTIVE");
            assertThat(json).extractingPath("$.nextCursor").isNull();
        });
    }

    @Test
    @DisplayName("role=host 는 내가 연 것만, role=member 는 내가 연 것을 뺀 것만")
    void roleFilter() {
        long hosted = study(hostId, inHours(48), 4, "ACTIVE");
        long joined = study(memberId, inHours(24), 4, "ACTIVE");
        addMember(joined, hostId);

        assertThat(list(host, "?role=host")).bodyJson().extractingPath("$.items[*].id").asArray()
                .containsExactly((int) hosted);
        assertThat(list(host, "?role=member")).bodyJson().extractingPath("$.items[*].id").asArray()
                .containsExactly((int) joined);
    }

    @Test
    @DisplayName("state=past 는 종료·파투된 스터디만 최근 것부터, upcoming 에는 나오지 않는다")
    void past() {
        long ended = study(hostId, inHours(-48), 4, "ENDED");
        long cancelled = study(hostId, inHours(-24), 4, "CANCELLED");
        long active = study(hostId, inHours(24), 4, "ACTIVE");

        assertThat(list(host, "?state=past")).bodyJson().extractingPath("$.items[*].id").asArray()
                .containsExactly((int) cancelled, (int) ended);
        assertThat(list(host, "?state=upcoming")).bodyJson().extractingPath("$.items[*].id").asArray()
                .containsExactly((int) active);
        assertThat(list(host, "?state=past")).bodyJson().extractingPath("$.items[0].status").isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("나간 스터디와 대기 중인 스터디는 내 스터디가 아니다")
    void leftAndWaitingAreExcluded() {
        long left = study(hostId, inHours(48), 4, "ACTIVE");
        addMember(left, memberId);
        jdbc.update("UPDATE study_member SET status = 'LEFT' WHERE study_id = ? AND user_id = ?", left, memberId);
        long waited = study(hostId, inHours(48), 2, "ACTIVE");
        jdbc.update("INSERT INTO study_waiting (study_id, user_id, created_at) VALUES (?, ?, ?)",
                waited, memberId, utc(now));

        assertThat(list(member, "")).bodyJson().extractingPath("$.items").asArray().isEmpty();
    }

    @Test
    @DisplayName("승계 후 새 주최자는 role=host 로 나오고 전 주최자는 목록에서 빠진다")
    void afterSuccession() {
        long id = study(hostId, inHours(48), 4, "ACTIVE");
        addMember(id, memberId);

        assertThat(mvc.delete().uri("/api/v1/studies/{id}/members/me", id)
                .cookie(host, csrf).header("X-XSRF-TOKEN", csrf.getValue()).exchange()).hasStatusOk();

        assertThat(list(member, "?role=host")).bodyJson().extractingPath("$.items[*].id").asArray()
                .containsExactly((int) id);
        assertThat(list(host, "")).bodyJson().extractingPath("$.items").asArray().isEmpty();
    }

    @Test
    @DisplayName("커서로 이어 읽으면 빠짐·중복 없이 모든 항목을 한 번씩 받는다 (다가오는·지난 모두)")
    void paging() throws Exception {
        List<Integer> upcoming = new ArrayList<>();
        List<Integer> past = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            upcoming.add((int) study(hostId, inHours(i * 10), 4, "ACTIVE"));
            past.add((int) study(hostId, inHours(-i * 10), 4, "ENDED")); // 최근(i 가 작은) 것부터
        }
        // 시작이 같은 항목: id 로 순서를 가른다.
        Instant same = inHours(200);
        upcoming.add((int) study(hostId, same, 4, "ACTIVE"));
        upcoming.add((int) study(hostId, same, 4, "ACTIVE"));

        assertThat(readAll(host, "upcoming")).isEqualTo(upcoming);
        assertThat(readAll(host, "past")).isEqualTo(past);
    }

    @Test
    @DisplayName("잘못된 role·state·size, 다른 state 의 커서는 400, 로그인하지 않으면 401")
    void invalidInput() throws Exception {
        study(hostId, inHours(48), 4, "ACTIVE");
        study(hostId, inHours(72), 4, "ACTIVE");
        String cursor = list(host, "?size=1").getResponse().getContentAsString()
                .replaceAll(".*\"nextCursor\":\"([^\"]+)\".*", "$1");

        assertThat(list(host, "?role=admin")).hasStatus(400);
        assertThat(list(host, "?state=later")).hasStatus(400);
        assertThat(list(host, "?size=0")).hasStatus(400);
        assertThat(list(host, "?size=51")).hasStatus(400);
        assertThat(list(host, "?cursor=zzz")).hasStatus(400);
        assertThat(list(host, "?state=past&cursor=" + cursor)).hasStatus(400);
        assertThat(mvc.get().uri("/api/v1/me/studies").exchange()).hasStatus(401);
    }

    /** 커서를 따라 끝까지 읽어 id 순서를 모은다. 페이지 크기는 3. */
    private List<Integer> readAll(Cookie user, String state) throws Exception {
        List<Integer> ids = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 10; guard++) {
            String query = "?state=" + state + "&size=3" + (cursor == null ? "" : "&cursor=" + cursor);
            MvcTestResult result = list(user, query);
            assertThat(result).hasStatusOk();
            String body = result.getResponse().getContentAsString();
            java.util.regex.Matcher id = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(body);
            while (id.find()) {
                ids.add(Integer.parseInt(id.group(1)));
            }
            java.util.regex.Matcher next = java.util.regex.Pattern.compile("\"nextCursor\":\"([^\"]+)\"").matcher(body);
            if (!next.find()) {
                return ids;
            }
            cursor = next.group(1);
        }
        throw new AssertionError("커서가 끝나지 않습니다");
    }

    private MvcTestResult list(Cookie user, String query) {
        return mvc.get().uri("/api/v1/me/studies" + query).cookie(user).exchange();
    }

    private Instant inHours(int hours) {
        return now.plus(Duration.ofHours(hours));
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private long study(long host, Instant startAt, int capacity, String status) {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SEOUL', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, ?, 120, ?, ?, NULL, TRUE, ?, ?, ?)
                """, host, utc(startAt), startAt.atZone(TimeProvider.KOREA).toLocalDate(), capacity, status,
                utc(now), utc(now));
        long id = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        addMember(id, host);
        return id;
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

    private void addMember(long studyId, long userId) {
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', ?)",
                studyId, userId, utc(now));
    }
}
