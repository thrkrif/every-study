-- 출석 원본 이벤트 (DATA-MODEL 5장, 추가만). 나가기(T-018)가 사전·직전 취소를 처음 기록한다 (PLAN T-018).
-- 현재 판정은 (study_id, user_id) 의 가장 최근 이벤트다. 이의 신청(appeal)·체크인 테이블은 그 기능의 작업에서 만든다.
--
-- 판정 이벤트는 한 번만 남기고(판정 작업을 다시 돌려도 중복이 생기지 않게) 취소 이벤트는 여러 번 남을 수 있다.
-- 나가기→재신청→다시 나가기가 가능하기 때문이다. MySQL 에는 부분 유일 인덱스가 없어 생성 컬럼으로 우회한다 (PLAN X-9):
-- 판정 종류일 때만 값을 갖고 나머지(취소, 정정)는 NULL 이며, NULL 은 유일 제약에서 서로 다른 값으로 본다.
CREATE TABLE attendance_event (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    study_id    BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    occurred_at DATETIME(6)  NOT NULL,
    note        VARCHAR(200) NULL,
    verdict_key VARCHAR(20)  GENERATED ALWAYS AS (
        CASE WHEN type IN ('ATTENDED', 'LATE', 'NO_SHOW_CANDIDATE', 'NO_SHOW', 'VOIDED') THEN type ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_attendance_event_verdict (study_id, user_id, verdict_key),
    KEY idx_attendance_event_user_occurred (user_id, occurred_at),
    CONSTRAINT fk_attendance_event_study FOREIGN KEY (study_id) REFERENCES study (id),
    CONSTRAINT fk_attendance_event_user FOREIGN KEY (user_id) REFERENCES user (id),
    CONSTRAINT ck_attendance_event_type CHECK (type IN
        ('ATTENDED', 'LATE', 'NO_SHOW_CANDIDATE', 'NO_SHOW', 'NO_SHOW_CLEARED', 'EARLY_CANCEL', 'LATE_CANCEL',
         'VOIDED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
