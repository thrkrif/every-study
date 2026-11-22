package com.everystudy.study.web;

import com.everystudy.study.domain.Study;
import com.everystudy.study.service.StudyUpdateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * 스터디 수정 요청 (API 4장). 생략한 항목은 바꾸지 않는다. 형식만 보고, 일정 규칙은 서비스가 설정값으로 검사한다.
 * 제목은 비울 수 없고({@code 2~40자}), 소개는 비우면 빈 문자열이 된다 (개설과 같다).
 */
record UpdateStudyRequest(
        @Size(min = 2, max = 40, message = "2~40자여야 합니다") String title,
        @Size(max = 2000, message = "2000자 이하여야 합니다") String description,
        Instant startAt,
        Integer durationMin,
        @Valid CreateStudyRequest.PlaceRequest place) {

    UpdateStudyRequest {
        title = title == null ? null : title.strip();
        description = description == null ? null : description.strip();
    }

    StudyUpdateService.Changes toChanges() {
        Study.Place mapped = place == null ? null
                : new Study.Place(place.name().strip(), place.address().strip(),
                        place.latitude().setScale(6, RoundingMode.HALF_UP),
                        place.longitude().setScale(6, RoundingMode.HALF_UP));
        return new StudyUpdateService.Changes(title, description, startAt, durationMin, mapped);
    }
}
