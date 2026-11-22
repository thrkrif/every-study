-- 주최자 승계 이력 (DATA-MODEL 4장)과 주최자 감점 이벤트 (DATA-MODEL 6장). PLAN T-020.
-- 승계 이력은 추가만 한다. 감점은 (스터디, 주최자, 사유)당 한 번만 부과한다 (중복 부과 방지).
CREATE TABLE host_transfer (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    study_id     BIGINT      NOT NULL,
    from_user_id BIGINT      NOT NULL,
    to_user_id   BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_host_transfer_study (study_id, id),
    CONSTRAINT fk_host_transfer_study FOREIGN KEY (study_id) REFERENCES study (id),
    CONSTRAINT fk_host_transfer_from FOREIGN KEY (from_user_id) REFERENCES user (id),
    CONSTRAINT fk_host_transfer_to FOREIGN KEY (to_user_id) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE host_penalty_event (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    host_id     BIGINT      NOT NULL,
    study_id    BIGINT      NOT NULL,
    points      TINYINT     NOT NULL,
    reason      VARCHAR(20) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_host_penalty_study_host_reason (study_id, host_id, reason),
    KEY idx_host_penalty_host_occurred (host_id, occurred_at),
    CONSTRAINT fk_host_penalty_host FOREIGN KEY (host_id) REFERENCES user (id),
    CONSTRAINT fk_host_penalty_study FOREIGN KEY (study_id) REFERENCES study (id),
    CONSTRAINT ck_host_penalty_reason CHECK (reason IN ('LEFT_WITHIN_WINDOW', 'ABSENT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
