package com.everystudy.notification;

import com.everystudy.notification.service.ChannelSender;
import com.everystudy.notification.service.NotificationChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * 테스트용 가짜 채널 (PLAN T-014). 보낸 내용을 모아 두고, 정해 둔 횟수만큼 실패한다.
 * 워커(T-038) 테스트가 재시도·실패 처리를 확인할 때 쓴다.
 */
public class FakeChannelSender implements ChannelSender {

    private final NotificationChannel channel;
    private final List<OutgoingNotification> sent = new ArrayList<>();
    private int failuresLeft;
    private boolean retryable = true;

    public FakeChannelSender(NotificationChannel channel) {
        this.channel = channel;
    }

    /** 다음 {@code times} 번은 실패한다. */
    public FakeChannelSender failNext(int times, boolean retryable) {
        this.failuresLeft = times;
        this.retryable = retryable;
        return this;
    }

    public List<OutgoingNotification> sent() {
        return List.copyOf(sent);
    }

    @Override
    public NotificationChannel channel() {
        return channel;
    }

    @Override
    public void send(OutgoingNotification message) {
        if (failuresLeft > 0) {
            failuresLeft--;
            throw new ChannelSendException("테스트: 발송 실패", retryable);
        }
        sent.add(message);
    }
}
