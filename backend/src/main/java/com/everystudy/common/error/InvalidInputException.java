package com.everystudy.common.error;

import java.util.List;

/**
 * 형식은 맞지만 현재 시각·설정값에 비춰 받을 수 없는 입력 (예: 개설 가능 범위를 넘은 날짜).
 * 형식 오류와 같이 {@code 400 VALIDATION_FAILED} 로 응답하고 {@code errors} 에 필드를 담는다.
 */
public class InvalidInputException extends RuntimeException {

    private final List<FieldViolation> violations;

    public InvalidInputException(List<FieldViolation> violations) {
        super("입력값이 올바르지 않음: " + violations.stream().map(FieldViolation::field).toList());
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
