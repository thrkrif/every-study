package com.everystudy.chat.service;

import com.everystudy.chat.domain.ChatRoom;
import com.everystudy.chat.repository.ChatRoomRepository;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.event.StudyCreated;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 스터디의 생애에 맞춰 채팅방을 만들고 닫는다 (PRD 4.12·4.14, PLAN X-8).
 * 동기 리스너라 스터디 변경과 같은 트랜잭션에서 실행된다. 여기서 실패하면 개설도 롤백된다.
 * {@code study} 는 {@code chat} 을 모른다 (의존 방향은 chat → study).
 */
@Component
class ChatRoomLifecycle {

    private final ChatRoomRepository rooms;
    private final TimeProvider time;

    ChatRoomLifecycle(ChatRoomRepository rooms, TimeProvider time) {
        this.rooms = rooms;
        this.time = time;
    }

    @EventListener
    void onStudyCreated(StudyCreated event) {
        rooms.save(ChatRoom.openFor(event.studyId(), time.now()));
    }
}
