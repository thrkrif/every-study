-- 운영자 검토 대상 표시 (PLAN T-035b, PRD 4.13C). 주최자 합계가 검토 단계에 이르러 개설 제한을 걸 때 켠다.
-- 조회·처리는 T-055 가 한다. 알림 종류 HOST_WARNING 도 함께 허용한다.
ALTER TABLE user_sanction ADD COLUMN review_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notification DROP CHECK ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type CHECK (type IN
    ('STUDY_CANCELLED', 'HOST_ASSIGNED', 'WAITING_OPENED', 'CHECKIN_OPEN', 'NO_SHOW_NOTICE',
     'SANCTION_WARNING', 'SANCTION_APPLIED', 'HOST_WARNING'));
