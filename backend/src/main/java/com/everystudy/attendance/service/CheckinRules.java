package com.everystudy.attendance.service;

import com.everystudy.attendance.domain.CheckinResult;
import com.everystudy.common.error.BusinessException;
import java.time.Instant;

/** 체크인 규칙 (PRD 4.6). DB·Spring 에 의존하지 않는다. */
final class CheckinRules {

    private static final double EARTH_RADIUS_M = 6_371_008.8;

    private CheckinRules() {
    }

    /**
     * 체크인 가능 시간인가: 시작 {@code opensAt} 이후(그 시각 포함) ~ {@code closesAt} 까지(그 시각 포함). 시작 10분 전
     * 정각부터, 시작 30분 뒤 정각까지다.
     *
     * @throws BusinessException {@code CHECKIN_WINDOW_CLOSED}
     */
    static void requireWindowOpen(Instant now, Instant opensAt, Instant closesAt) {
        if (now.isBefore(opensAt) || now.isAfter(closesAt)) {
            throw new BusinessException(AttendanceErrorCode.CHECKIN_WINDOW_CLOSED);
        }
    }

    /** 시작 후 {@code attendedWithinMinutes} 분까지(그 시각 포함)는 출석, 그 뒤 마감까지는 지각이다. 시작 전은 출석이다. */
    static CheckinResult resultAt(Instant now, Instant startAt, int attendedWithinMinutes) {
        Instant attendedUntil = startAt.plusSeconds(attendedWithinMinutes * 60L);
        return now.isAfter(attendedUntil) ? CheckinResult.LATE : CheckinResult.ATTENDED;
    }

    /**
     * 반경 안인가: 거리가 반경 이하(경계 포함)다. 거리는 반올림한 미터다. GPS 정확도는 판정에 쓰지 않는다 — 실내 오차는
     * 반경(150m)에 이미 반영돼 있다 (PRD 4.6).
     *
     * @throws BusinessException {@code CHECKIN_OUT_OF_RANGE}
     */
    static int requireWithinRadius(double latitude, double longitude, double placeLatitude, double placeLongitude,
            int radiusMeters) {
        long distance = Math.round(distanceMeters(latitude, longitude, placeLatitude, placeLongitude));
        if (distance > radiusMeters) {
            throw new BusinessException(AttendanceErrorCode.CHECKIN_OUT_OF_RANGE);
        }
        return Math.toIntExact(distance);
    }

    /** 두 지점의 거리(미터). Haversine 공식이다 (ARCHITECTURE 4.4, MySQL 공간 함수는 쓰지 않는다). */
    static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
