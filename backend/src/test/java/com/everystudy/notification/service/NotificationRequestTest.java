package com.everystudy.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationRequestTest {

    private static final Instant AT = Instant.parse("2026-11-24T10:00:00Z");

    @Test
    @DisplayName("모든 채널 요청은 PUSH 와 EMAIL 을 담는다")
    void allChannels() {
        NotificationRequest request = NotificationRequest.allChannels(1L, NotificationType.CHECKIN_OPEN, 3L, "제목",
                "본문", "K", AT, AT);

        assertThat(request.channels()).containsExactlyInAnyOrder(NotificationChannel.PUSH, NotificationChannel.EMAIL);
    }

    @Test
    @DisplayName("유효 기한이 예정 시각보다 앞이면 거부한다 (같으면 허용)")
    void expiresNotBeforeScheduled() {
        assertThatThrownBy(() -> request("K", "제목", AT, AT.minusSeconds(1), Set.of(NotificationChannel.PUSH)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(request("K", "제목", AT, AT, Set.of(NotificationChannel.PUSH))).isNotNull();
    }

    @Test
    @DisplayName("dedupe 키는 채널 접미사를 붙여도 열 길이(100)에 들어가야 한다")
    void dedupeKeyLength() {
        String max = "k".repeat(NotificationRequest.DEDUPE_KEY_MAX);

        assertThat(request(max, "제목", AT, AT, Set.of(NotificationChannel.EMAIL)).dedupeKey() + ":EMAIL")
                .hasSize(100);
        assertThatThrownBy(() -> request(max + "k", "제목", AT, AT, Set.of(NotificationChannel.EMAIL)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("빈 제목, 너무 긴 제목, 빈 채널은 거부한다")
    void rejectsBadValues() {
        Set<NotificationChannel> push = Set.of(NotificationChannel.PUSH);

        assertThatThrownBy(() -> request("K", " ", AT, AT, push)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request("K", "가".repeat(NotificationRequest.TITLE_MAX + 1), AT, AT, push))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request("K", "제목", AT, AT, Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    private static NotificationRequest request(String key, String title, Instant scheduled, Instant expires,
            Set<NotificationChannel> channels) {
        return new NotificationRequest(1L, NotificationType.CHECKIN_OPEN, null, title, "본문", key, scheduled, expires,
                channels);
    }
}
