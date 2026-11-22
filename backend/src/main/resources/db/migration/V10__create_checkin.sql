-- 체크인 (DATA-MODEL 5장, PLAN T-027). 한 사람이 한 스터디에 한 번만 체크인하고(멱등), 주최자도 같은 테이블이다.
-- 원본 좌표는 저장하지 않고 판정에 쓴 거리·정확도·방식만 남긴다 (PRD 8장 저장 최소화).
CREATE TABLE checkin (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    study_id      BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    checked_in_at DATETIME(6) NOT NULL,
    method        VARCHAR(10) NOT NULL,
    result        VARCHAR(10) NOT NULL,
    distance_m    SMALLINT    NULL,
    accuracy_m    SMALLINT    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_checkin_study_user (study_id, user_id),
    KEY idx_checkin_user (user_id, checked_in_at),
    CONSTRAINT fk_checkin_study FOREIGN KEY (study_id) REFERENCES study (id),
    CONSTRAINT fk_checkin_user FOREIGN KEY (user_id) REFERENCES user (id),
    CONSTRAINT ck_checkin_method CHECK (method IN ('LOCATION', 'CODE')),
    CONSTRAINT ck_checkin_result CHECK (result IN ('ATTENDED', 'LATE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
