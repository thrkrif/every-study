package com.everystudy.study.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 주최자 승계 이력 (DATA-MODEL 4장). 추가만 하고 고치거나 지우지 않는다. */
@Entity
@Table(name = "host_transfer")
public class HostTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false)
    private Long studyId;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected HostTransfer() {
    }

    public static HostTransfer of(long studyId, long fromUserId, long toUserId, Instant now) {
        HostTransfer transfer = new HostTransfer();
        transfer.studyId = studyId;
        transfer.fromUserId = fromUserId;
        transfer.toUserId = toUserId;
        transfer.createdAt = now;
        return transfer;
    }
}
