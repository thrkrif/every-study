package com.everystudy.attendance.web;

import com.everystudy.attendance.service.CheckinService;
import com.everystudy.common.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 체크인 (API 5장). 로그인한 참여자가 부른다. */
@RestController
@RequestMapping("/api/v1/studies/{id}")
class CheckinController {

    private final CheckinService checkins;

    CheckinController(CheckinService checkins) {
        this.checkins = checkins;
    }

    /** 위치 체크인. 이미 했으면 처음 결과를 {@code 200} 으로 돌려준다(멱등). */
    @PostMapping("/checkins")
    CheckinService.Result checkIn(@AuthenticationPrincipal LoginUser user, @PathVariable long id,
            @Valid @RequestBody CheckinRequest request) {
        return checkins.checkInByLocation(id, user.userId(), request.latitude(), request.longitude(),
                request.accuracyForStorage());
    }

    /** 코드 체크인. 위치 권한 거부·GPS 실패 시의 대체 수단이다. 이미 했으면 처음 결과를 {@code 200} 으로 돌려준다(멱등). */
    @PostMapping("/checkins/code")
    CheckinService.Result checkInByCode(@AuthenticationPrincipal LoginUser user, @PathVariable long id,
            @Valid @RequestBody CheckinCodeRequest request) {
        return checkins.checkInByCode(id, user.userId(), request.code());
    }

    /** 일회용 코드 발급(재발급). 주최자가 체크인한 뒤에만 된다. */
    @PutMapping("/checkin-code")
    CheckinService.IssuedCode issueCode(@AuthenticationPrincipal LoginUser user, @PathVariable long id) {
        return checkins.issueCode(id, user.userId());
    }

    /** 체크인 현황. 참여자는 자기 것, 주최자는 전원의 체크인 여부까지. */
    @GetMapping("/attendance")
    CheckinService.Attendance attendance(@AuthenticationPrincipal LoginUser user, @PathVariable long id) {
        return checkins.attendance(id, user.userId());
    }
}
