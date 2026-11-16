package com.everystudy.common.error;

/** 규칙 위반을 알리는 예외. {@link GlobalExceptionHandler} 가 오류 응답으로 바꾼다. */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    /** @param detail 응답의 {@code detail}. 토큰·이메일·좌표를 넣지 않는다. */
    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail != null ? detail : errorCode.name());
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** 응답 {@code detail}. 지정하지 않았으면 {@code null}. */
    public String detail() {
        return errorCode.name().equals(getMessage()) ? null : getMessage();
    }
}
