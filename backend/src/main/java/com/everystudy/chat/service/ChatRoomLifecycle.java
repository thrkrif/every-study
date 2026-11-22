package com.everystudy.chat.service;

import com.everystudy.chat.domain.ChatRoom;
import com.everystudy.chat.repository.ChatRoomRepository;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.event.StudyCancelled;
import com.everystudy.study.event.StudyCreated;
import com.everystudy.study.event.StudyEnded;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 스터디의 생애에 맞춰 채팅방을 만들고 닫는다 (PRD 4.12·4.14, PLAN X-8·D-16). 동기 리스너라 스터디 변경과 같은
 * 트랜잭션에서 실행된다. 여기서 실패하면 개설·종료도 롤백된다. {@code study} 는 {@code chat} 을 모른다 (의존 방향은
 * chat → study).
 *
 * <p>종료({@link StudyEnded})와 파투({@link StudyCancelled})는 같은 경로로 방을 닫는다: 방 상태 {@code CLOSED},
 * 보관 기간 뒤 삭제할 시각({@code purge_after}) 기록, 커밋 뒤 방 구독자에게 폐쇄를 알리고 구독을 모두 끊는다. 이미 닫힌
 * 방은 그대로 둔다(멱등).
 */
@Component
class ChatRoomLifecycle {

    private final ChatRoomRepository rooms;
    private final ChatProperties properties;
    private final TimeProvider time;
    private final ApplicationEventPublisher events;

    ChatRoomLifecycle(ChatRoomRepository rooms, ChatProperties properties, TimeProvider time,
            ApplicationEventPublisher events) {
        this.rooms = rooms;
        this.properties = properties;
        this.time = time;
        this.events = events;
    }

    @EventListener
    void onStudyCreated(StudyCreated event) {
        rooms.save(ChatRoom.openFor(event.studyId(), time.now()));
    }

    @EventListener
    void onStudyEnded(StudyEnded event) {
        close(event.studyId());
    }

    @EventListener
    void onStudyCancelled(StudyCancelled event) {
        close(event.studyId());
    }

    private void close(long studyId) {
        Instant now = time.now();
        rooms.findByStudyId(studyId).ifPresent(room -> {
            if (room.close(now, now.plus(properties.retention()))) {
                events.publishEvent(new ChatBroadcaster.RoomClosing(studyId));
            }
        });
    }
}
