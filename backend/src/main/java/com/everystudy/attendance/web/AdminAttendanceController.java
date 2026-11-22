package com.everystudy.attendance.web;

import com.everystudy.attendance.service.AttendanceCorrectionService;
import com.everystudy.common.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 개별 출석 정정 (API 7장). {@code /api/v1/admin/**} 는 보안 설정이 {@code ADMIN} 만 통과시킨다. */
@RestController
@RequestMapping("/api/v1/admin/attendance-events")
class AdminAttendanceController {

    private final AttendanceCorrectionService corrections;

    AdminAttendanceController(AttendanceCorrectionService corrections) {
        this.corrections = corrections;
    }

    @PostMapping("/{eventId}/corrections")
    AttendanceCorrectionService.Corrected correct(@AuthenticationPrincipal LoginUser user,
            @PathVariable long eventId, @Valid @RequestBody CorrectionRequest request) {
        return corrections.correct(user.userId(), eventId, request.result(), request.note().strip());
    }
}
