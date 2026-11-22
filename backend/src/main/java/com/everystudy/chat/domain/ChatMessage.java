package com.everystudy.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 채팅 메시지 한 건 (DATA-MODEL 8장). 보내기는 T-047, 시스템 메시지는 T-048, 운영자 삭제는 T-055 가 더한다. */
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    /** 시스템 메시지는 {@code null}. */
    @Column(name = "sender_id")
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private ChatMessageKind kind;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessage() {
    }

    /** 참여자가 보낸 텍스트 메시지. */
    public static ChatMessage text(long roomId, long senderId, String content, Instant now) {
        ChatMessage message = new ChatMessage();
        message.roomId = roomId;
        message.senderId = senderId;
        message.kind = ChatMessageKind.TEXT;
        message.content = content;
        message.createdAt = now;
        return message;
    }

    /** 입장·나감·새 주최자 안내. 보낸 사람이 없다 (DATA-MODEL 8장). */
    public static ChatMessage system(long roomId, String content, Instant now) {
        ChatMessage message = new ChatMessage();
        message.roomId = roomId;
        message.senderId = null;
        message.kind = ChatMessageKind.SYSTEM;
        message.content = content;
        message.createdAt = now;
        return message;
    }

    public Long getId() {
        return id;
    }

    public Long getSenderId() {
        return senderId;
    }

    public ChatMessageKind getKind() {
        return kind;
    }

    public String getContent() {
        return content;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
