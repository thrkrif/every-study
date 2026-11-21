-- 정원이 찬 스터디의 대기 (DATA-MODEL 4장, PRD 4.14). 대기 취소는 행 삭제이고 순번은 없다.
-- 체크인 오픈 이후의 행은 조회·알림에서 무효로 보고, 삭제는 스터디 종료·파투 처리 때 한다 (PLAN X-16).
CREATE TABLE study_waiting (
    study_id   BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (study_id, user_id),
    KEY idx_study_waiting_user (user_id),
    CONSTRAINT fk_study_waiting_study FOREIGN KEY (study_id) REFERENCES study (id),
    CONSTRAINT fk_study_waiting_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
