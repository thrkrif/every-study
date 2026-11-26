package com.everystudy.study;

import static com.everystudy.auth.FakeSocialProvider.kakaoUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.auth.FakeSocialProvider;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.repository.StudyRepository;
import com.everystudy.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 참여 신청 (PLAN T-015, API 4장, PRD 4.4). 스터디·제재·지표는 상황을 마음대로 만들려고 SQL 로 직접 넣는다.
 * 모든 시각은 실제 현재 시각에서 떨어진 거리로 만든다. 검사 순서 전체는 {@code StudyJoinRulesTest} 가 본다.
 */
@Import(FakeSocialProvider.class)
class StudyJoinIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    StudyRepository studies;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private long hostId;
    private long applicantId;
    private Cookie applicant;
    private Cookie csrf;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        login(1001L);
        applicant = login(1002L);
        hostId = userIdOf(1001L);
        applicantId = userIdOf(1002L);
        onboard(hostId, "주최자", "IT");
        onboard(applicantId, "신청자", "IT_BACKEND");
        csrf = mvc.get().uri("/api/v1/me").cookie(applicant).exchange().getResponse().getCookie("XSRF-TOKEN");
    }

    @Test
    @DisplayName("신청하면 바로 확정되어 201 이고, 상세에서 참여자로 보인다")
    void joinsImmediately() {
        long id = study(inHours(48), 4, List.of("IT"));

        assertThat(join(id, applicant)).hasStatus(201);

        assertThat(memberStatus(id, applicantId)).isEqualTo("ACTIVE");
        assertThat(mvc.get().uri("/api/v1/studies/{id}", id).cookie(applicant).exchange())
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.myState").isEqualTo("MEMBER");
                    assertThat(json).extractingPath("$.memberCount").isEqualTo(2);
                });
    }

    @Test
    @DisplayName("나갔던 사용자가 다시 신청하면 같은 행을 되살린다")
    void rejoinReactivatesSameRow() {
        long id = study(inHours(48), 4, List.of());
        jdbc.update("""
                INSERT INTO study_member (study_id, user_id, status, joined_at, left_at, cancel_type)
                VALUES (?, ?, 'LEFT', ?, ?, 'EARLY')
                """, id, applicantId, utc(now.minus(Duration.ofDays(1))), utc(now.minus(Duration.ofHours(1))));
        long rowId = memberRowId(id, applicantId);

        assertThat(join(id, applicant)).hasStatus(201);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT id, status, left_at, cancel_type FROM study_member WHERE study_id = ? AND user_id = ?",
                id, applicantId);
        assertThat(row).containsEntry("id", rowId).containsEntry("status", "ACTIVE");
        assertThat(row.get("left_at")).isNull();
        assertThat(row.get("cancel_type")).isNull();
    }

    @Nested
    @DisplayName("거절")
    class Rejections {

        @Test
        @DisplayName("로그인하지 않으면 401")
        void requiresLogin() {
            long id = study(inHours(48), 4, List.of());

            assertThat(mvc.post().uri("/api/v1/studies/{id}/members", id)
                    .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                    .hasStatus(401);
        }

        @Test
        @DisplayName("온보딩 전이면 403 ONBOARDING_REQUIRED (PLAN X-19)")
        void onboardingRequired() {
            long id = study(inHours(48), 4, List.of());
            jdbc.update("UPDATE user SET nickname = NULL, job_code = NULL WHERE id = ?", applicantId);

            assertCode(join(id, applicant), 403, "ONBOARDING_REQUIRED");
        }

        @Test
        @DisplayName("없는 스터디는 404 NOT_FOUND")
        void notFound() {
            assertCode(join(999_999L, applicant), 404, "NOT_FOUND");
        }

        @Test
        @DisplayName("종료·파투된 스터디는 409 STUDY_NOT_RECRUITING")
        void notActive() {
            long ended = study(inHours(48), 4, List.of());
            jdbc.update("UPDATE study SET status = 'ENDED' WHERE id = ?", ended);
            long cancelled = study(inHours(48), 4, List.of());
            jdbc.update("UPDATE study SET status = 'CANCELLED' WHERE id = ?", cancelled);

            assertCode(join(ended, applicant), 409, "STUDY_NOT_RECRUITING");
            assertCode(join(cancelled, applicant), 409, "STUDY_NOT_RECRUITING");
        }

        @Test
        @DisplayName("체크인이 열린 뒤(시작 10분 전부터)는 409 STUDY_NOT_RECRUITING")
        void checkinOpened() {
            long id = study(now.plus(Duration.ofMinutes(5)), 4, List.of());

            assertCode(join(id, applicant), 409, "STUDY_NOT_RECRUITING");
        }

        @Test
        @DisplayName("정원이 찼으면 409 STUDY_FULL")
        void full() {
            long id = study(inHours(48), 2, List.of());
            addMember(id, otherMember(1003L));

            assertCode(join(id, applicant), 409, "STUDY_FULL");
        }

        @Test
        @DisplayName("참여 신청 제한 중이면 403 USER_RESTRICTED 와 풀리는 시각. 여럿이면 가장 늦은 시각이다")
        void restricted() {
            long id = study(inHours(48), 4, List.of());
            Instant later = now.plus(Duration.ofDays(7));
            sanction("JOIN_RESTRICT", now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(3)), null);
            sanction("SUSPEND", now.minus(Duration.ofDays(1)), later, null);

            MvcTestResult result = join(id, applicant);

            assertCode(result, 403, "USER_RESTRICTED");
            assertThat(result).bodyJson().extractingPath("$.until").isEqualTo(later.toString());
        }

        @Test
        @DisplayName("해제됐거나 끝났거나 아직 시작 전인 제재, 개설 제한은 신청을 막지 않는다")
        void inactiveSanctionsDoNotBlock() {
            long id = study(inHours(48), 4, List.of());
            sanction("JOIN_RESTRICT", now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(3)), now);
            sanction("JOIN_RESTRICT", now.minus(Duration.ofDays(8)), now.minus(Duration.ofDays(1)), null);
            sanction("SUSPEND", now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(3)), null);
            sanction("HOST_RESTRICT", now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(3)), null);

            assertThat(join(id, applicant)).hasStatus(201);
        }

        @Test
        @DisplayName("지표 데이터가 없으면 신규로 보고, 신규 불허 스터디는 409 ENTRY_CRITERIA_NOT_MET")
        void newcomerNotAllowed() {
            long id = study(inHours(48), 4, List.of());
            jdbc.update("UPDATE study SET allow_newcomer = FALSE, min_attendance_rate = NULL WHERE id = ?", id);

            assertCode(join(id, applicant), 409, "ENTRY_CRITERIA_NOT_MET");
        }

        @Test
        @DisplayName("출석률이 최소 출석률보다 낮으면 409 ENTRY_CRITERIA_NOT_MET, 같거나 높으면 통과한다")
        void belowMinimumRate() {
            long id = study(inHours(48), 4, List.of());
            jdbc.update("UPDATE study SET allow_newcomer = FALSE, min_attendance_rate = 70 WHERE id = ?", id);
            trustSummary(applicantId, 10, "69.99");

            assertCode(join(id, applicant), 409, "ENTRY_CRITERIA_NOT_MET");

            jdbc.update("UPDATE user_trust_summary SET rate = 70.00 WHERE user_id = ?", applicantId);
            assertThat(join(id, applicant)).hasStatus(201);
        }

        @Test
        @DisplayName("내 직군의 대분류가 희망 직군에 없으면 409 JOB_NOT_ALLOWED")
        void jobNotAllowed() {
            long id = study(inHours(48), 4, List.of("PUBLIC", "DESIGN"));

            assertCode(join(id, applicant), 409, "JOB_NOT_ALLOWED");
        }

        @Test
        @DisplayName("이미 참여 중이면 409 ALREADY_JOINED. 주최자도 참여자라 같다")
        void alreadyJoined() {
            long id = study(inHours(48), 4, List.of());
            assertThat(join(id, applicant)).hasStatus(201);

            assertCode(join(id, applicant), 409, "ALREADY_JOINED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_member WHERE study_id = ?", Integer.class, id))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("여러 조건이 함께 어긋나면 PRD 4.4 순서상 앞선 오류가 나온다 (정원 > 이용 제한 > 희망 직군)")
        void earlierCheckWins() {
            long id = study(inHours(48), 2, List.of("PUBLIC"));
            addMember(id, otherMember(1003L));
            sanction("JOIN_RESTRICT", now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(3)), null);

            assertCode(join(id, applicant), 409, "STUDY_FULL");

            jdbc.update("UPDATE study SET capacity = 4 WHERE id = ?", id);
            assertCode(join(id, applicant), 403, "USER_RESTRICTED");
        }
    }

    @Test
    @DisplayName("다른 트랜잭션이 스터디 행을 잡고 놓지 않으면 락 대기 시간 뒤 409 LOCK_TIMEOUT 이고 참여는 생기지 않는다")
    void lockTimeout() throws Exception {
        long id = study(inHours(48), 4, List.of());
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = executor.submit(() -> tx.executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT id FROM study WHERE id = ? FOR UPDATE", Long.class, id);
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            assertCode(join(id, applicant), 409, "LOCK_TIMEOUT");

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_member WHERE study_id = ? AND user_id = ?",
                Integer.class, id, applicantId)).isZero();
        // 락이 풀리면 같은 요청이 성공한다 (재시도 가능).
        assertThat(join(id, applicant)).hasStatus(201);
    }

    @Test
    @DisplayName("락을 잡은 뒤에는 연결의 락 대기 시간을 원래 값으로 되돌린다 (연결 풀의 다른 요청에 번지지 않는다)")
    void lockWaitTimeoutIsRestored() {
        long id = study(inHours(48), 4, List.of());

        tx.executeWithoutResult(status -> {
            Long before = jdbc.queryForObject("SELECT @@SESSION.innodb_lock_wait_timeout", Long.class);
            assertThat(studies.findByIdForUpdate(id, Duration.ofSeconds(1))).isPresent();
            assertThat(jdbc.queryForObject("SELECT @@SESSION.innodb_lock_wait_timeout", Long.class)).isEqualTo(before);
        });
    }

    private MvcTestResult join(long studyId, Cookie user) {
        return mvc.post().uri("/api/v1/studies/{id}/members", studyId)
                .cookie(user, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .exchange();
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

    /** 로그인과 온보딩을 마친 다른 회원. */
    private long otherMember(long kakaoId) {
        login(kakaoId);
        long id = userIdOf(kakaoId);
        onboard(id, "회원" + kakaoId, "IT");
        return id;
    }

    private void onboard(long userId, String nickname, String jobCode) {
        jdbc.update("UPDATE user SET nickname = ?, job_code = ?, district_code = 'SEOUL' WHERE id = ?",
                nickname, jobCode, userId);
    }

    /** 주최자가 연 스터디 하나와 주최자 참여 행을 넣는다. 입장 기준은 신규 허용, 최소 출석률 없음. */
    private long study(Instant startAt, int capacity, List<String> targetJobs) {
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
        targetJobs.forEach(job -> jdbc.update("INSERT INTO study_target_job (study_id, job_code) VALUES (?, ?)", id, job));
        return id;
    }

    private void addMember(long studyId, long userId) {
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', ?)",
                studyId, userId, utc(now));
    }

    private void sanction(String kind, Instant startsAt, Instant endsAt, Instant revokedAt) {
        jdbc.update("""
                INSERT INTO user_sanction (user_id, kind, source, reason, starts_at, ends_at, revoked_at)
                VALUES (?, ?, 'AUTO', '테스트', ?, ?, ?)
                """, applicantId, kind, utc(startsAt), utc(endsAt), revokedAt == null ? null : utc(revokedAt));
    }

    private void trustSummary(long userId, int counted, String rate) {
        jdbc.update("""
                INSERT INTO user_trust_summary (user_id, counted, attended, late, no_show, late_cancel, rate,
                    recalculated_at)
                VALUES (?, ?, 0, 0, 0, 0, ?, ?)
                """, userId, counted, rate, utc(now));
    }

    private String memberStatus(long studyId, long userId) {
        return jdbc.queryForObject("SELECT status FROM study_member WHERE study_id = ? AND user_id = ?", String.class,
                studyId, userId);
    }

    private long memberRowId(long studyId, long userId) {
        return jdbc.queryForObject("SELECT id FROM study_member WHERE study_id = ? AND user_id = ?", Long.class,
                studyId, userId);
    }
}
