package com.everystudy.notification.web;

import com.everystudy.common.security.LoginUser;
import com.everystudy.notification.service.NotificationCenterService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 알림 센터 (API 3장). 로그인 회원이 자기 알림만 본다. */
@RestController
@RequestMapping("/api/v1/me/notifications")
class MyNotificationController {

    private final NotificationCenterService center;

    MyNotificationController(NotificationCenterService center) {
        this.center = center;
    }

    @GetMapping
    NotificationCenterService.Page list(@AuthenticationPrincipal LoginUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return center.list(user.userId(), cursor, size);
    }

    @PostMapping("/{id}/read")
    ResponseEntity<Void> read(@AuthenticationPrincipal LoginUser user, @PathVariable long id) {
        center.markRead(user.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    ResponseEntity<Void> readAll(@AuthenticationPrincipal LoginUser user) {
        center.markAllRead(user.userId());
        return ResponseEntity.noContent().build();
    }
}
