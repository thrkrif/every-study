package com.everystudy.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.attendance.service.AttendanceJudgementService;
import com.everystudy.support.IntegrationTestSupport;
import com.everystudy.support.MutableClock;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주최자 미도착 무효와 노쇼 후보 판정 (PLAN T-031, PRD 4.6). 시계를 고정하고 판정 서비스를 직접 부른다. 모든 스터디의 시작은
 * 2026-11-23T03:00:00Z 이고 체크인 마감은 03:30:00Z 다.
 */
@Import(AttendanceJudgementIntegrationTest.ClockConfig.class)
class AttendanceJudgementIntegrationTest extends IntegrationTestSupport {

    private static final String CLOSES_AT = "2026-11-23T03:30:00Z";
    private static final String AFTER_CLOSE = "2026-11-23T03:30:01Z";

    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(CLOSES_AT);
        }
    }

    @Autowired
    Clock clock;

    @Autowired
    AttendanceJudgementService judgement;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    private long hostId;
    private long memberA;
    private long memberB;
    private long leftMember;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        ((MutableClock) clock).set(AFTER_CLOSE);
        hostId = user("주최자");
        memberA = user("참여자A");
        memberB = user("참여자B");
        leftMember = user("나간사람");
    }

    @Test
    @DisplayName("체크인 마감 정각까지는 판정하지 않고, 1초 뒤부터 판정한다")
    void judgesAfterCloseOnly() {
        long id = study("ACTIVE");
        member(id, hostId, "ACTIVE");
        checkin(id, hostId);

        ((MutableClock) clock).set(CLOSES_AT);
        assertThat(judgement.judgeDueSessions()).isZero();
        assertThat(judgedAt(id)).isNull();

        ((MutableClock) clock).set(AFTER_CLOSE);
        assertThat(judgement.judgeDueSessions()).isEqualTo(1);
        assertThat(judgedAt(id)).isNotNull();
    }

    @Test
    @DisplayName("주최자가 체크인했으면 미체크인 참여자만 노쇼 후보가 되고 통지가 기록된다. 재실행해도 늘지 않는다")
    void noShowCandidates() {
        long id = study("ENDED");
        member(id, hostId, "ACTIVE");
        member(id, memberA, "ACTIVE");
        member(id, memberB, "ACTIVE");
        member(id, leftMember, "LEFT");
        checkin(id, hostId);
        checkin(id, memberA);

        assertThat(judgement.judgeDueSessions()).isEqualTo(1);
        assertThat(judgement.judgeDueSessions()).isZero();

        assertThat(events(id, "NO_SHOW_CANDIDATE")).containsExactly(memberB);
        assertThat(events(id, "VOIDED")).isEmpty();
        assertThat(notifiedUsers(id)).containsExactly(memberB);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox", Integer.class)).isEqualTo(2);
        assertThat(voidedAt(id)).isNull();
        assertThat(hostPenalties(id)).isZero();
    }

    @Test
    @DisplayName("주최자 미체크인이면 세션 무효: 참여자는 VOIDED, 주최자는 ABSENT 감점과 감사 로그, 노쇼 후보는 없다")
    void hostAbsentVoidsSession() {
        long id = study("ACTIVE");
        member(id, hostId, "ACTIVE");
        member(id, memberA, "ACTIVE");
        member(id, memberB, "ACTIVE");
        member(id, leftMember, "LEFT");
        checkin(id, memberA); // 주최자보다 먼저 온 참여자도 무효로 본다

        assertThat(judgement.judgeDueSessions()).isEqualTo(1);
        assertThat(judgement.judgeDueSessions()).isZero();

        assertThat(events(id, "VOIDED")).containsExactlyInAnyOrder(memberA, memberB);
        assertThat(events(id, "NO_SHOW_CANDIDATE")).isEmpty();
        assertThat(eventCount(id, hostId)).isZero();
        assertThat(voidedAt(id)).isNotNull();
        assertThat(hostPenalties(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'SESSION_VOID' AND target_id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'HOST_PENALTY' AND target_id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(notifiedUsers(id)).isEmpty();
    }

    @Test
    @DisplayName("주최자 혼자인 세션이 무효가 되면 감점하지 않는다 (영향받는 참여자가 있을 때만)")
    void hostAloneNotPenalized() {
        long id = study("ACTIVE");
        member(id, hostId, "ACTIVE");

        assertThat(judgement.judgeDueSessions()).isEqualTo(1);

        assertThat(voidedAt(id)).isNotNull();
        assertThat(hostPenalties(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_event", Integer.class)).isZero();
    }

    @Test
    @DisplayName("파투된 스터디는 ACTIVE 참여자마다 VOIDED 이고 감점은 없다. 재실행해도 늘지 않는다")
    void cancelledStudyVoidsMembers() {
        long id = study("CANCELLED");
        member(id, hostId, "ACTIVE");
        member(id, memberA, "ACTIVE");
        member(id, leftMember, "LEFT");

        assertThat(judgement.judgeDueSessions()).isEqualTo(1);
        assertThat(judgement.judgeDueSessions()).isZero();

        assertThat(events(id, "VOIDED")).containsExactlyInAnyOrder(hostId, memberA);
        assertThat(hostPenalties(id)).isZero();
        assertThat(voidedAt(id)).isNull();
        assertThat(judgedAt(id)).isNotNull();
        assertThat(notifiedUsers(id)).isEmpty();
    }

    @Test
    @DisplayName("이미 같은 판정 이벤트가 있어도 중복을 만들지 않고 판정을 마친다")
    void existingVerdictIsNotDuplicated() {
        long id = study("ACTIVE");
        member(id, hostId, "ACTIVE");
        member(id, memberB, "ACTIVE");
        checkin(id, hostId);
        jdbc.update("INSERT INTO attendance_event (study_id, user_id, type, occurred_at) "
                + "VALUES (?, ?, 'NO_SHOW_CANDIDATE', '2026-11-23 03:31:00')", id, memberB);

        assertThat(judgement.judgeDueSessions()).isEqualTo(1);

        assertThat(events(id, "NO_SHOW_CANDIDATE")).containsExactly(memberB);
        assertThat(judgedAt(id)).isNotNull();
    }

    @Test
    @DisplayName("한 번에 batch-limit 건까지만 판정하고 나머지는 다음 실행이 처리한다")
    void processesAllDueStudies() {
        long first = study("ACTIVE");
        long second = study("ACTIVE");
        long notYet = study("ACTIVE", "2026-11-23 03:01:00");

        assertThat(judgement.judgeDueSessions()).isEqualTo(2);

        assertThat(judgedAt(first)).isNotNull();
        assertThat(judgedAt(second)).isNotNull();
        assertThat(judgedAt(notYet)).isNull();
    }

    @Test
    @DisplayName("마감 직전 체크인이 아직 커밋되지 않았으면 판정은 락을 기다렸다가 그 체크인을 본다 (X-15)")
    void judgementWaitsForCheckinLock() throws Exception {
        long id = study("ACTIVE");
        member(id, hostId, "ACTIVE");
        member(id, memberA, "ACTIVE");
        checkin(id, memberA);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        CompletableFuture<Integer> judging = new CompletableFuture<>();
        tx.executeWithoutResult(status -> {
            jdbc.queryForList("SELECT id FROM study WHERE id = ? FOR UPDATE", id);
            // 체크인 트랜잭션이 락을 쥔 채 주최자 체크인을 쓰는 중이다.
            judging.completeAsync(judgement::judgeDueSessions);
            sleep(500);
            assertThat(judging).isNotDone();
            checkin(id, hostId);
        });

        assertThat(judging.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(voidedAt(id)).isNull();
        assertThat(events(id, "VOIDED")).isEmpty();
        assertThat(events(id, "NO_SHOW_CANDIDATE")).isEmpty();
        assertThat(hostPenalties(id)).isZero();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long user(String nickname) {
        jdbc.update("INSERT INTO user (nickname, role, status, created_at) VALUES (?, 'USER', 'ACTIVE', NOW(6))",
                nickname);
        return jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class);
    }

    private long study(String status) {
        return study(status, "2026-11-23 03:00:00");
    }

    private long study(String status, String startAtUtc) {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SEOUL', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, ?, 120, '2026-11-23', 4,
                    NULL, TRUE, ?, '2026-11-20 00:00:00', '2026-11-20 00:00:00')
                """, hostId, startAtUtc, status);
        return jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
    }

    private void member(long studyId, long userId, String status) {
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) "
                + "VALUES (?, ?, ?, '2026-11-20 00:00:00')", studyId, userId, status);
    }

    private void checkin(long studyId, long userId) {
        jdbc.update("INSERT INTO checkin (study_id, user_id, checked_in_at, method, result) "
                + "VALUES (?, ?, '2026-11-23 03:05:00', 'LOCATION', 'ATTENDED')", studyId, userId);
    }

    private List<Long> events(long studyId, String type) {
        return jdbc.queryForList("SELECT user_id FROM attendance_event WHERE study_id = ? AND type = ?", Long.class,
                studyId, type);
    }

    private int eventCount(long studyId, long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM attendance_event WHERE study_id = ? AND user_id = ?",
                Integer.class, studyId, userId);
    }

    private List<Long> notifiedUsers(long studyId) {
        return jdbc.queryForList("SELECT user_id FROM notification WHERE study_id = ? AND type = 'NO_SHOW_NOTICE'",
                Long.class, studyId);
    }

    private int hostPenalties(long studyId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM host_penalty_event WHERE study_id = ? AND reason = 'ABSENT'", Integer.class,
                studyId);
    }

    private Object judgedAt(long id) {
        return jdbc.queryForObject("SELECT judged_at FROM study WHERE id = ?", Object.class, id);
    }

    private Object voidedAt(long id) {
        return jdbc.queryForObject("SELECT voided_at FROM study WHERE id = ?", Object.class, id);
    }
}
