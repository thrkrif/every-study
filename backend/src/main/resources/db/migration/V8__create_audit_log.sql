-- 감사 로그 (DATA-MODEL 9장, PLAN T-019). 추가만 한다. 수정·삭제 경로를 만들지 않는다.
-- actor_id 가 NULL 이면 시스템(자동 감점·제재·판정 확정 등)이 한 행위다 (PLAN D-17).
-- action 은 코드의 AuditAction 이름이다. 종류가 늘 때마다 마이그레이션을 만들지 않으려고 CHECK 를 두지 않는다.
CREATE TABLE audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT       NULL,
    action      VARCHAR(40)  NOT NULL,
    target_type VARCHAR(30)  NOT NULL,
    target_id   BIGINT       NOT NULL,
    detail      JSON         NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_log_target (target_type, target_id, id),
    KEY idx_audit_log_actor (actor_id, id),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_id) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
