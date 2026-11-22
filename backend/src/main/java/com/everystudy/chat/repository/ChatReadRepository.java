package com.everystudy.chat.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 읽음 위치와 차단처럼 한 문장으로 처리하는 질의. 같은 키가 동시에 들어와도 호출한 쪽 트랜잭션이 깨지지 않게 조회 후 저장이
 * 아니라 {@code ON DUPLICATE KEY} 로 쓴다.
 */
@Repository
public class ChatReadRepository {

    private final JdbcClient jdbc;

    ChatReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 읽음 위치를 올린다. 이미 더 큰 값이 있으면 그대로 둔다 (되감기 금지). */
    public void advance(long roomId, long userId, long lastReadMessageId) {
        jdbc.sql("""
                INSERT INTO chat_read_state (room_id, user_id, last_read_message_id)
                VALUES (:roomId, :userId, :last)
                ON DUPLICATE KEY UPDATE last_read_message_id = GREATEST(last_read_message_id, :last)
                """).param("roomId", roomId).param("userId", userId).param("last", lastReadMessageId).update();
    }

    /**
     * 읽지 않은 메시지 수: 읽음 위치보다 뒤이고, 지워지지 않았고, 내가 보낸 것도 내가 차단한 사람이 보낸 것도 아닌 메시지.
     * 시스템 메시지는 센다.
     */
    public long unreadCount(long roomId, long userId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM chat_message m
                WHERE m.room_id = :roomId
                  AND m.id > COALESCE((SELECT s.last_read_message_id FROM chat_read_state s
                                       WHERE s.room_id = :roomId AND s.user_id = :userId), 0)
                  AND m.deleted_at IS NULL
                  AND (m.sender_id IS NULL
                       OR (m.sender_id <> :userId
                           AND m.sender_id NOT IN (SELECT b.blocked_id FROM block b WHERE b.blocker_id = :userId)))
                """).param("roomId", roomId).param("userId", userId).query(Long.class).single();
    }

    /** {@code candidates} 중 {@code blockerId} 가 차단한 사용자. */
    public Set<Long> blockedAmong(long blockerId, Collection<Long> candidates) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("SELECT blocked_id FROM block WHERE blocker_id = :blocker AND blocked_id IN (:ids)")
                .param("blocker", blockerId).param("ids", candidates).query(Long.class).list());
    }

    /** 차단한다. 이미 차단했으면 아무 일도 없다 (멱등). */
    public void block(long blockerId, long blockedId, Instant now) {
        jdbc.sql("INSERT IGNORE INTO block (blocker_id, blocked_id, created_at) VALUES (:blocker, :blocked, :now)")
                .param("blocker", blockerId).param("blocked", blockedId).param("now", now).update();
    }

    /** 차단을 푼다. 차단하지 않았어도 아무 일도 없다 (멱등). */
    public void unblock(long blockerId, long blockedId) {
        jdbc.sql("DELETE FROM block WHERE blocker_id = :blocker AND blocked_id = :blocked")
                .param("blocker", blockerId).param("blocked", blockedId).update();
    }
}
