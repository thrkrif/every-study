package com.everystudy.study.repository;

import com.everystudy.study.domain.StudyWaiting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyWaitingRepository extends JpaRepository<StudyWaiting, StudyWaiting.Key> {

    long countByStudyId(Long studyId);

    boolean existsByStudyIdAndUserId(Long studyId, Long userId);

    void deleteByStudyIdAndUserId(Long studyId, Long userId);
}
