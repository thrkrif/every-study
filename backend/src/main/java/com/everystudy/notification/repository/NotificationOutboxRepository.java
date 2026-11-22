package com.everystudy.notification.repository;

import com.everystudy.notification.domain.NotificationOutbox;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    /**
     * 워커가 처리할 건: {@code PENDING} 이고 시도 시각이 지났다. 보낼 채널이 있거나(채널 목록), 유효 기한이 지나 취소해야
     * 하는 건만 읽는다. 보낼 구현이 없는 채널의 건이 매번 배치를 채워 다른 건을 막지 않게 하려는 것이다. 시도 시각이 이른
     * 것부터.
     */
    @Query("""
            SELECT o.id FROM NotificationOutbox o
            WHERE o.status = com.everystudy.notification.domain.OutboxStatus.PENDING
              AND o.nextAttemptAt <= :now
              AND (o.channel IN :channels OR o.expiresAt < :now)
            ORDER BY o.nextAttemptAt, o.id
            """)
    List<Long> findDueIds(@Param("now") Instant now, @Param("channels") Collection<String> channels, Limit limit);

    /** 한 건을 잠그고 읽는다. 호출한 트랜잭션이 끝날 때까지 다른 워커가 같은 건을 보내지 못한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM NotificationOutbox o WHERE o.id = :id")
    Optional<NotificationOutbox> findByIdForUpdate(@Param("id") long id);
}
