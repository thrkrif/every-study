package com.everystudy.notification.service;

/**
 * 한 채널로 알림을 실제로 보내는 쪽 (ADR-0005). 워커(PLAN T-038)가 아웃박스 건마다 채널에 맞는 구현을 부른다.
 * 실제 구현(웹 푸시 VAPID, 이메일)은 T-038 이후에 만든다. 지금은 테스트용 가짜 구현만 있다.
 */
public interface ChannelSender {

    NotificationChannel channel();

    /**
     * @throws ChannelSendException 보내지 못함. {@code retryable} 이면 워커가 백오프로 다시 시도한다
     */
    void send(OutgoingNotification message);

    /** 보낼 내용. 받는 사람의 연락처(이메일, 푸시 구독)는 구현이 {@code userId} 로 찾는다. */
    record OutgoingNotification(long outboxId, long userId, NotificationType type, Long studyId, String title,
            String body) {
    }

    /** 발송 실패. 메시지에 토큰·이메일·구독 주소를 넣지 않는다 (AGENTS.md). */
    class ChannelSendException extends RuntimeException {

        private final boolean retryable;

        public ChannelSendException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }
}
