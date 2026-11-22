package com.everystudy.trust.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 주최자 감점 합계에 따른 경고·개설 제한·운영자 검토 표시 (PLAN T-035b, PRD 4.13B·4.13C). */
class HostSanctionIntegrationTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-12-10T00:00:00Z");

    @Autowired
    HostPenaltyRecorder recorder;

    @Autowired
    SanctionService sanctionService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    private TransactionTemplate tx;
    private long hostId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        tx = new TransactionTemplate(transactionManager);
        jdbc.update("INSERT INTO user (nickname, role, status, created_at) VALUES ('주최자', 'USER', 'ACTIVE', NOW(6))");
        hostId = jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class);
    }

    @Test
    @DisplayName("감점 1점은 아무 일도 없고, 2점은 경고 알림만, 3점은 7일 개설 제한과 안내 알림이다")
    void stagesByRollingTotal() {
        penalize(NOW);
        assertThat(sanctionRows()).isZero();
        assertThat(notifications("HOST_WARNING")).isZero();

        penalize(NOW);
        assertThat(notifications("HOST_WARNING")).isEqualTo(1);
        assertThat(sanctionRows()).isZero();

        penalize(NOW);
        Map<String, Object> row = jdbc.queryForMap("SELECT kind, source, review_required FROM user_sanction");
        assertThat(row.get("kind")).isEqualTo("HOST_RESTRICT");
        assertThat(row.get("source")).isEqualTo("AUTO");
        assertThat(row.get("review_required")).isIn(false, 0);
        assertThat(notifications("SANCTION_APPLIED")).isEqualTo(1);
        assertThat(audit("SANCTION_GRANT")).isEqualTo(1);
        assertThat(sanctionService.hostRestrictedUntil(hostId, NOW)).contains(NOW.plus(Duration.ofDays(7)));
        assertThat(sanctionService.hostRestrictedUntil(hostId, NOW.plus(Duration.ofDays(7)))).isEmpty();
        // 개설 제한은 참여 신청을 막지 않는다
        assertThat(sanctionService.joinRestrictedUntil(hostId, NOW)).isEmpty();
    }

    @Test
    @DisplayName("4점은 같은 제한을 다시 걸지 않고, 5점은 30일 개설 제한과 운영자 검토 표시를 남긴다")
    void reviewStageAtFivePoints() {
        for (int i = 0; i < 4; i++) {
            penalize(NOW);
        }
        assertThat(sanctionRows()).isEqualTo(1);

        penalize(NOW);
        assertThat(jdbc.queryForList("SELECT review_required FROM user_sanction ORDER BY id", Boolean.class))
                .containsExactly(false, true);
        assertThat(sanctionService.hostRestrictedUntil(hostId, NOW)).contains(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("90일이 지난 감점은 합계에서 빠진다")
    void oldPenaltiesExpire() {
        // 과거 감점은 그 당시의 판정과 무관하게 이력만 심는다.
        oldPenalty(NOW.minus(Duration.ofDays(100)));
        oldPenalty(NOW.minus(Duration.ofDays(95)));
        penalize(NOW);

        assertThat(sanctionRows()).isZero();
        assertThat(notifications("HOST_WARNING")).isZero();
    }

    @Test
    @DisplayName("감점 트랜잭션이 롤백되면 제한도 남지 않는다")
    void rollbackLeavesNoRestriction() {
        penalize(NOW);
        penalize(NOW);
        try {
            tx.executeWithoutResult(status -> {
                recorder.recordAbsent(study(), hostId, NOW);
                throw new IllegalStateException("롤백 유도");
            });
        } catch (IllegalStateException expected) {
            // 의도한 롤백
        }
        assertThat(sanctionRows()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM host_penalty_event", Integer.class)).isEqualTo(2);
    }

    private void oldPenalty(Instant at) {
        jdbc.update("INSERT INTO host_penalty_event (host_id, study_id, points, reason, occurred_at) "
                + "VALUES (?, ?, 1, 'ABSENT', ?)", hostId, study(), java.sql.Timestamp.from(at));
    }

    private void penalize(Instant at) {
        long studyId = study();
        tx.executeWithoutResult(status -> recorder.recordAbsent(studyId, hostId, at));
    }

    private long study() {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SUWON_JANGAN', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, '2026-11-23 03:00:00', 120, '2026-11-23', 4,
                    NULL, TRUE, 'ENDED', '2026-11-20 00:00:00', '2026-11-20 00:00:00')
                """, hostId);
        return jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
    }

    private int sanctionRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_sanction", Integer.class);
    }

    private int notifications(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = ?", Integer.class, type);
    }

    private int audit(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ?", Integer.class, action);
    }
}
