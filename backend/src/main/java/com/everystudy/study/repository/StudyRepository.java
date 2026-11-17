package com.everystudy.study.repository;

import com.everystudy.study.domain.Study;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyRepository extends JpaRepository<Study, Long> {
}
