package com.everystudy.study.repository;

import com.everystudy.study.domain.MemberStatus;
import com.everystudy.study.domain.StudyMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyMemberRepository extends JpaRepository<StudyMember, Long> {

    /** 참여 순서대로. 주최자도 들어 있다. */
    List<StudyMember> findByStudyIdAndStatusOrderByJoinedAtAscIdAsc(Long studyId, MemberStatus status);
}
