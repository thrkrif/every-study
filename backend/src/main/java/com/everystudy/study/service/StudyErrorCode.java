package com.everystudy.study.service;

import com.everystudy.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 스터디 모듈의 오류 코드 (API 명세 1장). */
public enum StudyErrorCode implements ErrorCode {

    STUDY_FULL(HttpStatus.CONFLICT, "정원이 찼습니다"),
    STUDY_NOT_RECRUITING(HttpStatus.CONFLICT, "모집 중인 스터디가 아닙니다"),
    USER_RESTRICTED(HttpStatus.FORBIDDEN, "제재 중이라 신청할 수 없습니다"),
    ENTRY_CRITERIA_NOT_MET(HttpStatus.CONFLICT, "입장 기준을 충족하지 않습니다"),
    JOB_NOT_ALLOWED(HttpStatus.CONFLICT, "희망 직군에 내 직군이 없습니다"),
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여 중인 스터디입니다"),
    STUDY_NOT_FULL(HttpStatus.CONFLICT, "정원에 여유가 있어 대기할 수 없습니다. 바로 참여해 주세요"),
    ALREADY_WAITING(HttpStatus.CONFLICT, "이미 대기 중인 스터디입니다");

    private final HttpStatus status;
    private final String title;

    StudyErrorCode(HttpStatus status, String title) {
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
