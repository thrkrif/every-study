package com.everystudy.trust.service;

import com.everystudy.trust.domain.AttendanceEventType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 한 사용자의 최근 세션 집계 (PRD 4.7, DATA-MODEL 6장). 이벤트를 읽지 않는 순수 계산이다.
 *
 * @param rate 출석률(%). 집계 대상 세션이 신규 기준 미만이면 {@code null}
 */
record AttendanceTally(int counted, int attended, int late, int noShow, int lateCancel, BigDecimal rate) {

    private static final BigDecimal LATE_WEIGHT = new BigDecimal("0.5");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * 분모에 드는 판정은 출석·지각·노쇼 확정·직전 취소다. 그 밖(노쇼 후보, 정정으로 취소된 판정, 사전 취소, 세션 무효)은
     * 세지 않고 최근 N회 창도 차지하지 않는다.
     *
     * @param currentVerdictsNewestFirst 세션마다 현재 판정 하나씩, 최근 것부터
     * @param recentSessions 분모에 드는 세션 중 최근 몇 회까지 세는가
     * @param newcomerBelowSessions 집계 대상 세션이 이 값보다 적으면 출석률을 비운다
     */
    static AttendanceTally of(List<AttendanceEventType> currentVerdictsNewestFirst, int recentSessions,
            int newcomerBelowSessions) {
        int attended = 0;
        int late = 0;
        int noShow = 0;
        int lateCancel = 0;
        int counted = 0;
        for (AttendanceEventType verdict : currentVerdictsNewestFirst) {
            if (counted == recentSessions) {
                break;
            }
            switch (verdict) {
                case ATTENDED -> attended++;
                case LATE -> late++;
                case NO_SHOW -> noShow++;
                case LATE_CANCEL -> lateCancel++;
                default -> {
                    continue;
                }
            }
            counted++;
        }
        BigDecimal rate = counted < newcomerBelowSessions ? null : rate(attended, late, counted);
        return new AttendanceTally(counted, attended, late, noShow, lateCancel, rate);
    }

    private static BigDecimal rate(int attended, int late, int counted) {
        BigDecimal numerator = BigDecimal.valueOf(attended).add(LATE_WEIGHT.multiply(BigDecimal.valueOf(late)));
        return numerator.multiply(HUNDRED).divide(BigDecimal.valueOf(counted), 2, RoundingMode.HALF_UP);
    }
}
