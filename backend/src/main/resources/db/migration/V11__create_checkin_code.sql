-- 일회용 체크인 코드 (DATA-MODEL 5장, PLAN T-028·X-28). 스터디(세션)당 1행이고 재발급하면 같은 행을 덮어써 이전 코드는 무효가 된다.
-- 주최자 체크인이 성공한 뒤에만 발급하며, 수명은 체크인 마감 시각까지다. 입력 실패 횟수는 메모리에서 센다 (ADR-0005).
CREATE TABLE checkin_code (
    study_id   BIGINT      NOT NULL,
    code       CHAR(6)     NOT NULL,
    issued_at  DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (study_id),
    CONSTRAINT fk_checkin_code_study FOREIGN KEY (study_id) REFERENCES study (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
