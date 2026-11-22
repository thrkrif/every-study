package com.everystudy.trust.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 주최자 감점 한 건 (DATA-MODEL 6장). 추가만 한다. 롤링 합계는 조회할 때 계산한다 (ARCHITECTURE 4.5). */
@Entity
@Table(name = "host_penalty_event")
public class HostPenaltyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "host_id", nullable = false)
    private Long hostId;

    @Column(name = "study_id", nullable = false)
    private Long studyId;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private int points;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private HostPenaltyReason reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected HostPenaltyEvent() {
    }

    public static HostPenaltyEvent of(long hostId, long studyId, int points, HostPenaltyReason reason,
            Instant occurredAt) {
        HostPenaltyEvent event = new HostPenaltyEvent();
        event.hostId = hostId;
        event.studyId = studyId;
        event.points = points;
        event.reason = reason;
        event.occurredAt = occurredAt;
        return event;
    }
}
