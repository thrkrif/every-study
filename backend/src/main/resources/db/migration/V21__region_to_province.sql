-- 지역을 구 단위(수원시 4개 구)에서 전국 시·도 한 단계로 바꾼다 (PRD 1장·4.1, DATA-MODEL 1장).
-- 기존 값은 모두 수원시의 구였으므로 경기(GYEONGGI)로 옮긴다. 컬럼 이름 district_code 는 그대로 둔다.
UPDATE study SET district_code = 'GYEONGGI' WHERE district_code LIKE 'SUWON%';
UPDATE user  SET district_code = 'GYEONGGI' WHERE district_code LIKE 'SUWON%';
