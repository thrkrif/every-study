package com.everystudy.trust.repository;

import com.everystudy.trust.domain.AttendanceEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, Long> {
}
