-- 참여자 제재 알림 종류 추가 (PLAN T-035a, X-4). 경고는 제재 행 없이 알림만 보내고(PRD 4.8),
-- 제한·정지는 부여했다는 알림을 보낸다. 병합된 마이그레이션은 고치지 않으므로 CHECK 제약을 새로 건다.
ALTER TABLE notification DROP CHECK ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type CHECK (type IN
    ('STUDY_CANCELLED', 'HOST_ASSIGNED', 'WAITING_OPENED', 'CHECKIN_OPEN', 'NO_SHOW_NOTICE',
     'SANCTION_WARNING', 'SANCTION_APPLIED'));
