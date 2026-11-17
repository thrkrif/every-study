package com.everystudy.study.web;

import com.everystudy.common.security.LoginUser;
import com.everystudy.study.service.StudyCreationService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 스터디 (API 4장). 목록·상세는 T-009 에서 더한다. */
@RestController
@RequestMapping("/api/v1/studies")
class StudyController {

    private final StudyCreationService creation;

    StudyController(StudyCreationService creation) {
        this.creation = creation;
    }

    record CreatedStudy(long id) {
    }

    /** 개설. {@code 201} 과 새 스터디 주소({@code Location}), 본문은 ID 만. */
    @PostMapping
    ResponseEntity<CreatedStudy> create(@AuthenticationPrincipal LoginUser user,
            @Valid @RequestBody CreateStudyRequest request) {
        long id = creation.create(user.userId(), request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/studies/" + id)).body(new CreatedStudy(id));
    }
}
