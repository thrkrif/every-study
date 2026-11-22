package com.everystudy.chat.service;

import com.everystudy.chat.domain.ChatRoom;
import com.everystudy.chat.repository.ChatMessageRepository;
import com.everystudy.chat.repository.ChatNoticeRepository;
import com.everystudy.chat.repository.ChatReadRepository;
import com.everystudy.chat.repository.ChatRoomRepository;
import com.everystudy.common.scheduling.SchedulerProperties;
import com.everystudy.common.time.TimeProvider;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 보관 기간이 지난 채팅 메시지를 영구 삭제한다 (PRD 4.14, ARCHITECTURE 4.7·4.8, PLAN T-049). 방 하나를 한 번에
 * {@code batchLimit} 건까지만 지우고, 메시지가 모두 없어지면 공지·읽음 위치도 지우고 {@code purged_at} 을 남긴다. 남은 건은
 * 다음 실행이 이어서 처리한다. 방마다 별도 트랜잭션이고 재실행해도 결과가 같다.
 */
@Service
public class ChatPurgeService {

    private static final Logger log = LoggerFactory.getLogger(ChatPurgeService.class);

    private final ChatRoomRepository rooms;
    private final ChatMessageRepository messages;
    private final ChatNoticeRepository notices;
    private final ChatReadRepository reads;
    private final SchedulerProperties scheduler;
    private final TimeProvider time;
    private final TransactionTemplate tx;

    ChatPurgeService(ChatRoomRepository rooms, ChatMessageRepository messages, ChatNoticeRepository notices,
            ChatReadRepository reads, SchedulerProperties scheduler, TimeProvider time, TransactionTemplate tx) {
        this.rooms = rooms;
        this.messages = messages;
        this.notices = notices;
        this.reads = reads;
        this.scheduler = scheduler;
        this.time = time;
        this.tx = tx;
    }

    /** @return 이번에 처리한 방 수. 한 번에 {@code batchLimit} 개 방까지, 방마다 {@code batchLimit} 건까지 지운다 */
    public int purgeDueRooms() {
        Instant now = time.now();
        List<Long> due = rooms.findPurgeDueIds(now, Limit.of(scheduler.batchLimit()));
        int processed = 0;
        for (long id : due) {
            try {
                tx.executeWithoutResult(status -> purgeOne(id, now));
                processed++;
            } catch (RuntimeException e) {
                log.error("채팅방 {} 메시지 삭제 실패. 다음 방을 계속 처리한다", id, e);
            }
        }
        return processed;
    }

    private void purgeOne(long roomId, Instant now) {
        ChatRoom room = rooms.findById(roomId).orElse(null);
        if (room == null || room.getPurgedAt() != null) {
            return;
        }
        messages.deleteOldest(roomId, scheduler.batchLimit());
        if (!messages.existsByRoomId(roomId)) {
            notices.deleteById(roomId);
            reads.deleteReadStates(roomId);
            room.markPurged(now);
        }
    }
}
