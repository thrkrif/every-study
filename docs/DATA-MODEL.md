---
status: draft
version: "0.2"
based_on:
  - docs/PRD.md@0.6
  - docs/ARCHITECTURE.md@0.4
---

# 데이터 명세

테이블, 컬럼, 제약, 인덱스를 정한다. **정책 수치(시간, 반경, 점수, 기간)는 PRD가 원본이므로 여기에 값을 적지 않고** `PRD 4.6`처럼 위치만 가리킨다. 구조 원칙(락, 아웃박스, UTC 등)은 [ARCHITECTURE 4~5장](ARCHITECTURE.md)을 따른다. 실제 DDL은 구현 시 Flyway 마이그레이션(`V<번호>__<설명>.sql`)으로 만든다.

## 1. 공통 규칙

| 항목 | 규칙 |
|---|---|
| DB / 문자셋 | MySQL 8, `utf8mb4` (ADR-0001) |
| 기본키 | `id BIGINT AUTO_INCREMENT`. 복합키가 자연스러운 연결 테이블만 복합 PK |
| 시각 | `DATETIME(6)`, **UTC** 저장. 컬럼 이름은 `*_at` |
| 날짜 | KST 기준 날짜는 `DATE` (`session_date`). 설명은 해당 컬럼에 적는다 |
| 열거형 | `VARCHAR`에 대문자 코드 저장 (MySQL `ENUM` 미사용: 값 추가가 마이그레이션이 되는 것을 피함). 허용 값은 코드의 enum과 `CHECK` 제약으로 맞춘다 |
| 고정 목록 | 주제, 직군, 지역(구)은 테이블이 아니라 **코드 값**이다. 목록 자체는 `GET /meta/options`로 내려준다 (API 명세). 값 추가는 코드 변경이다 |
| 삭제 | 기본은 물리 삭제 없이 상태 컬럼을 바꾼다. 예외는 각 테이블에 적는다 |
| 낙관적 잠금 | 쓰지 않는다. 멤버 구성은 `study` 행 비관적 락으로 직렬화한다 (ADR-0004) |
| `created_at` / `updated_at` | 모든 테이블에 `created_at`. 수정이 있는 테이블만 `updated_at` |
| 좌표 | `DECIMAL(9,6)` 위도·경도 (ADR-0001) |

## 2. 테이블 지도

```
user ── social_account, refresh_token, notification_setting, push_subscription, notification
 │
 ├─ study ── study_target_job, study_member, study_waiting, host_transfer, checkin_code
 │     │      └─ chat_room ── chat_message, chat_notice, chat_read_state
 │     └─ checkin, attendance_event ── appeal
 │
 ├─ user_trust_summary, host_penalty_event, user_sanction
 └─ report, block, notification_outbox, audit_log
```

## 3. 회원

### `user`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| nickname | VARCHAR(20) | 탈퇴 시 비운다 (화면은 "탈퇴한 사용자") |
| email | VARCHAR(255) NULL | 이메일 알림용. 탈퇴 시 삭제 |
| job_code | VARCHAR(30) | 내 직군 (단일 선택) |
| district_code | VARCHAR(20) | 활동 지역(구) |
| role | VARCHAR(10) | `USER` / `ADMIN` |
| status | VARCHAR(12) | `ACTIVE` / `WITHDRAWN` |
| created_at, withdrawn_at | DATETIME(6) | |

- 닉네임은 **중복 불허**(`UNIQUE (nickname)`, 확정). 탈퇴 시 값을 비우므로(`NULL`) 충돌하지 않는다.
- 탈퇴: 개인정보 컬럼만 비우고 행과 `id`는 남긴다. 다른 테이블의 `user_id`는 그대로 둔다 (PRD 4.1, ARCHITECTURE 5장).

### `social_account`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT FK | |
| provider | VARCHAR(10) | `KAKAO` / `GOOGLE` |
| provider_user_id | VARCHAR(100) | 공급자의 사용자 식별값 |

