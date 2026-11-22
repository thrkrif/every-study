package com.everystudy.trust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.everystudy.support.IntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 출석 이벤트가 쌓일 때의 집계 갱신과 전체 재계산 (PLAN T-032, PRD 4.7). */
class TrustSummaryIntegrationTest extends IntegrationTestSupport {

    private static final Instant T0 = Instant.parse("2026-11-01T03:00:00Z");

    @Autowired
    AttendanceEventRecorder recorder;

    @Autowired
    TrustSummaryService summaryService;

    @Autowired
    AttendanceStandingService standing;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private long userId;
    private long hostId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        tx = new TransactionTemplate(transactionManager);
        hostId = user("주최자");
        userId = user("참여자");
    }

    @Test
    @DisplayName("이벤트를 남기면 같은 트랜잭션에서 집계가 갱신되고, 신규 기준에 이르면 입장 기준 검사가 실제 출석률을 본다")
    void incrementalUpdateFeedsEntryCriteria() {
        assertThat(standing.standingOf(userId).newcomer()).isTrue();

        attended(1);
        attended(2);
        assertThat(summary(userId)).containsEntry("counted", 2).containsEntry("attended", 2);
        assertThat(standing.standingOf(userId).newcomer()).isTrue();

        tx.executeWithoutResult(s -> recorder.recordLate(study(), userId, T0.plusSeconds(3)));
        assertThat(standing.standingOf(userId).newcomer()).isFalse();
        assertThat(standing.standingOf(userId).rate()).isEqualByComparingTo("83.33");
    }

    @Test
    @DisplayName("집계 갱신이 있는 트랜잭션이 롤백되면 이벤트도 집계도 남지 않는다")
    void rollbackLeavesNothing() {
        long study = study();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            recorder.recordAttended(study, userId, T0);
            throw new IllegalStateException("롤백");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_trust_summary", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_event", Integer.class)).isZero();
    }

    @Test
    @DisplayName("전체 재계산 결과는 증분 갱신 결과와 같다")
    void recalculateAllMatchesIncremental() {
        long other = user("다른참여자");
        attended(1);
        long cancelled = study();
        tx.executeWithoutResult(s -> recorder.recordEarlyCancel(cancelled, userId, T0.plusSeconds(1)));
        long lateCancelled = study();
        tx.executeWithoutResult(s -> recorder.recordLateCancel(lateCancelled, userId, T0.plusSeconds(2)));
        long late = study();
        tx.executeWithoutResult(s -> recorder.recordLate(late, userId, T0.plusSeconds(3)));
        long otherStudy = study();
        tx.executeWithoutResult(s -> recorder.recordAttended(otherStudy, other, T0));
        Map<String, Object> incremental = summary(userId);
        Map<String, Object> otherIncremental = summary(other);

        jdbc.update("DELETE FROM user_trust_summary");
        assertThat(summaryService.recalculateAll()).isEqualTo(2);

        assertThat(summary(userId)).containsAllEntriesOf(withoutTime(incremental));
        assertThat(summary(other)).containsAllEntriesOf(withoutTime(otherIncremental));
    }

    @Test
    @DisplayName("같은 세션에서 나갔다가 다시 와서 출석하면 가장 최근 판정만 센다")
    void latestEventPerSessionWins() {
        long study = study();
        tx.executeWithoutResult(s -> recorder.recordLateCancel(study, userId, T0));
        tx.executeWithoutResult(s -> recorder.recordAttended(study, userId, T0.plusSeconds(10)));

        assertThat(summary(userId)).containsEntry("counted", 1).containsEntry("attended", 1)
                .containsEntry("late_cancel", 0);
    }

    @Test
    @DisplayName("최근 N회 창 밖의 오래된 세션은 집계에서 빠진다")
    void oldSessionsFallOutOfWindow() {
        long noShowStudy = study();
        // 저장 시각은 UTC 로 쓰이므로(Hibernate) UTC 값 그대로 넣는다. 다른 이벤트(T0 이후)보다 오래됐다.
        jdbc.update("INSERT INTO attendance_event (study_id, user_id, type, occurred_at) "
                + "VALUES (?, ?, 'NO_SHOW', '2026-11-01 02:58:20')", noShowStudy, userId);
        for (int i = 1; i <= 10; i++) {
            attended(i);
        }

        summaryService.recalculateAll();

        assertThat(summary(userId)).containsEntry("counted", 10).containsEntry("no_show", 0);
        assertThat(((BigDecimal) summary(userId).get("rate"))).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("트랜잭션 밖에서 집계를 갱신하면 실패한다")
    void refreshRequiresTransaction() {
        assertThatThrownBy(() -> summaryService.refresh(userId)).isInstanceOf(Exception.class);
    }

    private void attended(int offsetSeconds) {
        long study = study();
        tx.executeWithoutResult(s -> recorder.recordAttended(study, userId, T0.plusSeconds(offsetSeconds)));
    }

    private Map<String, Object> summary(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM user_trust_summary WHERE user_id = ?", id);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private static Map<String, Object> withoutTime(Map<String, Object> row) {
        Map<String, Object> copy = new java.util.HashMap<>(row);
        copy.remove("recalculated_at");
        return copy;
    }

    private long user(String nickname) {
        jdbc.update("INSERT INTO user (nickname, role, status, created_at) VALUES (?, 'USER', 'ACTIVE', NOW(6))",
                nickname);
        return jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class);
    }

    private long study() {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SUWON_JANGAN', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, '2026-11-01 03:00:00', 120, '2026-11-01', 4,
                    NULL, TRUE, 'ENDED', '2026-10-20 00:00:00', '2026-10-20 00:00:00')
                """, hostId);
        return jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
    }
}
