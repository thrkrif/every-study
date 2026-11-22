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

    @Column(name = "closed_at")
    private Instant closedAt;

    /** 이 시각 이후 메시지를 영구 삭제한다 (PRD 4.14 보관 기간). */
    @Column(name = "purge_after")
    private Instant purgeAfter;

    /** 삭제를 끝낸 시각. 있으면 다시 처리하지 않는다. */
    @Column(name = "purged_at")
    private Instant purgedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatRoom() {
    }

    /**
     * 방을 닫는다 (스터디 종료·파투, PRD 4.14). 이미 닫혔으면 처음 시각을 그대로 둔다(멱등).
     *
     * @return 이번에 닫았으면 {@code true}
     */
    public boolean close(Instant now, Instant purgeAfter) {
        if (status == ChatRoomStatus.CLOSED) {
            return false;
        }
        this.status = ChatRoomStatus.CLOSED;
        this.closedAt = now;
        this.purgeAfter = purgeAfter;
        return true;
    }

    /** 메시지 삭제를 끝냈다고 표시한다. */
    public void markPurged(Instant now) {
        this.purgedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getStudyId() {
        return studyId;
    }

    public Instant getPurgedAt() {
        return purgedAt;
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
