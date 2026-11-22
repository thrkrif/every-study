package com.everystudy.chat.service;

import com.everystudy.chat.domain.ChatMessage;
import com.everystudy.chat.repository.ChatMessageRepository;
import com.everystudy.chat.repository.ChatRoomRepository;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.event.HostChanged;
import com.everystudy.study.event.MemberJoined;
import com.everystudy.study.event.MemberLeft;
import com.everystudy.user.service.PublicUser;
import com.everystudy.user.service.UserDirectory;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 스터디의 사람 변화를 채팅방 시스템 메시지로 남긴다 (PRD 4.12, PLAN T-048). {@code study} 이벤트를 동기 리스너로 받아
 * 그 변경과 같은 트랜잭션에서 저장하므로 함께 커밋되거나 함께 롤백된다 (ARCHITECTURE 3장, PLAN X-8). {@code study} 는
 * {@code chat} 을 모른다. 나가면 그 사용자의 방 구독도 끊는다(커밋 뒤).
 */
@Component
class ChatSystemMessages {

    private static final String WITHDRAWN = "탈퇴한 사용자";

    private final ChatRoomRepository rooms;
    private final ChatMessageRepository messages;
    private final UserDirectory users;
    private final TimeProvider time;
    private final ApplicationEventPublisher events;

    ChatSystemMessages(ChatRoomRepository rooms, ChatMessageRepository messages, UserDirectory users,
            TimeProvider time, ApplicationEventPublisher events) {
        this.rooms = rooms;
        this.messages = messages;
        this.users = users;
        this.time = time;
        this.events = events;
    }

    @EventListener
    void onJoined(MemberJoined event) {
        post(event.studyId(), nameOf(event.userId()) + "님이 입장했어요");
    }

    @EventListener
    void onLeft(MemberLeft event) {
        // 나간 사람이 자기 나감 안내를 받지 않도록 구독 종료를 먼저 알린다 (둘 다 커밋 뒤에 순서대로 실행된다).
        events.publishEvent(new ChatBroadcaster.SubscriptionEnding(event.studyId(), event.userId(), "LEFT"));
        post(event.studyId(), nameOf(event.userId()) + "님이 나갔어요");
    }

    @EventListener
    void onHostChanged(HostChanged event) {
        post(event.studyId(), nameOf(event.newHostId()) + "님이 새 주최자가 됐어요");
    }

    private void post(long studyId, String content) {
        rooms.findByStudyId(studyId).ifPresent(room -> {
            ChatMessage saved = messages.saveAndFlush(ChatMessage.system(room.getId(), content, time.now()));
            events.publishEvent(new ChatBroadcaster.MessageSent(studyId, new ChatRoomService.MessageView(
                    saved.getId(), saved.getKind().name(), null, null, saved.getContent(), false, false,
                    saved.getCreatedAt())));
        });
    }

    private String nameOf(long userId) {
        PublicUser user = users.findPublic(Set.of(userId)).get(userId);
        return user == null || user.nickname() == null ? WITHDRAWN : user.nickname();
    }
}
