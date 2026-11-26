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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 대기 등록·취소 (PLAN T-017, API 4장, PRD 4.14, 결정 X-16). 스터디는 SQL 로 직접 넣는다. */
@Import(FakeSocialProvider.class)
class StudyWaitingIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private long hostId;
    private long waiterId;
    private Cookie host;
    private Cookie waiter;
    private Cookie csrf;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        host = login(1001L);
        waiter = login(1002L);
        hostId = userIdOf(1001L);
        waiterId = userIdOf(1002L);
        onboard(hostId, "주최자");
        onboard(waiterId, "대기자");
        csrf = mvc.get().uri("/api/v1/me").cookie(waiter).exchange().getResponse().getCookie("XSRF-TOKEN");
    }

    @Test
    @DisplayName("정원이 찬 스터디에 대기하면 201 이고, 상세의 myState 가 WAITING 이다")
    void waitsWhenFull() {
        long id = fullStudy(inHours(48));

        assertThat(wait(id, waiter)).hasStatus(201);

        assertThat(waitingRows(id)).isEqualTo(1);
        assertThat(mvc.get().uri("/api/v1/studies/{id}", id).cookie(waiter).exchange())
                .hasStatusOk().bodyJson().extractingPath("$.myState").isEqualTo("WAITING");
    }

    @Test
    @DisplayName("정원에 여유가 있으면 STUDY_NOT_FULL")
    void notFull() {
        long id = study(inHours(48), 4);

        assertCode(wait(id, waiter), 409, "STUDY_NOT_FULL");
        assertThat(waitingRows(id)).isZero();
    }

    @Test
    @DisplayName("이미 대기 중이면 ALREADY_WAITING")
    void alreadyWaiting() {
        long id = fullStudy(inHours(48));
        wait(id, waiter);

        assertCode(wait(id, waiter), 409, "ALREADY_WAITING");
        assertThat(waitingRows(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("체크인이 열린 뒤에는 STUDY_NOT_RECRUITING")
    void checkinOpened() {
        long id = fullStudy(now.plus(Duration.ofMinutes(5)));

        assertCode(wait(id, waiter), 409, "STUDY_NOT_RECRUITING");
    }

    @Test
    @DisplayName("이미 참여 중인 사람은 ALREADY_JOINED")
    void alreadyMember() {
        long id = fullStudy(inHours(48));
        addMember(id, waiterId);

        assertCode(wait(id, waiter), 409, "ALREADY_JOINED");
    }

    @Test
    @DisplayName("없는 스터디는 404, 로그인하지 않으면 401")
    void notFoundAndUnauthenticated() {
        assertCode(wait(999_999L, waiter), 404, "NOT_FOUND");
        assertThat(mvc.post().uri("/api/v1/studies/{id}/waiting", 1L)
                .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())).hasStatus(401);
    }

    @Test
    @DisplayName("대기를 취소하면 행이 삭제되고 myState 가 NONE 이다")
    void cancelDeletesRow() {
        long id = fullStudy(inHours(48));
        wait(id, waiter);

        assertThat(cancel(id, waiter)).hasStatus(204);

        assertThat(waitingRows(id)).isZero();
        assertThat(mvc.get().uri("/api/v1/studies/{id}", id).cookie(waiter).exchange())
                .hasStatusOk().bodyJson().extractingPath("$.myState").isEqualTo("NONE");
    }

    @Test
    @DisplayName("참여 신청 제한 중이면 대기도 403 USER_RESTRICTED 와 풀리는 시각")
    void restricted() {
        long id = fullStudy(inHours(48));
        Instant until = now.plus(Duration.ofDays(3));
        jdbc.update("""
                INSERT INTO user_sanction (user_id, kind, source, reason, starts_at, ends_at)
                VALUES (?, 'JOIN_RESTRICT', 'AUTO', '테스트', ?, ?)
                """, waiterId, utc(now.minus(Duration.ofDays(1))), utc(until));

        MvcTestResult result = wait(id, waiter);

        assertCode(result, 403, "USER_RESTRICTED");
        assertThat(result).bodyJson().extractingPath("$.until").isEqualTo(until.toString());
        assertThat(waitingRows(id)).isZero();
    }

    @Test
    @DisplayName("입장 기준을 충족하지 못하면 대기도 409 ENTRY_CRITERIA_NOT_MET (신규 불허, 최소 출석률 미달)")
    void entryCriteriaNotMet() {
        long id = fullStudy(inHours(48));
        jdbc.update("UPDATE study SET allow_newcomer = FALSE, min_attendance_rate = NULL WHERE id = ?", id);

        assertCode(wait(id, waiter), 409, "ENTRY_CRITERIA_NOT_MET");

        jdbc.update("UPDATE study SET min_attendance_rate = 70 WHERE id = ?", id);
        jdbc.update("""
                INSERT INTO user_trust_summary (user_id, counted, attended, late, no_show, late_cancel, rate,
                    recalculated_at)
                VALUES (?, 10, 0, 0, 0, 0, 69.99, ?)
                """, waiterId, utc(now));
        assertCode(wait(id, waiter), 409, "ENTRY_CRITERIA_NOT_MET");

        jdbc.update("UPDATE user_trust_summary SET rate = 70.00 WHERE user_id = ?", waiterId);
        assertThat(wait(id, waiter)).hasStatus(201);
    }

    @Test
    @DisplayName("내 직군의 대분류가 희망 직군에 없으면 대기도 409 JOB_NOT_ALLOWED")
    void jobNotAllowed() {
        long id = fullStudy(inHours(48));
        jdbc.update("INSERT INTO study_target_job (study_id, job_code) VALUES (?, 'DESIGN')", id);

        assertCode(wait(id, waiter), 409, "JOB_NOT_ALLOWED");
    }

    @Test
    @DisplayName("정원에 여유가 있으면 입장 기준보다 먼저 STUDY_NOT_FULL (여유가 있으면 신청을 안내한다)")
    void notFullComesBeforeCriteria() {
        long id = study(inHours(48), 4);
        jdbc.update("UPDATE study SET allow_newcomer = FALSE WHERE id = ?", id);

        assertCode(wait(id, waiter), 409, "STUDY_NOT_FULL");
    }

    @Test
    @DisplayName("대기 중이 아닌 사람의 취소도 204 이다 (멱등)")
    void cancelIsIdempotent() {
        long id = fullStudy(inHours(48));

        assertThat(cancel(id, waiter)).hasStatus(204);
    }

    @Test
    @DisplayName("waitingCount 는 주최자의 응답에만 있다")
    void waitingCountOnlyForHost() {
        long id = fullStudy(inHours(48));
        wait(id, waiter);

        assertThat(mvc.get().uri("/api/v1/studies/{id}", id).cookie(host).exchange())
                .hasStatusOk().bodyJson().extractingPath("$.waitingCount").isEqualTo(1);
        assertThat(mvc.get().uri("/api/v1/studies/{id}", id).cookie(waiter).exchange())
                .hasStatusOk().bodyJson().doesNotHavePath("$.waitingCount");
        assertThat(mvc.get().uri("/api/v1/studies/{id}", id).exchange())
                .hasStatusOk().bodyJson().doesNotHavePath("$.waitingCount");
    }

    @Test
    @DisplayName("체크인이 열린 뒤에는 남은 대기 행이 myState 와 waitingCount 에서 빠진다 (X-16)")
    void leftoverRowsAreHiddenAfterCheckinOpens() {
        long id = fullStudy(now.plus(Duration.ofMinutes(5)));
        jdbc.update("INSERT INTO study_waiting (study_id, user_id, created_at) VALUES (?, ?, ?)",
                id, waiterId, utc(now.minus(Duration.ofHours(1))));

        assertThat(mvc.get().uri("/api/v1/studies/{id}", id).cookie(waiter).exchange())
                .hasStatusOk().bodyJson().extractingPath("$.myState").isEqualTo("NONE");
        assertThat(mvc.get().uri("/api/v1/studies/{id}", id).cookie(host).exchange())
                .hasStatusOk().bodyJson().extractingPath("$.waitingCount").isEqualTo(0);
    }

    private MvcTestResult wait(long studyId, Cookie user) {
        return mvc.post().uri("/api/v1/studies/{id}/waiting", studyId)
                .cookie(user, csrf).header("X-XSRF-TOKEN", csrf.getValue()).exchange();
    }

    private MvcTestResult cancel(long studyId, Cookie user) {
        return mvc.delete().uri("/api/v1/studies/{id}/waiting/me", studyId)
                .cookie(user, csrf).header("X-XSRF-TOKEN", csrf.getValue()).exchange();
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
        jdbc.update("UPDATE user SET nickname = ?, job_code = 'IT_BACKEND', district_code = 'SEOUL' WHERE id = ?",
                nickname, userId);
    }

    private int waitingRows(long studyId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM study_waiting WHERE study_id = ?", Integer.class, studyId);
    }

    /** 정원 2명에 주최자와 다른 한 명이 이미 들어 있는 스터디. */
    private long fullStudy(Instant startAt) {
        long id = study(startAt, 2);
        login(1003L);
        addMember(id, userIdOf(1003L));
        return id;
    }

    private long study(Instant startAt, int capacity) {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SEOUL', '스터디 카페',
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
