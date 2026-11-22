package com.everystudy.attendance.service;

import com.everystudy.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 출석 모듈의 오류 코드 (API 명세 1장). */
public enum AttendanceErrorCode implements ErrorCode {

    CHECKIN_OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_CONTENT, "스터디 장소 반경 밖이라 체크인할 수 없습니다"),
    CHECKIN_WINDOW_CLOSED(HttpStatus.UNPROCESSABLE_CONTENT, "체크인 가능한 시간이 아닙니다"),
    CHECKIN_CODE_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "체크인 코드가 올바르지 않습니다"),
    HOST_NOT_CHECKED_IN(HttpStatus.UNPROCESSABLE_CONTENT, "주최자가 아직 체크인하지 않았습니다"),
    APPEAL_CLOSED(HttpStatus.CONFLICT, "이의를 받는 기간이 지났거나 이미 결정됐습니다"),
    APPEAL_NOT_ALLOWED(HttpStatus.CONFLICT, "이의를 신청할 수 없는 판정입니다"),
    ALREADY_APPEALED(HttpStatus.CONFLICT, "이미 이의를 신청한 판정입니다");

    private final HttpStatus status;
    private final String title;

    AttendanceErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String title() {
        return title;
    }
}
