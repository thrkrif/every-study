package com.everystudy.chat.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 저장이 커밋된 뒤에 구독자에게 보낸다 (ARCHITECTURE 4.8, PLAN T-047). "DB 에 먼저 저장한 뒤 전달"이라서 저장이
 * 롤백되면 전달도 없다. 전달에 실패해도 이전 대화 조회(REST)로 복구되므로 {@code AFTER_COMMIT} 에 둔다 (ARCHITECTURE 3장).
 *
 * <p>채팅방 주제 {@code /topic/studies/{id}/chat} 로 {@link ChatEvent} 를 보낸다. 차단 숨김은 받는 사람마다 달라 서버가
 * 하나의 주제로는 가려 줄 수 없으므로, 클라이언트가 {@code senderId} 로 가리고 다시 불러오면 REST 가 가려서 준다.
 */
@Component
public class ChatBroadcaster {

    /** 방 주제로 보내는 이벤트. {@code type} 에 따라 해당 필드만 채워진다. */
    public record ChatEvent(String type, ChatRoomService.MessageView message, ChatRoomService.Notice notice) {

        static ChatEvent message(ChatRoomService.MessageView message) {
            return new ChatEvent("MESSAGE", message, null);
        }

        static ChatEvent notice(ChatRoomService.Notice notice) {
            return new ChatEvent("NOTICE", null, notice);
        }
    }

    record MessageSent(long studyId, ChatRoomService.MessageView message) {
    }

    record NoticeChanged(long studyId, ChatRoomService.Notice notice) {
    }

    private final SimpMessagingTemplate template;

    ChatBroadcaster(SimpMessagingTemplate template) {
        this.template = template;
    }

    public static String topicOf(long studyId) {
        return "/topic/studies/" + studyId + "/chat";
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onMessage(MessageSent event) {
        template.convertAndSend(topicOf(event.studyId()), ChatEvent.message(event.message()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onNotice(NoticeChanged event) {
        template.convertAndSend(topicOf(event.studyId()), ChatEvent.notice(event.notice()));
    }
}
