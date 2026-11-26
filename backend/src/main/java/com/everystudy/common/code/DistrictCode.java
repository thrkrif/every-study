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
 * 회원·스터디에 저장하는 지역 코드({@link RegionCode})여야 한다. {@code null} 은 통과시킨다(필수 여부는 {@code @NotBlank} 로).
 *
 * <p>이름과 필드 이름(`districtCode`)은 구 단위였던 때의 것을 그대로 둔다. 지금 값은 시·도 코드다 (DATA-MODEL 1장).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DistrictCode.Validator.class)
public @interface DistrictCode {

    String message() default "지역을 골라야 합니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<DistrictCode, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || RegionCode.find(value).isPresent();
        }
    }
}
