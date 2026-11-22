-- 신고 (PLAN T-053, DATA-MODEL 9장). 스터디·사용자·채팅 메시지 신고가 한 큐에 쌓이고 운영자가 반려·처리 완료한다.
CREATE TABLE report (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT       NOT NULL,
    target_type VARCHAR(10)  NOT NULL,
    target_id   BIGINT       NOT NULL,
    reason      VARCHAR(30)  NOT NULL,
    detail      VARCHAR(500) NULL,
    status      VARCHAR(10)  NOT NULL,
    handled_by  BIGINT       NULL,
    handle_note VARCHAR(300) NULL,
    created_at  DATETIME(6)  NOT NULL,
    handled_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_report_status_created (status, created_at),
    KEY idx_report_reporter_target (reporter_id, target_type, target_id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES user (id),
    CONSTRAINT fk_report_handled_by FOREIGN KEY (handled_by) REFERENCES user (id),
    CONSTRAINT ck_report_target_type CHECK (target_type IN ('STUDY', 'USER', 'MESSAGE')),
    CONSTRAINT ck_report_reason CHECK (reason IN
        ('SPAM_AD', 'ABUSE', 'INAPPROPRIATE_RECRUIT', 'HARASSMENT', 'FRAUD', 'OTHER')),
    CONSTRAINT ck_report_status CHECK (status IN ('OPEN', 'DISMISSED', 'RESOLVED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
