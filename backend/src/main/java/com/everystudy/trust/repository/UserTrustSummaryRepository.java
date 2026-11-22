package com.everystudy.trust.repository;

import com.everystudy.trust.domain.UserTrustSummary;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTrustSummaryRepository extends JpaRepository<UserTrustSummary, Long> {

    /**
     * 집계 행을 넣거나 덮어쓴다. 같은 사용자의 첫 집계가 동시에 들어와도 한쪽이 키 중복으로 실패해 호출한 쪽 트랜잭션
     * (체크인 등)을 깨뜨리지 않도록, 조회 후 저장이 아니라 한 문장으로 처리한다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO user_trust_summary (user_id, counted, attended, late, no_show, late_cancel, rate,
                recalculated_at)
            VALUES (:userId, :counted, :attended, :late, :noShow, :lateCancel, :rate, :recalculatedAt)
            ON DUPLICATE KEY UPDATE counted = :counted, attended = :attended, late = :late, no_show = :noShow,
                late_cancel = :lateCancel, rate = :rate, recalculated_at = :recalculatedAt
            """, nativeQuery = true)
    void upsert(@Param("userId") long userId, @Param("counted") int counted, @Param("attended") int attended,
            @Param("late") int late, @Param("noShow") int noShow, @Param("lateCancel") int lateCancel,
            @Param("rate") BigDecimal rate, @Param("recalculatedAt") Instant recalculatedAt);
}
