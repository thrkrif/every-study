package com.everystudy.study.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/** 대기 (DATA-MODEL 4장). 참여가 아니라 "자리가 나면 알려 달라"는 신청이다. 기본 키는 {@code (study_id, user_id)}. */
@Entity
@Table(name = "study_waiting")
@IdClass(StudyWaiting.Key.class)
public class StudyWaiting {

    public record Key(Long studyId, Long userId) implements Serializable {
    }

    @Id
    @Column(name = "study_id")
    private Long studyId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StudyWaiting() {
    }

    public Long getUserId() {
        return userId;
    }

    public static StudyWaiting of(long studyId, long userId, Instant now) {
        StudyWaiting waiting = new StudyWaiting();
        waiting.studyId = studyId;
        waiting.userId = userId;
        waiting.createdAt = now;
        return waiting;
    }
}
