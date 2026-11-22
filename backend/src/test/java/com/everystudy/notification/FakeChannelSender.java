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
    private RuntimeException unexpected;

    public FakeChannelSender(NotificationChannel channel) {
        this.channel = channel;
    }

    /** 다음 {@code times} 번은 실패한다. */
    public FakeChannelSender failNext(int times, boolean retryable) {
        this.failuresLeft = times;
        this.retryable = retryable;
        return this;
    }

    /** 다음 한 번은 채널 구현이 예기치 못한 예외를 던진다. */
    public FakeChannelSender throwNext(RuntimeException error) {
        this.unexpected = error;
        return this;
    }

    /** 보낸 내용과 실패 설정을 비운다. 한 컨텍스트를 여러 테스트가 쓰므로 테스트마다 부른다. */
    public void reset() {
        sent.clear();
        failuresLeft = 0;
        retryable = true;
        unexpected = null;
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
        if (unexpected != null) {
            RuntimeException error = unexpected;
            unexpected = null;
            throw error;
        }
        if (failuresLeft > 0) {
            failuresLeft--;
            throw new ChannelSendException("테스트: 발송 실패", retryable);
        }
        sent.add(message);
    }
}
