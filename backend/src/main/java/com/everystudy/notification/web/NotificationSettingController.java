package com.everystudy.notification.web;

import com.everystudy.common.security.LoginUser;
import com.everystudy.notification.service.NotificationChannel;
import com.everystudy.notification.service.NotificationSettingService;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 알림 수신 설정 (API 3장). 로그인 회원이 자기 설정만 본다. */
@RestController
@RequestMapping("/api/v1/me/notification-settings")
class NotificationSettingController {

    /** 응답. 채널마다 받으면 {@code true}. */
    record Response(boolean push, boolean email) {

        static Response of(Map<NotificationChannel, Boolean> settings) {
            return new Response(settings.get(NotificationChannel.PUSH), settings.get(NotificationChannel.EMAIL));
        }
    }

    private final NotificationSettingService service;

    NotificationSettingController(NotificationSettingService service) {
        this.service = service;
    }

    @GetMapping
    Response get(@AuthenticationPrincipal LoginUser user) {
        return Response.of(service.get(user.userId()));
    }

    @PutMapping
    Response update(@AuthenticationPrincipal LoginUser user, @RequestBody NotificationSettingsRequest request) {
        Map<NotificationChannel, Boolean> changes = new EnumMap<>(NotificationChannel.class);
        changes.put(NotificationChannel.PUSH, request.push());
        changes.put(NotificationChannel.EMAIL, request.email());
        return Response.of(service.update(user.userId(), changes));
    }
}
