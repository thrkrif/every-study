package com.everystudy.common.code;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** {@link RegionCode} 의 코드여야 한다 (탐색 필터용). {@code null} 은 통과시킨다. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = KnownRegionCode.Validator.class)
public @interface KnownRegionCode {

    String message() default "알 수 없는 지역입니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<KnownRegionCode, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || RegionCode.find(value).isPresent();
        }
    }
}
