package com.everystudy.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 방의 공지 (DATA-MODEL 8장, PRD 4.12). 방당 한 건이고 주최자가 고정·수정한다. */
@Entity
@Table(name = "chat_notice")
public class ChatNotice {

    @Id
    @Column(name = "room_id")
    private Long roomId;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChatNotice() {
    }

    public static ChatNotice of(long roomId, String content, long updatedBy, Instant now) {
        ChatNotice notice = new ChatNotice();
        notice.roomId = roomId;
        notice.update(content, updatedBy, now);
        return notice;
    }

    public void update(String content, long updatedBy, Instant now) {
        this.content = content;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public String getContent() {
        return content;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
