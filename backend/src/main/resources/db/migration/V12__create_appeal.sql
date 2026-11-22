-- 이의 신청 (DATA-MODEL 5장, PLAN T-034). 노쇼 후보·지각 판정당 1건이고, 증빙은 MVP 에서 텍스트 사유만 받는다.
-- 운영자 결정(status, decided_by, decided_at)은 운영자 작업(T-056)이 채운다.
CREATE TABLE appeal (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    attendance_event_id BIGINT       NOT NULL,
    study_id            BIGINT       NOT NULL,
    user_id             BIGINT       NOT NULL,
    reason              VARCHAR(500) NOT NULL,
    evidence_url        VARCHAR(500) NULL,
    host_opinion        VARCHAR(500) NULL,
    status              VARCHAR(10)  NOT NULL,
    decided_by          BIGINT       NULL,
    due_at              DATETIME(6)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    decided_at          DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_appeal_event (attendance_event_id),
    KEY idx_appeal_user (user_id, id),
    CONSTRAINT fk_appeal_event FOREIGN KEY (attendance_event_id) REFERENCES attendance_event (id),
    CONSTRAINT fk_appeal_study FOREIGN KEY (study_id) REFERENCES study (id),
    CONSTRAINT fk_appeal_user FOREIGN KEY (user_id) REFERENCES user (id),
    CONSTRAINT fk_appeal_decider FOREIGN KEY (decided_by) REFERENCES user (id),
    CONSTRAINT ck_appeal_status CHECK (status IN ('OPEN', 'ACCEPTED', 'REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
