package com.everystudy.study.repository;

import com.everystudy.study.domain.StudyMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyMemberRepository extends JpaRepository<StudyMember, Long> {
}
