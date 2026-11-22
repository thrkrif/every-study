package com.everystudy.chat.repository;

import com.everystudy.chat.domain.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 방의 메시지, 최근 것부터. id 가 {@code beforeId} 보다 작은 것만 (이전 대화 커서, API 6장). */
    @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId AND m.id < :beforeId ORDER BY m.id DESC")
    List<ChatMessage> findBefore(@Param("roomId") long roomId, @Param("beforeId") long beforeId, Pageable pageable);

    @Query("SELECT COALESCE(MAX(m.id), 0) FROM ChatMessage m WHERE m.roomId = :roomId")
    long latestId(@Param("roomId") long roomId);
}