- `UNIQUE (provider, provider_user_id)`. **탈퇴 시 이 행을 삭제**한다 (식별값 삭제, PRD 4.1).
- 한 사용자가 여러 공급자를 연결할 수 있는지는 MVP에서 1:1로 둔다 (한 `user`에 `social_account` 1행, 제안).

### `refresh_token` (ADR-0002)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT FK | |
| family_id | CHAR(36) | 로그인 한 번에 하나. 재사용 탐지 시 가족 전체를 폐기 |
| token_hash | CHAR(64) UNIQUE | 토큰 원문은 저장하지 않고 해시만 |
| expires_at | DATETIME(6) | |
| used_at | DATETIME(6) NULL | 회전으로 소진된 시각 |
| revoked_at | DATETIME(6) NULL | |

- 인덱스 `(user_id)`, `(family_id)`. 만료된 행은 스케줄러가 주기적으로 지운다 (스케줄러 작업 목록에 포함, ARCHITECTURE 4.7).

## 4. 스터디

### `study`
하루 한 번의 모임이므로 일정과 장소를 이 테이블에 둔다 (ARCHITECTURE 5장).

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| host_id | BIGINT FK(user) | 현재 주최자. 승계되면 바뀜 |
| kind | VARCHAR(10) | `OFFLINE` 고정. 이후 비대면 추가를 위한 필드 (PRD 4.2) |
| title | VARCHAR(60) | |
| description | TEXT | |
| topic_code | VARCHAR(30) | |
| district_code | VARCHAR(20) | |
| place_name | VARCHAR(100) | 카카오맵에서 고른 장소명 |
| place_address | VARCHAR(200) | |
| latitude, longitude | DECIMAL(9,6) | 체크인 거리 계산 기준 |
| start_at | DATETIME(6) | 시작 시각 (UTC) |
| duration_min | SMALLINT | 진행 시간(분). 같은 날 안에 끝나야 한다 |
| session_date | DATE | `start_at`의 **KST 날짜**. 종료 처리와 목록 조회에 쓴다 |
| capacity | TINYINT | 정원 (주최자 포함). 범위는 PRD 4.14 |
| min_attendance_rate | TINYINT NULL | 입장 기준: 최소 출석률(%). `NULL`이면 기준 없음 |
| allow_newcomer | BOOLEAN | 입장 기준: 신규 허용 (기본 `TRUE`) |
| status | VARCHAR(10) | `ACTIVE` / `ENDED` / `CANCELLED` (PRD 4.14) |
| cancel_reason | VARCHAR(20) NULL | `HOST_LEFT`(주최자만 남고 포기), `OPERATOR`(강제 종료) |
| judged_at | DATETIME(6) NULL | 출석 판정 완료 시각. 노쇼 후보 판정 작업의 멱등 표시 |
| voided_at | DATETIME(6) NULL | 주최자 미도착으로 **세션 무효** 처리한 시각 (PRD 4.6) |
| ended_at | DATETIME(6) NULL | 종료 처리 시각 |
| created_at, updated_at | DATETIME(6) | |

- "모집 중 / 마감"은 저장하지 않는다. `status='ACTIVE'`, 체크인 오픈 전, 현재 참여자 수 < `capacity`로 **계산**한다 (ARCHITECTURE 4.2).
- 인덱스
  - `(status, district_code, start_at)` 목록 탐색
  - `(host_id, status)` 내 주최 스터디
  - `(status, session_date)` 종료 처리 작업 (`ACTIVE`이고 `session_date`가 오늘(KST) 이전)
  - `(status, judged_at, start_at)` 출석 판정 작업
- `CHECK`: `capacity BETWEEN 2 AND 6`은 쓰지 않는다. 상한이 설정값이라 DB 제약이 아니라 애플리케이션에서 검사한다 (PRD 4.14). 하한 2만 `CHECK (capacity >= 2)`로 둔다.

### `study_target_job`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| study_id | BIGINT FK | |
| job_code | VARCHAR(30) | 희망 직군. "직군 무관"은 코드 `ANY` 한 행 |

