package com.everystudy.study;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.common.error.BusinessException;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.service.StudyJoinService;
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
 * 참여 신청의 동시성 (PLAN T-016, ADR-0004 "테스트 필수" (a)·(c)). 여러 스레드가 출발선에서 함께 출발해 실제 MySQL 에서
 * {@code study} 행 락을 두고 경쟁한다. 웹 계층은 {@code StudyJoinIntegrationTest} 가 보므로 서비스를 바로 부른다.
 *
 * <p>스레드 순서는 매번 달라지므로 같은 시나리오를 여러 번 돌린다. 신청자 수는 연결 풀(기본 10)을 넘지 않게 잡는다.
 * 넘으면 연결을 기다리는 시간이 섞여 락 경쟁만 보기 어렵다.
 */
class StudyJoinConcurrencyIntegrationTest extends IntegrationTestSupport {

    /** 시나리오마다 반복 횟수. */
    private static final int REPETITIONS = 5;
    /** 스터디 정원 (주최자 포함). */
    private static final int CAPACITY = 4;
    /** 마지막 자리에 정원보다 이만큼 더 많은 사람이 신청한다. */
    private static final int EXTRA_APPLICANTS = 6;
    /** 같은 사용자가 동시에 보내는 신청 수. */
    private static final int DUPLICATE_REQUESTS = 8;

    private static final String JOINED = "JOINED";

    @Autowired
    StudyJoinService joins;

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
    @DisplayName("(a) 마지막 1자리에 정원보다 많은 사람이 동시에 신청해도 한 명만 확정되고 나머지는 STUDY_FULL")
    void lastSeat() throws Exception {
        long studyId = study(CAPACITY);
        for (int i = 1; i < CAPACITY - 1; i++) {
            addMember(studyId, user("기존" + i));
        }
        List<Long> applicants = new ArrayList<>();
        for (int i = 0; i < CAPACITY + EXTRA_APPLICANTS; i++) {
            applicants.add(user("신청" + i));
        }

        Map<String, Long> outcomes = runTogether(applicants.stream()
                .<Callable<String>>map(userId -> () -> attempt(studyId, userId))
                .toList());

        assertThat(outcomes).containsOnlyKeys(JOINED, "STUDY_FULL")
                .containsEntry(JOINED, 1L)
                .containsEntry("STUDY_FULL", (long) applicants.size() - 1);
        assertThat(activeMembers(studyId)).isEqualTo(CAPACITY);
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("(c) 같은 사용자가 동시에 여러 번 신청해도 한 건만 확정되고 나머지는 ALREADY_JOINED")
    void duplicateRequests() throws Exception {
        long studyId = study(CAPACITY);
        long applicantId = user("신청자");

        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < DUPLICATE_REQUESTS; i++) {
            tasks.add(() -> attempt(studyId, applicantId));
        }
        Map<String, Long> outcomes = runTogether(tasks);

        assertThat(outcomes).containsOnlyKeys(JOINED, "ALREADY_JOINED")
                .containsEntry(JOINED, 1L)
                .containsEntry("ALREADY_JOINED", (long) DUPLICATE_REQUESTS - 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM study_member WHERE study_id = ? AND user_id = ?",
                Integer.class, studyId, applicantId)).isEqualTo(1);
    }

    /**
     * 신청 한 건의 결과를 문자열로 돌려준다. 성공이면 {@link #JOINED}, 규칙 위반이면 오류 코드, 락 대기 초과면
     * {@code LOCK_TIMEOUT}. 예상하지 못한 예외(예: 유일 제약 위반)는 클래스 이름이 되어 단언에서 드러난다.
     */
    private String attempt(long studyId, long userId) {
        try {
            joins.join(studyId, userId);
            return JOINED;
        } catch (BusinessException ex) {
            return ex.errorCode().name();
        } catch (PessimisticLockingFailureException ex) {
            return "LOCK_TIMEOUT";
        } catch (RuntimeException ex) {
            return ex.getClass().getSimpleName();
        }
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
                VALUES (?, 'IT', 'SEOUL', 'USER', 'ACTIVE', ?)
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

    private int activeMembers(long studyId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM study_member WHERE study_id = ? AND status = 'ACTIVE'",
                Integer.class, studyId);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
