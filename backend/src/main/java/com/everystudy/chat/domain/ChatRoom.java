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

/** 스터디 전용 채팅방 (DATA-MODEL 8장, PRD 4.12). 스터디당 하나이고 개설과 함께 만들어진다. */
@Entity
@Table(name = "chat_room")
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false, unique = true)
    private Long studyId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private ChatRoomStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatRoom() {
    }

    public Long getId() {
        return id;
    }

    public Long getStudyId() {
        return studyId;
    }

    public ChatRoomStatus getStatus() {
        return status;
    }

    public static ChatRoom openFor(long studyId, Instant now) {
        ChatRoom room = new ChatRoom();
        room.studyId = studyId;
        room.status = ChatRoomStatus.OPEN;
        room.createdAt = now;
        return room;
    }
}
