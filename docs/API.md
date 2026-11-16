---
status: draft
version: "0.4"
based_on:
  - docs/PRD.md@0.6
  - docs/ARCHITECTURE.md@0.6
  - docs/DATA-MODEL.md@0.3
covers:
  - backend/src/main/java/com/everystudy/auth/web/**
---

# API 명세

백엔드 REST API와 채팅(WebSocket) 인터페이스의 **경로, 권한, 요청·응답 모양, 오류**를 정한다. 정책 수치는 PRD가 원본이라 값을 적지 않는다. 인증·CSRF의 이유는 [ADR-0002](adr/0002-auth-token-cookie.md), 테이블은 [DATA-MODEL](DATA-MODEL.md)을 본다. 상세 필드는 구현 시 OpenAPI(springdoc)로 생성하고, 이 문서는 그 골격과 규칙의 원본이다. 생성된 문서(`/v3/api-docs`)와 Swagger UI(`/swagger-ui/index.html`)는 개발용이라 `local`에서만 열고 `prod`에서는 끈다(`404`).

## 1. 공통 규칙

| 항목 | 규칙 |
|---|---|
| 기본 경로 | `/api/v1`. 인증 흐름은 `/oauth2/...`(Spring Security) |
| 형식 | JSON(UTF-8). 필드는 `camelCase` |
| 시각 | ISO-8601 UTC (`2026-11-25T05:00:00Z`). 화면 표시만 KST로 바꾼다. 날짜(`sessionDate`)는 KST 날짜 `YYYY-MM-DD` |
| 인증 | `Authorization` 헤더를 쓰지 않는다. `HttpOnly` 쿠키의 액세스 토큰 (ADR-0002) |
| CSRF | 상태 변경(POST/PUT/PATCH/DELETE)은 `X-XSRF-TOKEN` 헤더가 쿠키 값과 같아야 한다 (ADR-0002) |
| 멱등 | `PUT`, `DELETE`는 멱등. `POST` 중 체크인은 `(스터디, 사용자)`당 1회로 멱등 |
| 페이징 | 목록은 **커서 방식**: `?cursor=<opaque>&size=20` → `{ "items": [...], "nextCursor": "..."|null }`. 채팅은 `before=<messageId>` |
| 식별자 | JSON에서도 숫자 `id`. 서버는 소유·권한을 항상 다시 검사한다 |
| 버전 | 호환이 깨지는 변경만 `/v2`. 필드 추가는 같은 버전에서 허용 |

### 권한 표기
`공개` 비회원 가능 · `회원` 로그인 · `참여자` 해당 스터디 `ACTIVE` 멤버 · `주최자` 해당 스터디 현재 주최자 · `운영자` `role=ADMIN`.

### 오류 형식 (RFC 9457 problem details)
```json
{ "type": "about:blank", "title": "정원이 찼습니다", "status": 409,
  "code": "STUDY_FULL", "detail": "...", "traceId": "..." }
```
`type`이 기본값(`about:blank`)이면 응답에서 생략되고(RFC 9457), 요청 경로가 `instance`로 들어간다. `code`는 클라이언트 분기용 안정 값이다. 사용자 문구는 `title`/`detail`이 아니라 클라이언트가 `code`로 정한다.

| status | code | 의미 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 입력 오류(본문 필드, 쿼리·경로 파라미터의 형식·누락·제약 위반, 읽을 수 없는 JSON 본문). `errors: [{field, message}]` 포함. 어느 필드인지 알 수 없으면(깨진 JSON) 빈 배열 |
| 401 | `UNAUTHENTICATED` | 로그인 필요, 토큰 만료 (클라이언트는 `/auth/refresh` 후 1회 재시도) |
| 403 | `FORBIDDEN` / `CSRF_INVALID` | 권한 없음 / CSRF 불일치 |
| 403 | `USER_RESTRICTED` | 제재로 신청·개설 불가. `until` 포함 |
| 404 | `NOT_FOUND` | 대상 없음 |
| 409 | `STUDY_FULL` | 정원 참 → 클라이언트는 대기 안내 |
| 409 | `STUDY_NOT_RECRUITING` | 종료·파투 상태이거나 체크인 오픈 이후 |
| 409 | `ALREADY_JOINED` / `ALREADY_WAITING` | 중복 |
| 409 | `STUDY_NOT_FULL` | 정원에 여유가 있어 대기할 수 없음 (참여 신청을 안내) |
| 409 | `ENTRY_CRITERIA_NOT_MET` | 입장 기준(출석률·신규 불허) 미충족 |
| 409 | `CAPACITY_BELOW_MEMBERS` | 정원을 현재 참여자 수 미만으로 줄이려 함 |
| 409 | `SCHEDULE_LOCKED` | 참여자가 있어 일정·장소를 수정할 수 없음 (PRD 4.2). 새 스터디를 만들도록 안내 |
| 409 | `LOCK_TIMEOUT` | 락 대기 초과. 재시도 가능 (ADR-0004) |
| 422 | `CHECKIN_OUT_OF_RANGE` | 반경 밖 |
| 422 | `CHECKIN_WINDOW_CLOSED` | 체크인 가능 시간 아님 |
| 422 | `CHECKIN_CODE_INVALID` | 코드 오류·만료·시도 초과 |
| 422 | `HOST_NOT_CHECKED_IN` | 주최자 체크인 전이라 코드 체크인 불가 |
| 429 | `RATE_LIMITED` | 호출 제한 |
| 500 | `INTERNAL_ERROR` | 서버가 처리하지 못한 오류. 원인은 응답에 넣지 않는다(`detail` 없음). `traceId`로 서버 로그를 찾는다 |

표에 없는 HTTP 표준 오류(허용하지 않는 메서드 `405`, 받을 수 없는 응답 형식 `406`, 지원하지 않는 본문 형식 `415` 등)는 `code` 없이 같은 형식(`status`, `title`, `traceId`)으로 응답한다. 클라이언트는 `status`로 구분한다.

## 2. 인증

| 메서드 경로 | 권한 | 설명 |
|---|---|---|
| `GET /oauth2/authorization/kakao`, `/google` | 공개 | 소셜 로그인 시작 (리다이렉트) |
| `GET /login/oauth2/code/{provider}` | 공개 | 콜백. 쿠키 발급 후 프론트로 리다이렉트. 신규 사용자는 온보딩 화면으로 |
| `POST /api/v1/auth/refresh` | 쿠키 | 리프레시 회전. 새 쿠키 발급, 재사용 탐지 시 401과 로그아웃 |
| `POST /api/v1/auth/logout` | 회원 | 리프레시 토큰 폐기, 쿠키 삭제 |

**소셜 로그인 흐름** (화면은 `fetch`가 아니라 페이지 이동으로 시작한다)

1. 프론트가 브라우저를 `GET /oauth2/authorization/{provider}`로 보낸다 → 서버가 공급자 동의 화면으로 리다이렉트.
2. 공급자가 `GET /login/oauth2/code/{provider}`로 돌려보낸다 → 서버가 회원을 찾거나 만들고 아래 쿠키를 내려준 뒤 프론트로 리다이렉트한다.

| 결과 | 리다이렉트 대상 (프론트 오리진 기준) |
|---|---|
| 온보딩을 마친 회원 | `/` |
| 온보딩 전 회원(신규 포함) | `/onboarding` |
| 실패(동의 취소, 잘못된 `state`, 공급자 오류) | `/login?error=social_login_failed` (쿠키 없음) |

**토큰 쿠키** (ADR-0002. 값은 JS로 읽을 수 없다)

| 쿠키 | 내용 | `Path` | 수명 | 공통 속성 |
|---|---|---|---|---|
| `access_token` | 액세스 JWT | `/` | 액세스 수명 | `HttpOnly; Secure; SameSite=Lax` |
| `refresh_token` | 리프레시 토큰(불투명 값) | `/api/v1/auth` | 리프레시 수명 | 같음 |

- 리프레시 쿠키는 갱신과 로그아웃이 읽으므로 두 경로를 묶는 `/api/v1/auth`로 제한한다.
- 같은 소셜 계정은 항상 같은 회원이다. 응답 본문에는 토큰이나 회원 정보를 싣지 않는다(내 정보는 3장 `GET /me`).

## 3. 내 정보

| 메서드 경로 | 권한 | 설명 |
|---|---|---|
| `GET /api/v1/me` | 회원 | 프로필, `onboarded` 여부, 이용 제한 현황 |
| `PUT /api/v1/me/profile` | 회원 | 온보딩·수정: `nickname`, `jobCode`, `districtCode` |
| `DELETE /api/v1/me` | 회원 | 탈퇴 (PRD 4.1). 진행 중 스터디가 있으면 규칙을 먼저 적용한 뒤 처리 |
| `GET /api/v1/me/studies?role=host\|member&state=upcoming\|past` | 회원 | 내 스터디 (마이페이지, 다가오는 모임) |
| `GET /api/v1/me/attendance` | 회원 | 지난 출석 기록 (판정, 이의 가능 여부) |
| `GET /api/v1/me/notifications` | 회원 | 알림 센터 (커서). `POST /{id}/read`, `POST /read-all` |
| `GET/PUT /api/v1/me/notification-settings` | 회원 | 채널별 on/off |
| `PUT /api/v1/me/push-subscription` · `DELETE` | 회원 | 웹 푸시 구독 등록·해지 |
| `GET /api/v1/users/{id}` | 공개 | 공개 프로필: 닉네임, 직군, 지역, 출석 지표, 주최 지표, 가입일 (PRD 4.1) |

**출석 지표 응답** (PRD 4.7)
```json
{ "attendanceRate": 92, "windowCount": 10, "noShow": 0, "late": 1, "lateCancel": 0, "isNewcomer": false }
```
신규면 `attendanceRate: null`, `isNewcomer: true`. 주최 지표는 `{ "hostedCount": 7, "leftWithinWindowCount": 1, "showCountOnly": false }` (5회 미만이면 `showCountOnly: true`, PRD 4.13D).

## 4. 스터디

| 메서드 경로 | 권한 | 설명 |
|---|---|---|
| `GET /api/v1/meta/options` | 공개 | 주제·직군·지역 코드 목록, 정원 범위, 개설 가능 일수, 체크인 반경 등 **클라이언트가 알아야 할 설정값** (원본은 PRD/설정) |
| `GET /api/v1/studies` | 공개 | 목록. 필터 `districtCode`, `topicCode`, `jobCode`, `recruiting=true`, 정렬 `sort=latest\|deadline`, 커서 |
| `POST /api/v1/studies` | 회원 | 개설. 채팅방을 함께 만든다. `403 USER_RESTRICTED` 가능 |
| `GET /api/v1/studies/{id}` | 공개 | 상세. 참여자 닉네임·지표 포함(비회원도 열람). 로그인 시 내 상태(`myState`) 포함 |
| `PATCH /api/v1/studies/{id}` | 주최자 | 제목·소개는 항상 수정 가능. **일정·장소(`startAt`, `durationMin`, `place`)는 주최자 외 `ACTIVE` 참여자가 없을 때만** 수정 가능하고, 있으면 `SCHEDULE_LOCKED`. 응답 상세의 `scheduleEditable`로 클라이언트가 입력란을 잠근다 |
| `PUT /api/v1/studies/{id}/capacity` | 주최자 | `{ "capacity": 5 }`. 증액·감소. 증액 시 대기자에게 알림 |

**개설 요청**
```json
{ "title": "...", "description": "...", "topicCode": "...", "districtCode": "...",
  "targetJobCodes": ["ANY"], "place": { "name": "...", "address": "...", "latitude": 37.26, "longitude": 127.03 },
  "startAt": "2026-11-25T05:00:00Z", "durationMin": 120, "capacity": 4,
  "minAttendanceRate": 70, "allowNewcomer": true }
```
검증: 시작은 현재 이후이고 개설 가능 범위 안, 시작한 날(KST) 안에 끝남, 정원 범위(PRD 4.14), 입장 기준 형식.

**상세 응답 주요 필드**: 기본 정보, `status`, `recruiting`(파생), `memberCount`, `capacity`, `members[{userId, nickname, trust}]`, 주최자 요약, `entryCriteria`, `myState`(`NONE`/`MEMBER`/`WAITING`/`HOST`), 주최자에게만 `waitingCount`.

`myState`는 클라이언트가 버튼을 정하는 근거다: `NONE`+모집 중 → "참여", `NONE`+정원 참 → "대기하기", `WAITING` → "대기 취소", `MEMBER` → "나가기".

### 참여·대기

| 메서드 경로 | 권한 | 설명 |
|---|---|---|
| `POST /api/v1/studies/{id}/members` | 회원 | 참여 신청 → 즉시 확정 `201`. 오류 `STUDY_FULL`, `STUDY_NOT_RECRUITING`, `USER_RESTRICTED`, `ENTRY_CRITERIA_NOT_MET`, `ALREADY_JOINED` (검증 순서는 PRD 4.4) |
| `DELETE /api/v1/studies/{id}/members/me` | 참여자 | 나가기 = 참여 취소. 응답에 `cancelType`(`EARLY`/`LATE`). **주최자가 호출하면 주최 포기**로 승계·파투·감점 규칙 적용 (PRD 4.13), 응답에 `outcome`(`TRANSFERRED`/`CANCELLED`/`LEFT_AFTER_START`) |
| `GET /api/v1/studies/{id}/members` | 참여자 | 참여자 목록과 지표 |
| `POST /api/v1/studies/{id}/waiting` | 회원 | 대기 등록. 정원이 차 있을 때만 가능하다. 오류 `STUDY_NOT_FULL`(여유 있음 → 신청 안내), `STUDY_NOT_RECRUITING`, `ALREADY_WAITING` |
| `DELETE /api/v1/studies/{id}/waiting/me` | 회원 | 대기 취소 |

- 나가기 확인용 사전 조회: `GET /api/v1/studies/{id}/leave-preview` → `{ cancelType, hostOutcome, penaltyApplies }`. 클라이언트가 "직전 취소는 기록에 반영됩니다" 같은 안내를 띄우는 데 쓴다.
- 모집 마감 토글 API는 **없다** (PRD 4.14). 강퇴 API도 없다 (PRD 7장).

## 5. 출석

| 메서드 경로 | 권한 | 설명 |
|---|---|---|
| `POST /api/v1/studies/{id}/checkins` | 참여자 | 위치 체크인. 본문 `{ "latitude", "longitude", "accuracyM" }`. 판정은 서버. 성공 시 `{ "result": "ATTENDED"\|"LATE", "distanceM": 42 }`. 이미 했으면 같은 결과를 `200`으로 반환(멱등) |
| `POST /api/v1/studies/{id}/checkins/code` | 참여자 | 코드 체크인 `{ "code": "123456" }`. 위치 거부·GPS 실패 시의 유일한 대체 수단 |
| `PUT /api/v1/studies/{id}/checkin-code` | 주최자 | 일회용 코드 발급(재발급). 주최자 체크인 후에만, 아니면 `HOST_NOT_CHECKED_IN` |
| `GET /api/v1/studies/{id}/attendance` | 참여자 | 체크인 현황. 참여자는 자기 것, 주최자는 전원의 **체크인 여부만** 본다 (노쇼 확정 권한 없음, PRD 4.6) |

체크인 시간창과 판정 경계는 서버가 PRD 4.6 설정으로 판단하며, 클라이언트는 `GET /studies/{id}`의 `checkinOpensAt`, `checkinClosesAt`으로 버튼 상태만 정한다.

### 이의 신청 (PRD 4.9)

| 메서드 경로 | 권한 | 설명 |
|---|---|---|
| `POST /api/v1/attendance-events/{eventId}/appeals` | 본인 | `{ "reason": "..." }`. 판정당 1회, 기한 후 `409 APPEAL_CLOSED` |
| `GET /api/v1/me/appeals` | 회원 | 내 이의 내역 |
| `PUT /api/v1/appeals/{id}/host-opinion` | 주최자 | 1차 의견 `{ "opinion": "..." }` |

최종 결정은 운영자 API(7장).

## 6. 채팅

REST는 이력 조회와 부가 기능, 실시간 송수신은 STOMP이다 (ADR-0005).

| 메서드 경로 | 권한 | 설명 |
|---|---|---|
| `GET /api/v1/studies/{id}/chat` | 참여자 | 방 상태(`OPEN`/`CLOSED`), 공지, 내 읽지 않음 수 |
| `GET /api/v1/studies/{id}/chat/messages?before=&size=` | 참여자 | 이전 대화 (커서). 차단한 사용자 메시지는 숨김 처리 표시 |
| `PUT /api/v1/studies/{id}/chat/notice` | 주최자 | 공지 고정·수정 |
| `PUT /api/v1/studies/{id}/chat/read` | 참여자 | `{ "lastReadMessageId": 123 }` |

**WebSocket (STOMP)**
| 항목 | 값 |
|---|---|
| 연결 | `GET /ws` 핸드셰이크. 인증은 쿠키 (ADR-0002), 허용 오리진 검사 |
| 구독 | `/topic/studies/{id}/chat` : 새 메시지, 공지 변경, 방 폐쇄 이벤트 |
| 전송 | `/app/studies/{id}/chat` : `{ "content": "..." }` |
| 개인 | `/user/queue/events` : 강제 구독 종료 등 본인 대상 이벤트 |

- 전송과 구독마다 서버가 `ACTIVE` 참여 여부를 검사하고, 거부는 STOMP `ERROR`로 보낸다. 방이 `CLOSED`면 전송을 거부한다 (PRD 4.14).
- 메시지는 DB 저장 후 전달하고, 클라이언트는 재연결 시 `GET messages`로 빠진 구간을 채운다.

## 7. 신고·차단·운영자

| 메서드 경로 | 권한 | 설명 |
|---|---|---|
| `POST /api/v1/reports` | 회원 | `{ "targetType": "STUDY\|USER\|MESSAGE", "targetId", "reason", "detail" }` |
| `PUT /api/v1/blocks/{userId}` · `DELETE` | 회원 | 사용자 차단·해제 |
| `GET /api/v1/admin/reports?status=` | 운영자 | 신고 큐 |
| `POST /api/v1/admin/reports/{id}/dismiss` · `/resolve` | 운영자 | 반려 또는 처리 완료 (`note` 필수) |
| `POST /api/v1/admin/studies/{id}/force-end` | 운영자 | 강제 종료 = 파투. 참여자 지표 영향 없음 |
| `DELETE /api/v1/admin/chat-messages/{id}` | 운영자 | 메시지 삭제 |
| `POST /api/v1/admin/users/{id}/suspensions` · `DELETE .../{sid}` | 운영자 | 이용 정지 부여·해제 |
| `GET /api/v1/admin/appeals?status=open` · `POST /{id}/decision` | 운영자 | 이의 최종 결정 `{ "accept": true, "note": "..." }`. 인정 시 정정 이벤트와 지표 재계산 |
| `GET /api/v1/admin/sanctions?userId=` | 운영자 | 자동 제재 조회·해제 |
| `GET /api/v1/admin/audit-logs` | 운영자 | 감사 로그 (커서) |

- 모든 운영자 쓰기 API는 `audit_log`에 기록한다. 응답 코드는 `204` 또는 변경된 리소스.
- 운영자 API는 일반 API와 **경로 접두사(`/admin`)로 분리**하고 `role=ADMIN`을 필수로 검사한다.

## 8. 서버가 내보내는 이벤트와 알림

API가 아니라 서버 내부에서 만드는 알림 목록이다. 문구와 채널 발송은 [ARCHITECTURE 4.6](ARCHITECTURE.md), 저장은 [DATA-MODEL 7장](DATA-MODEL.md)을 따른다.

| type | 대상 | 시점 |
|---|---|---|
| `STUDY_CANCELLED` | 참여자 | 스터디 취소(파투, 강제 종료) |
| `CHECKIN_REMINDER_DAY`, `_HOUR`, `CHECKIN_OPEN` | 참여자 | 전날, 1시간 전, 체크인 오픈 (PRD 4.5) |
| `WAITING_OPENED` | 대기자 | 자리 발생, 정원 증액 (PRD 4.14) |
| `HOST_ASSIGNED` | 새 주최자 | 승계 |
| `NO_SHOW_NOTICE` | 해당 참여자 | 노쇼 후보 통지, 이의 기간 안내 |
| `APPEAL_RESULT` | 신청자 | 이의 결정 |
| `SANCTION_APPLIED` | 대상자 | 제재 부여 |

## 9. 결정 현황

**확정 (2026-11-13)**: 대기 등록은 정원이 찼을 때만(`STUDY_NOT_FULL`), 참여자가 있으면 일정·장소 수정 불가(`SCHEDULE_LOCKED`, 일정을 바꾸려면 주최자가 방을 나가 새 스터디를 만든다), 숨김 스터디 없음, 비회원도 상세에서 참여자 닉네임·지표 열람(비회원은 참여할 수 없고 프로필도 공개, PRD 4.3).

**구현하며 정할 항목**
1. 호출 제한(`429`) 대상과 수치: 체크인 코드 입력, 신청, 신고, 채팅 전송, 비회원 목록·상세 조회.
