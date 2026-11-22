package com.everystudy.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.chat.service.ChatPurgeService;
import com.everystudy.study.event.StudyCancelled;
import com.everystudy.study.event.StudyEnded;
import com.everystudy.support.IntegrationTestSupport;
import com.everystudy.support.MutableClock;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 채팅방 폐쇄와 보관 만료 삭제 (PLAN T-049, PRD 4.14, PLAN D-16). 고정 시계로 보관 기간 경계를 본다. 폐쇄는 스터디 이벤트를
 * 직접 발행해 확인한다(스터디 종료·파투 자체는 각 작업의 테스트가 본다).
 */
@Import(ChatCloseAndPurgeIntegrationTest.ClockConfig.class)
class ChatCloseAndPurgeIntegrationTest extends IntegrationTestSupport {

    private static final String T0 = "2026-11-24T15:00:00Z";
    /** T0 + 7일 */
    private static final String PURGE_AT = "2026-12-01T15:00:00Z";

    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(T0);
        }
    }

    @Autowired
    Clock clock;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    ChatPurgeService purge;

    private long hostId;
    private long studyId;
    private long roomId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        at(T0);
        jdbc.update("INSERT INTO user (nickname, role, status, created_at) VALUES ('주최자', 'USER', 'ACTIVE', ?)",
                utc(T0));
        hostId = jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class);
        studyId = study("ACTIVE");
        roomId = room(studyId);
    }

    @Test
    @DisplayName("스터디가 종료되면 방이 CLOSED 가 되고 보관 기간 뒤(7일)가 삭제 시각으로 남는다. 두 번 알려도 처음 값 그대로")
    void closesOnEnded() {
        message("안녕");

        tx.executeWithoutResult(s -> publisher.publishEvent(new StudyEnded(studyId)));

        Map<String, Object> room = jdbc.queryForMap("SELECT * FROM chat_room WHERE id = ?", roomId);
        assertThat(room).containsEntry("status", "CLOSED");
        assertThat(room.get("closed_at")).isEqualTo(utc(T0));
        assertThat(room.get("purge_after")).isEqualTo(utc(PURGE_AT));

        at("2026-11-25T15:00:00Z");
        tx.executeWithoutResult(s -> publisher.publishEvent(new StudyEnded(studyId)));
        assertThat(jdbc.queryForMap("SELECT * FROM chat_room WHERE id = ?", roomId).get("closed_at"))
                .isEqualTo(utc(T0));
    }

    @Test
    @DisplayName("파투(StudyCancelled)도 정상 종료와 같은 경로로 방을 닫는다 (D-16)")
    void closesOnCancelled() {
        tx.executeWithoutResult(s -> publisher.publishEvent(new StudyCancelled(studyId, "HOST_LEFT")));

        assertThat(jdbc.queryForObject("SELECT status FROM chat_room WHERE id = ?", String.class, roomId))
                .isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("SELECT purge_after FROM chat_room WHERE id = ?", LocalDateTime.class, roomId))
                .isEqualTo(utc(PURGE_AT));
    }

    @Test
    @DisplayName("폐쇄를 일으킨 트랜잭션이 롤백되면 방은 열려 있다")
    void rollbackKeepsOpen() {
        tx.executeWithoutResult(s -> {
            publisher.publishEvent(new StudyEnded(studyId));
            s.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT status FROM chat_room WHERE id = ?", String.class, roomId))
                .isEqualTo("OPEN");
    }

    @Test
    @DisplayName("보관 기간이 정확히 끝나는 시각부터 삭제하고, 1초 전에는 삭제하지 않는다")
    void purgeBoundary() {
        message("a");
        message("b");
        notice();
        tx.executeWithoutResult(s -> publisher.publishEvent(new StudyEnded(studyId)));

        at("2026-12-01T14:59:59Z");
        assertThat(purge.purgeDueRooms()).isZero();
        assertThat(count("chat_message")).isEqualTo(2);

        at(PURGE_AT);
        assertThat(purge.purgeDueRooms()).isEqualTo(1);
        assertThat(count("chat_message")).isZero();
        assertThat(count("chat_notice")).isZero();
        assertThat(jdbc.queryForObject("SELECT purged_at FROM chat_room WHERE id = ?", LocalDateTime.class, roomId))
                .isEqualTo(utc(PURGE_AT));
    }

    @Test
    @DisplayName("두 번 실행해도 결과가 같고, 열려 있는 방의 메시지는 지우지 않는다")
    void purgeIsIdempotentAndSkipsOpenRooms() {
        long otherStudy = study("ACTIVE");
        long otherRoom = room(otherStudy);
        message("닫힌 방 메시지");
        messageIn(otherRoom, "열린 방 메시지");
        tx.executeWithoutResult(s -> publisher.publishEvent(new StudyEnded(studyId)));
        at("2026-12-10T00:00:00Z");

        assertThat(purge.purgeDueRooms()).isEqualTo(1);
        assertThat(purge.purgeDueRooms()).isZero();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_message WHERE room_id = ?", Integer.class, roomId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_message WHERE room_id = ?", Integer.class,
                otherRoom)).isEqualTo(1);
    }

    @Test
    @DisplayName("한 번에 지우는 건수에 상한이 있어 남은 건은 다음 실행이 이어서 지운다")
    void batchLimit() {
        for (int i = 0; i < 250; i++) {
            jdbc.update("INSERT INTO chat_message (room_id, sender_id, kind, content, created_at) "
                    + "VALUES (?, NULL, 'SYSTEM', 'x', ?)", roomId, utc(T0));
        }
        tx.executeWithoutResult(s -> publisher.publishEvent(new StudyEnded(studyId)));
        at("2026-12-10T00:00:00Z");

        assertThat(purge.purgeDueRooms()).isEqualTo(1);
        assertThat(count("chat_message")).isEqualTo(150);
        assertThat(jdbc.queryForObject("SELECT purged_at FROM chat_room WHERE id = ?", LocalDateTime.class, roomId))
                .isNull();

        purge.purgeDueRooms();
        purge.purgeDueRooms();
        assertThat(count("chat_message")).isZero();
        assertThat(jdbc.queryForObject("SELECT purged_at FROM chat_room WHERE id = ?", LocalDateTime.class, roomId))
                .isNotNull();
        assertThat(purge.purgeDueRooms()).isZero();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void at(String utcInstant) {
        ((MutableClock) clock).set(utcInstant);
    }

    private static LocalDateTime utc(String instant) {
        return LocalDateTime.ofInstant(Instant.parse(instant), ZoneOffset.UTC);
    }

    private long study(String status) {
        jdbc.update("""
                INSERT INTO study (host_id, kind, title, description, topic_code, district_code, place_name,
                    place_address, latitude, longitude, start_at, duration_min, session_date, capacity,
                    min_attendance_rate, allow_newcomer, status, created_at, updated_at)
                VALUES (?, 'OFFLINE', '모각코', '', 'DEVELOPMENT', 'SUWON_JANGAN', '스터디 카페',
                    '경기 수원시 장안구', 37.300000, 127.010000, ?, 120, '2026-11-24', 6, NULL, TRUE, ?, ?, ?)
                """, hostId, utc(T0), status, utc(T0), utc(T0));
        return jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
    }

    private long room(long studyId) {
        jdbc.update("INSERT INTO chat_room (study_id, status, created_at) VALUES (?, 'OPEN', ?)", studyId, utc(T0));
        return jdbc.queryForObject("SELECT MAX(id) FROM chat_room", Long.class);
    }

    private void message(String content) {
        messageIn(roomId, content);
    }

    private void messageIn(long room, String content) {
        jdbc.update("INSERT INTO chat_message (room_id, sender_id, kind, content, created_at) "
                + "VALUES (?, ?, 'TEXT', ?, ?)", room, hostId, content, utc(T0));
    }

    private void notice() {
        jdbc.update("INSERT INTO chat_notice (room_id, content, updated_by, updated_at) VALUES (?, '공지', ?, ?)",
                roomId, hostId, utc(T0));
    }
}
