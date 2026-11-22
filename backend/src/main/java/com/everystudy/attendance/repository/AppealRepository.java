package com.everystudy.attendance.repository;

import com.everystudy.attendance.domain.Appeal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppealRepository extends JpaRepository<Appeal, Long> {

    Optional<Appeal> findByAttendanceEventId(Long attendanceEventId);

    boolean existsByAttendanceEventId(Long attendanceEventId);

    List<Appeal> findByAttendanceEventIdIn(Collection<Long> attendanceEventIds);

    /** 내 이의, 최근 것부터. {@code beforeId} 보다 id 가 작은 것만. */
    List<Appeal> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long beforeId, Pageable pageable);
}
