package com.everystudy.chat.web;

import com.everystudy.chat.service.ChatMessageService;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.InvalidInputException;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * STOMP 로 들어온 메시지를 저장한다 (API 6장). 권한은 {@link ChatSocketInterceptor} 가 이미 검사했고, 여기서는 저장 직전에
 * 다시 확인한다(서비스가 한다). 저장은 커밋 뒤 방 주제로 전달된다. 입력이 잘못되면 보낸 사람에게만
 * {@code /user/queue/events} 로 {@code SEND_REJECTED} 를 알린다.
 */
@Controller
class ChatSocketController {

    /** 본인 대상 이벤트({@code /user/queue/events}). */
    record PersonalEvent(String type, String code) {
    }

    private final ChatMessageService chat;

    ChatSocketController(ChatMessageService chat) {
        this.chat = chat;
    }

    @MessageMapping("/studies/{studyId}/chat")
    void send(@DestinationVariable long studyId, @Payload ChatSendRequest request, Principal principal) {
        chat.send(studyId, ((ChatPrincipal) principal).userId(), request == null ? null : request.content());
    }

    @MessageExceptionHandler({InvalidInputException.class, BusinessException.class})
    @SendToUser(destinations = "/queue/events", broadcast = false)
    PersonalEvent rejected(RuntimeException e) {
        String code = e instanceof BusinessException business ? business.errorCode().name() : "VALIDATION_FAILED";
        return new PersonalEvent("SEND_REJECTED", code);
    }
}