- PK `(study_id, job_code)`. 안내용이며 신청을 막지 않는다 (PRD 4.4).

### `study_member`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| study_id | BIGINT FK | |
| user_id | BIGINT FK | |
| status | VARCHAR(10) | `ACTIVE` / `LEFT` |
| joined_at | DATETIME(6) | 참여 확정 시각 |
| left_at | DATETIME(6) NULL | |
| cancel_type | VARCHAR(10) NULL | 나갈 때 판정: `EARLY`(사전) / `LATE`(직전). 기준 시간은 PRD 4.6 |

- `UNIQUE (study_id, user_id)` (ADR-0004 이중 방어선). **주최자도 `study_member`에 `ACTIVE`로 들어간다.** 정원 계산과 채팅 권한을 한 규칙으로 처리하기 위해서다. 주최자 여부는 `study.host_id`로 판단한다.
- 나간 뒤 다시 신청하면 같은 행을 `ACTIVE`로 되돌린다 (재신청 허용, 확정). 나간 이력은 `attendance_event`에 남아 있다.
- 인덱스 `(user_id, status)` 내 참여 스터디.

### `study_waiting`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| study_id | BIGINT FK | |
| user_id | BIGINT FK | |
| created_at | DATETIME(6) | |

- PK `(study_id, user_id)`. 대기 취소는 **행 삭제**, 스터디가 종료·파투되거나 체크인이 열리면 해당 스터디의 행을 지운다 (PRD 4.14). 대기 순번은 없다.
- 자리가 났을 때 알림 대상은 이 테이블의 해당 스터디 행 전부이다 (ARCHITECTURE 11장).

### `host_transfer`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| study_id | BIGINT FK | |
| from_user_id, to_user_id | BIGINT FK | |
| created_at | DATETIME(6) | |

- 승계 이력(추가만). 같은 트랜잭션에서 `study.host_id`를 바꾼다 (ADR-0004).

## 5. 출석

### `checkin`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| study_id | BIGINT FK | |
| user_id | BIGINT FK | 주최자도 같은 테이블 |
| checked_in_at | DATETIME(6) | **서버 시각** |
| method | VARCHAR(10) | `LOCATION` / `CODE` |
| result | VARCHAR(10) | `ATTENDED` / `LATE` |
| distance_m | SMALLINT NULL | 판정 시 거리. `CODE`면 `NULL` |
| accuracy_m | SMALLINT NULL | 클라이언트가 보고한 GPS 정확도 |

- `UNIQUE (study_id, user_id)`로 멱등 (ARCHITECTURE 4.4). **원본 좌표는 저장하지 않고** 거리·정확도·방식만 남긴다 (PRD 8장).

### `checkin_code`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| study_id | BIGINT PK FK | 스터디당 1개 |
| code | CHAR(6) | 일회용 코드 (자릿수는 제안) |
| issued_at, expires_at | DATETIME(6) | |

- 주최자 체크인이 성공한 뒤에만 발급한다 (PRD 4.6). 입력 실패 횟수 제한은 서버 1대 가정으로 메모리에서 센다 (서버 증설 시 DB·외부 저장소로 이동, ADR-0005).

### `attendance_event` (추가만, 수정·삭제 없음)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| study_id | BIGINT FK | |
| user_id | BIGINT FK | 대상 참여자 (주최자의 불참은 `host_penalty_event`) |
| type | VARCHAR(20) | 아래 표 |
| occurred_at | DATETIME(6) | |
| note | VARCHAR(200) NULL | 운영자 정정 사유 등 |

| type | 의미 | 지표 반영 (PRD 4.7) |
|---|---|---|
| `ATTENDED` | 출석 | 분자·분모 |
| `LATE` | 지각 | 분자·분모 |
| `NO_SHOW_CANDIDATE` | 노쇼 후보 (통지, 이의 기간 시작) | 반영 안 함 |
| `NO_SHOW` | 노쇼 확정 | 분모 |
| `NO_SHOW_CLEARED` | 이의 인정으로 노쇼·지각 판정 취소 | 직전 판정을 무효화 |
| `EARLY_CANCEL` | 사전 취소 | 분모 제외 |
| `LATE_CANCEL` | 직전 취소 | 분모 |
| `VOIDED` | 주최자 귀책(세션 무효, 주최자 포기)·파투로 진행 안 됨 | 분모 제외 |

