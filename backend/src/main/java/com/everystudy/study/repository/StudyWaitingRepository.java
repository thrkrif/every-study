package com.everystudy.study.repository;

import com.everystudy.study.domain.StudyWaiting;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyWaitingRepository extends JpaRepository<StudyWaiting, StudyWaiting.Key> {

    List<StudyWaiting> findByStudyId(Long studyId);

    long countByStudyId(Long studyId);

    boolean existsByStudyIdAndUserId(Long studyId, Long userId);

    void deleteByStudyIdAndUserId(Long studyId, Long userId);

    /** 스터디의 대기 행을 모두 지운다 (파투·종료 처리). */
    void deleteByStudyId(Long studyId);
}
