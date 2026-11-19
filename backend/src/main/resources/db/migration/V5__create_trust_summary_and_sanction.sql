-- 출석 지표 캐시와 제재 (DATA-MODEL 6장). 참여 신청이 이용 제한·입장 기준을 검사하려고 처음 만든다 (PLAN T-015).
-- 원본 이벤트(attendance_event)와 주최자 감점(host_penalty_event)은 그 기능의 작업에서 만든다.

-- 사용자별 최근 N회 집계. 원본은 attendance_event 이고 정책이 바뀌면 다시 계산한다 (PRD 4.7).
-- 행이 없거나 counted 가 신규 기준 미만이면 신규로 본다. 신규 조건이면 rate 는 NULL 이다.
CREATE TABLE user_trust_summary (
    user_id         BIGINT       NOT NULL,
    counted         SMALLINT     NOT NULL,
    attended        SMALLINT     NOT NULL,
    late            SMALLINT     NOT NULL,
    no_show         SMALLINT     NOT NULL,
    late_cancel     SMALLINT     NOT NULL,
    rate            DECIMAL(5, 2) NULL,
    recalculated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_trust_summary_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 참여 신청 제한·이용 정지·개설 제한. starts_at <= now < ends_at 이고 revoked_at 이 비어 있으면 유효하다.
-- 경고 단계는 제재가 아니라 알림이라 행을 만들지 않는다 (PRD 4.8).
CREATE TABLE user_sanction (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    kind       VARCHAR(20)  NOT NULL,
    source     VARCHAR(10)  NOT NULL,
    reason     VARCHAR(200) NOT NULL,
    starts_at  DATETIME(6)  NOT NULL,
    ends_at    DATETIME(6)  NOT NULL,
    revoked_at DATETIME(6)  NULL,
    created_by BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_user_sanction_user_kind_ends (user_id, kind, ends_at),
    CONSTRAINT fk_user_sanction_user FOREIGN KEY (user_id) REFERENCES user (id),
    CONSTRAINT fk_user_sanction_created_by FOREIGN KEY (created_by) REFERENCES user (id),
    CONSTRAINT ck_user_sanction_kind CHECK (kind IN ('JOIN_RESTRICT', 'SUSPEND', 'HOST_RESTRICT')),
    CONSTRAINT ck_user_sanction_source CHECK (source IN ('AUTO', 'OPERATOR'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
