package com.everystudy.common.audit;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 기록과 운영자 조회 전용. 수정·삭제 API 는 만들지 않는다. */
interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** 최근 것부터. {@code beforeId} 보다 id 가 작은 것만, {@code action}·{@code targetType} 은 주어졌을 때만 거른다. */
    @Query("""
            SELECT l FROM AuditLog l
            WHERE l.id < :beforeId
              AND (:action IS NULL OR l.action = :action)
              AND (:targetType IS NULL OR l.targetType = :targetType)
            ORDER BY l.id DESC
            """)
    List<AuditLog> findPage(@Param("beforeId") long beforeId, @Param("action") String action,
            @Param("targetType") String targetType, Limit limit);
}
