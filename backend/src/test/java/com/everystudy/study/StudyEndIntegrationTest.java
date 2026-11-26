package com.everystudy.study;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.auth.service.RefreshTokenCleanupService;
import com.everystudy.study.service.StudyEndService;
import com.everystudy.support.IntegrationTestSupport;
import com.everystudy.support.MutableClock;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** 스터디 종료 처리와 토큰 정리 (PLAN T-030, PRD 4.14). 시계를 고정하고 서비스를 직접 부른다. */
@Import(StudyEndIntegrationTest.ClockConfig.class)
class StudyEndIntegrationTest extends IntegrationTestSupport {

    /** 세션 날짜 2026-11-23(KST) 다음날 00:00 KST = 2026-11-23T15:00:00Z */
    private static final String MIDNIGHT = "2026-11-23T15:00:00Z";

    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(MIDNIGHT);
        }
    }

    @Autowired
    Clock clock;

    @Autowired
    StudyEndService endService;

    @Autowired
    RefreshTokenCleanupService cleanup;

    @Autowired
    JdbcTemplate jdbc;

    private long hostId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        ((MutableClock) clock).set(MIDNIGHT);
        jdbc.update("INSERT INTO user (nickname, role, status, created_at) VALUES ('주최자', 'USER', 'ACTIVE', NOW(6))");
        hostId = jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class);
    }

    @Test
    @DisplayName("세션 날짜 다음날 00:00(KST) 직전에는 종료하지 않고, 정각부터 종료한다. 대기 행도 지운다")
    void endsAtKoreanMidnight() {
        long id = study("2026-11-23", "ACTIVE");
        waiter(id);

        ((MutableClock) clock).set("2026-11-23T14:59:59Z");
        assertThat(endService.endDueStudies()).isZero();
        assertThat(status(id)).isEqualTo("ACTIVE");
        assertThat(waitingCount(id)).isEqualTo(1);

        ((MutableClock) clock).set(MIDNIGHT);
        assertThat(endService.endDueStudies()).isEqualTo(1);
        assertThat(status(id)).isEqualTo("ENDED");
        assertThat(waitingCount(id)).isZero();
    }

    @Test
    @DisplayName("두 번 실행해도 결과가 같고, 이미 끝났거나 파투된 스터디는 건드리지 않는다")
    void idempotent() {
        long active = study("2026-11-22", "ACTIVE");
        long cancelled = study("2026-11-22", "CANCELLED");
        long ongoing = study("2026-11-24", "ACTIVE");

        assertThat(endService.endDueStudies()).isEqualTo(1);
        assertThat(endService.endDueStudies()).isZero();

        assertThat(status(active)).isEqualTo("ENDED");
        assertThat(status(cancelled)).isEqualTo("CANCELLED");
        assertThat(status(ongoing)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("만료 시각이 된 리프레시 토큰만 지우고, 만료 직전 토큰은 남긴다")
    void cleansExpiredTokens() {
        token("expired", "2026-11-23T14:59:59Z");
        token("boundary", MIDNIGHT);
        token("alive", "2026-11-23T15:00:01Z");

        assertThat(cleanup.deleteExpired()).isEqualTo(2);
        assertThat(cleanup.deleteExpired()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT family_id FROM refresh_token", String.class)).isEqualTo(family("alive"));
    }

    private long study(String sessionDate, String status) {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SEOUL', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, '2026-11-20 03:00:00', 120, ?, 4,
                    NULL, TRUE, ?, '2026-11-20 00:00:00', '2026-11-20 00:00:00')
                """, hostId, sessionDate, status);
        return jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
    }

    private void waiter(long studyId) {
        jdbc.update("INSERT INTO study_waiting (study_id, user_id, created_at) VALUES (?, ?, '2026-11-20 00:00:00')",
                studyId, hostId);
    }

    private void token(String name, String expiresAtUtc) {
        jdbc.update("""
                INSERT INTO refresh_token (user_id, family_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, hostId, family(name), ("h" + name + "0".repeat(64)).substring(0, 64),
                expiresAtUtc.replace("T", " ").replace("Z", ""));
    }

    /** 36자 CHAR 계열 id 를 이름에서 만든다 */
    private static String family(String name) {
        return (name + "-".repeat(36)).substring(0, 36);
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM study WHERE id = ?", String.class, id);
    }

    private int waitingCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM study_waiting WHERE study_id = ?", Integer.class, id);
    }
}
