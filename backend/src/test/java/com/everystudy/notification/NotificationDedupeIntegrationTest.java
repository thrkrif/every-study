package com.everystudy.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.notification.service.NotificationRecorder;
import com.everystudy.notification.service.NotificationRequest;
import com.everystudy.notification.service.NotificationType;
import com.everystudy.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 종류마다 같은 원인으로 두 번 기록해도 알림은 한 건, 채널별 아웃박스는 채널마다 한 행인지 본다 (PLAN T-039). 키는
 * 각 생성 지점이 쓰는 형태 그대로다(API 8장).
 */
class NotificationDedupeIntegrationTest extends IntegrationTestSupport {

    @Autowired
    NotificationRecorder recorder;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private long userId;
    private long studyId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        jdbc.update("INSERT INTO user (nickname, role, status, created_at) VALUES ('받는이', 'USER', 'ACTIVE', NOW(6))");
        userId = jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class);
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SUWON_JANGAN', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, '2026-11-24 03:00:00', 120, '2026-11-24', 4,
                    NULL, TRUE, 'ACTIVE', NOW(6), NOW(6))
                """, userId);
        studyId = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(NotificationType.class)
    @DisplayName("같은 원인의 알림을 두 번 기록해도 알림 1건, 채널별 아웃박스 1행이다")
    void recordingTwiceKeepsOne(NotificationType type) {
        Instant now = Instant.now();
        String key = type.name() + ":" + studyId + ":" + userId;
        NotificationRequest request = NotificationRequest.allChannels(userId, type, studyId, "제목", "내용", key, now,
                now.plus(Duration.ofHours(1)));

        boolean first = Boolean.TRUE.equals(tx.execute(status -> recorder.record(request)));
        boolean second = Boolean.TRUE.equals(tx.execute(status -> recorder.record(request)));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = ?", Integer.class, type.name()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox", Integer.class)).isEqualTo(2);
    }
}
