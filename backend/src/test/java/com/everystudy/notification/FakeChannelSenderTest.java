package com.everystudy.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.everystudy.notification.service.ChannelSender.ChannelSendException;
import com.everystudy.notification.service.ChannelSender.OutgoingNotification;
import com.everystudy.notification.service.NotificationChannel;
import com.everystudy.notification.service.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakeChannelSenderTest {

    private final OutgoingNotification message =
            new OutgoingNotification(1L, 2L, NotificationType.CHECKIN_OPEN, 3L, "제목", "본문");

    @Test
    @DisplayName("정해 둔 횟수만큼 실패한 뒤 보낸 내용을 모은다")
    void failsThenSends() {
        FakeChannelSender sender = new FakeChannelSender(NotificationChannel.PUSH).failNext(1, true);

        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOfSatisfying(ChannelSendException.class, ex -> assertThat(ex.retryable()).isTrue());
        sender.send(message);

        assertThat(sender.channel()).isEqualTo(NotificationChannel.PUSH);
        assertThat(sender.sent()).containsExactly(message);
    }
}
