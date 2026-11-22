package com.everystudy.attendance.repository;

import com.everystudy.attendance.domain.CheckinCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinCodeRepository extends JpaRepository<CheckinCode, Long> {
}
