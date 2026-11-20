package com.everystudy.common.error;

import java.util.Map;

/** 규칙 위반을 알리는 예외. {@link GlobalExceptionHandler} 가 오류 응답으로 바꾼다. */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> properties;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    /** @param detail 응답의 {@code detail}. 토큰·이메일·좌표를 넣지 않는다. */
    public BusinessException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, Map.of());
    }

    /**
     * @param properties 응답 최상위에 더할 값 (예: {@code USER_RESTRICTED} 의 {@code until}). 이름은 API 명세 1장을 따른다
     */
    public BusinessException(ErrorCode errorCode, String detail, Map<String, Object> properties) {
        super(detail != null ? detail : errorCode.name());
        this.errorCode = errorCode;
        this.properties = Map.copyOf(properties);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** 응답 {@code detail}. 지정하지 않았으면 {@code null}. */
    public String detail() {
        return errorCode.name().equals(getMessage()) ? null : getMessage();
    }

    /** 응답 최상위에 더할 값. 없으면 빈 맵. */
    public Map<String, Object> properties() {
        return properties;
    }
}
