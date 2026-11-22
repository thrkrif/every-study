package com.everystudy.study.service;

import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.domain.Study;
import com.everystudy.study.domain.StudyStatus;
import com.everystudy.study.repository.StudyMemberRepository;
import com.everystudy.study.repository.StudyRepository;
import com.everystudy.study.repository.StudyWaitingRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴하는 회원이 들어 있는 스터디를 정리한다 (PRD 4.1 "진행 중인 스터디가 있으면 참여 취소 또는 주최자 승계 규칙이 먼저
 * 적용됨", PLAN T-062). 스터디마다 나가기와 같은 규칙({@link StudyLeaveService#leave})을 쓴다: 참여자는 사전·직전 취소,
 * 주최자는 승계·파투(시작 3시간 이내면 감점)다. 대기 행은 모두 지운다.
 *
 * <p>정리하지 않는 경우: 이미 끝났거나 파투된 스터디, 이미 시작한 스터디의 참여자(시작 뒤에는 나갈 수 없고 출석 판정으로
 * 다룬다, PRD 4.6). 시작한 스터디의 주최자는 나가기 규칙대로 승계 없이 빠진다. 스터디 번호가 작은 것부터 잠가 같은 회원의
 * 두 번 탈퇴나 다른 회원의 동시 나가기와 교착하지 않게 한다.
 */
@Service
public class StudyWithdrawalCleanup {

    private final StudyMemberRepository members;
    private final StudyRepository studies;
    private final StudyWaitingRepository waiting;
    private final StudyLeaveService leaving;
    private final TimeProvider time;

    StudyWithdrawalCleanup(StudyMemberRepository members, StudyRepository studies, StudyWaitingRepository waiting,
            StudyLeaveService leaving, TimeProvider time) {
        this.members = members;
        this.studies = studies;
        this.waiting = waiting;
        this.leaving = leaving;
        this.time = time;
    }

    /** @return 나가기 규칙을 적용한 스터디 수 */
    @Transactional(propagation = Propagation.MANDATORY)
    public int leaveAll(long userId) {
        Instant now = time.now();
        waiting.deleteByUserId(userId);
        int left = 0;
        List<Long> studyIds = members.findActiveStudyIds(userId);
        for (long studyId : studyIds) {
            Study study = studies.findById(studyId).orElse(null);
            if (study == null || study.getStatus() != StudyStatus.ACTIVE) {
                continue;
            }
            boolean host = study.getHostId().equals(userId);
            if (!host && !now.isBefore(study.getStartAt())) {
                continue;
            }
            leaving.leave(studyId, userId);
            left++;
        }
        return left;
    }
}
