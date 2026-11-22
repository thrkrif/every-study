-- 채팅 메시지·공지·읽음 상태와 사용자 차단 (DATA-MODEL 8·9장, PLAN T-046). 방(chat_room)은 V3 에서 만들었다.
-- 메시지 전송은 T-047 이 하고, 시스템 메시지(입장·나감·새 주최자)는 T-048 이 남긴다.

-- 메시지. 운영자가 지우면 deleted_at 만 채우고 내용은 비운다 (T-055). 시스템 메시지는 sender_id 가 NULL 이다.
CREATE TABLE chat_message (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    room_id    BIGINT        NOT NULL,
    sender_id  BIGINT        NULL,
    kind       VARCHAR(10)   NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    deleted_at DATETIME(6)   NULL,
    created_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_chat_message_room (room_id, id),
    CONSTRAINT fk_chat_message_room FOREIGN KEY (room_id) REFERENCES chat_room (id),
    CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_id) REFERENCES user (id),
    CONSTRAINT ck_chat_message_kind CHECK (kind IN ('TEXT', 'SYSTEM'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 방당 공지 1건 (PRD 4.12).
CREATE TABLE chat_notice (
    room_id    BIGINT       NOT NULL,
    content    VARCHAR(500) NOT NULL,
    updated_by BIGINT       NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (room_id),
    CONSTRAINT fk_chat_notice_room FOREIGN KEY (room_id) REFERENCES chat_room (id),
    CONSTRAINT fk_chat_notice_user FOREIGN KEY (updated_by) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 읽음 위치. 읽지 않음 개수는 last_read_message_id 보다 큰 메시지를 센다.
CREATE TABLE chat_read_state (
    room_id              BIGINT NOT NULL,
    user_id              BIGINT NOT NULL,
    last_read_message_id BIGINT NOT NULL,
    PRIMARY KEY (room_id, user_id),
    CONSTRAINT fk_chat_read_room FOREIGN KEY (room_id) REFERENCES chat_room (id),
    CONSTRAINT fk_chat_read_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 사용자 차단. 차단한 사람의 채팅 메시지를 숨기는 최소 기능이다 (PRD 4.12).
CREATE TABLE block (
    blocker_id BIGINT      NOT NULL,
    blocked_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT fk_block_blocker FOREIGN KEY (blocker_id) REFERENCES user (id),
    CONSTRAINT fk_block_blocked FOREIGN KEY (blocked_id) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
