package com.everystudy.study.repository;

import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyRepository extends JpaRepository<Study, Long>, StudyLockRepository {

    /** 종료 처리 대상: 세션 날짜가 {@code date} 보다 앞선 해당 상태의 스터디 id. 오래된 것부터. */
    @Query("select s.id from Study s where s.status = :status and s.sessionDate < :date order by s.sessionDate, s.id")
    List<Long> findIdsByStatusAndSessionDateBefore(@Param("status") StudyStatus status, @Param("date") LocalDate date,
            Pageable pageable);

    /** 판정 대상: 판정하지 않았고 시작이 {@code startedBefore} 보다 앞선 스터디 id (상태는 가리지 않는다). 오래된 것부터. */
    @Query("select s.id from Study s where s.judgedAt is null and s.startAt < :startedBefore order by s.startAt, s.id")
    List<Long> findUnjudgedIdsStartedBefore(@Param("startedBefore") Instant startedBefore, Pageable pageable);
}
