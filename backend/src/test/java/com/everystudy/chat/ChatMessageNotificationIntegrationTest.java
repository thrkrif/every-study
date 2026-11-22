package com.everystudy.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.everystudy.chat.service.ChatMessageService;
import com.everystudy.support.IntegrationTestSupport;
import com.everystudy.support.MutableClock;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 채팅 새 메시지 알림 (PLAN T-050, D-12). 방을 열어 둔(구독 중인) 사용자 제외는 STOMP 연결이 필요해 여기서 다루지 않는다.
 * 시계는 5분 시간 창의 한가운데로 고정해 창 경계에 걸리지 않게 한다.
 */
@Import(ChatMessageNotificationIntegrationTest.ClockConfig.class)
class ChatMessageNotificationIntegrationTest extends IntegrationTestSupport {

    private static final String T0 = "2026-11-23T03:02:30Z";
    private static final String NEXT_WINDOW = "2026-11-23T03:07:30Z";

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
    ChatMessageService messages;

    @Autowired
    JdbcTemplate jdbc;

    private long host;
    private long memberA;
    private long memberB;
    private long leftMember;
    private long studyId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        ((MutableClock) clock).set(T0);
        host = user("주최자");
        memberA = user("참여자A");
        memberB = user("참여자B");
        leftMember = user("나간사람");
        studyId = study();
        member(host, "ACTIVE");
        member(memberA, "ACTIVE");
        member(memberB, "ACTIVE");
        member(leftMember, "LEFT");
    }

    @Test
    @DisplayName("보낸 사람 본인과 나간 사람을 뺀 참여자에게 푸시 알림 한 건씩만 기록하고 이메일은 만들지 않는다")
    void notifiesOtherActiveMembersByPushOnly() {
        messages.send(studyId, host, "안녕하세요");

        assertThat(recipients()).containsExactlyInAnyOrder(memberA, memberB);
        assertThat(jdbc.queryForList("SELECT DISTINCT channel FROM notification_outbox", String.class))
                .containsExactly("PUSH");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'CHAT_MESSAGE'"
                + " AND body NOT LIKE '%안녕하세요%'", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 시간 창의 메시지는 한 건으로 묶이고, 다음 창의 메시지는 새 알림이다")
    void groupsWithinWindow() {
        messages.send(studyId, host, "하나");
        messages.send(studyId, host, "둘");
        messages.send(studyId, memberA, "셋");

        // host 는 첫 메시지의 보낸 사람이라 알림이 없다가, memberA 의 메시지로 처음 받는다.
        assertThat(count(memberA)).isEqualTo(1);
        assertThat(count(memberB)).isEqualTo(1);
        assertThat(count(host)).isEqualTo(1);

        ((MutableClock) clock).set(NEXT_WINDOW);
        messages.send(studyId, host, "넷");

        assertThat(count(memberA)).isEqualTo(2);
        assertThat(count(memberB)).isEqualTo(2);
        assertThat(count(host)).isEqualTo(1);
    }

    @Test
    @DisplayName("보낸 사람을 차단한 참여자에게는 알리지 않는다")
    void skipsRecipientsWhoBlockedSender() {
        jdbc.update("INSERT INTO block (blocker_id, blocked_id, created_at) VALUES (?, ?, NOW(6))", memberA, host);

        messages.send(studyId, host, "안녕하세요");

        assertThat(recipients()).containsExactly(memberB);
    }

    private List<Long> recipients() {
        return jdbc.queryForList("SELECT user_id FROM notification WHERE type = 'CHAT_MESSAGE'", Long.class);
    }

    private int count(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'CHAT_MESSAGE' AND user_id = ?",
                Integer.class, userId);
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
                    '경기 수원시 장안구', 37.300000, 127.010000, '2026-11-24 03:00:00', 120, '2026-11-24', 6,
                    NULL, TRUE, 'ACTIVE', NOW(6), NOW(6))
                """, host);
        long id = jdbc.queryForObject("SELECT MAX(id) FROM study", Long.class);
        jdbc.update("INSERT INTO chat_room (study_id, status, created_at) VALUES (?, 'OPEN', NOW(6))", id);
        return id;
    }

    private void member(long userId, String status) {
        jdbc.update("INSERT INTO study_member (study_id, user_id, status, joined_at) VALUES (?, ?, ?, NOW(6))",
                studyId, userId, status);
    }
}
