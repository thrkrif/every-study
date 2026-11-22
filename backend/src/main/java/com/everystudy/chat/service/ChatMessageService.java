package com.everystudy.chat.service;

import com.everystudy.chat.domain.ChatMessage;
import com.everystudy.chat.domain.ChatRoom;
import com.everystudy.chat.domain.ChatRoomStatus;
import com.everystudy.chat.repository.ChatMessageRepository;
import com.everystudy.chat.repository.ChatRoomRepository;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.error.FieldViolation;
import com.everystudy.common.error.InvalidInputException;
import com.everystudy.common.ratelimit.RateLimitProperties;
import com.everystudy.common.ratelimit.RateLimiter;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.service.StudySessions;
import com.everystudy.user.service.PublicUser;
import com.everystudy.user.service.UserDirectory;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅 메시지 보내기 (PRD 4.12, API 6장, PLAN T-047). 참여자({@code ACTIVE})만, 열린 방에만 보낼 수 있다. DB 에 저장한 뒤
 * 커밋되면 구독자에게 전달한다 ({@link ChatBroadcaster}). 같은 규칙을 구독·전송 때마다 STOMP 인터셉터가 먼저 검사한다.
 */
@Service
public class ChatMessageService {

    /** 메시지 길이 상한. DATA-MODEL 8장 `chat_message.content`. */
    public static final int CONTENT_MAX = 1000;

    private final StudySessions sessions;
    private final ChatRoomRepository rooms;
    private final ChatMessageRepository messages;
    private final UserDirectory users;
    private final TimeProvider time;
    private final ApplicationEventPublisher events;
    private final ChatMessageNotifier notifier;
    private final RateLimiter limiter;
    private final RateLimitProperties rateLimits;

    ChatMessageService(StudySessions sessions, ChatRoomRepository rooms, ChatMessageRepository messages,
            UserDirectory users, TimeProvider time, ApplicationEventPublisher events,
            ChatMessageNotifier notifier, RateLimiter limiter, RateLimitProperties rateLimits) {
        this.sessions = sessions;
        this.rooms = rooms;
        this.messages = messages;
        this.users = users;
        this.time = time;
        this.events = events;
        this.notifier = notifier;
        this.limiter = limiter;
        this.rateLimits = rateLimits;
    }

    /**
     * 이 사용자가 이 스터디의 채팅방을 구독·열람할 수 있는지 확인한다. 검사 순서: 스터디 없음 {@code NOT_FOUND} →
     * 참여자 아님·나간 사람 {@code FORBIDDEN}. 닫힌 방도 구독은 할 수 있다(폐쇄 이벤트를 받아야 한다).
     */
    @Transactional(readOnly = true)
    public void requireMember(long studyId, long userId) {
        StudySessions.Session session = sessions.read(studyId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!session.activeMember()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    /** 보낼 수 있는지 확인한다: {@link #requireMember} 에 더해 방이 닫혔으면 {@code CHAT_CLOSED}. */
    @Transactional(readOnly = true)
    public void requireCanSend(long studyId, long userId) {
        requireMember(studyId, userId);
        if (room(studyId).getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ChatErrorCode.CHAT_CLOSED);
        }
    }

    /**
     * 메시지를 저장하고 커밋 뒤 방 구독자에게 전달한다. 내용은 앞뒤 공백을 자르고 1~{@value #CONTENT_MAX}자여야 한다
     * ({@code VALIDATION_FAILED}).
     */
    @Transactional
    public ChatRoomService.MessageView send(long studyId, long userId, String content) {
        requireCanSend(studyId, userId);
        limiter.check("chat-send", "u" + userId, rateLimits.chatSendPerMinute(), Duration.ofMinutes(1));
        String text = content == null ? "" : content.strip();
        if (text.isEmpty() || text.length() > CONTENT_MAX) {
            throw new InvalidInputException(List.of(new FieldViolation("content",
                    "메시지는 1자 이상 " + CONTENT_MAX + "자 이하여야 합니다")));
        }
        ChatRoom room = room(studyId);
        ChatMessage saved = messages.saveAndFlush(ChatMessage.text(room.getId(), userId, text, time.now()));
        PublicUser sender = users.findPublic(Set.of(userId)).get(userId);
        ChatRoomService.MessageView view = new ChatRoomService.MessageView(saved.getId(), saved.getKind().name(),
                userId, sender == null ? null : sender.nickname(), saved.getContent(), false, false,
                saved.getCreatedAt());
        events.publishEvent(new ChatBroadcaster.MessageSent(studyId, view));
        notifier.notifyNewMessage(studyId, userId);
        return view;
    }

    private ChatRoom room(long studyId) {
        return rooms.findByStudyId(studyId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
