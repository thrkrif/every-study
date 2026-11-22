package com.everystudy.chat.web;

import com.everystudy.chat.service.ChatMessageService;
import com.everystudy.common.error.BusinessException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * 클라이언트가 보내는 STOMP 프레임마다 권한을 검사한다 (ARCHITECTURE 4.8, API 6장). 구독과 전송 때마다 서버가
 * {@code ACTIVE} 참여 여부를 다시 확인하고, 거부하면 예외를 던져 STOMP {@code ERROR} 프레임이 나간다. 사유 코드가
 * {@code ERROR} 의 {@code message} 헤더에 실린다.
 *
 * <ul>
 *   <li>{@code CONNECT}: 핸드셰이크에서 인증된 사용자가 있어야 한다.</li>
 *   <li>{@code SUBSCRIBE}: {@code /topic/studies/{id}/chat}(그 스터디 참여자) 또는 {@code /user/queue/events}(본인).</li>
 *   <li>{@code SEND}: {@code /app/studies/{id}/chat}(그 스터디 참여자, 열린 방). 그 밖의 목적지는 거부한다.</li>
 * </ul>
 */
@Component
class ChatSocketInterceptor implements ChannelInterceptor {

    private static final Pattern TOPIC = Pattern.compile("^/topic/studies/(\\d{1,15})/chat$");
    private static final Pattern SEND = Pattern.compile("^/app/studies/(\\d{1,15})/chat$");
    private static final String USER_EVENTS = "/user/queue/events";

    private final ChatMessageService chat;

    ChatSocketInterceptor(ChatMessageService chat) {
        this.chat = chat;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        StompCommand command = accessor == null ? null : accessor.getCommand();
        if (command == null) {
            return message;
        }
        switch (command) {
            case CONNECT, STOMP -> userOf(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> authorizeSend(accessor);
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK 등은 검사할 것이 없다.
            }
        }
        return message;
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        long userId = userOf(accessor);
        String destination = accessor.getDestination();
        if (USER_EVENTS.equals(destination)) {
            return;
        }
        Matcher topic = destination == null ? null : TOPIC.matcher(destination);
        if (topic == null || !topic.matches()) {
            throw new ChatSocketException("FORBIDDEN");
        }
        check(() -> chat.requireMember(Long.parseLong(topic.group(1)), userId));
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        long userId = userOf(accessor);
        String destination = accessor.getDestination();
        Matcher send = destination == null ? null : SEND.matcher(destination);
        if (send == null || !send.matches()) {
            throw new ChatSocketException("FORBIDDEN");
        }
        check(() -> chat.requireCanSend(Long.parseLong(send.group(1)), userId));
    }

    private static long userOf(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof ChatPrincipal principal) {
            return principal.userId();
        }
        throw new ChatSocketException("UNAUTHENTICATED");
    }

    private static void check(Runnable authorization) {
        try {
            authorization.run();
        } catch (BusinessException e) {
            throw new ChatSocketException(e.errorCode().name());
        }
    }

    /** 권한 검사 실패. 메시지가 오류 코드이고 STOMP {@code ERROR} 프레임의 {@code message} 헤더가 된다. */
    static class ChatSocketException extends MessagingException {

        ChatSocketException(String code) {
            super(code);
        }
    }
}
