package com.everystudy.notification.service;

import com.everystudy.common.time.TimeProvider;
import com.everystudy.notification.domain.NotificationSetting;
import com.everystudy.notification.domain.NotificationSettingId;
import com.everystudy.notification.repository.NotificationSettingRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 채널별 알림 수신 설정 (PRD 4.5, API 3장). 행이 없는 채널은 켜짐이다. */
@Service
public class NotificationSettingService {

    private final NotificationSettingRepository settings;
    private final TimeProvider time;

    NotificationSettingService(NotificationSettingRepository settings, TimeProvider time) {
        this.settings = settings;
        this.time = time;
    }

    /** 모든 채널의 설정. 저장된 값이 없으면 켜짐이다. */
    @Transactional(readOnly = true)
    public Map<NotificationChannel, Boolean> get(long userId) {
        Map<NotificationChannel, Boolean> result = new EnumMap<>(NotificationChannel.class);
        for (NotificationChannel channel : NotificationChannel.values()) {
            result.put(channel, true);
        }
        settings.findByUserId(userId).forEach(row -> result.put(NotificationChannel.valueOf(row.getChannel()),
                row.isEnabled()));
        return result;
    }

    /**
     * 주어진 채널만 바꾼다. 값이 {@code null} 인 채널은 그대로 둔다.
     *
     * @return 바꾼 뒤의 전체 설정
     */
    @Transactional
    public Map<NotificationChannel, Boolean> update(long userId, Map<NotificationChannel, Boolean> changes) {
        Instant now = time.now();
        changes.forEach((channel, enabled) -> {
            if (enabled == null) {
                return;
            }
            settings.findById(new NotificationSettingId(userId, channel.name())).ifPresentOrElse(
                    row -> row.change(enabled, now),
                    () -> settings.save(NotificationSetting.of(userId, channel.name(), enabled, now)));
        });
        settings.flush();
        return get(userId);
    }

    /** 발송 시점에 이 채널로 받기로 했는지. 워커가 부른다. */
    @Transactional(readOnly = true)
    public boolean isEnabled(long userId, NotificationChannel channel) {
        return settings.findById(new NotificationSettingId(userId, channel.name()))
                .map(NotificationSetting::isEnabled).orElse(true);
    }
}