- **현재 판정은 (study_id, user_id)의 가장 최근 이벤트**다. 이의 인정 시 정정 이벤트를 **추가**한다 (ARCHITECTURE 4.5).
- `UNIQUE (study_id, user_id, type)`로 판정 작업을 재실행해도 중복이 생기지 않게 한다. 단 정정이 여러 번 필요한 경우를 위해 `NO_SHOW_CLEARED`는 예외로 하고 `appeal` 쪽에서 1회를 보장한다 (이의는 판정당 1회).
- 인덱스 `(user_id, occurred_at)` 최근 N회 집계.

### `appeal`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| attendance_event_id | BIGINT FK UNIQUE | 대상 판정. 판정당 이의 1건 |
| study_id, user_id | BIGINT FK | |
| reason | VARCHAR(500) | |
| evidence_url | VARCHAR(500) NULL | 증빙 (MVP는 텍스트만 받고 이 컬럼은 비워 둔다, 파일 업로드는 후속) |
| host_opinion | VARCHAR(500) NULL | 1차 주최자 의견 |
| status | VARCHAR(10) | `OPEN` / `ACCEPTED` / `REJECTED` |
| decided_by | BIGINT NULL FK(user) | 운영자 |
| due_at | DATETIME(6) | 이의 기간 마감 (PRD 4.9) |
| created_at, decided_at | DATETIME(6) | |

## 6. 신뢰 지표와 제재

### `user_trust_summary` (캐시)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| user_id | BIGINT PK | |
| counted | SMALLINT | 집계 대상 세션 수 (최근 N회) |
| attended, late, no_show, late_cancel | SMALLINT | |
| rate | DECIMAL(5,2) NULL | 출석률. 신규 조건이면 `NULL` |
| recalculated_at | DATETIME(6) | |

- 원본은 `attendance_event`다. 이벤트가 쌓일 때 갱신하고, 정책이 바뀌면 전체 재계산한다 (PRD 4.7). 신규 여부는 `counted`가 기준 미만인지로 판단한다.

### `host_penalty_event`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| host_id | BIGINT FK | |
| study_id | BIGINT FK | |
| points | TINYINT | 감점 (값은 PRD 4.13B) |
| reason | VARCHAR(20) | `LEFT_WITHIN_WINDOW`(3시간 이내 이탈) / `ABSENT`(미도착) |
| occurred_at | DATETIME(6) | |

- `UNIQUE (study_id, host_id, reason)`로 중복 부과를 막는다. 롤링 기간 합계는 **조회 시 계산**한다 (ARCHITECTURE 4.5). 표시용 "주최 N회 중 직전 이탈 N회"는 `study`와 이 테이블로 센다.

### `user_sanction`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT FK | |
| kind | VARCHAR(20) | `JOIN_RESTRICT`(참여 신청 제한) / `SUSPEND`(이용 정지) / `HOST_RESTRICT`(개설 제한) |
| source | VARCHAR(10) | `AUTO` / `OPERATOR` |
| reason | VARCHAR(200) | |
| starts_at, ends_at | DATETIME(6) | 기간은 PRD 4.8, 4.13C |
| revoked_at | DATETIME(6) NULL | 운영자 해제 |
| created_by | BIGINT NULL FK(user) | 수동 조치자 |

- 신청·개설 시점에 `starts_at <= now < ends_at AND revoked_at IS NULL`인 행이 있는지 검사한다. 경고 단계는 제재가 아니라 알림이므로 행을 만들지 않는다.
- 인덱스 `(user_id, kind, ends_at)`.

## 7. 알림

