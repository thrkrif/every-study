package com.everystudy.user.service;

import com.everystudy.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 회원 모듈의 오류 코드 (API 명세 1장). */
public enum UserErrorCode implements ErrorCode {

    NICKNAME_TAKEN(HttpStatus.CONFLICT, "이미 쓰이고 있는 닉네임입니다");

    private final HttpStatus status;
    private final String title;

    UserErrorCode(HttpStatus status, String title) {
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
