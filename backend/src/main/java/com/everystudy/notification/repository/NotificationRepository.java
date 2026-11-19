package com.everystudy.notification.repository;

import com.everystudy.notification.domain.Notification;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    /** 알림 센터 첫 페이지. 보이는 시각이 지난 것만, 최근에 보인 순서로. */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId AND n.visibleAt <= :now
            ORDER BY n.visibleAt DESC, n.id DESC
            """)
    List<Notification> findVisible(@Param("userId") long userId, @Param("now") Instant now, Limit limit);

    /** 알림 센터 다음 페이지. 직전 페이지 마지막 항목 ({@code afterVisibleAt}, {@code afterId}) 뒤부터. */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId AND n.visibleAt <= :now
              AND (n.visibleAt < :afterVisibleAt OR (n.visibleAt = :afterVisibleAt AND n.id < :afterId))
            ORDER BY n.visibleAt DESC, n.id DESC
            """)
    List<Notification> findVisibleAfter(@Param("userId") long userId, @Param("now") Instant now,
            @Param("afterVisibleAt") Instant afterVisibleAt, @Param("afterId") long afterId, Limit limit);

    /** 보이는 알림 중 안 읽은 것을 모두 읽음으로. 아직 보이지 않는 예약 알림은 건드리지 않는다. */
    @Modifying
    @Query("""
            UPDATE Notification n SET n.readAt = :now
            WHERE n.userId = :userId AND n.readAt IS NULL AND n.visibleAt <= :now
            """)
    int markAllVisibleRead(@Param("userId") long userId, @Param("now") Instant now);
}
