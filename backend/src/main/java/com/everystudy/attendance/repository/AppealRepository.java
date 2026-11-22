package com.everystudy.attendance.repository;

import com.everystudy.attendance.domain.Appeal;
import com.everystudy.attendance.domain.AppealStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppealRepository extends JpaRepository<Appeal, Long> {

    Optional<Appeal> findByAttendanceEventId(Long attendanceEventId);

    boolean existsByAttendanceEventId(Long attendanceEventId);

    boolean existsByAttendanceEventIdAndStatus(Long attendanceEventId, AppealStatus status);

    List<Appeal> findByAttendanceEventIdIn(Collection<Long> attendanceEventIds);

    /** 한 스터디의 이의, 최근 신청부터. 주최자 화면에 쓴다. */
    List<Appeal> findByStudyIdOrderByIdDesc(Long studyId);

    /** 내 이의, 최근 것부터. {@code beforeId} 보다 id 가 작은 것만. */
    List<Appeal> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long beforeId, Pageable pageable);

    /** 사용자의 기각된 이의 결정 시각 중 {@code since} 이후의 것, 최근 것부터. */
    @Query("SELECT a.decidedAt FROM Appeal a WHERE a.userId = :userId AND a.status = "
            + "com.everystudy.attendance.domain.AppealStatus.REJECTED AND a.decidedAt > :since ORDER BY a.decidedAt DESC")
    List<Instant> findRejectedDecisionTimes(@Param("userId") long userId, @Param("since") Instant since);

    /** 결정할 이의를 잠그고 읽는다. 두 운영자가 같은 이의를 동시에 결정하지 못하게 한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appeal a WHERE a.id = :id")
    Optional<Appeal> findByIdForUpdate(@Param("id") long id);

    /** 운영자 목록: 상태별, 최근 신청부터. */
    List<Appeal> findByStatusAndIdLessThanOrderByIdDesc(AppealStatus status, Long beforeId, Pageable pageable);

    /** 운영자 목록: 모든 상태, 최근 신청부터. */
    List<Appeal> findByIdLessThanOrderByIdDesc(Long beforeId, Pageable pageable);
}
