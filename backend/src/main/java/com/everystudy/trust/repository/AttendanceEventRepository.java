package com.everystudy.trust.repository;

import com.everystudy.trust.domain.AttendanceEvent;
import com.everystudy.trust.domain.AttendanceEventType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, Long> {

    boolean existsByStudyIdAndUserIdAndType(Long studyId, Long userId, AttendanceEventType type);

    /** 한 사용자의 모든 이벤트, 최근 것부터. 발생 시각이 같으면 나중에 쌓인 것이 앞이다. */
    List<AttendanceEvent> findByUserIdOrderByOccurredAtDescIdDesc(Long userId);

    @Query("select distinct e.userId from AttendanceEvent e order by e.userId")
    List<Long> findAllUserIds();
}
