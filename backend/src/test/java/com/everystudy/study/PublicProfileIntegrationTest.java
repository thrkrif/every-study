package com.everystudy.study;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.auth.FakeSocialProvider;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.support.IntegrationTestSupport;
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

/** 공개 프로필과 목록·상세·참여자 목록의 실제 지표 (PLAN T-033, API 3장, PRD 4.7·4.13D). */
@Import(FakeSocialProvider.class)
class PublicProfileIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private long hostId;
    private long memberId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        FakeSocialProvider.login(mvc, "kakao", kakaoUser(2001L, null));
        FakeSocialProvider.login(mvc, "kakao", kakaoUser(2002L, null));
        hostId = userIdOf(2001L);
        memberId = userIdOf(2002L);
        onboard(hostId, "주최자", "IT");
        onboard(memberId, "참여자", "IT_BACKEND");
    }

    @Test
    @DisplayName("출석 기록이 없는 회원은 신규이고 출석률이 null 이며, 주최 지표는 0과 건수만 표시다. 로그인 없이 볼 수 있다")
    void newcomerHasNoRate() {
        assertThat(mvc.get().uri("/api/v1/users/{id}", memberId).exchange()).hasStatusOk().bodyJson()
                .isStrictlyEqualTo(String.format("""
                        {"id": %d, "nickname": "참여자", "jobCode": "IT_BACKEND", "districtCode": "SEOUL",
                         "joinedAt": "%s",
                         "attendance": {"attendanceRate": null, "windowCount": 0, "noShow": 0, "late": 0,
                                        "lateCancel": 0, "isNewcomer": true},
                         "hostStats": {"hostedCount": 0, "leftWithinWindowCount": 0, "showCountOnly": true}}
                        """, memberId, joinedAt(memberId)));
    }

    @Test
    @DisplayName("집계가 있으면 출석률은 내림한 정수이고, 신규 기준 미만이면 숫자 없이 신규다")
    void rateIsFlooredAndNewcomerBelowThreshold() {
        summary(memberId, 10, 8, 2, 0, 0, "89.99");

        assertThat(mvc.get().uri("/api/v1/users/{id}", memberId).exchange()).hasStatusOk().bodyJson()
                .extractingPath("$.attendance").convertTo(String.class).asString()
                .contains("\"attendanceRate\":89", "\"windowCount\":10", "\"late\":2", "\"isNewcomer\":false");

        summary(memberId, 2, 2, 0, 0, 0, "100.00");

        assertThat(mvc.get().uri("/api/v1/users/{id}", memberId).exchange()).hasStatusOk().bodyJson()
                .extractingPath("$.attendance.isNewcomer").isEqualTo(true);
        assertThat(mvc.get().uri("/api/v1/users/{id}", memberId).exchange()).bodyJson()
                .extractingPath("$.attendance.attendanceRate").isNull();
    }

    @Test
    @DisplayName("주최 횟수는 끝난 스터디와 직전 이탈 승계를 세고, 5회가 되면 건수만 표시에서 벗어난다")
    void hostedCountSwitchesShowCountOnlyAtFive() {
        for (int i = 0; i < 3; i++) {
            study(hostId, "ENDED");
        }
        study(hostId, "CANCELLED");
        study(hostId, "ACTIVE");
        long transferred = study(memberId, "ACTIVE");
        jdbc.update("INSERT INTO host_penalty_event (host_id, study_id, points, reason, occurred_at) "
                + "VALUES (?, ?, 1, 'LEFT_WITHIN_WINDOW', ?)", hostId, transferred, LocalDateTime.now());

        // 끝난 3 + 직전 이탈 1 = 4회: 아직 건수만
        assertThat(mvc.get().uri("/api/v1/users/{id}", hostId).exchange()).bodyJson()
                .extractingPath("$.hostStats").convertTo(String.class).asString()
                .contains("\"hostedCount\":4", "\"leftWithinWindowCount\":1", "\"showCountOnly\":true");

        study(hostId, "ENDED");

        assertThat(mvc.get().uri("/api/v1/users/{id}", hostId).exchange()).bodyJson()
                .extractingPath("$.hostStats").convertTo(String.class).asString()
                .contains("\"hostedCount\":5", "\"showCountOnly\":false");
    }

    @Test
    @DisplayName("탈퇴한 회원은 닉네임·직군·지역이 비고, 없는 회원과 온보딩 전 회원은 404 다")
    void withdrawnAndMissing() {
        jdbc.update("UPDATE user SET status = 'WITHDRAWN', nickname = NULL, job_code = NULL, district_code = NULL "
                + "WHERE id = ?", memberId);
        assertThat(mvc.get().uri("/api/v1/users/{id}", memberId).exchange()).hasStatusOk().bodyJson()
                .extractingPath("$.nickname").isNull();

        assertThat(mvc.get().uri("/api/v1/users/{id}", 999_999L).exchange()).hasStatus(404);

        FakeSocialProvider.login(mvc, "kakao", kakaoUser(2003L, null));
        assertThat(mvc.get().uri("/api/v1/users/{id}", userIdOf(2003L)).exchange()).hasStatus(404);
    }

    @Test
    @DisplayName("스터디 상세의 주최자와 참여자에 실제 지표가 실린다")
    void studyDetailCarriesRealMetrics() {
        summary(memberId, 10, 9, 0, 1, 0, "90.00");
        study(hostId, "ENDED");
        long open = study(hostId, "ACTIVE");
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', ?)",
                open, memberId, LocalDateTime.now());

        var body = mvc.get().uri("/api/v1/studies/{id}", open).exchange();

        assertThat(body).hasStatusOk().bodyJson().extractingPath("$.host.stats.hostedCount").isEqualTo(1);
        assertThat(body).bodyJson().extractingPath("$.members[?(@.userId==" + memberId + ")].trust.attendanceRate")
                .asArray().containsExactly(90);
        assertThat(body).bodyJson().extractingPath("$.members[?(@.userId==" + hostId + ")].trust.isNewcomer")
                .asArray().containsExactly(true);
    }

    private long study(long host, String status) {
        LocalDateTime start = LocalDateTime.ofInstant(now.plus(Duration.ofHours(48)), ZoneOffset.UTC);
        LocalDateTime created = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '스터디', '같이 공부해요. 조용히 집중하는 모임입니다.', 'DEVELOPMENT', 'SEOUL',
                    '스터디 카페', '경기 수원시 장안구', 37.300000, 127.010000, ?, 120, ?, 4, 70, TRUE, ?, ?, ?)
                """, host, start, now.plus(Duration.ofHours(48)).atZone(TimeProvider.KOREA).toLocalDate(), status,
                created, created);
        long id = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', ?)", id,
                host, created);
        return id;
    }

    private void summary(long userId, int counted, int attended, int late, int noShow, int lateCancel, String rate) {
        jdbc.update("DELETE FROM user_trust_summary WHERE user_id = ?", userId);
        jdbc.update("""
                INSERT INTO user_trust_summary (user_id, counted, attended, late, no_show, late_cancel, rate,
                    recalculated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, counted, attended, late, noShow, lateCancel, new java.math.BigDecimal(rate),
                LocalDateTime.now());
    }

    private String joinedAt(long userId) {
        return jdbc.queryForObject("SELECT created_at FROM user WHERE id = ?", LocalDateTime.class, userId)
                .toInstant(ZoneOffset.UTC).toString();
    }

    private long userIdOf(long kakaoId) {
        return jdbc.queryForObject("SELECT user_id FROM social_account WHERE provider_user_id = ?", Long.class,
                Long.toString(kakaoId));
    }

    private void onboard(long userId, String nickname, String jobCode) {
        jdbc.update("UPDATE user SET nickname = ?, job_code = ?, district_code = 'SEOUL' WHERE id = ?",
                nickname, jobCode, userId);
    }
}
