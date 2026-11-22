package com.everystudy.notification.domain;

import java.io.Serializable;
import java.util.Objects;

/** {@link NotificationSetting} 의 복합 키 (회원, 채널). */
public class NotificationSettingId implements Serializable {

    private Long userId;
    private String channel;

    protected NotificationSettingId() {
    }

    public NotificationSettingId(Long userId, String channel) {
        this.userId = userId;
        this.channel = channel;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NotificationSettingId other && Objects.equals(userId, other.userId)
                && Objects.equals(channel, other.channel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, channel);
    }
}
