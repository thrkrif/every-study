-- 알림 수신 설정과 건너뜀 상태 (PLAN T-043, D-6, PRD 4.5). 행이 없으면 켜짐이 기본이다.
CREATE TABLE notification_setting (
    user_id    BIGINT      NOT NULL,
    channel    VARCHAR(10) NOT NULL,
    enabled    BOOLEAN     NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, channel),
    CONSTRAINT fk_notification_setting_user FOREIGN KEY (user_id) REFERENCES user (id),
    CONSTRAINT ck_notification_setting_channel CHECK (channel IN ('PUSH', 'EMAIL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 수신 설정이 꺼져 있어 보내지 않은 건은 취소와 구분해 SKIPPED 로 남긴다 (D-6).
ALTER TABLE notification_outbox DROP CHECK ck_notification_outbox_status;
ALTER TABLE notification_outbox ADD CONSTRAINT ck_notification_outbox_status CHECK (status IN
    ('PENDING', 'SENT', 'FAILED', 'CANCELLED', 'SKIPPED'));
