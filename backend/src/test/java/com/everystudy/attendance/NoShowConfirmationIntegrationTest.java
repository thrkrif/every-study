package com.everystudy.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.attendance.service.NoShowConfirmationService;
import com.everystudy.support.IntegrationTestSupport;
import com.everystudy.support.MutableClock;
import com.everystudy.trust.service.ParticipantSanctionService;
import com.everystudy.trust.service.SanctionService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * 노쇼 확정과 참여자 제재 (PLAN T-035a, PRD 4.6·4.8). 시계를 고정하고 확정 서비스를 직접 부른다. 후보는 2026-11-23T03:30:01Z 에
 * 났고, 이의 기간 48시간은 2026-11-25T03:30:01Z 에 끝난다.
 */
@Import(NoShowConfirmationIntegrationTest.ClockConfig.class)
class NoShowConfirmationIntegrationTest extends IntegrationTestSupport {

    private static final Instant CANDIDATE_AT = Instant.parse("2026-11-23T03:30:01Z");
    private static final Instant APPEAL_DUE = CANDIDATE_AT.plus(Duration.ofHours(48));

    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock("2026-11-25T03:30:02Z");
        }
    }

    @Autowired
    Clock clock;

    @Autowired
    NoShowConfirmationService confirmation;

    @Autowired
    SanctionService sanctionService;

    @Autowired
    ParticipantSanctionService participantSanctions;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    private TransactionTemplate tx;

    private long hostId;
    private long member;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        tx = new TransactionTemplate(transactionManager);
        ((MutableClock) clock).set(APPEAL_DUE.plusSeconds(1).toString());
        hostId = user("주최자");
        member = user("참여자");
    }

    @Test
    @DisplayName("이의 기간이 끝나는 정각까지는 확정하지 않고 1초 뒤부터 확정한다. 경고 알림과 감사 로그가 남고 재실행해도 늘지 않는다")
    void confirmsAfterAppealWindowOnly() {
        long study = study();
        candidate(study, member);

        ((MutableClock) clock).set(APPEAL_DUE.toString());
        assertThat(confirmation.confirmDueNoShows()).isZero();
        assertThat(count("NO_SHOW")).isZero();

        ((MutableClock) clock).set(APPEAL_DUE.plusSeconds(1).toString());
        assertThat(confirmation.confirmDueNoShows()).isEqualTo(1);
        assertThat(confirmation.confirmDueNoShows()).isZero();

        assertThat(count("NO_SHOW")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT no_show FROM user_trust_summary WHERE user_id = ?", Integer.class,
                member)).isEqualTo(1);
        assertThat(notifications("SANCTION_WARNING")).isEqualTo(1);
        assertThat(notifications("SANCTION_APPLIED")).isZero();
        assertThat(sanctionRows()).isZero();
        assertThat(audit("NO_SHOW_CONFIRM")).isEqualTo(1);
        assertThat(audit("SANCTION_GRANT")).isZero();
    }

    @Test
    @DisplayName("열린 이의가 있는 후보는 확정하지 않고, 기각되면 확정한다")
    void openAppealBlocksConfirmation() {
        long study = study();
        long candidateId = candidate(study, member);
        appeal(candidateId, study, member, "OPEN");

        assertThat(confirmation.confirmDueNoShows()).isZero();
        assertThat(count("NO_SHOW")).isZero();

        jdbc.update("UPDATE appeal SET status = 'REJECTED' WHERE attendance_event_id = ?", candidateId);
        assertThat(confirmation.confirmDueNoShows()).isEqualTo(1);
        assertThat(count("NO_SHOW")).isEqualTo(1);
    }

    @Test
    @DisplayName("이의가 인정되어 후보가 현재 판정이 아니면 확정하지 않는다")
    void clearedCandidateIsNotConfirmed() {
        long study = study();
        candidate(study, member);
        jdbc.update("INSERT INTO attendance_event (study_id, user_id, type, occurred_at) "
                + "VALUES (?, ?, 'NO_SHOW_CLEARED', ?)", study, member, "2026-11-24 00:00:00");

        assertThat(confirmation.confirmDueNoShows()).isZero();
        assertThat(count("NO_SHOW")).isZero();
    }

    @Test
    @DisplayName("열린 이의가 한 번에 처리하는 건수보다 많이 쌓여도 뒤의 후보가 확정된다")
    void openAppealsDoNotStarveOthers() {
        for (int i = 0; i < 101; i++) {
            long study = study();
            long other = user("대기" + i);
            appeal(candidate(study, other), study, other, "OPEN");
        }
        candidate(study(), member);

        assertThat(confirmation.confirmDueNoShows()).isEqualTo(1);
        assertThat(confirmedUsers()).containsExactly(member);
    }

    @Test
    @DisplayName("최근 5회 중 환산 노쇼 2회가 되는 확정은 7일 참여 신청 제한과 안내 알림을 남기고, 재실행해도 제재는 1건이다")
    void secondNoShowRestrictsJoin() {
        noShow(study(), member, "2026-11-10 03:30:00");
        candidate(study(), member);

        assertThat(confirmation.confirmDueNoShows()).isEqualTo(1);
        assertThat(confirmation.confirmDueNoShows()).isZero();

        Map<String, Object> row = jdbc.queryForMap("SELECT kind, source, starts_at, ends_at FROM user_sanction");
        assertThat(row.get("kind")).isEqualTo("JOIN_RESTRICT");
        assertThat(row.get("source")).isEqualTo("AUTO");
        assertThat(sanctionRows()).isEqualTo(1);
        assertThat(notifications("SANCTION_APPLIED")).isEqualTo(1);
        assertThat(notifications("SANCTION_WARNING")).isZero();
        assertThat(audit("SANCTION_GRANT")).isEqualTo(1);

        Instant now = clock.instant();
        assertThat(sanctionService.joinRestrictedUntil(member, now)).contains(now.plus(Duration.ofDays(7)));
        assertThat(sanctionService.joinRestrictedUntil(member, now.plus(Duration.ofDays(7)))).isEmpty();
    }

    @Test
    @DisplayName("제한을 받은 뒤 노쇼가 한 번 더 확정되면 14일 이용 정지가 된다")
    void recurrenceSuspends() {
        noShow(study(), member, "2026-11-01 03:30:00");
        noShow(study(), member, "2026-11-02 03:30:00");
        jdbc.update("INSERT INTO user_sanction (user_id, kind, source, reason, starts_at, ends_at) "
                + "VALUES (?, 'JOIN_RESTRICT', 'AUTO', '테스트', '2026-11-02 04:00:00', '2026-11-09 04:00:00')",
                member);
        candidate(study(), member);

        assertThat(confirmation.confirmDueNoShows()).isEqualTo(1);
        assertThat(confirmation.confirmDueNoShows()).isZero();

        assertThat(jdbc.queryForList("SELECT kind FROM user_sanction ORDER BY id", String.class))
                .containsExactly("JOIN_RESTRICT", "SUSPEND");
        Instant now = clock.instant();
        assertThat(sanctionService.joinRestrictedUntil(member, now)).contains(now.plus(Duration.ofDays(14)));
        assertThat(audit("SANCTION_GRANT")).isEqualTo(1);
    }

    @Test
    @DisplayName("지각이 최근 5회 중 3회가 되면 경고 알림만 남기고 제재는 만들지 않는다. 같은 기록으로 다시 판정해도 알림은 1건이다")
    void lateWarningOnly() {
        long last = 0;
        for (int i = 1; i <= 3; i++) {
            last = study();
            jdbc.update("INSERT INTO attendance_event (study_id, user_id, type, occurred_at) "
                    + "VALUES (?, ?, 'LATE', ?)", last, member, "2026-11-0" + i + " 03:20:00");
        }
        long studyId = last;
        Instant now = clock.instant();
        tx.executeWithoutResult(status -> participantSanctions.judgeAfterLate(member, studyId, now));
        tx.executeWithoutResult(status -> participantSanctions.judgeAfterLate(member, studyId, now));

        assertThat(notifications("SANCTION_WARNING")).isEqualTo(1);
        assertThat(sanctionRows()).isZero();
    }

    @Test
    @DisplayName("지각이 2회면 알림도 제재도 없다")
    void twoLatesDoNothing() {
        long last = 0;
        for (int i = 1; i <= 2; i++) {
            last = study();
            jdbc.update("INSERT INTO attendance_event (study_id, user_id, type, occurred_at) "
                    + "VALUES (?, ?, 'LATE', ?)", last, member, "2026-11-0" + i + " 03:20:00");
        }
        long studyId = last;
        Instant now = clock.instant();
        tx.executeWithoutResult(status -> participantSanctions.judgeAfterLate(member, studyId, now));

        assertThat(notifications("SANCTION_WARNING")).isZero();
        assertThat(sanctionRows()).isZero();
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
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SEOUL', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, '2026-11-23 03:00:00', 120, '2026-11-23', 4,
                    NULL, TRUE, 'ENDED', '2026-11-20 00:00:00', '2026-11-20 00:00:00')
                """, hostId);
        return jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
    }

    /** 노쇼 후보를 후보 시각에 남기고 그 이벤트 id 를 돌려준다. */
    private long candidate(long studyId, long userId) {
        jdbc.update("INSERT INTO attendance_event (study_id, user_id, type, occurred_at) "
                + "VALUES (?, ?, 'NO_SHOW_CANDIDATE', ?)", studyId, userId, java.sql.Timestamp.from(CANDIDATE_AT));
        return jdbc.queryForObject("SELECT MAX(id) FROM attendance_event", Long.class);
    }

    /** 이미 확정된 노쇼 (이전 세션). */
    private void noShow(long studyId, long userId, String occurredAtUtc) {
        jdbc.update("INSERT INTO attendance_event (study_id, user_id, type, occurred_at) "
                + "VALUES (?, ?, 'NO_SHOW', ?)", studyId, userId, occurredAtUtc);
    }

    private void appeal(long eventId, long studyId, long userId, String status) {
        jdbc.update("""
                INSERT INTO appeal (attendance_event_id, study_id, user_id, reason, status, due_at, created_at)
                VALUES (?, ?, ?, '사정이 있었어요', ?, ?, ?)
                """, eventId, studyId, userId, status, java.sql.Timestamp.from(APPEAL_DUE),
                java.sql.Timestamp.from(CANDIDATE_AT.plusSeconds(60)));
    }

    private int count(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM attendance_event WHERE type = ?", Integer.class, type);
    }

    private List<Long> confirmedUsers() {
        return jdbc.queryForList("SELECT user_id FROM attendance_event WHERE type = 'NO_SHOW'", Long.class);
    }

    private int notifications(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = ?", Integer.class, type);
    }

    private int sanctionRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_sanction", Integer.class);
    }

    private int audit(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ?", Integer.class, action);
    }
}
