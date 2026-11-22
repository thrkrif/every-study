package com.everystudy.study.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 스터디 참여자 (DATA-MODEL 4장). 주최자도 {@code ACTIVE} 참여자로 들어간다.
 * 정원 계산과 채팅 권한을 한 규칙으로 처리하기 위해서다. 주최자 여부는 {@code study.host_id} 로 판단한다.
 */
@Entity
@Table(name = "study_member")
public class StudyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false)
    private Long studyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private MemberStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "cancel_type", length = 10)
    private CancelType cancelType;

    protected StudyMember() {
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    /** 나간다: 행은 지우지 않고 {@code LEFT} 로 바꾼다. 나간 이력은 {@code attendance_event} 에도 남는다. */
    public void leave(Instant now, CancelType type) {
        if (!isActive()) {
            throw new IllegalStateException("참여 중이 아닌 행은 나갈 수 없습니다");
        }
        status = MemberStatus.LEFT;
        leftAt = now;
        cancelType = type;
    }

    /**
     * 주최자로서 나간다 (PRD 4.13A). 참여 취소가 아니므로 사전/직전 구분({@code cancel_type})은 비워 둔다. 출석 이벤트도
     * 만들지 않는다 (PLAN X-13).
     */
    public void leaveAsHost(Instant now) {
        if (!isActive()) {
            throw new IllegalStateException("참여 중이 아닌 행은 나갈 수 없습니다");
        }
        status = MemberStatus.LEFT;
        leftAt = now;
        cancelType = null;
    }

    public static StudyMember join(long studyId, long userId, Instant now) {
        StudyMember member = new StudyMember();
        member.studyId = studyId;
        member.userId = userId;
        member.status = MemberStatus.ACTIVE;
        member.joinedAt = now;
        return member;
    }

    /**
     * 나갔던 참여자가 다시 신청하면 같은 행을 되살린다 (DATA-MODEL 4장, 유일 제약 {@code (study_id, user_id)}).
     * 참여 순서는 다시 들어온 시각을 따른다. 나간 이력은 {@code attendance_event} 에 남는다.
     */
    public void rejoin(Instant now) {
        if (isActive()) {
            throw new IllegalStateException("이미 참여 중인 행은 되살릴 수 없습니다");
        }
        status = MemberStatus.ACTIVE;
        joinedAt = now;
        leftAt = null;
        cancelType = null;
    }
}
