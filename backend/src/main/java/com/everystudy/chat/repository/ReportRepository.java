package com.everystudy.chat.repository;

import com.everystudy.chat.domain.Report;
import com.everystudy.chat.domain.ReportStatus;
import com.everystudy.chat.domain.ReportTargetType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(Long reporterId, ReportTargetType targetType,
            Long targetId, ReportStatus status);

    /** 처리할 신고를 잠그고 읽는다. 두 운영자가 같은 신고를 동시에 처리하지 못하게 한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r WHERE r.id = :id")
    Optional<Report> findByIdForUpdate(@Param("id") long id);

    /** 상태별 신고 큐, 최근 신고부터. */
    List<Report> findByStatusAndIdLessThanOrderByIdDesc(ReportStatus status, Long beforeId, Pageable pageable);

    /** 모든 상태, 최근 신고부터. */
    List<Report> findByIdLessThanOrderByIdDesc(Long beforeId, Pageable pageable);
}
