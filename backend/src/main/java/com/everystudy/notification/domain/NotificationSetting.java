package com.everystudy.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/** 회원의 채널별 알림 수신 설정 (DATA-MODEL 7장). 행이 없으면 켜짐이다. */
@Entity
@Table(name = "notification_setting")
@IdClass(NotificationSettingId.class)
public class NotificationSetting {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(length = 10)
    private String channel;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationSetting() {
    }

    public static NotificationSetting of(long userId, String channel, boolean enabled, Instant now) {
        NotificationSetting setting = new NotificationSetting();
        setting.userId = userId;
        setting.channel = channel;
        setting.enabled = enabled;
        setting.updatedAt = now;
        return setting;
    }

    public void change(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public String getChannel() {
        return channel;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
