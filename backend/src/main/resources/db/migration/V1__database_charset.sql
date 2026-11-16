-- 이 데이터베이스에 만드는 테이블의 기본 문자셋을 utf8mb4 로 고정한다 (ADR-0001).
-- 테이블은 그 테이블을 처음 쓰는 작업의 마이그레이션에서 만든다 (PLAN 진행 규칙 5).
ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
