package com.everystudy.chat.repository;

import com.everystudy.chat.domain.ChatRoom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByStudyId(Long studyId);

    /** 보관 기간이 지났고 메시지 삭제를 끝내지 않은 방, 오래된 것부터. */
    @Query("""
            SELECT r.id FROM ChatRoom r
            WHERE r.status = com.everystudy.chat.domain.ChatRoomStatus.CLOSED
              AND r.purgeAfter <= :now AND r.purgedAt IS NULL
            ORDER BY r.purgeAfter, r.id
            """)
    List<Long> findPurgeDueIds(@Param("now") Instant now, Limit limit);
}
