---
status: draft
version: "0.14"
based_on:
  - docs/PRD.md@0.13
  - docs/ARCHITECTURE.md@0.13
  - docs/DATA-MODEL.md@0.11
covers:
  - backend/src/main/java/com/everystudy/auth/web/**
  - backend/src/main/java/com/everystudy/user/web/**
  - backend/src/main/java/com/everystudy/meta/web/**
  - backend/src/main/java/com/everystudy/study/web/**
  - backend/src/main/java/com/everystudy/notification/web/**
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
| CSRF | 상태 변경(POST/PUT/PATCH/DELETE)은 `X-XSRF-TOKEN` 헤더가 `XSRF-TOKEN` 쿠키 값과 같아야 한다. 쿠키는 2장 (ADR-0002) |
| 오리진 | 브라우저 요청은 프론트 오리진 하나만 허용한다(CORS, 쿠키 포함). 다른 `Origin`의 요청은 `403`으로 거절하고 처리하지 않는다 |
| 멱등 | `PUT`, `DELETE`는 멱등. `POST` 중 체크인은 `(스터디, 사용자)`당 1회로 멱등 |
| 페이징 | 목록은 **커서 방식**: `?cursor=<opaque>&size=20` → `{ "items": [...], "nextCursor": "..."|null }`. 채팅은 `before=<messageId>` |
| 식별자 | JSON에서도 숫자 `id`. 서버는 소유·권한을 항상 다시 검사한다 |
| 버전 | 호환이 깨지는 변경만 `/v2`. 필드 추가는 같은 버전에서 허용 |

### 권한 표기
`공개` 비회원 가능 · `회원` 로그인 · `참여자` 해당 스터디 `ACTIVE` 멤버 · `주최자` 해당 스터디 현재 주최자 · `운영자` `role=ADMIN` · `쿠키` 액세스 토큰 없이 리프레시 쿠키로 동작(2장).

**기본은 `회원`이다.** 서버는 `공개`로 명시해 연 경로만 비회원에게 열고, 나머지는 모두 로그인을 요구한다. 그래서 없는 경로도 로그인하지 않았으면 `404`가 아니라 `401`이다.

### 오류 형식 (RFC 9457 problem details)
```json
{ "type": "about:blank", "title": "정원이 찼습니다", "status": 409,
  "code": "STUDY_FULL", "detail": "...", "traceId": "..." }
```
`type`이 기본값(`about:blank`)이면 응답에서 생략되고(RFC 9457), 요청 경로가 `instance`로 들어간다. `code`는 클라이언트 분기용 안정 값이다. 사용자 문구는 `title`/`detail`이 아니라 클라이언트가 `code`로 정한다.

| status | code | 의미 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 입력 오류(본문 필드, 쿼리·경로 파라미터의 형식·누락·제약 위반, 읽을 수 없는 JSON 본문). `errors: [{field, message}]` 포함. 어느 필드인지 알 수 없으면(깨진 JSON) 빈 배열 |
| 401 | `UNAUTHENTICATED` | 로그인 필요, 액세스 토큰 만료·위조 (클라이언트는 `/auth/refresh` 후 1회 재시도). `/auth/refresh`가 이 코드를 주면 다시 로그인해야 한다 |
| 403 | `FORBIDDEN` / `CSRF_INVALID` | 권한 없음 / CSRF 헤더 없음·불일치 (클라이언트는 CSRF 쿠키를 다시 읽어 1회 재시도) |
| 403 | `USER_RESTRICTED` | 제재로 신청·대기·개설 불가. `until` 포함 |
| 403 | `ONBOARDING_REQUIRED` | 온보딩(닉네임·직군·지역)을 마치지 않아 쓸 수 없는 기능 (스터디 개설 등). 클라이언트는 온보딩 화면으로 보낸다 |
| 404 | `NOT_FOUND` | 대상 없음 |
| 409 | `NICKNAME_TAKEN` | 다른 회원이 쓰는 닉네임 (영문 대소문자 무시). 클라이언트는 닉네임 입력란에 안내한다 |
| 409 | `STUDY_FULL` | 정원 참 → 클라이언트는 대기 안내 |
| 409 | `STUDY_NOT_RECRUITING` | 종료·파투 상태이거나 체크인 오픈 이후 |
| 409 | `ALREADY_JOINED` / `ALREADY_WAITING` | 중복 |
| 409 | `SESSION_STARTED` | 이미 시작했거나 종료·파투된 스터디라 참여자가 나갈 수 없음 (PRD 4.6) |
| 409 | `STUDY_NOT_FULL` | 정원에 여유가 있어 대기할 수 없음 (참여 신청을 안내) |
| 409 | `ENTRY_CRITERIA_NOT_MET` | 입장 기준(출석률·신규 불허) 미충족 |
| 409 | `JOB_NOT_ALLOWED` | 내 직군의 대분류가 스터디의 희망 직군에 없음 (PRD 4.4) |
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

허용하지 않은 `Origin`의 요청에 대한 `403`만 예외로 problem details가 아니다(본문은 짧은 평문). 브라우저는 다른 오리진의 응답 본문을 스크립트에 주지 않으므로 클라이언트가 읽을 일이 없다.

## 2. 인증

| 메서드 경로 | 권한 | 설명 |
|---|---|---|
| `GET /oauth2/authorization/kakao`, `/google` | 공개 | 소셜 로그인 시작 (리다이렉트) |
| `GET /login/oauth2/code/{provider}` | 공개 | 콜백. 쿠키 발급 후 프론트로 리다이렉트. 신규 사용자는 온보딩 화면으로 |
| `POST /api/v1/auth/refresh` | 쿠키 | 리프레시 회전. 새 토큰 쿠키 한 쌍 발급, `204` |
| `POST /api/v1/auth/logout` | 쿠키 | 리프레시 토큰 폐기, 쿠키 삭제. `204` |

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
- 액세스 토큰은 쿠키로만 받는다. `Authorization` 헤더의 토큰은 보지 않는다.

**갱신** (`POST /api/v1/auth/refresh`)

- 성공하면 새 `access_token`·`refresh_token` 쿠키를 내려준다. 쓴 리프레시 토큰은 다시 쓸 수 없고, 새 리프레시 토큰의 수명은 그 시점부터 다시 센다.
- 액세스 토큰이 만료된 상태에서 부르는 경로라 액세스 쿠키는 보지 않는다. CSRF 헤더는 필요하다.
- 아래 경우는 `401 UNAUTHENTICATED`이고 토큰 쿠키를 지운다. 클라이언트는 로그인 화면으로 보낸다.

| 경우 | 서버가 하는 일 |
|---|---|
| 리프레시 쿠키가 없음, 모르는 값, 만료, 이미 폐기됨 | 거절만 한다 |
| **이미 쓴 토큰이 다시 옴** (재사용 탐지) | 탈취로 보고 같은 로그인(계열)의 토큰을 모두 폐기한다. 그 로그인으로 받은 최신 토큰도 못 쓰게 된다 |
| 탈퇴한 회원 | 그 계열을 폐기한다 |

- 클라이언트는 갱신을 **한 번에 하나만** 보낸다(동시에 여러 요청이 `401`을 받아도 갱신은 한 번). 같은 리프레시 토큰으로 두 번 보내면 두 번째가 재사용으로 처리돼 로그아웃된다.

**로그아웃** (`POST /api/v1/auth/logout`)

- 리프레시 쿠키가 속한 로그인(계열)의 토큰을 모두 폐기하고 `access_token`·`refresh_token`·`XSRF-TOKEN` 쿠키를 지운다. 다른 기기의 로그인은 남는다.
- 액세스 토큰이 만료됐어도, 리프레시 쿠키가 없거나 이미 무효여도 `204`다. CSRF 헤더는 필요하다.
- 이미 발급된 액세스 토큰은 서버에서 무효로 만들지 않는다. 쿠키가 지워지고, 길어도 액세스 수명 뒤에는 만료된다.

**CSRF 토큰** (이중 제출 쿠키)

| 쿠키 | 내용 | `Path` | 속성 |
|---|---|---|---|
| `XSRF-TOKEN` | CSRF 토큰. JS가 읽어 `X-XSRF-TOKEN` 헤더로 보낸다 | `/` | `Secure; SameSite=Lax` (`HttpOnly` 아님). `Domain`은 토큰 쿠키와 같다 |

- 서버는 이 쿠키가 없는 요청이면 응답에 새 쿠키를 내려준다(조회 요청과 오류 응답 포함). 받기 위한 전용 경로는 없다.
- 로그인과 로그아웃은 이전 CSRF 쿠키를 지운다. 그 뒤 첫 요청의 응답에서 새 쿠키를 받는다.
- 상태 변경 요청에 헤더가 없거나 쿠키와 다르면 `403 CSRF_INVALID`이고 요청은 처리되지 않는다. 갱신·로그아웃도 같다. 쿠키 없이 보낸 첫 요청이 이렇게 거절되면 그 응답에 쿠키가 실려 오므로, 클라이언트는 쿠키를 다시 읽어 한 번 재시도한다.

## 3. 내 정보

| 메서드 경로 | 권한 | 설명 |
|---|---|---|
| `GET /api/v1/me` | 회원 | 프로필, `onboarded` 여부. 이용 제한 현황은 제재 작업(PLAN T-035a)에서 더한다 |
| `PUT /api/v1/me/profile` | 회원 | 온보딩·수정: `nickname`, `jobCode`, `districtCode`. 바뀐 내 정보를 돌려준다 |
| `DELETE /api/v1/me` | 회원 | 탈퇴 (PRD 4.1). 진행 중 스터디가 있으면 규칙을 먼저 적용한 뒤 처리 |
| `GET /api/v1/me/studies?role=host\|member&state=upcoming\|past` | 회원 | 내 스터디 (마이페이지, 다가오는 모임) |
| `GET /api/v1/me/attendance` | 회원 | 지난 출석 기록 (판정, 이의 가능 여부) |
| `GET /api/v1/me/notifications` | 회원 | 알림 센터 (커서). `POST /{id}/read`, `POST /read-all` (아래) |
| `GET/PUT /api/v1/me/notification-settings` | 회원 | 채널별 on/off |
| `PUT /api/v1/me/push-subscription` · `DELETE` | 회원 | 웹 푸시 구독 등록·해지 |
| `GET /api/v1/users/{id}` | 공개 | 공개 프로필: 닉네임, 직군, 지역, 출석 지표, 주최 지표, 가입일 (PRD 4.1) |

**내 정보 응답** (`GET /me`, `PUT /me/profile`)
```json
{ "id": 42, "nickname": "준비생", "jobCode": "IT_BACKEND", "districtCode": "SUWON_YEONGTONG",
  "role": "USER", "onboarded": true, "joinedAt": "2026-11-17T05:00:00Z" }
```
- 온보딩 전에는 `nickname`·`jobCode`·`districtCode`가 `null`이고 `onboarded: false`다. 셋을 모두 저장하면 `true`가 된다.
- 탈퇴한 회원은 액세스 토큰이 남아 있어도 `401 UNAUTHENTICATED`다.

**온보딩·수정 요청** (`PUT /me/profile`) — 온보딩과 이후 수정이 같은 경로이고, 세 값을 모두 보낸다.

| 필드 | 규칙 |
|---|---|
| `nickname` | 2~20자의 한글(완성형)·영문·숫자·밑줄(`_`). 앞뒤 공백은 서버가 잘라 낸다. 다른 회원이 쓰면 `409 NICKNAME_TAKEN` |
| `jobCode` | 직군 대분류(필수), 또는 그 아래 세부 직무를 골랐으면 그 코드. `/meta/options`의 `jobs` 어느 단계의 코드든 된다 |
| `districtCode` | 활동 지역. **구** 단위 코드만 (`/meta/options`의 `regions` 맨 아래 단계) |

형식이 틀리면 `400 VALIDATION_FAILED`이고 `errors`에 필드가 담긴다.

**알림 센터** (`GET /me/notifications?cursor=&size=20`, size 1~50)
```json
{ "items": [ { "id": 7, "type": "CHECKIN_OPEN", "studyId": 3, "title": "체크인이 열렸어요",
               "body": "장소에 도착하면 체크인해 주세요", "read": false, "notifiedAt": "2026-11-25T04:30:00Z" } ],
  "nextCursor": null }
```
- 보이기 시작한 시각(`notifiedAt`)이 지난 내 알림만, 최근 것부터 준다. 미리 예약된 일정 알림은 그 시각 전에는 보이지 않는다 (PLAN X-10, DATA-MODEL 7장). `studyId`는 없으면 `null`.
- `POST /me/notifications/{id}/read`: `204`. 이미 읽었어도 `204`. 남의 알림, 아직 보이지 않는 알림, 없는 알림은 `404 NOT_FOUND`.
- `POST /me/notifications/read-all`: 보이는 내 알림을 모두 읽음으로 바꾸고 `204`.

**출석 지표 응답** (PRD 4.7)
```json
{ "attendanceRate": 92, "windowCount": 10, "noShow": 0, "late": 1, "lateCancel": 0, "isNewcomer": false }
```
신규면 `attendanceRate: null`, `isNewcomer: true`. 주최 지표는 `{ "hostedCount": 7, "leftWithinWindowCount": 1, "showCountOnly": false }` (5회 미만이면 `showCountOnly: true`, PRD 4.13D).

## 4. 스터디

| 메서드 경로 | 권한 | 설명 |
|---|---|---|
| `GET /api/v1/meta/options` | 공개 | 주제·직군·지역 코드 목록, 정원 범위, 개설 가능 일수, 체크인 반경 등 **클라이언트가 알아야 할 설정값** (원본은 PRD/설정). 아래 응답 |
| `GET /api/v1/studies` | 공개 | 목록. 기본은 모집 중만. 아래 "목록 조회" |
| `POST /api/v1/studies` | 회원 | 개설. 채팅방을 함께 만든다. `201`과 `Location`, 본문 `{ "id": 1 }`. 온보딩 전이면 `403 ONBOARDING_REQUIRED`, 제재 중이면 `403 USER_RESTRICTED`(T-035b에서 추가) |
| `GET /api/v1/studies/{id}` | 공개 | 상세. 참여자 닉네임·지표 포함(비회원도 열람). 로그인 시 내 상태(`myState`) 포함. 종료·파투된 스터디도 주소로 열면 보인다. 없으면 `404 NOT_FOUND` |
| `PATCH /api/v1/studies/{id}` | 주최자 | 제목·소개는 항상 수정 가능. **일정·장소(`startAt`, `durationMin`, `place`)는 주최자 외 `ACTIVE` 참여자가 없을 때만** 수정 가능하고, 있으면 `SCHEDULE_LOCKED`. 응답 상세의 `scheduleEditable`로 클라이언트가 입력란을 잠근다 |
| `PUT /api/v1/studies/{id}/capacity` | 주최자 | `{ "capacity": 5 }`. 증액·감소. 증액 시 자격이 남은 대기자에게 알림 |

**메타 옵션 응답** (`GET /meta/options`)
```json
{ "topics":  [{ "code": "JOB_PREP", "name": "취업준비", "children": [] }],
  "jobs":    [{ "code": "IT", "name": "IT·개발",
                "children": [{ "code": "IT_BACKEND", "name": "백엔드", "children": [] }] }],
  "regions": [{ "code": "GYEONGGI", "name": "경기",
                "children": [{ "code": "SUWON", "name": "수원시",
                               "children": [{ "code": "SUWON_JANGAN", "name": "장안구", "children": [] }] }] }],
  "study":   { "capacityMin": 2, "capacityMax": 6, "openableDays": 14,
               "startMinuteUnit": 10, "durationMinuteUnit": 10, "durationMinMinutes": 30,
               "minAttendanceRates": [50, 60, 70, 80, 90, 100] },
  "checkin": { "radiusMeters": 150 } }
```
- 목록의 순서는 화면에 보일 순서다. 코드는 저장·요청에 쓰고, 화면에는 `name`을 쓴다.
- 수치는 서버 설정값이라 바뀔 수 있다. 클라이언트는 값을 따로 갖지 않고 이 응답을 쓴다. 다른 설정값은 그 기능의 작업에서 더한다.

**개설 요청**
```json
{ "title": "...", "description": "...", "topicCode": "...", "districtCode": "...",
  "targetJobCodes": ["IT"], "place": { "name": "...", "address": "...", "latitude": 37.26, "longitude": 127.03 },
  "startAt": "2026-11-25T05:00:00Z", "durationMin": 120, "capacity": 4,
  "minAttendanceRate": 70, "allowNewcomer": true }
```
검증 (실패하면 `400 VALIDATION_FAILED`와 필드. 수치는 `/meta/options`의 `study`와 같은 설정값이다):

| 필드 | 규칙 |
|---|---|
| `title` | 2~40자. 앞뒤 공백은 잘라 낸다 |
| `description` | 선택. 2000자 이하. 앞뒤 공백은 잘라 내고, 생략하거나 공백만 쓰면 빈 문자열로 저장한다 (PRD 4.2) |
| `topicCode` | 주제 코드 |
| `districtCode` | 구 단위 지역 코드 |
| `targetJobCodes` | 직군 **대분류** 코드 목록(세부 직무는 거절). 생략하거나 비우면 직군 무관. 고르면 참여 조건이다 (PRD 4.2·4.4) |
| `place` | `name` 100자, `address` 200자 이하. 좌표는 소수점 6자리로 저장한다 |
| `startAt` | 현재 이후. 시작 날짜(KST)가 오늘부터 개설 가능 일수 뒤까지(오늘이 11일이고 14일이면 25일까지). `startMinuteUnit`분 단위이고 초가 없다 |
| `durationMin` | `durationMinMinutes`분 이상, `durationMinuteUnit`분 단위. 시작한 날(KST) 안에 끝난다. 자정(24:00)에 끝나는 것까지 받는다 |
| `capacity` | `capacityMin`~`capacityMax` (주최자 포함, PRD 4.2) |
| `minAttendanceRate` | `null`(기준 없음) 또는 `minAttendanceRates` 중 하나 |
| `allowNewcomer` | 생략하면 `true` (PRD 4.7 기본값) |

개설하면 주최자가 `ACTIVE` 참여자로 들어가고 채팅방이 함께 만들어진다. 셋 중 하나라도 실패하면 아무것도 만들어지지 않는다.

**목록 조회** (`GET /studies`) — 이미 시작했거나 끝난(종료·파투) 스터디는 어떤 조건에도 나오지 않는다 (PRD 4.3)

| 파라미터 | 뜻 |
|---|---|
| `regionCode` | 시·도·시·구 어느 단계든. 그 아래 구의 스터디. 생략하면 전체. "내 지역"은 클라이언트가 내 구의 시 코드로 부른다 |
| `topicCode` | 주제 |
| `jobCode` | 직군 **대분류**. 그 직군이 희망 직군에 명시된 스터디만(직군 무관 스터디는 빠진다) |
| `myJob=true` | 내 직군이 신청할 수 있는 스터디만: 희망 직군이 없거나 내 대분류가 있음. 로그인 필요(아니면 `401`), 직군이 없으면 `403 ONBOARDING_REQUIRED` |
| `includeClosed=true` | 시작 전인 마감 스터디(정원 참, 체크인 오픈 후)도 함께. 기본은 모집 중만 |
| `sort` | `latest`(기본, 개설 순의 역순) 또는 `deadline`(마감임박순 = 시작이 빠른 순, 같으면 `id` 순) |
| `cursor`, `size` | 커서는 직전 응답의 `nextCursor`. 다른 `sort`의 커서는 `400`. `size`는 1~50, 기본 20 |

잘못된 값은 `400 VALIDATION_FAILED`이고 `errors`에 파라미터 이름이 담긴다.

```json
{ "items": [{ "id": 1, "title": "모각코", "topicCode": "DEVELOPMENT", "districtCode": "SUWON_YEONGTONG",
              "startAt": "2026-11-25T05:00:00Z", "durationMin": 120, "capacity": 4, "memberCount": 2,
              "recruiting": true, "targetJobCodes": ["IT"],
              "entryCriteria": { "minAttendanceRate": 70, "allowNewcomer": true },
              "host": { "userId": 7, "nickname": "주최자",
                        "stats": { "hostedCount": 0, "leftWithinWindowCount": 0, "showCountOnly": true } } }],
  "nextCursor": null }
```

**상세 응답 필드** (`GET /studies/{id}`)

| 필드 | 뜻 |
|---|---|
| `id`, `title`, `description`, `topicCode`, `districtCode`, `targetJobCodes`, `entryCriteria` | 개설 때 정한 값 |
| `place` | `{ name, address, latitude, longitude }` |
| `startAt`, `durationMin`, `sessionDate` | 일정. `sessionDate`는 KST 날짜 |
| `status` | `ACTIVE` / `ENDED` / `CANCELLED` |
| `recruiting` | 모집 중(파생): `ACTIVE`이고 체크인 오픈 전이고 정원에 여유가 있음 |
| `capacity`, `memberCount` | 정원과 `ACTIVE` 참여자 수(주최자 포함) |
| `host` | `{ userId, nickname, stats }`. 주최 지표 형식은 3장 |
| `members` | `[{ userId, nickname, trust }]` 참여 순서대로, 주최자 포함. 출석 지표 형식은 3장 |
| `myState` | `NONE` / `MEMBER` / `WAITING` / `HOST`. 비회원은 `NONE` |
| `scheduleEditable` | 일정·장소를 고칠 수 있나: `ACTIVE`이고 시작 전이며 주최자 외 참여자가 없음 (PRD 4.2) |
| `checkinOpensAt`, `checkinClosesAt` | 체크인 시간창 (PRD 4.6). 신청·대기는 `checkinOpensAt`에 닫힌다 |
| `waitingCount` | **주최자에게만** 있는 필드. 다른 사람의 응답에는 아예 없다 |

- 탈퇴한 회원의 `nickname`은 `null`이다. 화면은 "탈퇴한 사용자"로 표시한다.
- 출석 집계(M3) 전까지 지표는 모두 신규(`isNewcomer: true`), 주최 지표는 0과 `showCountOnly: true`다.
- 체크인 오픈 이후에는 남은 대기 행이 `myState`(`WAITING`)와 `waitingCount`에 반영되지 않는다 (PLAN X-16).

`myState`는 클라이언트가 버튼을 정하는 근거다: `NONE`+모집 중 → "참여", `NONE`+정원 참 → "대기하기", `WAITING` → "대기 취소", `MEMBER` → "나가기".

### 참여·대기

| 메서드 경로 | 권한 | 설명 |
|---|---|---|
| `POST /api/v1/studies/{id}/members` | 회원 | 참여 신청 → 즉시 확정 `201`, 본문 없음(결과는 상세의 `myState`). 오류 `STUDY_NOT_RECRUITING`, `STUDY_FULL`, `USER_RESTRICTED`, `ENTRY_CRITERIA_NOT_MET`, `JOB_NOT_ALLOWED`, `ALREADY_JOINED` (검증 순서는 PRD 4.4). 그 밖에 온보딩 전이면 `403 ONBOARDING_REQUIRED`(검증보다 먼저, PLAN X-19), 스터디가 없으면 `404 NOT_FOUND`, 락 대기 초과면 `409 LOCK_TIMEOUT`(재시도) |
| `DELETE /api/v1/studies/{id}/members/me` | 참여자 | 나가기 = 참여 취소 → `200 { cancelType }`(`EARLY`/`LATE`). 오류 `FORBIDDEN`(참여 중이 아님), `SESSION_STARTED`(시작 후·종료·파투), `NOT_FOUND`, `LOCK_TIMEOUT`. **주최자가 호출하면 주최 포기**로 승계·파투·감점 규칙을 적용하고(PRD 4.13) 응답에 `outcome`(`TRANSFERRED`/`CANCELLED`/`LEFT_AFTER_START`)을 담는다. **T-020 전까지는 주최자의 호출을 `403 FORBIDDEN`으로 거절한다 (PLAN X-21)** |
| `GET /api/v1/studies/{id}/members` | 참여자 | 참여자 목록과 지표(상세의 `members`와 같은 형식). 그 스터디의 `ACTIVE` 참여자(주최자 포함)가 아니면 `403 FORBIDDEN` |
| `POST /api/v1/studies/{id}/waiting` | 회원 | 대기 등록 → `201`, 본문 없음. 정원이 차 있을 때만 가능하다. 오류 `STUDY_NOT_RECRUITING`, `ALREADY_JOINED`, `STUDY_NOT_FULL`(여유 있음 → 신청 안내), `USER_RESTRICTED`, `ENTRY_CRITERIA_NOT_MET`, `JOB_NOT_ALLOWED`(참여 신청과 같은 자격 검사, PRD 4.14), `ALREADY_WAITING` (이 순서로 검사). 그 밖에 `ONBOARDING_REQUIRED`, `NOT_FOUND`, `LOCK_TIMEOUT` |
| `DELETE /api/v1/studies/{id}/waiting/me` | 회원 | 대기 취소 → `204`. 대기 중이 아니어도 `204`(멱등) |

- 나가기 확인용 사전 조회: `GET /api/v1/studies/{id}/leave-preview` → `{ cancelType, hostOutcome, penaltyApplies }`(`hostOutcome`은 값이 없으면 생략하며 T-020 전에는 항상 생략, 주최자·비참여자는 `403`, 시작 후·종료·파투는 `409 SESSION_STARTED`). 클라이언트가 "직전 취소는 기록에 반영됩니다" 같은 안내를 띄우는 데 쓴다.
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
| `WAITING_OPENED` | 대기자 | 자리 발생, 정원 증액. 알림 시점에 신청 자격이 남은 대기자만 (PRD 4.14, PLAN X-20) |
| `HOST_ASSIGNED` | 새 주최자 | 승계 |
| `NO_SHOW_NOTICE` | 해당 참여자 | 노쇼 후보 통지, 이의 기간 안내 |
| `APPEAL_RESULT` | 신청자 | 이의 결정 |
| `SANCTION_APPLIED` | 대상자 | 제재 부여 |

## 9. 결정 현황

**확정 (2026-11-13)**: 대기 등록은 정원이 찼을 때만(`STUDY_NOT_FULL`), 참여자가 있으면 일정·장소 수정 불가(`SCHEDULE_LOCKED`, 일정을 바꾸려면 주최자가 방을 나가 새 스터디를 만든다), 숨김 스터디 없음, 비회원도 상세에서 참여자 닉네임·지표 열람(비회원은 참여할 수 없고 프로필도 공개, PRD 4.3).

**구현하며 정할 항목**
1. 호출 제한(`429`) 대상과 수치: 체크인 코드 입력, 신청, 신고, 채팅 전송, 비회원 목록·상세 조회.
