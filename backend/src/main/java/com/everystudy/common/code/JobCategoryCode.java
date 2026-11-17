package com.everystudy.common.code;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link JobCode} 의 대분류 코드여야 한다(세부 직무는 거절). {@code null} 은 통과시킨다.
 * 스터디 희망 직군은 지금 대분류로만 고른다 (PRD 4.2, PLAN X-18).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = JobCategoryCode.Validator.class)
public @interface JobCategoryCode {

    String message() default "직군 대분류를 골라야 합니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<JobCategoryCode, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || JobCode.find(value).filter(JobCode::isCategory).isPresent();
        }
    }
}
