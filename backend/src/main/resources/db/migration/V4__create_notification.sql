-- 알림 센터와 채널별 아웃박스 (DATA-MODEL 7장, ADR-0005). 수신 설정·푸시 구독 테이블은 발송 작업(T-038 이후)에서 만든다.

-- 알림 센터의 한 건. 일정 알림은 미리 기록하고 visible_at 이 지나야 센터에 보인다 (PLAN X-10).
-- 같은 dedupe_key 로 두 번 기록하면 한 건만 남는다.
CREATE TABLE notification (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(30)  NOT NULL,
    study_id   BIGINT       NULL,
    title      VARCHAR(100) NOT NULL,
    body       VARCHAR(300) NOT NULL,
    dedupe_key VARCHAR(100) NOT NULL,
    visible_at DATETIME(6)  NOT NULL,
    read_at    DATETIME(6)  NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_dedupe_key (dedupe_key),
    KEY idx_notification_user_visible (user_id, visible_at, id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES user (id),
    CONSTRAINT fk_notification_study FOREIGN KEY (study_id) REFERENCES study (id),
    CONSTRAINT ck_notification_type CHECK (type IN
        ('STUDY_CANCELLED', 'HOST_ASSIGNED', 'WAITING_OPENED', 'CHECKIN_OPEN', 'NO_SHOW_NOTICE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 채널별 발송 건. 워커(T-038)가 PENDING 이고 next_attempt_at 이 지난 건을 읽어 보낸다.
CREATE TABLE notification_outbox (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    notification_id BIGINT       NOT NULL,
    channel         VARCHAR(10)  NOT NULL,
    dedupe_key      VARCHAR(100) NOT NULL,
    status          VARCHAR(10)  NOT NULL,
    scheduled_at    DATETIME(6)  NOT NULL,
    attempts        TINYINT      NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6)  NOT NULL,
    expires_at      DATETIME(6)  NOT NULL,
    last_error      VARCHAR(300) NULL,
    sent_at         DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_outbox_dedupe_key (dedupe_key),
    KEY idx_notification_outbox_status_next (status, next_attempt_at),
    CONSTRAINT fk_notification_outbox_notification FOREIGN KEY (notification_id) REFERENCES notification (id),
    CONSTRAINT ck_notification_outbox_channel CHECK (channel IN ('PUSH', 'EMAIL')),
    CONSTRAINT ck_notification_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_notification_outbox_window CHECK (scheduled_at <= expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
