package com.everystudy.trust.repository;

import com.everystudy.trust.domain.AttendanceEvent;
import com.everystudy.trust.domain.AttendanceEventType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, Long> {

    boolean existsByStudyIdAndUserIdAndType(Long studyId, Long userId, AttendanceEventType type);

    /** 한 사용자의 모든 이벤트, 최근 것부터. 발생 시각이 같으면 나중에 쌓인 것이 앞이다. */
    List<AttendanceEvent> findByUserIdOrderByOccurredAtDescIdDesc(Long userId);

    /** (스터디, 사용자)의 현재 판정: 가장 최근 이벤트. 발생 시각이 같으면 나중에 쌓인 것이다. */
    Optional<AttendanceEvent> findFirstByStudyIdAndUserIdOrderByOccurredAtDescIdDesc(Long studyId, Long userId);

    /** 사용자의 스터디별 현재 판정, id 가 {@code beforeId} 보다 작은 것을 큰 것부터. */
    @Query("""
            select e from AttendanceEvent e
            where e.userId = :userId and e.id < :beforeId
              and not exists (select 1 from AttendanceEvent n
                              where n.studyId = e.studyId and n.userId = e.userId
                                and (n.occurredAt > e.occurredAt or (n.occurredAt = e.occurredAt and n.id > e.id)))
            order by e.id desc
            """)
    List<AttendanceEvent> findCurrentVerdicts(@Param("userId") Long userId, @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("select distinct e.userId from AttendanceEvent e order by e.userId")
    List<Long> findAllUserIds();
}
