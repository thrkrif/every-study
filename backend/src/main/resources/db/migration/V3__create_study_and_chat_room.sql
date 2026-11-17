-- 스터디·희망 직군·참여자 (DATA-MODEL 4장)와 채팅방 (DATA-MODEL 8장). 대기·승계·메시지 테이블은 그 기능의 작업에서 만든다.

-- 하루 한 번의 모임이라 일정과 장소를 이 행에 둔다. 정원 상한은 설정값이라 CHECK 로 두지 않는다 (하한 2 만).
CREATE TABLE study (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    host_id             BIGINT        NOT NULL,
    kind                VARCHAR(10)   NOT NULL,
    title               VARCHAR(60)   NOT NULL,
    description         TEXT          NOT NULL,
    topic_code          VARCHAR(30)   NOT NULL,
    district_code       VARCHAR(20)   NOT NULL,
    place_name          VARCHAR(100)  NOT NULL,
    place_address       VARCHAR(200)  NOT NULL,
    latitude            DECIMAL(9, 6) NOT NULL,
    longitude           DECIMAL(9, 6) NOT NULL,
    start_at            DATETIME(6)   NOT NULL,
    duration_min        SMALLINT      NOT NULL,
    session_date        DATE          NOT NULL,
    capacity            TINYINT       NOT NULL,
    min_attendance_rate TINYINT       NULL,
    allow_newcomer      BOOLEAN       NOT NULL DEFAULT TRUE,
    status              VARCHAR(10)   NOT NULL,
    cancel_reason       VARCHAR(20)   NULL,
    judged_at           DATETIME(6)   NULL,
    voided_at           DATETIME(6)   NULL,
    ended_at            DATETIME(6)   NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_study_status_district_start (status, district_code, start_at),
    KEY idx_study_host_status (host_id, status),
    KEY idx_study_status_session_date (status, session_date),
    KEY idx_study_status_judged_start (status, judged_at, start_at),
    CONSTRAINT fk_study_host FOREIGN KEY (host_id) REFERENCES user (id),
    CONSTRAINT ck_study_capacity CHECK (capacity >= 2),
    CONSTRAINT ck_study_kind CHECK (kind IN ('OFFLINE')),
    CONSTRAINT ck_study_status CHECK (status IN ('ACTIVE', 'ENDED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 희망 직군. 행이 없으면 직군 무관이다 (PRD 4.2).
CREATE TABLE study_target_job (
    study_id BIGINT      NOT NULL,
    job_code VARCHAR(30) NOT NULL,
    PRIMARY KEY (study_id, job_code),
    CONSTRAINT fk_study_target_job_study FOREIGN KEY (study_id) REFERENCES study (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 주최자도 ACTIVE 참여자로 들어간다. 같은 사람은 한 스터디에 한 행 (재신청은 같은 행을 되살린다. ADR-0004 이중 방어선).
CREATE TABLE study_member (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    study_id    BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    status      VARCHAR(10) NOT NULL,
    joined_at   DATETIME(6) NOT NULL,
    left_at     DATETIME(6) NULL,
    cancel_type VARCHAR(10) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_study_member_study_user (study_id, user_id),
    KEY idx_study_member_user_status (user_id, status),
    CONSTRAINT fk_study_member_study FOREIGN KEY (study_id) REFERENCES study (id),
    CONSTRAINT fk_study_member_user FOREIGN KEY (user_id) REFERENCES user (id),
    CONSTRAINT ck_study_member_status CHECK (status IN ('ACTIVE', 'LEFT')),
    CONSTRAINT ck_study_member_cancel_type CHECK (cancel_type IS NULL OR cancel_type IN ('EARLY', 'LATE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 스터디 전용 채팅방. 스터디당 하나이고 개설과 같은 트랜잭션에서 만든다 (PRD 4.12, PLAN X-8).
CREATE TABLE chat_room (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    study_id    BIGINT      NOT NULL,
    status      VARCHAR(10) NOT NULL,
    closed_at   DATETIME(6) NULL,
    purge_after DATETIME(6) NULL,
    purged_at   DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_room_study (study_id),
    CONSTRAINT fk_chat_room_study FOREIGN KEY (study_id) REFERENCES study (id),
    CONSTRAINT ck_chat_room_status CHECK (status IN ('OPEN', 'CLOSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
