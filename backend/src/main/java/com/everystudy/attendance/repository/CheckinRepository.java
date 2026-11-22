package com.everystudy.attendance.repository;

import com.everystudy.attendance.domain.Checkin;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinRepository extends JpaRepository<Checkin, Long> {

    Optional<Checkin> findByStudyIdAndUserId(Long studyId, Long userId);

    List<Checkin> findByStudyId(Long studyId);
}
