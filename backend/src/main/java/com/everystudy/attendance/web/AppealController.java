package com.everystudy.attendance.web;

import com.everystudy.attendance.service.AppealService;
import com.everystudy.attendance.service.MyAttendanceService;
import com.everystudy.common.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 이의 신청과 내 출석 기록 (API 3장·5장). 로그인한 회원이 부른다. */
@RestController
class AppealController {

    private final AppealService appeals;
    private final MyAttendanceService attendance;

    AppealController(AppealService appeals, MyAttendanceService attendance) {
        this.appeals = appeals;
        this.attendance = attendance;
    }

    @PostMapping("/api/v1/attendance-events/{eventId}/appeals")
    @ResponseStatus(HttpStatus.CREATED)
    AppealService.View file(@AuthenticationPrincipal LoginUser user, @PathVariable long eventId,
            @Valid @RequestBody AppealRequest request) {
        return appeals.file(user.userId(), eventId, request.reason().strip());
    }

    @PutMapping("/api/v1/appeals/{id}/host-opinion")
    AppealService.View hostOpinion(@AuthenticationPrincipal LoginUser user, @PathVariable long id,
            @Valid @RequestBody HostOpinionRequest request) {
        return appeals.writeHostOpinion(user.userId(), id, request.opinion().strip());
    }

    @GetMapping("/api/v1/me/appeals")
    AppealService.Page myAppeals(@AuthenticationPrincipal LoginUser user, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return appeals.mine(user.userId(), cursor, size);
    }

    @GetMapping("/api/v1/me/attendance")
    MyAttendanceService.Page myAttendance(@AuthenticationPrincipal LoginUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return attendance.mine(user.userId(), cursor, size);
    }
}