### `notification` (알림 센터, 화면 12)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT FK | |
| type | VARCHAR(30) | 예: `STUDY_CANCELLED`, `HOST_ASSIGNED`, `WAITING_OPENED`, `CHECKIN_OPEN`, `NO_SHOW_NOTICE` |
| study_id | BIGINT NULL | 연결 대상 |
| title, body | VARCHAR | 중복을 견디는 문장 (ADR-0005) |
| read_at | DATETIME(6) NULL | |
| created_at | DATETIME(6) | |

- 인덱스 `(user_id, id DESC)`.

### `notification_outbox` (ADR-0005)
채널별로 한 행이다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| notification_id | BIGINT FK | |
| channel | VARCHAR(10) | `PUSH` / `EMAIL` |
| dedupe_key | VARCHAR(100) UNIQUE | 예: `CHECKIN_OPEN:{studyId}:{userId}:PUSH`. 같은 알림이 두 번 만들어지지 않게 한다 |
| status | VARCHAR(10) | `PENDING` / `SENT` / `FAILED` / `CANCELLED` |
| scheduled_at | DATETIME(6) | 예정 발송 시각 (일정 알림은 미리 기록) |
| attempts | TINYINT | |
| next_attempt_at | DATETIME(6) | 백오프 |
| expires_at | DATETIME(6) | 유효 기한. 이 시각이 지난 `PENDING` 건은 발송하지 않고 `CANCELLED`로 바꾼다 (서버 중단 후 늦은 알림 방지, ARCHITECTURE 4.6) |
| last_error | VARCHAR(300) NULL | |
| sent_at | DATETIME(6) NULL | |

- 워커는 `status='PENDING' AND next_attempt_at <= now`를 한 번에 상한 건수만큼 읽는다. 인덱스 `(status, next_attempt_at)`.
- 스터디가 취소되면 해당 스터디의 미발송 일정 알림 행을 `CANCELLED`로 바꾼다. 주최자 혼자일 때 일정을 수정하면 미발송 행을 취소하고 새로 만든다 (ARCHITECTURE 4.6).
- 수신 설정은 발송 시점에 읽는다. 꺼져 있으면 건너뛴다. 상태를 `CANCELLED`로 둘지 `SKIPPED`를 추가할지는 11장 2번.

### `notification_setting`
PK `(user_id, channel)`, `enabled BOOLEAN`. 행이 없으면 기본값(켜짐, 제안).

### `push_subscription`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT FK | |
| endpoint_hash | CHAR(64) UNIQUE | 엔드포인트 URL의 해시 (URL이 길어 유일 제약용) |
| endpoint | TEXT | |
| p256dh, auth | VARCHAR(200) | 웹 푸시 키 |
| created_at | DATETIME(6) | |

- 푸시 서비스가 만료·삭제로 응답한 구독은 행을 지운다 (ADR-0005).

## 8. 채팅

### `chat_room`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| study_id | BIGINT UNIQUE FK | 스터디 생성 시 함께 만든다 (PRD 4.12) |
| status | VARCHAR(10) | `OPEN` / `CLOSED` |
| closed_at | DATETIME(6) NULL | |
| purge_after | DATETIME(6) NULL | 이 시각 이후 메시지 영구 삭제 (보관 기간은 PRD 4.14) |
| purged_at | DATETIME(6) NULL | 삭제 완료 표시 (재실행해도 결과가 같게) |

### `chat_message`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | 커서 페이징 기준 |
| room_id | BIGINT FK | |
| sender_id | BIGINT NULL FK | 시스템 메시지는 `NULL` |
| kind | VARCHAR(10) | `TEXT` / `SYSTEM` (입장·나감·새 주최자 안내) |
| content | VARCHAR(1000) | |
| deleted_at | DATETIME(6) NULL | 운영자 삭제 (내용은 비운다) |
| created_at | DATETIME(6) | |

- 인덱스 `(room_id, id)`. 삭제 작업은 `chat_room.purge_after <= now AND purged_at IS NULL`인 방의 메시지를 상한 건수씩 지운다.

