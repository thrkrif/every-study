package com.everystudy.trust.repository;

import com.everystudy.trust.domain.AttendanceEvent;
import com.everystudy.trust.domain.AttendanceEventType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, Long> {

    boolean existsByStudyIdAndUserIdAndType(Long studyId, Long userId, AttendanceEventType type);
}
