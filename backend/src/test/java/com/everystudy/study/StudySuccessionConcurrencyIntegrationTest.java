package com.everystudy.study;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.common.error.BusinessException;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.service.StudyLeaveService;
import com.everystudy.support.IntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 주최자 나가기와 참여자 나가기의 동시성 (PLAN T-021, ADR-0004 "테스트 필수" (b)). 실제 {@code SecureRandom} 추첨으로
 * 여러 스레드가 함께 나가도 승계 대상이 정확히 한 명이고 이미 나간 사람이 아님을 반복해서 확인한다. 추첨 순서와 스레드
 * 순서가 매번 달라지므로 같은 시나리오를 여러 번 돌린다. 서비스를 바로 부른다.
 */
class StudySuccessionConcurrencyIntegrationTest extends IntegrationTestSupport {

    /** 시나리오마다 반복 횟수. */
    private static final int REPETITIONS = 10;
    private static final String LEFT = "LEFT";

    @Autowired
    StudyLeaveService leaves;

    @Autowired
    JdbcTemplate jdbc;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private long hostId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        hostId = user("주최자");
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("주최자와 참여자 한 명이 함께 나가면 남은 한 명이 주최자가 되고, 나간 사람은 주최자가 아니다")
    void hostAndOneParticipantLeave() throws Exception {
        long studyId = study(4);
        long leaver = user("나가는참여자");
        long stayer = user("남는참여자");
        addMember(studyId, leaver);
        addMember(studyId, stayer);

        Map<String, Long> outcomes = runTogether(List.of(
                () -> attempt(studyId, hostId), () -> attempt(studyId, leaver)));

        assertThat(outcomes).containsOnlyKeys("OK").containsEntry("OK", 2L);
        assertThat(hostOf(studyId)).isEqualTo(stayer);
        assertThat(memberStatus(studyId, stayer)).isEqualTo("ACTIVE");
        assertThat(memberStatus(studyId, hostId)).isEqualTo(LEFT);
        assertThat(memberStatus(studyId, leaver)).isEqualTo(LEFT);
        assertThat(activeMembers(studyId)).isEqualTo(1);
        assertThat(status(studyId)).isEqualTo("ACTIVE");
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("남은 참여자가 0명이 되는 경계에서는 어느 순서로 나가든 파투 한 번으로 끝나고 살아 있는 주최자는 없다")
    void boundaryEndsInCancellation() throws Exception {
        long studyId = study(4);
        long participant = user("참여자");
        addMember(studyId, participant);

        Map<String, Long> outcomes = runTogether(List.of(
                () -> attempt(studyId, hostId), () -> attempt(studyId, participant)));

        assertThat(outcomes).containsOnlyKeys("OK").containsEntry("OK", 2L);
        assertThat(status(studyId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT cancel_reason FROM study WHERE id = ?", String.class, studyId))
                .isEqualTo("HOST_LEFT");
        assertThat(activeMembers(studyId)).isZero();
        // 승계가 있었다면(주최자가 먼저 나간 경우) 한 번뿐이다. 파투와 승계가 겹쳐 둘 다 일어나지 않는다.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM host_transfer WHERE study_id = ?", Integer.class,
                studyId)).isLessThanOrEqualTo(1);
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("주최자와 참여자 둘이 함께 나가도 끝까지 남은 한 명만 주최자가 된다 (승계된 사람이 또 나가는 경우 포함)")
    void hostAndTwoParticipantsLeave() throws Exception {
        long studyId = study(6);
        long a = user("A");
        long b = user("B");
        long stayer = user("남는참여자");
        addMember(studyId, a);
        addMember(studyId, b);
        addMember(studyId, stayer);

        Map<String, Long> outcomes = runTogether(List.of(
                () -> attempt(studyId, hostId), () -> attempt(studyId, a), () -> attempt(studyId, b)));

        assertThat(outcomes).containsOnlyKeys("OK").containsEntry("OK", 3L);
        assertThat(hostOf(studyId)).isEqualTo(stayer);
        assertThat(activeMembers(studyId)).isEqualTo(1);
        assertThat(status(studyId)).isEqualTo("ACTIVE");
        // 이력은 한 줄씩 이어진다: 받는 사람은 그 시점의 주최자가 아니었던 살아 있던 참여자다.
        List<Map<String, Object>> chain = jdbc.queryForList(
                "SELECT from_user_id, to_user_id FROM host_transfer WHERE study_id = ? ORDER BY id", studyId);
        assertThat(chain).isNotEmpty();
        long expectedFrom = hostId;
        for (Map<String, Object> transfer : chain) {
            assertThat(((Number) transfer.get("from_user_id")).longValue()).isEqualTo(expectedFrom);
            expectedFrom = ((Number) transfer.get("to_user_id")).longValue();
        }
        assertThat(expectedFrom).isEqualTo(stayer);
    }

    /** 나가기 한 건의 결과. 성공이면 {@code OK}, 규칙 위반이면 오류 코드, 락 대기 초과면 {@code LOCK_TIMEOUT}. */
    private String attempt(long studyId, long userId) {
        try {
            leaves.leave(studyId, userId);
            return "OK";
        } catch (BusinessException ex) {
            return ex.errorCode().name();
        } catch (PessimisticLockingFailureException ex) {
            return "LOCK_TIMEOUT";
        } catch (RuntimeException ex) {
            return ex.getClass().getSimpleName();
        }
    }

    private String status(long studyId) {
        return jdbc.queryForObject("SELECT status FROM study WHERE id = ?", String.class, studyId);
    }

    /** 모든 작업을 준비시킨 뒤 동시에 출발시키고, 결과별 개수를 센다. */
    private static Map<String, Long> runTogether(List<Callable<String>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        } finally {
            executor.shutdownNow();
        }
    }

    /** 온보딩을 마친 회원을 SQL 로 바로 만든다 (로그인 흐름은 동시성과 관계없다). */
    private long user(String nickname) {
        jdbc.update("""
                INSERT INTO user (nickname, job_code, district_code, role, status, created_at)
                VALUES (?, 'IT', 'SUWON_JANGAN', 'USER', 'ACTIVE', ?)
                """, nickname, utc(now));
        return jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class);
    }

    /** 주최자가 연 모집 중인 스터디와 주최자 참여 행. 입장 기준은 신규 허용, 희망 직군 없음. */
    private long study(int capacity) {
        Instant startAt = now.plus(2, ChronoUnit.DAYS);
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

    private int activeMembers(long studyId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM study_member WHERE study_id = ? AND status = 'ACTIVE'",
                Integer.class, studyId);
    }

    private long hostOf(long studyId) {
        return jdbc.queryForObject("SELECT host_id FROM study WHERE id = ?", Long.class, studyId);
    }

    private String memberStatus(long studyId, long userId) {
        return jdbc.queryForObject("SELECT status FROM study_member WHERE study_id = ? AND user_id = ?",
                String.class, studyId, userId);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
