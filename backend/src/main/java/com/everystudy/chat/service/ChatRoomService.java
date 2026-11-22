package com.everystudy.chat.service;

import com.everystudy.chat.domain.ChatMessage;
import com.everystudy.chat.domain.ChatMessageKind;
import com.everystudy.chat.domain.ChatNotice;
import com.everystudy.chat.domain.ChatRoom;
import com.everystudy.chat.domain.ChatRoomStatus;
import com.everystudy.chat.repository.ChatMessageRepository;
import com.everystudy.chat.repository.ChatNoticeRepository;
import com.everystudy.chat.repository.ChatReadRepository;
import com.everystudy.chat.repository.ChatRoomRepository;
import com.everystudy.common.error.BusinessException;
import com.everystudy.common.error.CommonErrorCode;
import com.everystudy.common.time.TimeProvider;
import com.everystudy.study.service.StudySessions;
import com.everystudy.user.service.PublicUser;
import com.everystudy.user.service.UserDirectory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅방 REST (PRD 4.12, API 6장, PLAN T-046). 방 상태·공지·읽지 않음 수, 이전 대화, 공지 수정, 읽음 갱신.
 *
 * <p>접근은 스터디의 {@code ACTIVE} 참여자만 한다 ({@code chat → study} 공개 서비스로 조회). 검사 순서: 스터디 없음
 * {@code NOT_FOUND} → 참여자 아님·나간 사람 {@code FORBIDDEN}. 닫힌 방은 상태만 볼 수 있고 나머지는
 * {@code 409 CHAT_CLOSED} 다 (PRD 4.14).
 */
@Service
public class ChatRoomService {

    /** 방 상태. {@code notice} 는 없으면 {@code null}. */
    public record RoomView(String status, Notice notice, long unreadCount) {
    }

    public record Notice(String content, Instant updatedAt) {
    }

    /**
     * 메시지 한 건. 내가 차단한 사람의 메시지는 {@code hidden}, 운영자가 지운 메시지는 {@code deleted} 이고 둘 다
     * {@code content} 가 {@code null} 이다. 시스템 메시지는 {@code senderId} 가 {@code null} 이다.
     */
    public record MessageView(long id, String kind, Long senderId, String senderNickname, String content,
            boolean hidden, boolean deleted, Instant createdAt) {
    }

    /** 이전 대화 한 페이지. 최근 것부터이고, {@code nextCursor} 는 다음 요청의 {@code before} 값이다. */
    public record MessagePage(List<MessageView> items, String nextCursor) {
    }

    private final StudySessions sessions;
    private final ChatRoomRepository rooms;
    private final ChatMessageRepository messages;
    private final ChatNoticeRepository notices;
    private final ChatReadRepository reads;
    private final UserDirectory users;
    private final TimeProvider time;

    ChatRoomService(StudySessions sessions, ChatRoomRepository rooms, ChatMessageRepository messages,
            ChatNoticeRepository notices, ChatReadRepository reads, UserDirectory users, TimeProvider time) {
        this.sessions = sessions;
        this.rooms = rooms;
        this.messages = messages;
        this.notices = notices;
        this.reads = reads;
        this.users = users;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public RoomView room(long studyId, long userId) {
        ChatRoom room = roomOfMember(studyId, userId);
        Notice notice = notices.findById(room.getId()).map(n -> new Notice(n.getContent(), n.getUpdatedAt()))
                .orElse(null);
        return new RoomView(room.getStatus().name(), notice, reads.unreadCount(room.getId(), userId));
    }

    @Transactional(readOnly = true)
    public MessagePage history(long studyId, long userId, Long before, int size) {
        ChatRoom room = openRoomOfMember(studyId, userId);
        List<ChatMessage> rows = messages.findBefore(room.getId(), before == null ? Long.MAX_VALUE : before,
                PageRequest.of(0, size + 1));
        boolean hasNext = rows.size() > size;
        List<ChatMessage> page = hasNext ? rows.subList(0, size) : rows;

        Set<Long> senderIds = page.stream().map(ChatMessage::getSenderId).filter(id -> id != null)
                .collect(Collectors.toSet());
        Set<Long> blocked = reads.blockedAmong(userId, senderIds);
        Map<Long, PublicUser> senders = users.findPublic(senderIds);
        List<MessageView> items = page.stream().map(m -> view(m, blocked, senders)).toList();
        return new MessagePage(items, hasNext ? String.valueOf(page.get(page.size() - 1).getId()) : null);
    }

    /** 공지를 고정·수정한다. 현재 주최자가 {@code ACTIVE} 참여자일 때만 된다 (PLAN X-13). */
    @Transactional
    public Notice updateNotice(long studyId, long userId, String content) {
        StudySessions.Session session = memberSession(studyId, userId);
        if (session.hostId() != userId) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        ChatRoom room = openRoom(studyId);
        Instant now = time.now();
        ChatNotice notice = notices.findById(room.getId()).orElse(null);
        if (notice == null) {
            notice = ChatNotice.of(room.getId(), content, userId, now);
        } else {
            notice.update(content, userId, now);
        }
        notices.save(notice);
        return new Notice(notice.getContent(), notice.getUpdatedAt());
    }

    /**
     * 읽음 위치를 올린다. 방의 마지막 메시지보다 크면 마지막 메시지로 맞추고, 이미 더 뒤까지 읽었으면 되감지 않는다.
     */
    @Transactional
    public void markRead(long studyId, long userId, long lastReadMessageId) {
        ChatRoom room = openRoomOfMember(studyId, userId);
        long clamped = Math.min(lastReadMessageId, messages.latestId(room.getId()));
        reads.advance(room.getId(), userId, clamped);
    }

    private MessageView view(ChatMessage m, Set<Long> blocked, Map<Long, PublicUser> senders) {
        Long senderId = m.getSenderId();
        PublicUser sender = senderId == null ? null : senders.get(senderId);
        boolean hidden = senderId != null && blocked.contains(senderId);
        boolean deleted = m.isDeleted();
        String content = hidden || deleted ? null : m.getContent();
        String kind = m.getKind() == ChatMessageKind.SYSTEM ? "SYSTEM" : "TEXT";
        return new MessageView(m.getId(), kind, senderId, sender == null ? null : sender.nickname(), content, hidden,
                deleted, m.getCreatedAt());
    }

    private StudySessions.Session memberSession(long studyId, long userId) {
        StudySessions.Session session = sessions.read(studyId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!session.activeMember()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return session;
    }

    private ChatRoom roomOfMember(long studyId, long userId) {
        memberSession(studyId, userId);
        return rooms.findByStudyId(studyId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private ChatRoom openRoomOfMember(long studyId, long userId) {
        ChatRoom room = roomOfMember(studyId, userId);
        if (room.getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ChatErrorCode.CHAT_CLOSED);
        }
        return room;
    }

    private ChatRoom openRoom(long studyId) {
        ChatRoom room = rooms.findByStudyId(studyId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (room.getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ChatErrorCode.CHAT_CLOSED);
        }
        return room;
    }
}
