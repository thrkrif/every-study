package com.everystudy.study.web;

import com.everystudy.common.security.LoginUser;
import com.everystudy.study.service.StudyForceEndService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 스터디 조치 (API 7장). {@code /api/v1/admin/**} 는 보안 설정이 {@code ADMIN} 만 통과시킨다. */
@RestController
@RequestMapping("/api/v1/admin/studies")
class AdminStudyController {

    private final StudyForceEndService forceEnd;

    AdminStudyController(StudyForceEndService forceEnd) {
        this.forceEnd = forceEnd;
    }

    @PostMapping("/{id}/force-end")
    ResponseEntity<Void> forceEnd(@AuthenticationPrincipal LoginUser user, @PathVariable long id,
            @Valid @RequestBody ForceEndRequest request) {
        forceEnd.forceEnd(user.userId(), id, request.note().strip());
        return ResponseEntity.noContent().build();
    }
}
