package com.everystudy.chat.service;

import com.everystudy.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 채팅 모듈의 오류 코드 (API 명세 1장). */
public enum ChatErrorCode implements ErrorCode {

    CHAT_CLOSED(HttpStatus.CONFLICT, "닫힌 채팅방입니다"),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 대상입니다"),
    REPORT_ALREADY_HANDLED(HttpStatus.CONFLICT, "이미 처리된 신고입니다");

    private final HttpStatus status;
    private final String title;

    ChatErrorCode(HttpStatus status, String title) {
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
