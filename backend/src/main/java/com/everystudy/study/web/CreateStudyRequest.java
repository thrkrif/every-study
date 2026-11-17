package com.everystudy.study.web;

import com.everystudy.common.code.DistrictCode;
import com.everystudy.common.code.JobCategoryCode;
import com.everystudy.common.code.KnownTopicCode;
import com.everystudy.study.domain.Study;
import com.everystudy.study.service.CreateStudyCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 스터디 개설 요청 (API 4장). 여기서는 형식만 본다. 시각·정원·입장 기준 값의 범위는 서비스가 설정값으로 검사한다.
 *
 * @param targetJobCodes 희망 직군 대분류. 생략하거나 비우면 직군 무관. 세부 직무 코드는 받지 않는다 (PRD 4.2)
 * @param allowNewcomer  신규 허용. 생략하면 허용 (PRD 4.7 기본값)
 */
record CreateStudyRequest(
        @NotBlank(message = "필수 값입니다") @Size(min = 2, max = 40, message = "2~40자여야 합니다") String title,
        @NotBlank(message = "필수 값입니다") @Size(min = 10, max = 2000, message = "10~2000자여야 합니다")
        String description,
        @NotBlank(message = "필수 값입니다") @KnownTopicCode String topicCode,
        @NotBlank(message = "필수 값입니다") @DistrictCode String districtCode,
        List<@NotBlank(message = "필수 값입니다") @JobCategoryCode String> targetJobCodes,
        @NotNull(message = "필수 값입니다") @Valid PlaceRequest place,
        @NotNull(message = "필수 값입니다") Instant startAt,
        @NotNull(message = "필수 값입니다") Integer durationMin,
        @NotNull(message = "필수 값입니다") Integer capacity,
        Integer minAttendanceRate,
        Boolean allowNewcomer) {

    CreateStudyRequest {
        title = strip(title);
        description = strip(description);
        targetJobCodes = targetJobCodes == null ? List.of() : targetJobCodes;
    }

    record PlaceRequest(
            @NotBlank(message = "필수 값입니다") @Size(max = 100, message = "100자 이하여야 합니다") String name,
            @NotBlank(message = "필수 값입니다") @Size(max = 200, message = "200자 이하여야 합니다") String address,
            @NotNull(message = "필수 값입니다") @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull(message = "필수 값입니다") @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {
    }

    CreateStudyCommand toCommand() {
        return new CreateStudyCommand(title, description, topicCode, districtCode,
                new LinkedHashSet<>(targetJobCodes),
                new Study.Place(place.name().strip(), place.address().strip(),
                        // DB 는 소수점 6자리(약 10cm)까지 저장한다 (DATA-MODEL 1장).
                        place.latitude().setScale(6, RoundingMode.HALF_UP),
                        place.longitude().setScale(6, RoundingMode.HALF_UP)),
                startAt, durationMin, capacity,
                new Study.EntryCriteria(minAttendanceRate, allowNewcomer == null || allowNewcomer));
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