### `chat_notice`
`room_id` PK, `content VARCHAR(500)`, `updated_by`, `updated_at`. 방당 공지 1건 (PRD 4.12).

### `chat_read_state`
PK `(room_id, user_id)`, `last_read_message_id BIGINT`. "읽지 않음" 개수는 `id > last_read_message_id`로 센다.

## 9. 신고와 운영

### `report`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| reporter_id | BIGINT FK | |
| target_type | VARCHAR(10) | `STUDY` / `USER` / `MESSAGE` |
| target_id | BIGINT | |
| reason | VARCHAR(20) | 고정 분류 (부적절한 모집, 욕설 등. 목록은 11장 1번) |
| detail | VARCHAR(500) NULL | |
| status | VARCHAR(10) | `OPEN` / `DISMISSED` / `RESOLVED` |
| handled_by | BIGINT NULL FK | |
| handle_note | VARCHAR(300) NULL | 사유 (PRD 4.11 조치 1) |
| created_at, handled_at | DATETIME(6) | |

- 메시지 신고 시점에 내용 사본을 남기지는 않는다. 보관 기간 안에 처리한다 (PRD 4.14 "신고 대응용 보관"). 인덱스 `(status, created_at)`.

### `block`
PK `(blocker_id, blocked_id)`, `created_at`. 채팅 메시지 숨김용 최소 기능 (PRD 4.12).

### `audit_log`
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| actor_id | BIGINT FK | 운영자 또는 시스템(`NULL`) |
| action | VARCHAR(40) | 예: `REPORT_DISMISS`, `STUDY_FORCE_END`, `MESSAGE_DELETE`, `SANCTION_GRANT`, `SANCTION_REVOKE`, `APPEAL_DECIDE`, `ATTENDANCE_CORRECT` |
| target_type, target_id | VARCHAR, BIGINT | |
| detail | JSON NULL | 변경 전후 값 |
| created_at | DATETIME(6) | |

- 추가만 한다. 점수 변동과 운영자 조치는 모두 여기에 남긴다 (PRD 4.11, 8장).

## 10. 스케줄러가 읽는 조건 (ADR-0005)

작업은 "시각이 지났고 아직 처리되지 않은 건"을 조회한다. 인덱스는 위 표에 있다.

| 작업 | 대상 조건 |
|---|---|
| 알림 워커 | `notification_outbox.status='PENDING' AND next_attempt_at <= now`. `expires_at < now`인 건은 발송하지 않고 취소 |
| 주최자 미도착 무효 처리 | `study.status='ACTIVE' AND voided_at IS NULL AND 주최자 checkin 없음 AND start_at + (미도착 기준) < now` |
| 노쇼 후보 판정 | `study.judged_at IS NULL AND start_at + (체크인 마감) < now`, 무효·파투 스터디는 `VOIDED` 이벤트로 처리 |
| 이의 기간 경과 확정 | `NO_SHOW_CANDIDATE`이고 이의 없는 채 기간 경과 |
| 스터디 종료 처리 | `study.status='ACTIVE' AND session_date < 오늘(KST)` (세션 날짜 다음날 00:00) |
| 채팅 보관 만료 삭제 | `chat_room.purge_after <= now AND purged_at IS NULL` |
| 토큰 정리 | `refresh_token.expires_at < now` |

## 11. 결정 현황

**확정 (2026-11-13)**: 닉네임 중복 불허, 나갔다가 재신청 허용(같은 행 재활성화, 직전 취소 기록은 이벤트에 남음), 이의 증빙은 텍스트만, 만료 리프레시 토큰 정리, 참여자가 있으면 일정·장소 수정 불가, 스터디 숨김 없음.

**구현하며 정할 항목**
1. 신고 사유 분류 목록 (`report.reason`)
2. 수신 설정으로 건너뛴 알림의 상태 표기 (`CANCELLED` 재사용 vs `SKIPPED` 추가)
3. 일회용 코드 자릿수와 수명
