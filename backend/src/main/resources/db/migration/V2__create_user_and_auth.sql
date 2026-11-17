-- 회원과 인증 테이블 (DATA-MODEL 3장, ADR-0002).

-- 닉네임·직군·지역은 온보딩에서 채운다. 소셜 로그인 직후에는 비어 있다(NULL).
-- 닉네임은 중복 불허. NULL 은 UNIQUE 에서 서로 충돌하지 않는다 (온보딩 전, 탈퇴 후).
CREATE TABLE user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    nickname      VARCHAR(20)  NULL,
    email         VARCHAR(255) NULL,
    job_code      VARCHAR(30)  NULL,
    district_code VARCHAR(20)  NULL,
    role          VARCHAR(10)  NOT NULL,
    status        VARCHAR(12)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    withdrawn_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_nickname (nickname)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 소셜 공급자의 사용자 식별값. 소셜 토큰은 저장하지 않는다.
CREATE TABLE social_account (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(10)  NOT NULL,
    provider_user_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_social_account_provider_user (provider, provider_user_id),
    KEY idx_social_account_user (user_id),
    CONSTRAINT fk_social_account_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 리프레시 토큰. 원문은 저장하지 않고 SHA-256 해시(16진수 64자)만 둔다.
CREATE TABLE refresh_token (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    family_id  CHAR(36)    NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_token_user (user_id),
    KEY idx_refresh_token_family (family_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
