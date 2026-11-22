package com.everystudy.chat.service;

import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 채팅 구독을 서버가 끊는다 (ARCHITECTURE 4.8, PLAN T-048). 나간 사람이 방 주제를 계속 받지 못하도록 브로커의 구독 등록을
 * 지우고 본인에게만 {@code /user/queue/events} 로 알린다. 전송은 인터셉터가 매번 {@code ACTIVE} 를 검사해 따로 막는다.
 * 서버 1대의 내장 브로커 기준이다 (서버를 늘리면 외부 브로커 릴레이에서 같은 일을 하는 방식으로 바꾼다).
 */
@Component
public class ChatSubscriptions {

    /** 본인 대상 이벤트 `/user/queue/events` 중 구독 종료. */
    public record SubscriptionEnded(String type, long studyId, String reason) {

        static SubscriptionEnded of(long studyId, String reason) {
            return new SubscriptionEnded("SUBSCRIPTION_ENDED", studyId, reason);
        }
    }

    private final SimpUserRegistry users;
    private final SimpleBrokerMessageHandler broker;
    private final SimpMessagingTemplate template;

    /**
     * 내장 단순 브로커는 선언된 반환 형이 {@code AbstractBrokerMessageHandler} 라서 구체 형으로는 주입되지 않는다. 이름으로 받아
     * 내장 브로커인지 확인한다 (외부 브로커 릴레이로 바꾸면 이 클래스를 함께 바꾼다).
     */
    ChatSubscriptions(SimpUserRegistry users,
            @Qualifier("simpleBrokerMessageHandler") AbstractBrokerMessageHandler broker,
            SimpMessagingTemplate template) {
        if (!(broker instanceof SimpleBrokerMessageHandler simple)) {
            throw new IllegalStateException("내장 단순 브로커가 아닙니다: " + broker.getClass().getName());
        }
        this.users = users;
        this.broker = simple;
        this.template = template;
    }

    /** 지금 이 방 주제를 구독 중인(방을 열어 둔) 사용자. 새 메시지 알림에서 제외한다 (PLAN D-12). */
    Set<Long> viewersOf(long studyId) {
        String topic = ChatBroadcaster.topicOf(studyId);
        Set<Long> viewers = new HashSet<>();
        for (SimpSubscription subscription : users.findSubscriptions(s -> topic.equals(s.getDestination()))) {
            if (subscription.getSession().getUser() == null) {
                continue;
            }
            try {
                viewers.add(Long.parseLong(subscription.getSession().getUser().getName()));
            } catch (NumberFormatException ignored) {
                // 회원 ID 가 아닌 이름은 구독자로 세지 않는다.
            }
        }
        return viewers;
    }

    /** 이 사용자의 이 방 구독을 모두 끊고 본인에게 알린다. */
    void endFor(long studyId, long userId, String reason) {
        String name = Long.toString(userId);
        unsubscribe(ChatBroadcaster.topicOf(studyId), name);
        template.convertAndSendToUser(name, "/queue/events", SubscriptionEnded.of(studyId, reason));
    }

    /** 방이 닫혀 이 방의 구독을 모두 끊는다. 구독자에게는 방 주제로 폐쇄 이벤트가 이미 갔다. */
    void endAll(long studyId) {
        unsubscribe(ChatBroadcaster.topicOf(studyId), null);
    }

    /** {@code userName} 이 {@code null} 이면 그 주제의 모든 구독을 끊는다. */
    private void unsubscribe(String topic, String userName) {
        Set<SimpSubscription> subscriptions = users.findSubscriptions(subscription ->
                topic.equals(subscription.getDestination())
                        && (userName == null || subscription.getSession().getUser() != null
                                && userName.equals(subscription.getSession().getUser().getName())));
        for (SimpSubscription subscription : subscriptions) {
            SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
            accessor.setSessionId(subscription.getSession().getId());
            accessor.setSubscriptionId(subscription.getId());
            broker.getSubscriptionRegistry()
                    .unregisterSubscription(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()));
        }
    }
}
