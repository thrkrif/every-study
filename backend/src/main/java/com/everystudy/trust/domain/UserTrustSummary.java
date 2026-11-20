package com.everystudy.trust.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사용자별 출석 지표 캐시 (DATA-MODEL 6장, PRD 4.7). 원본은 {@code attendance_event} 이고, 갱신은 출석 집계 작업(M3)이 한다.
 * 지금은 입장 기준 검사에서 읽기만 한다.
 */
@Entity
@Table(name = "user_trust_summary")
public class UserTrustSummary {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** 집계 대상 세션 수 (최근 N회). */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int counted;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int attended;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int late;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "no_show", nullable = false)
    private int noShow;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "late_cancel", nullable = false)
    private int lateCancel;

    /** 출석률(%). 신규 조건이면 {@code null}. */
    @Column(precision = 5, scale = 2)
    private BigDecimal rate;

    @Column(name = "recalculated_at", nullable = false)
    private Instant recalculatedAt;

    protected UserTrustSummary() {
    }

    public int getCounted() {
        return counted;
    }

    public BigDecimal getRate() {
        return rate;
    }
}
