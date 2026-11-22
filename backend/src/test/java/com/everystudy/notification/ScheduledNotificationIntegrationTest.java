package com.everystudy.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.event.MemberJoined;
import com.everystudy.study.event.MemberLeft;
import com.everystudy.study.event.StudyCancelled;
import com.everystudy.study.event.StudyCreated;
import com.everystudy.study.service.StudyUpdateService;
import com.everystudy.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 일정 알림 예약과 취소 (PLAN T-040, PRD 4.5). 참여 확정 이벤트에서 예약하고 나가기·파투에서 취소한다. */
class ScheduledNotificationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    StudyUpdateService updates;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    private TransactionTemplate tx;
    private long hostId;
    private long memberId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        tx = new TransactionTemplate(transactionManager);
        hostId = user("주최자");
        memberId = user("참여자");
    }

    @Test
    @DisplayName("참여가 확정되면 전날·1시간 전·체크인 오픈 알림을 예약하고, 알림 센터에는 아직 보이지 않는다. 같은 이벤트를 다시 처리해도 늘지 않는다")
    void schedulesThreeNotifications() {
        long id = study(inHours(72));

        publish(new MemberJoined(id, memberId));
        publish(new MemberJoined(id, memberId));

        assertThat(types(id, memberId)).containsExactlyInAnyOrder("CHECKIN_REMINDER_DAY", "CHECKIN_REMINDER_HOUR",
                "CHECKIN_OPEN");
        assertThat(secondsBeforeStart("CHECKIN_REMINDER_DAY", id)).isEqualTo(24 * 3600);
        assertThat(secondsBeforeStart("CHECKIN_REMINDER_HOUR", id)).isEqualTo(3600);
        assertThat(secondsBeforeStart("CHECKIN_OPEN", id)).isEqualTo(600);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE visible_at <= UTC_TIMESTAMP(6)",
                Integer.class)).isZero();
        // 알림마다 유효 기한: 전날 → 1시간 전 시각, 1시간 전 → 체크인 오픈, 체크인 오픈 → 체크인 마감 (PLAN D-18)
        assertThat(expiresAfterStartSeconds("CHECKIN_REMINDER_DAY", id)).isEqualTo(-3600);
        assertThat(expiresAfterStartSeconds("CHECKIN_REMINDER_HOUR", id)).isEqualTo(-600);
        assertThat(expiresAfterStartSeconds("CHECKIN_OPEN", id)).isEqualTo(30 * 60);
    }

    @Test
    @DisplayName("이미 지난 시점은 예약하지 않는다")
    void skipsPastTimes() {
        long soon = study(Instant.now().plus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.MINUTES));
        publish(new MemberJoined(soon, memberId));

        assertThat(types(soon, memberId)).containsExactly("CHECKIN_OPEN");

        long afterOpen = study(Instant.now().plus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MINUTES));
        publish(new MemberJoined(afterOpen, memberId));
        assertThat(types(afterOpen, memberId)).isEmpty();
    }

    @Test
    @DisplayName("개설하면 주최자 몫을 예약한다")
    void hostIsScheduledOnCreate() {
        long id = study(inHours(72));
        publish(new StudyCreated(id, hostId));

        assertThat(types(id, hostId)).hasSize(3);
    }

    @Test
    @DisplayName("나가면 그 사람의 미발송 알림만 취소되고, 다시 들어오면 다시 예약한다")
    void leaveCancelsOnlyThatUser() {
        long id = study(inHours(72));
        publish(new StudyCreated(id, hostId));
        publish(new MemberJoined(id, memberId));

        publish(new MemberLeft(id, memberId));
        assertThat(types(id, memberId)).isEmpty();
        assertThat(types(id, hostId)).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE status = 'PENDING'",
                Integer.class)).isEqualTo(6);

        publish(new MemberJoined(id, memberId));
        assertThat(types(id, memberId)).hasSize(3);
    }

    @Test
    @DisplayName("파투되면 그 스터디의 미발송 일정 알림이 모두 취소되고 다른 스터디는 그대로다. 이미 나간 알림은 남는다")
    void cancelRemovesUnsentOnly() {
        long id = study(inHours(72));
        long other = study(inHours(72));
        publish(new MemberJoined(id, memberId));
        publish(new MemberJoined(other, memberId));
        // 하루 전 알림은 이미 나갔다고 본다 (예정 시각이 지났고 발송 완료)
        jdbc.update("UPDATE notification SET visible_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 HOUR) "
                + "WHERE study_id = ? AND type = 'CHECKIN_REMINDER_DAY'", id);
        jdbc.update("UPDATE notification_outbox o JOIN notification n ON n.id = o.notification_id "
                + "SET o.status = 'SENT', o.scheduled_at = n.visible_at WHERE n.study_id = ? "
                + "AND n.type = 'CHECKIN_REMINDER_DAY'", id);

        publish(new StudyCancelled(id, "HOST_LEFT"));

        assertThat(types(id, memberId)).containsExactly("CHECKIN_REMINDER_DAY");
        assertThat(types(other, memberId)).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE status = 'PENDING'",
                Integer.class)).isEqualTo(6);
    }

    @Test
    @DisplayName("주최자 혼자일 때 일정을 바꾸면 예약을 새 시각으로 다시 만든다")
    void rescheduleRecreates() {
        Instant start = inHours(72);
        long id = study(start);
        publish(new StudyCreated(id, hostId));

        Instant newStart = start.plus(Duration.ofDays(1));
        tx.executeWithoutResult(status -> updates.update(id, hostId,
                new StudyUpdateService.Changes(null, null, newStart, null, null)));

        assertThat(types(id, hostId)).hasSize(3);
        assertThat(secondsBeforeStart("CHECKIN_OPEN", id)).isEqualTo(600);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox", Integer.class)).isEqualTo(6);
    }

    private void publish(Object event) {
        tx.executeWithoutResult(status -> events.publishEvent(event));
    }

    private static Instant inHours(int hours) {
        return Instant.now().plus(Duration.ofHours(hours)).truncatedTo(ChronoUnit.MINUTES)
                .minusSeconds(Instant.now().atZone(ZoneOffset.UTC).getMinute() % 10 * 60L);
    }

    private long user(String nickname) {
        jdbc.update("INSERT INTO user (nickname, role, status, created_at) VALUES (?, 'USER', 'ACTIVE', NOW(6))",
                nickname);
        return jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class);
    }

    private long study(Instant startAt) {
        LocalDateTime start = LocalDateTime.ofInstant(startAt, ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SUWON_JANGAN', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, ?, 120, ?, 4, NULL, TRUE, 'ACTIVE', NOW(6), NOW(6))
                """, hostId, start, startAt.atZone(TimeProvider.KOREA).toLocalDate());
        long id = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, 'ACTIVE', NOW(6))",
                id, hostId);
        return id;
    }

    private List<String> types(long studyId, long userId) {
        return jdbc.queryForList("SELECT type FROM notification WHERE study_id = ? AND user_id = ? ORDER BY id",
                String.class, studyId, userId);
    }

    private long secondsBeforeStart(String type, long studyId) {
        return jdbc.queryForObject("""
                SELECT TIMESTAMPDIFF(SECOND, MAX(n.visible_at), s.start_at) FROM notification n
                JOIN study s ON s.id = n.study_id WHERE n.study_id = ? AND n.type = ? GROUP BY s.start_at
                """, Long.class, studyId, type);
    }

    private long expiresAfterStartSeconds(String type, long studyId) {
        return jdbc.queryForObject("""
                SELECT TIMESTAMPDIFF(SECOND, s.start_at, MAX(o.expires_at)) FROM notification n
                JOIN notification_outbox o ON o.notification_id = n.id
                JOIN study s ON s.id = n.study_id WHERE n.study_id = ? AND n.type = ? GROUP BY s.start_at
                """, Long.class, studyId, type);
    }
}
