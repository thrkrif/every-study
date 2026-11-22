package com.everystudy.trust.service;

/**
 * 다른 사람에게 보여 주는 출석 지표 (PRD 4.7, API 3장 출석 지표 응답).
 *
 * @param attendanceRate 출석률(%)을 내림한 정수. 신규면 {@code null}. 내림은 입장 기준(최소 출석률 N%)을 채운 사람만
 *                       N 이상으로 보이게 하려는 것이다
 * @param windowCount    집계 대상 세션 수 (최근 N회)
 */
public record AttendanceMetrics(Integer attendanceRate, int windowCount, int noShow, int late, int lateCancel,
        boolean isNewcomer) {

    /** 출석 기록이 아직 없는 사용자. */
    public static final AttendanceMetrics NEWCOMER = new AttendanceMetrics(null, 0, 0, 0, 0, true);
}
