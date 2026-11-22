package com.everystudy.study.repository;

import com.everystudy.study.domain.MemberStatus;
import com.everystudy.study.domain.StudyMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyMemberRepository extends JpaRepository<StudyMember, Long> {

    /** 참여 순서대로. 주최자도 들어 있다. */
    List<StudyMember> findByStudyIdAndStatusOrderByJoinedAtAscIdAsc(Long studyId, MemberStatus status);

    long countByStudyIdAndStatus(Long studyId, MemberStatus status);

    /** 나간 행도 찾는다. 같은 사람은 한 스터디에 한 행이다. */
    Optional<StudyMember> findByStudyIdAndUserId(Long studyId, Long userId);

    /** 회원이 {@code ACTIVE} 인 스터디 번호, 작은 것부터. 탈퇴할 때 스터디마다 잠금을 같은 순서로 잡으려고 정렬한다. */
    @org.springframework.data.jpa.repository.Query("select m.studyId from StudyMember m where m.userId = :userId and m.status = com.everystudy.study.domain.MemberStatus.ACTIVE order by m.studyId")
    List<Long> findActiveStudyIds(@org.springframework.data.repository.query.Param("userId") Long userId);
}
