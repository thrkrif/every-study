package com.everystudy.common.error;

/** 입력 오류 하나. {@code VALIDATION_FAILED} 응답의 {@code errors} 항목이다 (API 1장). */
public record FieldViolation(String field, String message) {
}
