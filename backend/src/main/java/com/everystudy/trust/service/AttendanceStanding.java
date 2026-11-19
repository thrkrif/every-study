package com.everystudy.trust.service;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 입장 기준(PRD 4.7)을 판단하는 데 필요한 내 출석 지표. 신규면 출석률이 없다.
 *
 * @param rate 출석률(%). 신규면 {@code null}
 */
public record AttendanceStanding(boolean newcomer, BigDecimal rate) {

    public static final AttendanceStanding NEWCOMER = new AttendanceStanding(true, null);

    public AttendanceStanding {
        if (!newcomer) {
            Objects.requireNonNull(rate, "신규가 아니면 출석률이 있어야 합니다");
        }
    }

    public static AttendanceStanding rated(BigDecimal rate) {
        return new AttendanceStanding(false, rate);
    }

    /**
     * 집계값에서 만든다. 집계 대상 세션이 신규 기준 미만이거나 출석률이 비어 있으면 신규다.
     *
     * @param newcomerBelowSessions {@link TrustProperties#newcomerBelowSessions()}
     */
    static AttendanceStanding of(int counted, BigDecimal rate, int newcomerBelowSessions) {
        return counted < newcomerBelowSessions || rate == null ? NEWCOMER : rated(rate);
    }
}
