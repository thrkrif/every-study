package com.everystudy.common.code;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 구 단위({@link RegionCode.Level#DISTRICT}) 지역 코드여야 한다. {@code null} 은 통과시킨다(필수 여부는 {@code @NotBlank} 로). */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DistrictCode.Validator.class)
public @interface DistrictCode {

    String message() default "구 단위 지역을 골라야 합니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<DistrictCode, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null
                    || RegionCode.find(value).filter(region -> region.level() == RegionCode.Level.DISTRICT).isPresent();
        }
    }
}
