---
status: draft
version: "0.39"
based_on:
  - docs/PRD.md@0.17
  - docs/ARCHITECTURE.md@0.19
  - docs/DATA-MODEL.md@0.34
  - docs/API.md@0.39
---

# PLAN — 모두의 스터디 MVP 구현 계획

- 무엇을 만드는지와 정책 수치는 [PRD](PRD.md), 구조는 [ARCHITECTURE](ARCHITECTURE.md)와 [`adr/`](adr/), 테이블은 [DATA-MODEL](DATA-MODEL.md), 경로·오류 코드는 [API](API.md)가 원본이다. 이 문서는 **순서와 완료 기준**만 정하고, 값과 규칙은 위치(`PRD 4.6`, `ADR-0004` 등)로만 가리킨다.
- 이미 확정된 결정은 다시 열지 않는다. 확정되지 않은 것과 문서 간 충돌은 맨 아래 [7장](#7-미결정과-문서-불일치)에 모으고, 해당 작업의 "선행 결정"으로 건다.

## 1. 목표와 범위

**목표**: PRD 4장의 MVP 기능을 모바일 웹으로 출시해 PRD 1장의 시범 지역에서 시범 운영을 시작한다 (PRD 1장 초기 운영 전략).

**범위 (MVP)**: 소셜 로그인·온보딩, 스터디 개설·탐색, 참여·취소·대기·정원 변경, 주최자 승계, 위치·코드 체크인과 자동 판정, 출석 지표·제재·이의 신청, 알림(웹 푸시·이메일), 채팅, 신고·운영자 조치, 탈퇴, 단일 VM Docker 배포.

**이번에 하지 않는 것**: PRD 7장(범위 밖)과 PRD 9장의 "운영 후 과제" 전부. 계획 중에 이 목록에 있는 기능이 필요해 보이면 작업을 추가하지 않고 7장에 기록한다.

## 2. 진행 규칙

1. **작업 하나 = PR 하나.** 한 세션(몇 시간) 안에 끝나는 크기다. 커지면 PR을 내기 전에 이 문서에서 작업을 나눈다 (새 번호를 뒤에 붙이거나 `T-035a`처럼 접미사를 붙이고, 기존 번호는 바꾸지 않는다). 예: T-001의 springdoc·로그 마스킹이 커지면 별도 작업으로 떼어 내도 된다.
2. **체크박스는 그 작업의 PR 안에서 체크한다.** 별도 "체크용 PR"을 만들지 않는다.
3. **PR 제목 앞에 작업 번호**를 붙인다 (예: `T-015 참여 신청 API`). 본문은 `.github/pull_request_template.md`를 따르고 "관련 문서" 칸에 이 문서의 작업 번호를 적는다. 선행 작업이 모두 `develop`에 병합되고 선행 결정이 원본 문서에 기록된 뒤 시작한다.
4. 브랜치 이름, 커밋 메시지, PR 대상, 금지 명령은 [AGENTS.md](../AGENTS.md) 3장을 따른다 (여기에 복사하지 않는다).
5. **수직 슬라이스**: 기능 하나를 "백엔드 API + 테스트" PR과 "프론트 화면" PR로 나눈다. 계층별(DB만, 화면만)로 쪼개지 않는다. 테이블은 그것을 처음 쓰는 API 작업의 Flyway 마이그레이션으로 만든다. `[프론트]` 표시가 없는 작업은 백엔드(또는 인프라) 작업이다.
6. **공통 완료 기준** (모든 작업에 적용, 각 작업에는 다시 쓰지 않는다): CI의 `CI 통과`가 초록, 새 코드에 테스트 포함, 시크릿·실제 키 미포함, 정책 수치는 설정값(ARCHITECTURE 4.9)으로 주입하고 코드에 숫자를 직접 쓰지 않음, 알림은 비즈니스 트랜잭션 안에서 아웃박스에 기록(X-8 결정 방식).
7. **문서 동기화**: 각 작업의 "문서" 줄에 적힌 문서가 구현과 같은지 확인하고, 다르면 같은 PR에서 고친다. 의미 있는 변경이면 그 문서의 `version`을 올리고, 그 문서를 `based_on`으로 고정한 하위 문서(이 PLAN 포함)의 `based_on`도 **같은 PR에서** 올린다. 코드가 처음 생긴 모듈은 그 모듈을 설명하는 문서 머리말에 `covers`를 추가한다 (docs/README.md 4장).
8. 구현 중 문서 간 충돌이나 빈칸을 발견하면 추측으로 채우지 않고 7장에 추가한 뒤 사람에게 확인한다.
9. **기술 파라미터 기본값**(락 대기 시간, 재시도 백오프·최대 횟수, 배치 상한)은 선행 결정이 아니다. 그 값을 처음 쓰는 작업의 PR에서 정하고 ARCHITECTURE 4.2·4.6·4.7에 기록한다 (해당 작업의 "문서" 줄에 적혀 있다). 정책 수치(PRD)와 달리 사람 확인을 기다리지 않는다.
10. **결정 게이트.** 7장의 결정(D)·불일치(X)는 한꺼번에 정하지 않고, **그 항목이 "막는 작업" 열에 적힌 가장 이른 작업을 시작할 때** 사람과 함께 정한다. 에이전트는 작업 시작 전에 그 작업을 막는 항목을 찾아 선택지와 결과를 설명하고 권장안을 낸다. 결정은 그 작업의 PR에서 원본 문서에 기록하고 7장에서 "결정됨(문서 위치)"으로 바꾼다. 결정이 없으면 작업을 시작하지 않는다. 마일스톤을 시작할 때는 그 마일스톤의 작업을 막는 미결정을 한 번에 훑어, 개별 작업에서 보이지 않던 의존을 미리 잡는다.

## 3. 마일스톤

| 마일스톤 | 이름 | 여기까지 되면 시연 가능 | 작업 |
|---|---|---|---|
| M1 | 걸어다니는 뼈대 | 로그인한 사용자가 스터디를 하나 만들고 목록·상세에서 본다. 같은 흐름이 HTTPS 임시 환경에서도 동작한다 | T-001 ~ T-013, T-069, T-070, T-071 |
| M2 | 참여·정원·대기 | 여러 사용자가 참여·나가기·대기·승계를 하고, 그 결과 알림이 알림 센터에 쌓인다. 동시 신청·동시 나가기 테스트가 CI에서 통과한다 | T-014 ~ T-026 |
| M3 | 출석과 신뢰 | 장소에서 체크인하면 출석·지각이 기록되고, 시간이 지나면 무효·노쇼 후보·확정·제재가 자동으로 진행되어 지표에 반영된다 | T-027 ~ T-037 |
| M4 | 알림 발송과 채팅 | 알림이 실제 푸시·이메일로 나가고, 참여자가 채팅으로 대화한다. 종료된 스터디의 채팅방은 닫힌다 | T-038 ~ T-051 |
| M5 | 운영과 출시 | 운영자가 신고·이의·정정을 처리하고, 운영 환경에서 실기기로 전체 흐름이 동작한다 | T-052 ~ T-068 |

### 순서의 이유와 버린 대안

- **M1을 가장 얇게 끝까지 관통하고, HTTPS 임시 배포(T-013)까지 M1에 넣는다.** 인증(쿠키·CSRF·same-site), Flyway, Testcontainers, CI가 처음부터 맞물려야 이후 슬라이스가 같은 틀을 반복한다. 위치 API·서비스 워커·웹 푸시는 HTTPS에서만 동작하므로, 실기기 위험(체크인, 푸시)을 일찍 보려면 HTTPS 환경이 먼저 있어야 한다.
  - 버린 대안: 배포를 M5에 한 번에 → 실기기 위치·푸시 문제를 출시 직전에 알게 되어 기각. 인프라 계층(DB 전체·인증 전체)부터 쌓기 → 시연 가능한 흐름이 늦고 계층 간 불일치가 늦게 드러나 기각.
- **정원 동시성(M2)을 두 번째로** 둔다. PRD 8장과 ARCHITECTURE 1장의 1순위 품질 속성(정합성)이며, 락 구조(ADR-0004)가 틀리면 참여·취소·승계·정원 변경·종료 처리를 모두 다시 써야 한다. 동시성 테스트를 별도 작업(T-016, T-021)으로 두어 실패를 일찍 본다.
- **알림 기반(아웃박스·알림 센터)을 M2 초반(T-014)에** 둔다. 그래야 M2·M3 작업이 같은 트랜잭션에서 알림을 직접 기록하고(X-8), 나중에 "이벤트를 알림에 다시 연결"하는 작업이 생기지 않는다. 실제 발송(워커·채널)은 M4로 미룬다.
- **출석 판정(M3)을 채팅·발송(M4)보다 먼저** 둔다. 서비스의 차별점(PRD 1장)이고, 시간 경계(KST 자정, 체크인 시간창)와 스케줄러 멱등성 버그가 지표·제재에 직접 번진다.
  - 버린 대안: 채팅을 먼저(눈에 보이는 기능 우선) → 지표·제재가 출석 판정에 의존하므로 판정 버그를 늦게 발견하게 되어 기각.
- **감사 로그 기록 기능(T-019)을 첫 자동 감점(주최자 이탈 감점, T-020) 앞에** 둔다. PRD 8장은 점수 변동을 감사 로그에 남기라고 요구하므로, 운영자 화면보다 기록 기능이 먼저 필요하다. 권한·조회 API는 M5(T-052).

## 4. 작업 목록

형식: `- [ ] T-번호 제목` 아래에 **선행**(작업·결정·사람 준비), **참조**(원본 문서 위치), **완료**(체크 가능한 기준), **문서**(확인·갱신할 문서와 `covers`). 모듈 이름은 ARCHITECTURE 3장 기준이다.

### M1 걸어다니는 뼈대

- [x] T-001 백엔드 프로젝트 뼈대
  - 선행: 결정 D-1 · 사람 H-4
  - 참조: ADR-0001, ADR-0003, ARCHITECTURE 3장·5장·7장·8장, AGENTS.md 6장, API 첫머리(springdoc)
  - 완료: Gradle Wrapper 프로젝트가 `com.everystudy` 아래 `common`(주입 가능한 `Clock`, RFC 9457 오류 응답, 설정 속성 틀)을 갖춘다 · 로컬 MySQL은 Docker Compose로 뜬다 · Testcontainers MySQL에 Flyway를 적용하고 JPA `validate`로 기동하는 테스트 통과 · springdoc으로 OpenAPI 문서가 생성된다 · 로그 마스킹(토큰·이메일·좌표) 기본 설정과, 로그 캡처 테스트에서 해당 패턴이 0건임을 확인 · `.env.example`에 키 이름만 · CI `backend` job이 자동으로 켜져 통과
    · Gradle 테스트 작업이 `test`(단위·슬라이스)와 `integrationTest`(Testcontainers, 태그)로 나뉘어 CI에서 따로 보이고, Testcontainers MySQL 컨테이너는 JVM 하나에서 공유한다 (ARCHITECTURE 8.1) · 단위 테스트 예시 하나(고정 `Clock`)와 테스트 데이터 빌더 위치를 정한다
  - 문서: ARCHITECTURE 3장과 실제 패키지 일치 확인, ARCHITECTURE 머리말 `covers`에 `backend/src/main/java/com/everystudy/common/**` 추가, ADR-0001의 D-1 결과 기록, AGENTS.md 6장 "테스트"에 이름·빌더·태그 규칙 추가

- [x] T-002 [프론트] 프론트 프로젝트 뼈대
  - 선행: 결정 D-1
  - 참조: ARCHITECTURE 6장, ADR-0001, API 1장(오류 형식·CSRF)
  - 완료: Next.js(App Router)·TypeScript·ESLint·Prettier·Vitest·Testing Library·TanStack Query 구성 · API 클라이언트 한 곳에서 `credentials` 포함, `X-XSRF-TOKEN` 헤더, 오류 `code` 분기 처리 · 단위 테스트: CSRF 헤더 부착, `code`별 오류 변환 · 토큰을 JS로 읽거나 저장하지 않음 · CI `frontend` job(lint, test, build) 통과
  - 문서: ARCHITECTURE 6장 확인, 머리말 `covers`에 프론트 소스 경로 추가

- [x] T-003 모듈 경계 테스트와 CI 확장
  - 선행: T-001 · 결정 D-2, X-8, X-11
  - 참조: ADR-0003, ARCHITECTURE 3장(의존 방향), ADR-0001(Flyway 번호 검사 예정)
  - 완료: 아키텍처 테스트가 "다른 모듈의 `domain`·`repository` 참조 금지", "`web → service → domain/repository`", 모듈 순환 금지(특히 `study`가 `chat`에 의존하지 않음, X-8)를 검사하고, 위반 예시로 실패함을 확인 · Flyway 파일명 형식·번호 중복·누락 검사 스크립트가 `tools/`에 있고 자체 테스트와 함께 CI에서 실행
  - 문서: ADR-0003에 D-2 결과 반영(결정 변경이면 새 ADR), AGENTS.md 6장 코드 컨벤션에 확정 규칙 추가, ARCHITECTURE 3장에 X-11 결과 반영

- [x] T-070 문서 `covers` 검사
  - 선행: T-001, T-003
  - 참조: docs/README.md 4장(`covers`), AGENTS.md 3장(코드가 바뀌면 문서도 같은 PR), `tools/docs-check.sh`
  - 완료: PR에서 바뀐 코드 경로가 어떤 문서의 `covers`에 해당하는데 그 문서가 같은 PR에서 바뀌지 않았으면 `docs-check`가 보고한다 (로컬은 경고, CI는 오류, 문서 갱신이 필요 없는 PR을 위한 명시적 예외 방법 포함) · 자체 테스트(`tools/tests/`)에 통과·실패·예외 사례 · CI에서 실행
  - 문서: docs/README.md 4장의 "`covers`는 코드가 생긴 뒤에 검사한다" 문구를 실제 동작으로 고침

- [x] T-071 CI 경로별 실행
  - 선행: T-003
  - 참조: docs/guides/agent-hooks.md 1장(CI), `.github/workflows/ci.yml`
  - 완료: PR에서 `backend` job은 `backend/**`가, `frontend` job은 `frontend/**`가 바뀌었을 때만 실행하고, CI 워크플로가 바뀌면 둘 다 실행한다 · develop·main으로의 push에서는 전부 실행 · 판단 규칙은 `tools/`의 스크립트에 있고 자체 테스트와 함께 CI에서 실행 · 필수 상태 검사(`CI 통과`)는 건너뛴 job을 통과로 본다 · 저장소는 나누지 않는다(문서·API 명세·도구를 한곳에서 맞추기 위해. 배포 분리는 T-013)
  - 문서: docs/guides/agent-hooks.md 1장

- [x] T-004 소셜 로그인과 토큰 쿠키 발급
  - 선행: T-001 · 결정 D-9 · 사람 H-1
  - 참조: ADR-0002 결정 1·2·4·확정 값, DATA-MODEL 3장(`user`, `social_account`, `refresh_token`), API 2장
  - 완료: 카카오·구글 OAuth2 로그인 성공 시 자체 액세스 JWT와 리프레시 토큰을 쿠키로 발급 · 소셜 토큰 미저장 · 같은 `(provider, provider_user_id)` 재로그인은 같은 사용자 · 신규 사용자는 온보딩으로 리다이렉트 · 쿠키 속성(HttpOnly, Secure, SameSite, Path)을 통합 테스트로 확인 (OAuth 공급자는 테스트에서 대체)
  - 문서: DATA-MODEL 3장·API 2장 확인, 두 문서 머리말 `covers`에 `auth/**`, `user/**` 추가

- [x] T-005 토큰 갱신·로그아웃·CSRF
  - 선행: T-004
  - 참조: ADR-0002 결정 2·3·4·6, ARCHITECTURE 4.1·8장(인증·CSRF), API 1장·2장
  - 완료: `/auth/refresh` 회전, 재사용 탐지 시 같은 `family_id` 전체 폐기와 401 · `/auth/logout` 폐기·쿠키 삭제 · CSRF 누락·불일치 시 `403 CSRF_INVALID` · 허용 외 `Origin` 거부, CORS는 프론트 오리진 하나 · 만료 액세스 토큰은 `401 UNAUTHENTICATED` · 각 경우 통합 테스트
  - 문서: API 1장 오류 표, ADR-0002와 구현 일치 확인

- [x] T-006 내 정보·온보딩 API와 메타 옵션
  - 선행: T-005 · 결정 X-6
  - 참조: PRD 4.1, API 3장(`GET /me`, `PUT /me/profile`)·4장(`GET /meta/options`), DATA-MODEL 1장(고정 목록)·3장
  - 완료: 온보딩(닉네임·직군·지역) 저장 · 닉네임 중복은 X-6에서 정한 코드로 거부(테스트) · `onboarded` 여부 반환 · `/meta/options`가 코드 목록과 클라이언트용 설정값을 설정 객체에서 읽어 반환(설정을 바꾸면 응답이 바뀌는 테스트)
  - 문서: API 1장(X-6 코드)·3·4장 갱신 확인

- [x] T-069 [프론트] 디자인 리서치와 DESIGN.md
  - 선행: T-002 · 사람: 레퍼런스 화면 캡처 제공
  - 참조: PRD 1장(대상 사용자)·5장(화면), AGENTS.md 5장, 아래 도구 주의
  - 완료: 서비스 성격(취업 준비생, 모바일 웹, 신뢰·출석 지표 중심)에 맞는 레퍼런스 서비스 3~5개와 선정 이유, 사람이 제공한 캡처를 분석한 결과(정보 순서·신뢰 지표 표현·정원/대기 표시)와 따라 할 것·버릴 것을 `docs/DESIGN.md`에 정리 · 캡처에서 가늠한 색·타이포·간격·모서리·그림자는 참고 자료로만 쓰고 그대로 복제하지 않은, 우리 값으로 정한 토큰표(라이트 기준, 다크는 후속 여부 명시) · 공통 컴포넌트 규칙(버튼·입력·카드·상태 뱃지·빈 화면·오류), M1 화면(로그인·온보딩·목록·상세·개설)의 레이아웃 원칙 · "템플릿처럼 보이지 않기" 점검 목록(기본 라이브러리 스타일 방치, 과한 그라데이션·그림자, 의미 없는 아이콘 장식 등)을 만들고 화면 시안에 적용 · 토큰이 프론트 코드(CSS 변수 또는 Tailwind 설정)에 반영되어 있고 테스트나 스토리 수준의 확인 방법이 있음 · 360px 폭 기준
  - 도구 주의: 레퍼런스 서비스(문토, 네이버 예약, 당근 등)는 robots.txt와 약관상 자동 수집·자동 열람이 막혀 있어 dembrandt 같은 도구나 브라우저 자동화를 쓰지 않는다. 입력은 사람이 직접 찍은 캡처뿐이다(개인정보는 가림). 레이아웃 패턴과 정보 구조만 참고하고, 상표·로고·일러스트·고유 문구·브랜드 정체성은 복제하지 않는다. 캡처 원본은 저장소에 커밋하지 않는다. 토큰·컴포넌트 규칙을 만드는 단계에서 `frontend-design` 스킬을 쓴다. dembrandt는 우리 서비스 화면이 만들어진 뒤 토큰이 DESIGN.md와 어긋나는지 확인하는 선택적 용도로만 쓸 수 있다
  - 문서: docs/README.md 문서 지도에 DESIGN 추가, 머리말 `covers`에 프론트 스타일 경로

- [x] T-007 [프론트] 랜딩·로그인·온보딩 화면
  - 선행: T-002, T-006, T-069
  - 참조: PRD 5장 화면 1·2, API 1장(401 후 refresh 1회 재시도), ADR-0002 결과(갱신 실패 시 로그인)
  - 완료: 카카오·구글 로그인 버튼, 온보딩 입력과 검증, 미온보딩 사용자는 온보딩으로 이동 · 테스트: 401 → refresh 1회 → 성공 시 원 요청 재시도, 실패 시 로그인 화면 · 브라우저 기기 에뮬레이션 360px 폭에서 가로 스크롤 없이 모든 입력이 보임을 PR에 스크린샷으로 남김
  - 문서: 해당 없음

- [x] T-008 스터디 개설 API
  - 선행: T-006 · 결정 X-8
  - 참조: PRD 4.2·4.12, API 4장(개설 요청·검증), DATA-MODEL 4장(`study`, `study_target_job`, `study_member`)·8장(`chat_room`), ADR-0004
  - 완료: 개설 시 주최자를 `study_member` `ACTIVE`로 넣는다 · `chat_room`은 `study`가 직접 만들지 않고, `chat` 모듈이 X-8에서 정한 방식으로 `StudyCreated`를 받아 **같은 트랜잭션에서** 만든다(개설 롤백 시 채팅방 0건 테스트) · `session_date`는 KST 날짜 · 고정 `Clock`으로 개설 가능 범위 마지막 날, 자정을 넘기는 진행 시간, 과거 시작, 정원 범위 경계 테스트 · 개설 제한 제재 검사는 T-035b에서 추가
  - 문서: DATA-MODEL 4장, API 4장 확인, 머리말 `covers`에 `study/**`, `chat/**` 추가

- [x] T-009 스터디 목록·상세·참여자 목록 API
  - 선행: T-008
  - 참조: PRD 4.3·4.14(모집 중 파생), API 1장(커서)·4장(목록 필터·상세 필드·`myState`, `GET members`), DATA-MODEL 4장 인덱스
  - 완료: 공개 목록(지역·주제·직군·모집 중 필터, 최신순·마감임박순, 커서) · 상세(`recruiting` 파생, `memberCount`, `members`, `myState`, `scheduleEditable`, `checkinOpensAt`, `checkinClosesAt`, 주최자에게만 `waitingCount`) · `GET /studies/{id}/members`(참여자만) · 지표는 M3 전까지 신규로 응답 · 테스트: 비회원·회원·주최자별 필드 노출 차이, 비참여자의 참여자 목록 `403`
  - 문서: API 4장 상세 필드 목록에 실제 필드 반영 (X-5)

- [x] T-010 [프론트] 스터디 만들기 화면
  - 선행: T-007, T-008 · 사람 H-2
  - 참조: PRD 4.2·5장 화면 5, ARCHITECTURE 6장(카카오맵은 클라이언트에서만)
  - 완료: 카카오맵에서 장소를 고르면 좌표·장소명·주소가 채워진다 (실제 지도 확인은 H-2 보류로 운영 준비 때. 그 전까지는 예시 장소로 확인) · 입력 검증은 `/meta/options` 값 기준 · 테스트: 서버 `VALIDATION_FAILED`의 필드 오류가 해당 입력 아래 표시, 범위 밖 날짜 선택 불가 · 개설 후 상세로 이동
  - 문서: 해당 없음

- [x] T-011 [프론트] 스터디 목록·상세 화면
  - 선행: T-009, T-010
  - 참조: PRD 4.3·5장 화면 3·4, API 4장
  - 완료: 필터·정렬·커서 무한 스크롤 목록, 카드 표시 항목(PRD 4.3) · 상세의 지도·일정·참여자 목록 · 테스트: 비회원 상세 열람, 필터 변경 시 첫 페이지부터 다시 조회
  - 문서: 해당 없음

- [x] T-012 로컬 통합 실행
  - 선행: T-011
  - 참조: ARCHITECTURE 9장, ADR-0002 결과(로컬 same-site)
  - 완료: 명령 하나로 MySQL·백엔드·프론트가 로컬에서 같은 사이트로 뜨고 쿠키 로그인이 동작 · 실행 방법을 저장소 루트 README에 적음 · M1 시연 시나리오의 로컬 부분을 처음부터 끝까지 수행
  - 문서: ARCHITECTURE 9장 로컬 항목 확인

- [ ] T-013 최소 HTTPS 배포
  - 선행: T-012 · 결정 D-3 · 사람 H-6, H-7
  - 참조: ARCHITECTURE 9장, ADR-0002 결정 4·확정 값(`Domain`), PRD 9장 남은 것 1
  - 완료: 백엔드·프론트 이미지와 Compose로 HTTPS 환경(D-3에서 정한 호스트, 임시 호스트·터널 포함)에 같은 사이트로 배포 · 휴대폰 브라우저에서 HTTPS로 로그인·개설·목록 조회가 동작 · 배포 명령이 문서화되어 다시 실행할 수 있음 · 백엔드와 프론트는 따로 배포할 수 있다(배포를 자동화하면 `backend/**` 변경은 백엔드만, `frontend/**` 변경은 프론트만 다시 배포) · 백업·복구·운영 마감은 T-065
  - 문서: ARCHITECTURE 9장·11장 1번에 D-3 결과 반영

**M1 시연 시나리오**: 로컬에서 명령 하나로 전체를 띄운다. 비회원으로 빈 목록을 본 뒤 카카오로 로그인하면 온보딩 화면이 나오고, 닉네임·직군·지역을 입력한다. 스터디 만들기에서 지도로 장소를 고르고 날짜·시간·정원을 입력해 개설하면 상세 화면으로 이동하고, 로그아웃 후 비회원으로도 목록과 상세에서 그 스터디를 볼 수 있다. 같은 흐름을 휴대폰으로 HTTPS 임시 환경에서 반복한다. CI는 백엔드·프론트·문서·모듈 경계 검사를 모두 통과한다.

### M2 참여·정원·대기

- [x] T-014 알림 기반: 아웃박스·알림 센터
  - 선행: T-006 · 결정 X-8, X-10
  - 참조: ADR-0005 알림, ARCHITECTURE 4.6, DATA-MODEL 7장(`notification`, `notification_outbox`), API 3장(`/me/notifications`)
  - 완료: `notification` 모듈의 공개 서비스로 알림과 채널별 아웃박스 행을 **호출한 쪽 트랜잭션 안에서** 기록 · `dedupe_key`로 중복 생성 방지 · 채널 발송은 인터페이스와 테스트용 가짜 구현만(실제 워커는 T-038) · 알림 센터 조회(커서)·읽음·모두 읽음 API, 예약 건 노출은 X-10 결정대로 · 테스트: 원 트랜잭션 커밋 시 아웃박스 행 존재, 원 트랜잭션 롤백 시 아웃박스 0건, 같은 `dedupe_key` 두 번 기록 시 1건
  - 문서: DATA-MODEL 7장(X-10 반영), API 3장 확인, 머리말 `covers`에 `notification/**` 추가

- [x] T-015 참여 신청 API
  - 선행: T-009 · 결정 X-8
  - 참조: PRD 4.4·4.7(입장 기준)·4.8, ADR-0004 결정 1~3·5, ARCHITECTURE 4.2, DATA-MODEL 4장(`study_member`)·6장(`user_sanction`, `user_trust_summary`), API 4장 참여
  - 완료: `study` 행 비관적 락 안에서 PRD 4.4 순서대로 검사하고 즉시 확정 · `user_sanction`·`user_trust_summary` 테이블을 만들고 이용 제한·입장 기준 검사(지표 데이터가 없으면 신규로 취급) · 나간 사용자의 재신청은 같은 행 재활성화 · 희망 직군에 내 대분류가 없으면 `JOB_NOT_ALLOWED`(X-18) · 체크인 오픈 이후 `STUDY_NOT_RECRUITING` · 락 대기 초과 시 `409 LOCK_TIMEOUT` · 테스트: 오류 코드별 1건씩, 여러 조건이 동시에 어긋날 때 PRD 4.4 순서상 앞선 오류가 나옴
  - 문서: DATA-MODEL 4·6장, API 4장 확인, 머리말 `covers`에 `trust/**` 추가

- [x] T-016 정원 동시성 테스트
  - 선행: T-015
  - 참조: ADR-0004 결과 "테스트 필수" (a)·(c), ARCHITECTURE 8장
  - 완료: Testcontainers MySQL에서 (a) 정원보다 많은 수(정원 + 여유, 테스트 설정값)의 사용자가 마지막 1자리에 동시 신청해도 확정은 정원만큼, (c) 같은 사용자의 동시 중복 신청은 1건만 확정 · 각 시나리오를 반복 실행(`@RepeatedTest`, 횟수는 테스트 설정값)해 전부 통과 · CI에서 실행
  - 문서: ARCHITECTURE 8장 확인, 락 대기 시간 기본값을 ARCHITECTURE 4.2에 기록(진행 규칙 9)

- [x] T-017 대기 등록·취소 API
  - 선행: T-015 · 결정 X-16
  - 참조: PRD 4.14(정원과 대기), API 4장(`waiting`), DATA-MODEL 4장(`study_waiting`)
  - 완료: 정원이 찼을 때만 대기 · 취소는 행 삭제 · 체크인 오픈 이후 등록은 `STUDY_NOT_RECRUITING` · 체크인 오픈 이후 남은 대기 행의 처리는 X-16 결정대로(권장안: 조회·알림 대상에서 걸러 내고 종료 처리 T-030에서 삭제) · 상세의 `myState=WAITING`, 주최자에게만 대기 인원 수 · 테스트: `STUDY_NOT_FULL`, `ALREADY_WAITING`, `STUDY_NOT_RECRUITING`, `USER_RESTRICTED`, `ENTRY_CRITERIA_NOT_MET`, `JOB_NOT_ALLOWED` 각 1건, 비주최자 응답에 `waitingCount` 없음, 체크인 오픈 후 대기했던 사용자의 `myState`가 `WAITING`이 아니고 `waitingCount`에서 빠짐
  - 문서: DATA-MODEL 4장, API 4장 확인

- [x] T-018 나가기(참여 취소) API와 사전 조회
  - 선행: T-014, T-017 · 결정 X-9
  - 참조: PRD 4.4 5항·4.6(사전/직전 취소)·4.12(나가기 의미)·4.14, API 4장(`DELETE members/me`, `leave-preview`), DATA-MODEL 4장·5장(`attendance_event`)
  - 완료: 락 안에서 `LEFT` 처리, `cancel_type` 판정과 `attendance_event`(`EARLY_CANCEL`/`LATE_CANCEL`) 기록(X-9 결정대로 재신청 후 재취소가 가능) · `leave-preview`가 같은 판정 로직 사용 · 자리가 나면 **자격이 남은** 대기자(이용 제한·입장 기준·희망 직군 재검사, X-20)에게 대기 알림을 같은 트랜잭션에서 아웃박스에 기록 · 테스트: 대기 뒤 제재를 받거나 출석률이 기준 아래로 떨어진 대기자는 알림이 기록되지 않음, 고정 `Clock`으로 취소 기준 시각(PRD 4.6) 직전·직후, 나가기→재신청→재나가기
  - 문서: DATA-MODEL 5장(X-9 반영), API 4장 확인 (`attendance_event`는 `trust` 모듈 소속, X-11)

- [x] T-019 감사 로그 기록 기능
  - 선행: T-003 · 결정 D-17
  - 참조: PRD 4.11·8장(점수 변동 감사 로그), ARCHITECTURE 1장(감사 가능성)·4.5, DATA-MODEL 9장(`audit_log`)
  - 완료: `audit_log` 테이블과 기록용 공개 서비스(호출한 트랜잭션 안에서 기록, 시스템 행위자는 `NULL`) · 기록 범위는 D-17 결정대로 · 테스트: 원 트랜잭션 롤백 시 감사 로그 0건, 변경 전후 값이 `detail`에 남음 · 조회 API·권한은 T-052
  - 문서: DATA-MODEL 9장 확인, 머리말 `covers`에 감사 로그 경로(`common/**` 아래, X-11) 포함 확인

- [x] T-020 주최자 나가기: 승계·파투·감점
  - 선행: T-018, T-019 · 결정 X-13
  - 참조: PRD 4.13A·B, ADR-0004 결정 4, ARCHITECTURE 4.3, DATA-MODEL 4장(`host_transfer`)·6장(`host_penalty_event`), API 4장(`outcome`)
  - 완료: 같은 락 안에서 남은 참여자 없으면 파투(`CANCELLED`, `HOST_LEFT`)와 대기 행 삭제, 있으면 주입 가능한 `RandomSource`로 1명 승계 · 세션 시작 후에는 승계하지 않고 `LEFT_AFTER_START`(멤버 상태·출석 처리는 X-13 결정대로) · 감점 조건(PRD 4.13B)일 때만 `host_penalty_event`와 감사 로그 · 파투 시 참여자 지표 영향 없음 · 새 주최자 알림과 파투 알림을 아웃박스에 기록 · 테스트: 고정 `RandomSource`로 승계 대상 확인, 감점 기준 시각 직전·직후
  - 문서: DATA-MODEL 4·6장, API 4장 확인

- [x] T-021 승계 동시성 테스트
  - 선행: T-020
  - 참조: ADR-0004 결과 "테스트 필수" (b), PRD 8장
  - 완료: Testcontainers MySQL에서 주최자 나가기와 참여자 나가기를 동시에 실행해 승계 대상이 정확히 1명이고 나간 참여자가 아님을 반복 실행(`@RepeatedTest`, 횟수는 테스트 설정값)으로 확인 · 남은 참여자가 0명이 되는 경계에서 파투와 승계가 동시에 일어나지 않음
  - 문서: 해당 없음

- [x] T-022 정원 변경과 스터디 수정 API
  - 선행: T-014, T-017 · 결정 X-11
  - 참조: PRD 4.2(일정 수정 불가 조건)·4.14, API 4장(`PATCH`, `PUT capacity`), ADR-0004 결정 1
  - 완료: 정원 변경은 락 안에서, 상한은 설정값, 증액 시 자격이 남은 대기자에게만 대기 알림을 아웃박스에 기록(T-018과 같은 재검사, X-20) · 제목·소개는 항상 수정, 일정·장소는 주최자 외 참여자가 있으면 `SCHEDULE_LOCKED` · 일정 수정 코드는 X-11에서 정한 모듈에 둔다 · 테스트: `CAPACITY_BELOW_MEMBERS`, `SCHEDULE_LOCKED`, 비주최자 `403`, 상한 초과 `VALIDATION_FAILED`
  - 문서: API 4장 확인, X-11 결과에 따라 해당 모듈 `covers` 추가

- [x] T-023 내 스터디 조회 API
  - 선행: T-020
  - 참조: PRD 4.10, API 3장(`GET /me/studies`)
  - 완료: `role=host|member`, `state=upcoming|past` 조합 응답과 커서 페이징 · 테스트: 승계 후 새 주최자는 `role=host`, 전 주최자는 목록에서 빠짐
  - 문서: API 3장 확인

- [x] T-024 [프론트] 상세의 참여·나가기·대기
  - 선행: T-011, T-018, T-020
  - 참조: API 4장(`myState`별 버튼, 오류 코드), PRD 4.4 3항(직군 안내)
  - 완료: `myState`와 모집 여부로 버튼 결정 · 나가기 전에 `leave-preview` 결과로 확인 창(주최자는 승계·파투 안내) · 희망 직군이 맞지 않으면 신청 전에 안내(`JOB_NOT_ALLOWED`, X-18) · 테스트: `STUDY_FULL` → 대기 안내, `ENTRY_CRITERIA_NOT_MET`·`USER_RESTRICTED`·`LOCK_TIMEOUT`(재시도 안내) 분기, `myState` 4종별 버튼
  - 문서: 해당 없음

- [x] T-025 [프론트] 알림 센터
  - 선행: T-007, T-014
  - 참조: PRD 5장 화면 12, API 3장(`/me/notifications`)
  - 완료: 알림 목록(커서), 읽음·모두 읽음, 알림에서 해당 스터디로 이동 · 테스트: 읽지 않은 알림 표시가 읽음 후 사라짐 · 설정 화면은 T-045
  - 문서: 해당 없음

- [x] T-026 [프론트] 내 스터디 관리와 내 스터디 목록
  - 선행: T-022, T-023, T-024
  - 참조: PRD 5장 화면 6, API 4장(`scheduleEditable`, `waitingCount`)
  - 완료: 정원 변경, 대기 인원 표시, 참여자 목록 · 참여·주최 중 스터디 목록 · 테스트: `scheduleEditable=false`면 일정·장소 입력 잠금과 새 스터디 안내, `CAPACITY_BELOW_MEMBERS` 안내
  - 문서: 해당 없음

**M2 시연 시나리오**: 사용자 A가 스터디를 만들고 B, C가 참여해 정원이 차면 D에게는 "대기하기"가 보인다. D가 대기하고, A는 관리 화면에서 대기 인원을 보고 정원을 늘린다. D의 알림 센터에 자리가 났다는 알림이 생긴다. B가 직전 취소 기준(PRD 4.6) 안에 나가면 기록 반영 안내가 먼저 뜬다. A가 나가면 남은 사람 중 한 명이 새 주최자가 되고 알림 센터에 알림이 생기며, 일정 수정은 잠겨 있다. CI에서 마지막 1자리 동시 신청 테스트와 주최자·참여자 동시 나가기 테스트가 통과한다.

### M3 출석과 신뢰

- [x] T-027 위치 체크인 API와 체크인 현황
  - 선행: T-015, T-018 · 결정 X-15
  - 참조: PRD 4.6, ARCHITECTURE 4.4, DATA-MODEL 5장(`checkin`), API 5장, ADR-0004 결정 1
  - 완료: 체크인은 `study` 행 락 안에서 처리하고, 시간창은 락을 잡은 뒤의 서버 시각으로 검사한다(X-15, 자동 판정 T-031과 직렬화) · 서버 시각과 Haversine 거리로 판정 · `(study_id, user_id)` 멱등(재요청은 같은 결과 `200`) · 원본 좌표 미저장 · 출석·지각 `attendance_event` 기록 · `GET attendance`는 참여자 본인 것, 주최자는 전원 체크인 여부만 · 테스트: 고정 `Clock`으로 PRD 4.6의 체크인 시작·출석 경계·지각 경계·마감 각각 직전·직후, 반경 경계 안·밖(`CHECKIN_OUT_OF_RANGE`), 시간창 밖(`CHECKIN_WINDOW_CLOSED`), 나간 사용자 `403`
  - 문서: DATA-MODEL 5장, API 5장 확인, 머리말 `covers`에 `attendance/**` 추가

- [x] T-028 일회용 코드 발급과 코드 체크인
  - 선행: T-027 · 결정 D-7
  - 참조: PRD 4.6(보조 수단), ARCHITECTURE 4.4, DATA-MODEL 5장(`checkin_code`), API 5장
  - 완료: 재발급 시 이전 코드 무효 · 코드 체크인도 시각 기준 판정이 같음 · 테스트: 주최자 체크인 전 발급 `HOST_NOT_CHECKED_IN`, 틀린 코드·만료·시도 초과 각각 `CHECKIN_CODE_INVALID`, 재발급 후 이전 코드 거부
  - 문서: DATA-MODEL 5장·11장 3번 갱신(D-7 결과)

- [ ] T-029 [프론트] 체크인 화면과 실기기 위치 초기 점검
  - 선행: T-013, T-024, T-028
  - 참조: PRD 4.6·5장 화면 7, API 5장(`checkinOpensAt`, `checkinClosesAt`)
  - 완료: 체크인 순간에만 위치 요청, 권한 거부·GPS 실패 시 코드 입력 안내, 주최자 화면에 코드 표시 · 테스트: 시간창 전·중·후 버튼 상태, 위치 거부 시 코드 입력으로 전환, `CHECKIN_OUT_OF_RANGE` 안내, `LOCK_TIMEOUT`이면 같은 요청을 1회 자동 재시도하고 다시 실패하면 재시도 버튼 표시 · HTTPS 임시 환경(T-013)에서 iOS Safari와 Android Chrome 실기기로 위치 권한 요청·거부·정확도 값을 확인하고 결과를 PR에 기록(문제는 새 작업으로 추가)
  - 문서: 해당 없음

- [x] T-030 스케줄러 기반, 스터디 종료 처리, 토큰 정리
  - 선행: T-005, T-017 · 결정 X-16
  - 참조: ADR-0005 스케줄러, ARCHITECTURE 4.7, PRD 4.14(종료 처리)·8장, DATA-MODEL 10장
  - 완료: `@Scheduled` 작업 틀(한 번에 처리 상한, 멱등, 작업별 예외 격리) · 종료 처리는 KST 자정 기준, 락 안에서 상태 변경, 해당 스터디 대기 행 삭제, `StudyEnded` 발행(채팅방 폐쇄는 T-049) · 만료 리프레시 토큰 정리 · 테스트: 고정 `Clock`으로 자정 직전·직후, 두 번 실행해도 결과 동일, 토큰 만료 직전 행은 남음
  - 문서: ARCHITECTURE 4.7·DATA-MODEL 10장·ADR-0005 작업 목록에 X-16 결과 반영 확인, 배치 상한 기본값을 ARCHITECTURE 4.7에 기록(진행 규칙 9) — 확인함(작업 목록은 이미 일치), 배치 상한 100·주기를 4.7에 기록

- [x] T-031 주최자 미도착 무효와 노쇼 후보 판정
  - 선행: T-019, T-020, T-027, T-030 · 결정 X-11, X-15
  - 참조: PRD 4.6(자동 판정)·4.13B, ARCHITECTURE 4.4, DATA-MODEL 4장(`judged_at`, `voided_at`)·5장·10장, ADR-0004 결정 1·6
  - 완료: 스터디마다 `study` 행 락을 잡은 뒤 대상 조건을 다시 확인하고 판정한다(X-15) · 주최자 미체크인 세션 무효(`voided_at`, 참여자 `VOIDED`, 참여자가 있을 때만 주최자 `ABSENT` 감점과 감사 로그) · 체크인 마감 후 미체크인 참여자 `NO_SHOW_CANDIDATE`와 노쇼 후보 통지 알림 · 파투 스터디(주최자 포기·운영자 강제 종료 모두)의 참여자 `VOIDED`는 이 작업이 유일하게 기록 · `judged_at`으로 멱등 · 작업의 소속 모듈은 X-11 결정대로 · 테스트: 고정 `Clock` 경계, 재실행 시 이벤트·알림 중복 0건, 이벤트 유일 제약(X-9) 위반은 오류가 아니라 "이미 처리됨"으로 넘기고 다음 스터디를 계속 처리, Testcontainers로 마감 경계에서 체크인과 판정을 동시에 실행하면 결과가 하나(체크인 성공이면 그 사용자에게 노쇼 후보 없음, 주최자 체크인 성공이면 무효 아님)
  - 문서: DATA-MODEL 4·5·10장 확인 — 10장의 두 작업(무효 처리·노쇼 후보 판정)을 한 판정 작업으로 합친 결정을 반영(X-30), ARCHITECTURE 4.4·4.7 반영

- [x] T-032 출석 지표 집계와 재계산
  - 선행: T-031 · 결정 X-12
  - 참조: PRD 4.7, ARCHITECTURE 4.5, DATA-MODEL 5장(현재 판정 규칙)·6장(`user_trust_summary`)
  - 완료: 이벤트가 쌓일 때 `user_trust_summary` 갱신, 이벤트로부터 전체 재계산 기능 · 테스트: PRD 4.7의 분자·분모 포함·제외 규칙을 이벤트 종류별로 1건씩(지각은 분자 0.5), 신규 기준 경계, 최근 N회 창 밖 이벤트 제외, 재계산 결과가 증분 갱신 결과와 같음 · 입장 기준 검사(T-015)가 실제 지표로 동작
  - 결과: `trust.service.TrustSummaryService`(`refresh`·`recalculateAll`)와 순수 계산 `AttendanceTally` · `AttendanceEventRecorder`가 출석률에 영향을 주는 이벤트(출석·지각·사전/직전 취소·세션 무효)를 남길 때 같은 트랜잭션에서 집계 갱신(노쇼 후보는 지표에 반영되지 않아 갱신 안 함) · 집계 행은 `INSERT ... ON DUPLICATE KEY UPDATE` 한 문장으로 써서 첫 집계의 동시 삽입이 호출 트랜잭션을 깨지 않게 함 · 새 설정 `everystudy.trust.recent-sessions`(기본 10) · 노쇼 확정(T-035a)과 이의 정정(T-056)은 이벤트를 남긴 뒤 `TrustSummaryService.refresh`를 부른다
  - 문서: DATA-MODEL 6장에 집계 규칙(최근 10회의 기준, 제외 판정, 동시성 한계) 추가(v0.18)

- [x] T-033 공개 프로필과 주최 지표
  - 선행: T-032
  - 참조: PRD 4.1(공개 정보)·4.13D, API 3장(지표 응답, `GET /users/{id}`)
  - 완료: 공개 프로필, 출석 지표 응답, 주최 지표(주최 횟수, 직전 이탈 횟수, 건수만 표시 조건) · 목록·상세·참여자 목록이 실제 지표 반환 · 테스트: 신규 사용자 `attendanceRate: null`, 주최 횟수 경계(PRD 4.13D)에서 `showCountOnly` 전환, 탈퇴 사용자 표시
  - 결과: `GET /api/v1/users/{id}`(공개, `study.web.PublicProfileController`·`study.service.PublicProfileService`) · `trust.service.TrustMetricsService`가 출석 지표와 직전 이탈 건수를 한 번에 읽고(`AttendanceMetrics`), `study.service.PersonMetrics`가 주최 횟수를 더해 목록·상세·참여자 목록·프로필이 같은 계산을 쓴다 · 프로필을 `study`에 둔 이유: `study`가 `user`·`trust`를 이미 참조하고 주최 횟수는 `study` 데이터라, `user`나 `trust`에 두면 모듈이 순환한다 · 내가 정한 세부 결정(API 3장에 기록): 출석률은 내림한 정수 · 주최 횟수 = 끝난 스터디(현재 주최자) + 직전 이탈 승계 · 온보딩 전 회원은 404, 탈퇴 회원은 200에 이름 비움 · 새 설정 `everystudy.trust.host-count-only-below`(기본 5)
  - 문서: API 3장 확인(v0.20)

- [x] T-034 이의 신청 API와 출석 기록 조회
  - 선행: T-031 · 결정 X-14
  - 참조: PRD 4.9, DATA-MODEL 5장(`appeal`), API 3장(`/me/attendance`)·5장(이의 신청)
  - 완료: 노쇼·지각 판정당 1회, 본인만 · 주최자 1차 의견 등록 · `/me/appeals`, `/me/attendance`(이의 가능 여부 포함) · 테스트: 기한 후 `409 APPEAL_CLOSED`, 같은 판정 두 번째 이의 거부, 타인 판정 `403`, 비주최자 의견 `403`
  - 문서: DATA-MODEL 5장, API 1장(X-14)·3·5장 확인
  - 결과: `attendance.service.AppealService`(신청·주최자 의견·내 이의)와 `MyAttendanceService`(`/me/attendance`), 마이그레이션 V12 `appeal` · 출석 이벤트 읽기는 `trust.service.AttendanceLedger`(공개 서비스)로만 한다 · **`appeal`을 `trust`가 아니라 `attendance` 모듈에 둔 이유**: 주최자 확인에 `study`를 읽어야 하는데 `study`가 이미 `trust`를 참조해 `trust → study`는 순환이다(ARCHITECTURE 3장 갱신) · 내가 정한 세부 결정(DATA-MODEL 5장·API 3·5장에 기록): 이의 대상은 `NO_SHOW_CANDIDATE`·`LATE`뿐 · 기한 = 그 판정 이벤트 시각 + `appeal-window-hours`, 정각까지 허용 · 이의 시점에 그 판정이 현재 판정이 아니면 거부 · 새 오류 `APPEAL_NOT_ALLOWED`·`ALREADY_APPEALED`, `APPEAL_CLOSED`는 결정된 이의에 대한 주최자 의견에도 씀 · 주최자 의견은 결정 전까지 덮어쓰기 · `/me/attendance`는 스터디마다 현재 판정 하나씩 이벤트 id 내림차순 커서

- [x] T-035a 노쇼 확정과 참여자 제재
  - 선행: T-032, T-034 · 결정 D-15, X-4
  - 참조: PRD 4.8·4.6(노쇼 확정), ARCHITECTURE 4.5, DATA-MODEL 6장(`user_sanction`)·10장
  - 완료: 이의 없이 기간이 지난 후보를 `NO_SHOW`로 확정(멱등) · 참여자 제재(PRD 4.8, 환산 노쇼 = 노쇼 + ⌊지각 ÷ 3⌋)를 노쇼 확정과 지각 기록 때 판정해 설정값으로 부여하고 감사 로그와 알림(type 이름은 X-4 결정대로) 기록, 경고 단계는 행 없이 알림만 · 신청 거부 시 `USER_RESTRICTED`와 `until` · 제재 판정 로직은 T-056에서 재사용할 수 있게 공개 서비스로 둔다 · 테스트: 열린 이의가 있는 후보는 확정하지 않음, 단계별 경계(지각만 3회 → 경고, 노쇼 1 + 지각 3 → 제한), 재실행 시 제재 중복 0건
  - 문서: DATA-MODEL 6장, API 1장 확인
  - 결과: `attendance.service.NoShowConfirmationService`(10분마다, `NoShowConfirmationJob`)가 이의 기간(48시간)이 **지난** 후보를 확정 · 열린 이의가 있으면 건너뛰고 기각되면 확정, 정정으로 현재 판정이 아니면 확정하지 않음 · 열린 이의가 한 번에 처리할 건수보다 많아도 뒤의 후보가 굶지 않게 id 를 따라 쪽을 넘겨 봄 · 판정은 `trust.service.ParticipantSanctionService`(`judgeAfterNoShow`·`judgeAfterLate`, T-056 재사용)가 같은 트랜잭션에서 하고, 순수 계산은 `SanctionJudgement`(단위 테스트) · 지각 판정은 `CheckinService`가 지각 이벤트를 남긴 직후 부름 · 새 알림 type `SANCTION_WARNING`·`SANCTION_APPLIED`(V14, X-4 중 참여자 몫) · 내가 정한 세부 결정: ① 이의 기한 정각에는 확정하지 않고 1초 뒤부터(이의는 기한 정각까지 받으므로 겹치지 않게) ② 제한은 해제되지 않은 자동 제한이 있으면 다시 걸지 않고, 정지는 유효한 정지가 있으면 다시 걸지 않음(정지가 끝난 뒤에도 재발 기준이 계속 충족되면 새 정지) ③ 노쇼 확정마다 경고 알림(제한·정지가 걸리면 그 알림이 대신), 지각은 최근 5회 중 3회 이상일 때만 경고 ④ 같은 시각에 확정된 노쇼는 제한을 부른 건으로 보아 재발로 세지 않음 ⑤ 제재 알림 유효 기간은 설정 `everystudy.trust.notice-valid-days`(기본 7일, D-18에서 종류별 기준이 정해지면 따름) ⑥ 단계 수치는 모두 `everystudy.trust.*` 설정값(PRD 4.8 기본값)
  - 로컬 검증: Docker Desktop 이 멈춰(ryuk `Created`) 통합 테스트(`NoShowConfirmationIntegrationTest`)는 CI 에서 확인

- [x] T-035b 주최자 개설 제한과 운영자 검토 표시
  - 선행: T-031, T-035a · 결정 X-4
  - 참조: PRD 4.13B·4.13C, ARCHITECTURE 4.5, DATA-MODEL 6장(`host_penalty_event`, `user_sanction`)
  - 완료: 주최자 개설 제한은 감점이 기록되는 시점(주최자 이탈 T-020 경로, 미도착 무효 T-031 경로)에 **같은 트랜잭션에서** 롤링 합계(PRD 4.13B)를 계산해 판정하고, 개설 제한(`HOST_RESTRICT`)과 감사 로그·알림 기록 · 주최자 합계가 운영자 검토 단계(PRD 4.13C)에 이르면 운영자 검토 대상으로 표시(조회는 T-055) · 개설(T-008)에 개설 제한 검사 추가, 거부 시 `USER_RESTRICTED`와 `until` · 테스트: 단계별 경계, 롤링 기간 밖 감점 제외, 감점 트랜잭션 롤백 시 제한 0건
  - 문서: DATA-MODEL 6장, API 1장 확인
  - 결과: `trust.service.HostPenaltyRecorder`가 감점(이탈 T-020·미도착 T-031 경로 모두)을 저장한 같은 트랜잭션에서 90일 롤링 합계로 판정 · 합계 2점 `HOST_WARNING` 알림, 3점 7일 `HOST_RESTRICT`, 5점 30일 `HOST_RESTRICT` + `review_required`(V15) · 개설(`StudyCreationService`)은 `SanctionService.hostRestrictedUntil`로 검사해 `USER_RESTRICTED`와 `until` · 내가 정한 세부 결정: ① 개설은 개설 제한과 이용 정지가 막고 참여 신청 제한은 막지 않음 ② 이미 더 늦게 끝나는 유효한 개설 제한이 있으면 같은 제한을 다시 걸지 않음(3점 → 4점은 그대로, 5점은 30일로 새로 걸림) ③ 경고는 합계가 경고 단계인 감점에서 나가고(감점마다 한 건이라 중복 없음) 개설 제한이 걸리면 `SANCTION_APPLIED` 알림이 대신 ④ 검토 표시는 `user_sanction.review_required` 컬럼, 조회는 T-055 ⑤ 수치는 `everystudy.trust.host-*` 설정값(PRD 4.13C 제안값, D-13 확정 전)
  - 로컬 검증: 단위 테스트 통과, 통합 테스트(`HostSanctionIntegrationTest`)는 CI 에서 확인

- [x] T-036 [프론트] 마이페이지: 지표와 출석 기록
  - 선행: T-026, T-033
  - 참조: PRD 4.7(표시 형태)·4.10·5장 화면 10
  - 완료: 지표 문구가 PRD 4.7 표시 형태를 따르고 신규 배지 표시 · 지난 출석 기록 · 참여자 목록에서 공개 프로필 이동 · 테스트: 신규·일반·주최 지표 건수만 표시 3가지 렌더링
  - 문서: 해당 없음
  - 결과: `/me`(마이페이지: 내 지표 + `GET /me/attendance` 지난 출석 기록 "더 보기"), `/users/{id}`(공개 프로필) · 지표 블록은 `app/users/ProfileMetrics`를 두 화면이 함께 쓰고 문구는 `studyFormat.trustSummaryText`가 정한다 · 헤더 메뉴에 "마이페이지"를 추가, 스터디 상세의 참여자 이름이 공개 프로필로 이어진다 · 내가 정한 세부 결정: 내 지표는 `GET /users/{내 id}`(남이 보는 것과 같은 응답)로 받는다 · 신규는 숫자 없이 "신규" 배지와 "최근 N회" 안내만 보인다 · 출석 기록 줄은 판정 이름·스터디·날짜(KST)까지만 보이고 이의 신청 버튼은 T-037에서 더한다 · 출석 기록은 `AttendanceRow`(최근 N회 모양 줄)를 쓰지 않는다: API가 건수만 주고 회차별 판정을 주지 않기 때문

- [x] T-037 [프론트] 이의 신청과 주최자 1차 의견
  - 선행: T-034, T-036
  - 참조: PRD 4.9·5장 화면 11, API 5장(이의 신청)
  - 완료: 이의 신청과 내역 화면, 주최자의 1차 의견 입력 화면 · 테스트: 기한이 지난 판정은 신청 버튼 없음, `APPEAL_CLOSED` 안내, 주최자가 아니면 의견 입력 없음
  - 문서: 해당 없음
  - 결과: 마이페이지 출석 기록 줄에서 바로 신청(`app/me/AppealForm`, `appealable`일 때만 버튼), `/me/appeals` 내 이의 내역, `/studies/{id}/appeals` 주최자 의견 화면(상세의 "들어온 이의 보기" 링크는 주최자에게만) · **백엔드 보완(T-034 명세 누락)**: 주최자가 자기 스터디의 이의를 찾을 방법이 API에 없어 `GET /studies/{id}/appeals`(주최자 전용, 신청자 닉네임·사유 포함)를 이 PR에서 추가하고 API 5장에 기록(v0.22) · 내가 정한 세부 결정: 신청 입력은 별도 화면 없이 기록 줄에서 펼친다 · 결정(`ACCEPTED`/`REJECTED`)된 이의는 의견 입력칸 없이 남긴 의견만 보인다 · 이의 관련 오류 3종(`APPEAL_CLOSED` 등)을 프론트 오류 코드 목록에 추가

**M3 시연 시나리오**: 개발용으로 시작 시각을 가까이 둔 스터디에서, HTTPS 임시 환경의 휴대폰으로 주최자와 참여자가 장소 근처에서 체크인해 출석·지각이 기록되고, 위치를 거부한 참여자는 주최자가 보여준 코드로 체크인한다. 체크인하지 않은 참여자는 마감 뒤 자동으로 노쇼 후보가 되어 알림 센터에 통지를 받고 이의를 신청하며, 주최자는 1차 의견을 남긴다. 주최자가 오지 않은 다른 스터디는 무효가 되어 참여자 지표에 영향이 없다. 시각 경계·기간 경과 확정·제재·자정 종료 처리는 고정 `Clock` 테스트로 보인다.

### M4 알림 발송과 채팅

- [x] T-038 알림 워커·재시도·만료
  - 선행: T-014, T-030
  - 참조: ADR-0005 알림, ARCHITECTURE 4.6, DATA-MODEL 7장·10장
  - 완료: 워커가 상한 건수씩 읽어 가짜 채널로 발송, 실패 시 백오프 재시도, 최대 횟수 후 `FAILED`, `expires_at` 지난 건은 발송하지 않고 `CANCELLED` · 테스트: 고정 `Clock`으로 재시도 시각, 최대 횟수 도달, 만료 건 미발송, 워커 재실행 시 `SENT` 건 재발송 0건
  - 문서: DATA-MODEL 7장 확인, 재시도·최대 횟수 기본값을 ARCHITECTURE 4.6에 기록(진행 규칙 9)
  - 결과: `notification.service.NotificationDispatchService.dispatchDue()`와 스케줄 작업 `NotificationDispatchJob`(10초) · 기본값 5회·1분 두 배 백오프를 ARCHITECTURE 4.6에 기록, DATA-MODEL 7장은 확인(변경 없음) · 내가 정한 세부 결정: 보낼 구현이 없는 채널(이메일·푸시 구현은 T-041·T-042)의 건은 `PENDING`으로 두고 만료되면 `CANCELLED`(다른 건이 배치에서 밀리지 않게 조회에서 걸러 냄) · 구현이 예기치 못한 예외를 던지면 재시도 대상 실패로 보고 `last_error`에는 예외 종류만 남김(연락처 유출 방지) · 수신 설정으로 건너뛰는 건 T-043

- [x] T-039 알림 종류 정리와 중복 방지 검증
  - 선행: T-035a, T-035b, T-038
  - 참조: API 8장, PRD 4.5·4.8(경고)·4.13C(경고)·4.14
  - 완료: API 8장의 모든 type과 X-4의 누락 type이 생성 지점과 `dedupe_key` 규칙을 가진다 · 테스트: type마다 같은 원인 이벤트를 두 번 처리해도 사용자·채널별 아웃박스 행이 1건, 알림 문구에 "다시 받을 수 있음"을 해칠 상대 표현(예: "방금")이 없음
  - 문서: API 8장 갱신(X-4)
  - 결과: API 8장 표에 종류마다 생성 지점과 `dedupe_key` 형태를 적음(종류는 T-035a·T-035b·T-040·T-050·T-056에서 이미 모두 생성 지점을 가졌고 X-4 의 누락 type 도 반영됨) · `NotificationCatalogTest`(소스 문구에 상대 표현 없음, 모든 종류가 API 8장에 있음)와 `NotificationDedupeIntegrationTest`(종류마다 같은 원인 두 번 기록 시 알림 1건·아웃박스 채널당 1행) · 내가 정한 세부 결정: ① 종류별 생성 경로의 멱등은 각 작업의 테스트가 이미 보장하므로 여기서는 기록기 수준의 종류별 점검과 문구·문서 점검만 추가 ② 문구 점검은 주석을 뺀 소스 문자열을 검사 ③ `WAITING_OPENED`·`HOST_ASSIGNED`는 키에 시각이 있어(자리가 날 때마다·다시 주최자가 될 때마다 새 알림) 같은 원인의 재처리 방지는 그 생성 지점의 상태 전이가 맡음
  - 로컬 검증: 단위 테스트 통과, 통합 테스트는 CI 에서 확인

- [x] T-040 일정 알림 예약
  - 선행: T-018, T-020, T-022, T-038 · 결정 X-2, X-10, D-18
  - 참조: PRD 4.5(일정 알림), ARCHITECTURE 4.6, DATA-MODEL 7장
  - 완료: PRD 4.5의 일정 알림 3종을 X-2에서 정한 시점(개설 시 또는 참여 확정 시)에 예정 시각으로 기록하고 이미 지난 시점은 만들지 않음 · 파투·나가기 시 미발송 건 취소 · 주최자 혼자일 때 일정 수정 시 취소 후 재생성 · 예약 건의 알림 센터 노출은 X-10 결정대로 · 알림 종류별 `expires_at` 기준은 D-18 결정대로 · 테스트: 고정 `Clock`으로 이미 지난 시점 생략, 취소 후 미발송, 발송 전 알림 센터 미노출
  - 문서: DATA-MODEL 7장, ARCHITECTURE 4.6·4.7(X-2) 확인
  - 결과: `study.service.StudyScheduleNotifier`가 `StudyCreated`(주최자)·`MemberJoined`를 동기 리스너로 받아 3종을 예약하고 `MemberLeft`·`StudyCancelled`에서 취소, 일정 수정(`StudyUpdateService`)은 주최자 몫을 지우고 다시 만듦 · 취소는 `notification.service.ScheduledNotificationCanceller`(예정 시각 전 `PENDING`은 알림과 함께 삭제, 지난 `PENDING`은 `CANCELLED`, 이미 나간 알림은 유지) · 새 type `CHECKIN_REMINDER_DAY`·`CHECKIN_REMINDER_HOUR`(V16) · 내가 정한 세부 결정: ① "전날" = 시작 24시간 전, 시점은 설정 `everystudy.study.reminder.*` ② 예약 취소는 행 삭제(같은 키 재예약을 위해, DATA-MODEL 7장) ③ D-18은 PLAN 권장안을 적용(사용자에게 따로 묻지 않음, 바꾸려면 알려 달라) ④ 알림 문구는 절대 시각("M월 d일 HH:mm")을 써서 늦게 받아도 틀리지 않게 함
  - 로컬 검증: 단위 테스트 통과, 통합 테스트(`ScheduledNotificationIntegrationTest`)는 CI 에서 확인

- [ ] T-041 이메일 채널
  - 선행: T-038 · 결정 D-8, D-9 · 사람 H-5
  - 참조: PRD 4.5, ADR-0005, ARCHITECTURE 7장(로그에 이메일 금지)
  - 완료: SMTP 발송 구현, 이메일이 없는 사용자는 건너뜀 · 테스트: 가짜 SMTP 서버로 발송 성공, 일시 실패 후 재시도 성공, 로그 캡처에서 이메일 주소 0건
  - 문서: ARCHITECTURE 2장·4.6에 D-8 결과 확인

- [ ] T-042 웹 푸시 채널과 구독 API
  - 선행: T-038 · 사람 H-3
  - 참조: ADR-0005, DATA-MODEL 7장(`push_subscription`), API 3장(`push-subscription`)
  - 완료: VAPID 웹 푸시 발송, 구독 등록·해지 API · 테스트: 같은 엔드포인트 재등록 시 1행, 푸시 서비스의 만료·삭제 응답 시 구독 삭제, 일시 오류는 재시도
  - 문서: DATA-MODEL 7장, API 3장 확인

- [x] T-043 알림 수신 설정
  - 선행: T-038 · 결정 D-6
  - 참조: PRD 4.5, DATA-MODEL 7장(`notification_setting`)·11장 2번, API 3장
  - 완료: 채널별 on/off 조회·저장, 행이 없으면 기본값 · 발송 시점에 확인하고 꺼져 있으면 D-6 결과대로 상태 기록 · 테스트: 발송 예약 후 설정을 끄면 발송되지 않음, 기본값 동작
  - 문서: DATA-MODEL 7장·11장 2번 갱신
  - 결과: `GET/PUT /api/v1/me/notification-settings`(`{ push, email }`, 보낸 채널만 변경) · `notification_setting` 테이블과 아웃박스 `SKIPPED`(V17) · `NotificationDispatchService`가 발송 직전에 설정을 읽어 꺼져 있으면 `SKIPPED`(예약 뒤에 끈 알림도 나가지 않음) · 내가 정한 세부 결정: ① 요청·응답 모양은 `push`/`email` 불리언 두 개(채널이 둘뿐이라 목록보다 단순) ② `SKIPPED`가 된 건은 설정을 다시 켜도 되살리지 않음 ③ 알림 센터의 알림은 설정과 무관하게 남김(채널 설정은 푸시·이메일 발송에만 적용)
  - 로컬 검증: 단위 테스트 통과, 통합 테스트는 CI 에서 확인

- [ ] T-044 [프론트] PWA와 푸시 구독
  - 선행: T-013, T-025, T-042
  - 참조: PRD 4.5·4.14(iOS 푸시 조건), ARCHITECTURE 6장
  - 완료: manifest와 서비스 워커, 푸시 권한 요청과 구독 등록·해지 · iOS에서 홈 화면 추가 안내 · 테스트: 권한 거부 시 이메일 안내, 구독 성공 시 서버 등록 호출 · HTTPS 임시 환경에서 Android Chrome 푸시 수신 1회 확인
  - 문서: 해당 없음

- [ ] T-045 [프론트] 알림 설정 화면
  - 선행: T-043, T-044
  - 참조: PRD 4.5·5장 화면 12
  - 완료: 채널별 on/off와 푸시 구독 상태 표시 · 테스트: 토글 저장 실패 시 이전 값으로 되돌림
  - 문서: 해당 없음

- [x] T-046 채팅 REST와 차단
  - 선행: T-015, T-020 · 결정 X-13
  - 참조: PRD 4.12, ADR-0005 채팅, DATA-MODEL 8장·9장(`block`), API 6장·7장(`blocks`)
  - 완료: 방 상태·공지·읽지 않음 수, 커서 이력 조회, 공지 수정(주최자), 읽음 갱신 · 사용자 차단·해제와 차단한 사용자 메시지 숨김 표시 · `chat`은 `study`의 공개 서비스로 참여 여부만 조회(의존 방향 `chat → study`) · 주최자 권한은 `study.host_id` 일치 **그리고** `study_member` `ACTIVE`로 검사한다(시작 후 나간 주최자는 `host_id`가 남으므로, X-13) · 테스트: 비참여자·나간 사용자 `403`, 비주최자 공지 수정 `403`, 시작 후 나간 주최자의 공지 수정 `403`, 읽지 않음 수 계산
  - 문서: DATA-MODEL 8장, API 6장 확인
  - 결과: `chat.service.ChatRoomService`(방 상태·이전 대화·공지·읽음)와 `BlockService`, 마이그레이션 V13(`chat_message`·`chat_notice`·`chat_read_state`·`block`) · 참여 여부는 `study.service.StudySessions`로만 조회(`chat → study`) · 내가 정한 세부 결정(API 6장·DATA-MODEL 8·9장에 기록): 닫힌 방은 상태 조회만 되고 이전 대화·공지·읽음은 `409 CHAT_CLOSED`(PRD 4.14 "열람 차단", T-049가 폐쇄 처리를 더한다) · 이전 대화는 최근 순, `nextCursor`는 다음 `before` 값, `size` 1~100(기본 30) · 차단·지운 메시지는 `hidden`/`deleted`로 내용 없이 표시 · 읽지 않음 수는 내 메시지·차단한 사람·지운 메시지를 빼고 시스템 메시지는 셈 · 읽음 위치는 올리기만 하고 마지막 메시지 번호로 맞춤 · 공지는 고쳐 쓰기만 하고 지우는 API는 없음 · 차단은 멱등, 자기 자신 `400`, 없는 사용자 `404`

- [x] T-047 실시간 채팅: 연결·권한·전달
  - 선행: T-046
  - 참조: ADR-0005 채팅, ADR-0002 결정 5, ARCHITECTURE 4.8, API 6장(WebSocket)
  - 완료: `/ws` 핸드셰이크는 쿠키 인증과 `Origin` 허용 목록 · 구독·전송마다 `ACTIVE` 검사, 거부는 STOMP `ERROR` · DB 저장 후 전달 · 테스트: 쿠키 없는 연결 거부, 허용 외 `Origin` 거부, 비참여자 구독 거부, 저장 실패 시 전달 없음
  - 문서: API 6장 확인
  - 결과: `chat.web.WebSocketConfig`(`/ws`, 단순 브로커, `/app`·`/topic`·`/queue`·`/user`), `ChatSocketInterceptor`(CONNECT·SUBSCRIBE·SEND마다 권한 검사, 거부는 `ERROR`), `ChatSocketController`(저장), `chat.service.ChatMessageService`·`ChatBroadcaster`(커밋 뒤 전달) · 의존성 `spring-boot-starter-websocket`(잠금 파일 갱신) · 내가 정한 세부 결정(API 6장에 기록): 핸드셰이크 인증은 기존 HTTP 보안 필터가 하고 STOMP 사용자는 회원 id · `ERROR`의 `message` 헤더에 오류 코드 · 전송 본문 검증 실패는 연결을 끊지 않고 `/user/queue/events`로 `SEND_REJECTED` · 공지 변경도 방 주제로 `NOTICE` 이벤트 · 차단 숨김은 주제 하나로 서버가 가릴 수 없어 클라이언트가 `senderId`로 가림(REST는 가려서 줌) · 테스트용 `WebSocketIntegrationTestSupport`(임의 포트)를 추가

- [x] T-048 채팅 시스템 메시지와 구독 종료
  - 선행: T-020, T-047
  - 참조: PRD 4.12(나가기 의미), ARCHITECTURE 4.8, API 6장(`/user/queue/events`)
  - 완료: 입장·나감·새 주최자 시스템 메시지 · 나가기 시 해당 사용자 구독 종료와 `/user/queue/events` 통지 · `chat`은 `study`의 이벤트를 받아 처리(`study`는 `chat`을 모름) · 테스트: 나간 직후 전송 거부, 승계 시 시스템 메시지 1건
  - 문서: 해당 없음
  - 결과: `study.event`에 `MemberJoined`·`MemberLeft`·`HostChanged`를 추가해 참여·나가기·승계에서 발행하고, `chat.service.ChatSystemMessages`가 동기 리스너로 받아 같은 트랜잭션에서 시스템 메시지를 저장 · `ChatSubscriptions`가 커밋 뒤 방 구독을 끊고 본인에게 `SUBSCRIPTION_ENDED`를 보냄 · 내가 정한 세부 결정(API 6장에 기록): 문구는 "OO님이 입장했어요/나갔어요/새 주최자가 됐어요"(탈퇴 회원은 "탈퇴한 사용자") · 주최자가 나가면 "나갔어요"와 승계 시 "새 주최자" 두 건 · 나간 사람이 자기 나감 안내를 받지 않도록 구독 종료를 먼저 처리 · 단일 서버 내장 브로커의 구독 등록을 직접 지우는 방식이라 외부 브로커로 바꿀 때 `ChatSubscriptions`를 함께 바꿈

- [x] T-049 채팅방 폐쇄와 메시지 보관 만료 삭제
  - 선행: T-030, T-048 · 결정 D-16
  - 참조: PRD 4.14(종료 처리), ADR-0005, DATA-MODEL 8장·10장
  - 완료: `chat`이 `StudyEnded`(그리고 D-16에 따라 `StudyCancelled`)를 받아 방 `CLOSED`, 전체 구독 종료, 전송 거부, `purge_after` 설정 · 보관 만료 메시지를 상한 건수씩 삭제하고 `purged_at` 표시 · 테스트: 고정 `Clock`으로 보관 기간 경계, 두 번 실행해도 결과 동일, 폐쇄된 방 REST 조회는 `CLOSED` 반환
  - 문서: DATA-MODEL 8·10장 확인
  - 결과: `chat.service.ChatRoomLifecycle`이 `StudyEnded`·`StudyCancelled`를 동기 리스너로 받아 방을 닫고(`purge_after` = 닫은 시각 + `everystudy.chat.retention` 7일) 커밋 뒤 `ROOM_CLOSED`를 보낸 다음 방 구독을 모두 끊음 · `ChatPurgeService`(10분마다)가 보관 만료 방의 메시지를 방마다 상한 건수씩 지우고 다 지우면 공지·읽음 위치를 지운 뒤 `purged_at` 표시 · 이 PR에서 PRD 4.12·4.14에 D-16(파투도 같은 경로) 문구를 반영(v0.15) · 내가 정한 세부 결정: 보관 기간은 설정값 `retention`(기본 7d) · 이미 닫힌 방은 처음 시각 유지(멱등) · 닫힌 방 전송은 인터셉터가 `CHAT_CLOSED`로 거부

- [x] T-050 새 메시지 알림
  - 선행: T-043, T-047 · 결정 D-12
  - 참조: PRD 4.12(새 메시지 알림), API 8장
  - 완료: D-12에서 정한 방식대로 새 메시지 알림 생성, 수신 설정 준수 · 테스트: D-12 규칙의 묶음·제외 조건(예: 보낸 사람 본인 제외)
  - 문서: API 8장에 type 추가
  - 결과: `chat.service.ChatMessageNotifier`가 메시지 저장과 같은 트랜잭션에서 방의 `ACTIVE` 참여자 중 보낸 사람·방을 열어 둔 사람(STOMP 구독 중, `ChatSubscriptions.viewersOf`)·보낸 사람을 차단한 사람을 뺀 나머지에게 `CHAT_MESSAGE` 푸시 알림 기록(채널 PUSH만) · 수신 설정은 발송 시점에 워커가 확인(T-043) · 내가 정한 세부 결정: ① 묶음은 고정 시간 창(5분, `everystudy.chat.notify-window`)이라 같은 창의 메시지는 키가 같아 한 건 ② 알림에 메시지 내용을 싣지 않음(잠금 화면 노출 방지) ③ 유효 기간 1시간(`notify-valid`, D-18 즉시 알림 고정 기간) ④ 알림 센터에도 같은 알림이 남음 ⑤ 시스템 메시지(입장·나감 안내)는 알리지 않음
  - 로컬 검증: 단위 테스트 통과, 통합 테스트(`ChatMessageNotificationIntegrationTest`)는 CI 에서 확인. "방을 열어 둔 사람 제외"는 STOMP 연결이 필요해 자동 테스트가 없다

- [x] T-051 [프론트] 채팅방 화면
  - 선행: T-024, T-048
  - 참조: PRD 4.12·5장 화면 8·9, API 6장
  - 완료: STOMP 연결, 공지 고정, 읽지 않음 표시, 이전 대화 무한 스크롤, 새 주최자 안내 화면 · 테스트: 재연결 시 빠진 구간을 이력 조회로 채움(중복 메시지 없음), `CLOSED` 방 입력 비활성화, 차단한 사용자 메시지 숨김 표시
  - 문서: 해당 없음
  - 결과: `/studies/{id}/chat`(`ChatRoomView`) · 연결은 `lib/chatSocket.ts`(`@stomp/stompjs` 7.5.0 추가, ADR-0001 목록에 기록), 메시지 합치기는 `lib/chatMessages.ts` · 스터디 상세의 참여 중 영역에 "채팅" 버튼 추가 · 내가 정한 세부 결정: 최근 대화는 `useQuery`로 받고 재연결 때마다 다시 받아 id로 합침 · 위로 스크롤은 `IntersectionObserver`가 있으면 자동, 없으면 "이전 대화 보기" 버튼 · 읽지 않은 수는 입장 시점 값을 "읽지 않은 메시지 N개 · 맨 아래로" 띠로 보여 줌(마지막 읽음 위치를 API가 주지 않아 구분선은 두지 않음) · 새 주최자 안내(PRD 화면 9)는 별도 화면 없이 시스템 메시지를 강조해 보임(알림 센터의 `HOST_ASSIGNED`와 함께) · 차단한 사람 표시는 이전 대화에서 알게 된 `hidden` 보낸 사람 id로 실시간 메시지도 가림 · 차단 설정 UI는 T-061

**M4 시연 시나리오**: 참여가 확정된 사용자가 채팅방에 들어가 대화하고, 주최자가 공지를 고정한다. 나간 사용자는 바로 메시지를 받지 못한다. HTTPS 임시 환경에서 홈 화면에 추가한 휴대폰이 체크인 오픈 푸시를 받고, 푸시를 끈 사용자는 이메일로 받는다. 서버를 재시작해도 밀린 알림이 이어서 나가고 기한이 지난 알림은 나가지 않으며, 종료된 스터디의 채팅방은 닫힌다.

### M5 운영과 출시

- [x] T-052 운영자 권한과 감사 로그 조회
  - 선행: T-019 · 결정 D-10
  - 참조: PRD 4.11, API 7장(`/admin` 분리, `audit-logs`), ADR-0002 결정 7
  - 완료: `/admin/**`은 `role=ADMIN`만 · 운영자 지정은 D-10 결과대로 · 감사 로그 조회(커서) · 테스트: 일반 사용자 `403`, 비회원 `401`
  - 문서: API 7장 확인
  - 결과: `SecurityConfig`가 `/api/v1/admin/**`을 `ROLE_ADMIN`만 통과(비회원 401·일반 회원 403) · `user.service.AdminProperties`(`everystudy.admin.emails`, 환경변수 `ADMIN_EMAILS`; `.env.example`·`deploy/compose.yaml`에 키 추가)와 `SocialUserService`가 로그인 때 목록의 이메일이면 `ADMIN` 승격 · `GET /api/v1/admin/audit-logs`(`common.audit.AuditLogQueryService`·`AuditLogController`, 커서·`action`·`targetType` 필터) · 내가 정한 세부 결정: ① 운영자 지정은 이메일 목록(ID는 가입 전에 알 수 없어서), 승격은 로그인 때와 같은 트랜잭션 ② 자동 강등 없음 ③ 역할은 토큰에 있어 변경 반영에 최대 15분 ④ 감사 로그 목록은 기본 50건, 최대 100건
  - 로컬 검증: 단위 테스트 통과, 통합 테스트(`AdminAccessIntegrationTest`)는 CI 에서 확인

- [x] T-053 신고 접수와 신고 큐 처리
  - 선행: T-046, T-052 · 결정 D-4
  - 참조: PRD 4.11, DATA-MODEL 9장(`report`)·11장 1번, API 7장
  - 완료: 스터디·사용자·메시지 신고 접수 · 신고 큐 조회, 반려·처리 완료와 감사 로그 · 테스트: 사유 코드 외 값 `VALIDATION_FAILED`, `note` 없는 처리 거부
  - 문서: DATA-MODEL 9장·11장 1번 갱신
  - 결과: `chat.service.ReportService`·`ReportController`(`POST /api/v1/reports`, `GET /api/v1/admin/reports`, `POST .../dismiss`·`/resolve`) · 테이블 `report`(V20), 감사 로그 `REPORT_RESOLVE` 추가 · 내가 정한 세부 결정: ① 신고는 `chat` 모듈 소속(ARCHITECTURE 3장) ② 같은 신고자·같은 대상의 열린 신고는 하나만(`ALREADY_REPORTED`), 처리 뒤에는 다시 신고 가능 ③ 메시지 신고는 그 방의 `ACTIVE` 참여자만, 내 메시지·시스템·지워진 메시지는 불가 ④ 처리 완료는 표시일 뿐이고 실제 조치는 T-054·T-055 의 API ⑤ 처리 사유 `note`는 300자 ⑥ 큐 항목에 대상 요약(제목·닉네임·메시지 앞 100자)을 붙임
  - 로컬 검증: 단위 테스트 통과, 통합 테스트(`ReportIntegrationTest`)는 CI 에서 확인

- [x] T-054 운영자 강제 종료
  - 선행: T-031, T-040, T-049, T-053 · 결정 D-16
  - 참조: PRD 4.11 조치 2, ADR-0004 결정 1, DATA-MODEL 4장(`cancel_reason`)·10장, API 7장(`force-end`)
  - 완료: 락 안에서 파투(`CANCELLED`, `OPERATOR`), 대기 행 삭제, 미발송 일정 알림 취소(T-040), 참여자 파투 알림, 채팅방 처리는 D-16 결정대로, 감사 로그 · 참여자 `VOIDED`는 이 작업에서 기록하지 않고 판정 작업(T-031, `judged_at`)에 맡긴다 · 테스트: 강제 종료 후 판정 작업을 실행하면 참여자마다 `VOIDED` 1건이고 지표가 강제 종료 전과 같음, 재호출 멱등, 예약 알림 미발송
  - 문서: API 7장 확인
  - 결과: `study.service.StudyForceEndService`와 `AdminStudyController`(`POST /api/v1/admin/studies/{id}/force-end`, `note` 필수) · 락 안에서 `cancel(OPERATOR)`, 대기 행 삭제, 참여자·대기자 `STUDY_CANCELLED` 알림, `StudyCancelled` 발행(예약 일정 알림 취소 T-040, 채팅방 폐쇄 T-049가 받음), 감사 로그 `STUDY_FORCE_END` · 내가 정한 세부 결정: ① 이미 운영자가 강제 종료한 스터디는 `204` 멱등, 정상 종료·주최자 포기 파투는 `409 STUDY_CLOSED` ② 알림 유효 기한은 시작 전이면 시작 시각, 시작 뒤면 하루 ③ 이미 시작한 스터디도 강제 종료할 수 있고, 체크인 기록이 있어도 판정 작업이 참여자마다 `VOIDED`를 남겨 지표에서 빠짐
  - 로컬 검증: 단위 테스트 통과, 통합 테스트(`ForceEndIntegrationTest`)는 CI 에서 확인

- [x] T-055 메시지 삭제·이용 정지·제재 조회
  - 선행: T-035a, T-035b, T-053
  - 참조: PRD 4.11 조치 3·4·4.13C(운영자 검토), DATA-MODEL 6장·8장, API 7장
  - 완료: 메시지 삭제는 내용을 비움 · 수동 이용 정지 부여·해제, 자동 제재 조회·해제, 운영자 검토 대상 주최자 목록 · 모든 조치 감사 로그 · 테스트: 삭제된 메시지 이력 조회 시 내용 없음, 해제 후 즉시 신청 가능
  - 문서: API 7장 확인
  - 결과: `chat.service.ChatModerationService`와 `AdminChatController`(`DELETE /admin/chat-messages/{id}`), `trust.service.SanctionAdminService`와 `trust.web.AdminSanctionController`(`GET /admin/sanctions`, `POST /admin/users/{id}/suspensions`, `DELETE .../suspensions/{sid}`, `DELETE /admin/sanctions/{id}`) · 공통 커서 도구 `common.web.IdCursor` · 내가 정한 세부 결정: ① 모든 쓰기에 사유 `note` 필수(DELETE도 JSON 본문) ② 메시지 삭제·제재 해제는 멱등 `204` ③ 수동 정지 기간은 1~365일, 대상에게 `SANCTION_APPLIED` 알림 ④ 제재 조회는 자동·수동을 한 목록에서 보고 `reviewRequired`로 운영자 검토 대상만 거를 수 있음(별도 검토 완료 표시는 두지 않음, 해제·만료로 사라짐) ⑤ 이용 정지는 신청·개설을 막음(T-035a 범위 그대로) ⑥ 삭제 사실은 실시간으로 방에 알리지 않고 이전 대화 조회·재연결 때 반영
  - 로컬 검증: 단위 테스트 통과, 통합 테스트(`ModerationIntegrationTest`)는 CI 에서 확인

- [x] T-056 이의 최종 결정
  - 선행: T-035a, T-052 · 결정 X-12
  - 참조: PRD 4.9, ARCHITECTURE 4.5, DATA-MODEL 5장(`NO_SHOW_CLEARED`), API 7장(`admin/appeals`)
  - 완료: 인정 시 정정 이벤트 추가(지각 인정의 집계는 X-12 결정대로)와 지표 재계산, 관련 자동 제재 재판정 · 기각 시 `NO_SHOW` 확정 이벤트 추가와 T-035a의 제재 판정 재사용 · 결과 알림과 감사 로그 · 테스트: 인정 후 지표 복구, 기각 후 확정·제재 1회, 같은 이의 두 번 결정 거부
  - 문서: DATA-MODEL 5장, API 7장 확인
  - 결과: `attendance.service.AppealDecisionService`와 `AdminAppealController`(`GET/POST /api/v1/admin/appeals`) · 이의 행을 잠그고 한 번만 결정(`APPEAL_CLOSED`) · 인정은 `AttendanceEventRecorder.recordAppealAccepted`(`NO_SHOW_CLEARED`, 지각이면 `ATTENDED`)와 `ParticipantSanctionService.reassessAfterCorrection`(근거를 잃은 자동 제재 해제), 기각은 후보를 즉시 `NO_SHOW`로 확정하고 `judgeAfterNoShow` 재사용 · 신청자 알림 `APPEAL_RESULT`(V19) · 내가 정한 세부 결정: ① 사유(`note`)는 필수·150자 이하이고 알림 본문에 실음 ② 제재 재판정은 정정된 판정 시각 이후에 시작된 자동 제한·정지만 대상이고 정정된 기록으로 그 단계에 못 이르면 해제(제한 먼저, 정지는 그다음) ③ 노쇼 후보 인정은 `NO_SHOW_CLEARED`만(지각만 출석 정정) ④ 기각된 지각은 판정 유지, 별도 제재 판정 없음 ⑤ 결과 알림 유효 기간 `everystudy.attendance.appeal-result-notice-days`(기본 7일)
  - 로컬 검증: 단위 테스트 통과, 통합 테스트(`AppealDecisionIntegrationTest`)는 CI 에서 확인

- [x] T-057 운영자 개별 출석 정정
  - 선행: T-056 · 결정 X-7
  - 참조: PRD 9장(운영자 개별 정정), DATA-MODEL 5장·9장(`ATTENDANCE_CORRECT`)
  - 완료: X-7에서 정한 경로로 정정 이벤트 추가, 지표 재계산, 감사 로그 · 테스트: 정정 전후 지표 변화, 사유 없는 정정 거부
  - 문서: API 7장에 경로 추가(X-7)
  - 결과: `attendance.service.AttendanceCorrectionService`와 `AdminAttendanceController` · 이의 인정과 같은 정정 이벤트(`recordCorrection`)·지표 재계산·`reassessAfterCorrection`(근거를 잃은 자동 제재 해제)·감사 로그 `ATTENDANCE_CORRECT` · 내가 정한 세부 결정: ① 정정 대상은 현재 판정이 `NO_SHOW_CANDIDATE`·`LATE`·`NO_SHOW`인 이벤트만(출석·취소·무효는 대상 아님) ② 열린 이의가 있으면 거부하고 이의 결정으로 처리하게 함 ③ 사용자에게는 알림을 보내지 않음(필요하면 T-059 이후 검토) ④ 오류는 `409 CORRECTION_NOT_ALLOWED`
  - 로컬 검증: 단위 테스트 통과, 통합 테스트(`AttendanceCorrectionIntegrationTest`)는 CI 에서 확인

- [x] T-058 이의 반복 기각 제한
  - 선행: T-056 · 결정 D-11
  - 참조: PRD 4.9 악용 방지
  - 완료: D-11에서 정한 기준·효과 구현 · 테스트: 기준 직전·도달 시 동작 · D-11이 출시 전까지 정해지지 않으면 이 작업은 출시 범위에서 빼고 7장에 남긴다
  - 문서: PRD 4.9 위치를 가리키는 설정값 확인, DATA-MODEL 필요 시 갱신
  - 결과: `AppealService.file`이 이의를 받기 전에 최근 90일 기각 결정 수를 보고 3회 이상이면 마지막 기각 + 30일까지 `403 APPEAL_RESTRICTED`(`until`) · 설정 `everystudy.attendance.appeal-reject-limit`·`appeal-reject-window-days`·`appeal-restrict-days` · 내가 정한 세부 결정: ① 별도 제재 행 없이 이의 행에서 계산(기록이 하나라 어긋날 일이 없음) ② 제한이 끝나면(마지막 기각 +30일) 3회가 아직 90일 안에 있어도 다시 낼 수 있고, 그 이의가 또 기각되면 새 30일 ③ 검사 순서는 이의 대상 종류 다음, 이미 신청함 앞
  - 로컬 검증: 단위 테스트 통과, 통합 테스트(`AppealIntegrationTest`)는 CI 에서 확인

- [ ] T-059 [프론트] 관리자: 이의 큐와 출석 정정
  - 선행: T-007, T-056, T-057
  - 참조: PRD 5장 화면 13
  - 완료: 이의 큐(사유·주최자 의견 표시)와 결정, 출석 정정 입력 · 테스트: 운영자가 아니면 화면 접근 불가, 결정 후 큐에서 사라짐
  - 문서: 해당 없음

- [ ] T-060 [프론트] 관리자: 신고 큐와 제재 관리
  - 선행: T-054, T-055, T-059
  - 참조: PRD 5장 화면 13, PRD 4.11
  - 완료: 신고 큐와 반려·처리·강제 종료·메시지 삭제, 제재 부여·해제, 운영자 검토 대상 주최자 표시 · 테스트: 강제 종료 확인 창, `note` 미입력 시 제출 불가
  - 문서: 해당 없음

- [ ] T-061 [프론트] 신고·차단 UI
  - 선행: T-051, T-053
  - 참조: PRD 4.11·4.12
  - 완료: 스터디·사용자·메시지 신고 입력, 사용자 차단·해제 · 테스트: 신고 사유 선택 필수, 차단 즉시 채팅에서 숨김 표시
  - 문서: 해당 없음

- [ ] T-062 탈퇴 API
  - 선행: T-017, T-020, T-038, T-042
  - 참조: PRD 4.1(탈퇴), ADR-0002 결정 6, DATA-MODEL 3장·4장·7장, API 3장(`DELETE /me`)
  - 완료: 진행 중 스터디에 참여 취소·주최자 승계 규칙을 먼저 적용 · 개인정보 컬럼 비움, `social_account` 삭제, 리프레시 전부 폐기, 쿠키 삭제, `push_subscription`·`study_waiting` 삭제, 미발송 아웃박스(이메일 포함) 취소 · 다른 사용자 화면에 "탈퇴한 사용자" 표시 · 테스트: 탈퇴 후 해당 사용자 대상 발송 0건, 주최 중 스터디 승계, 같은 소셜 계정 재로그인은 신규 가입
  - 문서: DATA-MODEL 3장, API 3장 확인

- [ ] T-063 [프론트] 탈퇴 화면
  - 선행: T-036, T-062
  - 참조: PRD 4.1
  - 완료: 진행 중 스터디에 미치는 영향 안내 후 확인 · 테스트: 확인 전 호출 없음, 탈퇴 후 로그아웃 상태
  - 문서: 해당 없음

- [ ] T-064 호출 제한
  - 선행: T-028, T-047, T-053 · 결정 D-5
  - 참조: API 1장(`429`)·9장 1번(대상)
  - 완료: API 9장 1번의 대상에 `429 RATE_LIMITED`, 수치는 설정값 · 테스트: 대상별 한도 직전 허용·초과 거부
  - 문서: API 9장 1번 갱신(D-5)

- [ ] T-065 운영 환경 마감
  - 선행: T-013 · 결정 D-3
  - 참조: ARCHITECTURE 9장, AGENTS.md 5장
  - 완료: 운영 Compose에 `restart: unless-stopped`, 운영 도메인 TLS · DB 백업을 D-3에서 정한 주기로 수행하고 복구를 한 번 실제로 수행 · 배포·복구 절차 문서화
  - 메모(2026-11-19, 확정 아님. 이 작업에서 결정): 관측은 외부 무료 플랜(예: Grafana Cloud)에 보내고 서버에는 수집기(예: Grafana Alloy)만 둔다. 2GB VM에 Prometheus·Loki·Grafana를 함께 띄우면 메모리가 모자란다. 백엔드 지표는 Actuator·Micrometer. MAU·기능 사용량은 우선 DB 질의로 보고, 화면 단위 분석 도구(PostHog·GA4 등)를 붙이면 개인정보 처리방침(T-066)에 반영한다. 새벽 시간대 인스턴스 중지는 예약 알림(ADR-0005)이 빠질 수 있어 알림 발송(M4) 뒤에 판단한다
  - 문서: ARCHITECTURE 9장·11장 1번 갱신

- [ ] T-066 개인정보 처리방침과 위치 정보 고지
  - 선행: T-029 · 결정 D-14
  - 참조: PRD 8장, ARCHITECTURE 7장
  - 완료: 처리방침 화면과 위치 사용 고지가 첫 체크인 전에 보임 · 로그 캡처 테스트로 로그인·체크인·이메일 발송 흐름에서 토큰·이메일·좌표 패턴이 0건
  - 문서: ARCHITECTURE 7장 확인

- [ ] T-067 출시 전 실기기 점검
  - 선행: T-044, T-051, T-065, T-066
  - 참조: PRD 9장 남은 것 1, ADR-0005 결과(iOS 푸시)
  - 완료: 운영 환경에서 iOS Safari(홈 화면 PWA 포함)와 Android Chrome으로 로그인, 개설, 참여, 위치·코드 체크인, 웹 푸시 수신, 채팅을 확인하고 결과표를 PR에 남김 · 발견한 결함은 새 작업 번호로 추가
  - 문서: PRD 9장 "남은 것" 결과는 사람이 PRD에 반영하도록 PR에 적음

- [ ] T-068 시범 운영 준비
  - 선행: T-037, T-039, T-040, T-041, T-045, T-050, T-058(또는 D-11 미정으로 7장에 이관), T-060, T-061, T-063, T-064, T-067 · 결정 D-13
  - 참조: PRD 1장(초기 운영 전략)
  - 완료: 운영자 계정 준비, 운영 환경에서 아래 M5 시연 시나리오 수행, 운영자가 시범 스터디를 개설할 수 있는 상태 · 이 문서의 모든 작업 체크와 문서 `covers` 누락 점검 · 에이전트 훅을 운영용으로 복귀(H-8)
  - 문서: 이 문서의 `status`·`based_on` 최신화

**M5 시연 시나리오**: 운영 도메인에서 휴대폰으로 로그인해 스터디를 만들고, 다른 휴대폰이 참여해 장소에서 체크인하고 푸시를 받는다. 다른 사용자가 부적절한 스터디를 신고하면 운영자가 관리자 화면에서 강제 종료하고, 참여자에게 파투 알림이 가며 지표는 그대로다. 노쇼 판정에 대한 이의를 주최자 의견과 함께 운영자가 인정하면 지표가 다시 계산되고, 모든 조치가 감사 로그에 남는다. 한 사용자가 탈퇴하면 채팅과 참여자 목록에 "탈퇴한 사용자"로 보이고 그 사용자에게 알림이 더 나가지 않는다.

## 5. 사람이 준비할 것

에이전트는 시크릿을 만들거나 읽지 않는다 (AGENTS.md 5장). 아래는 사람이 발급해 환경변수로 넣고, 키 이름만 `.env.example`에 둔다.

| 번호 | 내용 | 필요한 작업 |
|---|---|---|
| H-1 | 카카오·구글 OAuth 앱 등록(로컬 리다이렉트 URI), JWT 서명 키. 등록할 값과 환경변수 이름은 `backend/README.md` "소셜 로그인 준비" | T-004 |
| H-2 | 카카오맵 JavaScript 키와 로컬 도메인 제한. **일부 보류(2026-11-18)**: 키와 JavaScript SDK 도메인은 등록했고, 카카오맵 사용 설정은 운영 준비 때 켠다. 그 전까지 개발 서버(`next dev`)와 `NEXT_PUBLIC_SAMPLE_PLACES=true`로 빌드한 임시 배포는 지도 대신 예시 장소를 보여 준다(`frontend/src/lib/devPlaces.ts`) | T-010 |
| H-3 | VAPID 키 쌍 | T-042 |
| H-4 | **확인됨(2026-11-16)**: GitHub 룰셋으로 main·develop에 PR 필수, 필수 검사 `CI 통과`, 삭제·force push 금지를 적용 (저장소 공개 전환) | T-001 |
| H-5 | SMTP 계정과 발신 주소 | T-041 |
| H-6 | 서버(또는 임시 호스트·터널), 도메인, DNS, TLS 인증서 | T-013, T-065 |
| H-7 | 배포 도메인을 카카오맵 허용 도메인과 OAuth 리다이렉트 URI에 등록 | T-013 |
| H-8 | MVP 동안 쓰는 에이전트 훅(`guard-bash-mvp`, 에이전트가 CI 통과 PR 을 병합)을 운영용(`guard-bash`)으로 복귀: `! bash tools/hooks-ctl.sh install` 후 `.claude/` 변경 커밋. main 대상 PR 에 MVP 훅이 남아 있으면 CI 가 막는다 | T-068 |

## 6. 위험

| 위험 | 영향 | 대응 | 관련 작업 |
|---|---|---|---|
| 정원·승계 동시성 오류 | 정원 초과, 이중 승계 (PRD 8장 1순위) | 락 구조를 M2 초반에 만들고 Testcontainers 동시성 테스트를 CI에 고정 | T-015, T-016, T-020, T-021 |
| 출석 시간 경계·KST 자정 처리 오류 | 잘못된 노쇼·지표·제재 | 모든 시간 로직에 주입된 `Clock`, 경계값 테스트, 스케줄러 재실행 테스트 | T-027, T-030, T-031 |
| iOS Safari 위치·웹 푸시 제약 | 체크인 불가, 알림 미수신 | M1에서 HTTPS 임시 환경, M3에서 위치 실기기 점검, 이메일 보조 채널, 출시 전 정식 점검 | T-013, T-029, T-041, T-067 |
| 알림 유실 | 이벤트가 커밋됐는데 알림이 없음 | X-8 결정 전 M2 착수 금지, 아웃박스를 같은 트랜잭션에 기록하는 테스트를 공통 완료 기준으로 | T-014, T-038, T-039 |
| 배포 지연 | HTTPS가 없어 실기기 검증이 막힘 | D-3을 M1 안에 결정, 임시 호스트·터널 허용 | T-013 |
| 스케줄러 중복·누락 | 알림 중복, 판정 누락 | DB 상태 기반 "지난 미처리 건" 조회, 멱등 키, 상한 처리 | T-030, T-031, T-038 |
| 인증·CSRF 설정 오류 | 로그인 실패, CSRF 취약 | M1 통합 테스트로 쿠키 속성·CSRF·Origin 고정, 구현 전 Spring Security 최신 문서 확인(ADR-0002) | T-004, T-005 |
| 모듈 순환 | `study`와 `chat`·`notification`이 서로 의존해 경계 붕괴 | X-8·X-11 결정 후 아키텍처 테스트로 강제 | T-003, T-008 |
| 범위 확장 | 일정 지연 | PRD 7장·9장 항목은 작업으로 추가하지 않고 7장에 기록 | 전체 |

## 7. 미결정과 문서 불일치

추측으로 채우지 않는다. 결정되면 원본 문서(PRD·ARCHITECTURE·DATA-MODEL·API·ADR)에 기록하고 여기서는 "결정됨(문서 위치)"으로 바꾼다. 번호는 재사용하지 않는다.

### 7.1 결정이 필요한 항목 (D)

| 번호 | 항목 | 출처 | 막는 작업 |
|---|---|---|---|
| D-1 | **결정됨(2026-11-16, ADR-0001)**: Spring Boot 4.1.x, JDK 21, Gradle 9.x, Next.js 16.x, Node 24(CI 반영), ESLint·Prettier, Vitest 5·Testing Library. 패치 버전은 T-001·T-002에서 최신 안정 버전을 확인하고, JUnit·Testcontainers는 Boot 4.1이 관리하는 버전을 T-001에서 확정한다 | ADR-0001, ci.yml | T-001, T-002 |
| D-2 | **결정됨(2026-11-16, ADR-0003 후속 결정)**: 모듈 경계 검사 도구는 ArchUnit 1.5.1(코어만 사용). T-003에서 함께 정한 것: 모듈의 공개 면은 `service`·`event` 패키지, `web`은 같은 모듈의 `domain` 타입을 쓸 수 있고 `repository`는 못 쓴다 | ADR-0003 | T-003 |
| D-3 | **일부 결정(2026-11-18, ARCHITECTURE 9장)**: M1 배포는 클라우드 VM 1대 + 도메인 + Caddy(HTTPS), MySQL은 같은 VM의 컨테이너, 이미지는 SSH로 전달(`deploy/`). 임시 호스트·터널은 쓰지 않는다. 남은 것: 업체·도메인 이름(사람), 백업(T-065). 원래 항목: 호스팅 업체·도메인·DB 운영 방식·백업. M1의 HTTPS 임시 배포에 필요한 최소 결정(임시 호스트·터널 허용 여부 포함)을 먼저 | ARCHITECTURE 9장·11장 1번 | T-013, T-065 |
| D-4 | **결정됨(2026-11-22, 사용자 확인, 권장안대로. DATA-MODEL 9장).** 신고 사유는 6종: `SPAM_AD`(광고·홍보), `ABUSE`(욕설·비방), `INAPPROPRIATE_RECRUIT`(부적절한 모집), `HARASSMENT`(괴롭힘·성적 불쾌감), `FRAUD`(사기·위장), `OTHER`(기타, 상세 필수) | DATA-MODEL 11장 1번 | T-053 |
| D-5 | 호출 제한 수치 (대상은 API 9장 1번에 있음) | API 9장 1번 | T-064 |
| D-6 | **결정됨(2026-11-22, 사용자 확인, 권장안대로. DATA-MODEL 7장·11장 2번).** 수신 설정 때문에 건너뛴 알림은 `SKIPPED` 상태를 추가해 표기한다 | DATA-MODEL 11장 2번 | T-043 |
| D-7 | **결정됨(2026-11-22, 사용자 확인. DATA-MODEL 5장·11장 3번, ARCHITECTURE 4.4).** 일회용 코드 자릿수·수명·입력 시도 제한 횟수 | 6자리 숫자, 체크인 마감 시각까지 유효, 한 사람이 한 스터디에서 5번 틀리면 입력 불가(`everystudy.attendance.code-max-failures`) | T-028 |
| D-8 | 이메일 발송 수단(SMTP 업체·발신 주소) — 문서에 없음 | 없음 | T-041 |
| D-9 | **결정됨(2026-11-17, DATA-MODEL 3장)**: 공급자가 확인한 이메일만 저장한다. 구글은 기본 제공 이메일, 카카오는 선택 동의 항목(`account_email`)이고 동의하지 않으면 `NULL`로 가입된다. T-004에서 함께 정한 것: 온보딩 전 회원은 닉네임·직군·지역이 `NULL`(DATA-MODEL 3장), 액세스 JWT는 HS256, 리프레시 쿠키 `Path`는 `/api/v1/auth`(ADR-0002 후속 결정). T-041은 이메일이 없는 회원에게 이메일 알림을 보내지 않는 경우를 다룬다 | DATA-MODEL 3장(`email` NULL 허용) | T-004, T-041 |
| D-10 | **결정됨(2026-11-22, 사용자 확인, 권장안대로. DATA-MODEL 3장, backend/README).** 운영자 계정은 환경변수 `ADMIN_EMAILS`(쉼표로 구분)에 이메일을 적고, 그 이메일로 소셜 로그인하면 `ADMIN`이 된다. 공급자가 확인한 이메일만 저장되므로 남이 가장할 수 없다. 목록에서 빼도 자동으로 내려가지 않는다(DB에서 직접). 운영자는 먼저 한 번 로그인해야 한다 | 없음 | T-052 |
| D-11 | **결정됨(2026-11-22, 사용자 확인, 권장안대로. PRD 4.9 v0.17).** 최근 90일에 이의가 3회 기각되면 마지막 기각 결정부터 30일간 새 이의를 받지 않는다(설정값) | PRD 4.9 악용 방지 | T-058 |
| D-12 | **결정됨(2026-11-22, 사용자 확인, 권장안대로. API 8장).** 채팅 새 메시지 알림은 푸시만, 방별로 5분 시간 창마다 한 건으로 묶고 보낸 사람 본인과 방을 열어 둔 사람은 제외한다. 이메일은 보내지 않는다 | PRD 4.12 | T-050 |
| D-13 | 주최자 제재 수치("확인 필요" 표기) 확정. 구현은 설정값이라 먼저 진행 가능 | PRD 4.13C | T-068 |
| D-14 | 개인정보 처리방침 작성 주체와 검토 방법 | PRD 8장, ARCHITECTURE 7장 | T-066 |
| D-15 | **결정됨(2026-11-22, 사용자 확인, 권장안대로. PRD 4.8 v0.16, DATA-MODEL 6장).** "제한 후 재발"은 마지막 참여 신청 제한이 시작된 이후 확정된 노쇼 1회 또는 지각 3회이고, 제한 기간이 끝난 뒤에도 계속 누적해 센다 | PRD 4.8 | T-035a |
| D-16 | **결정됨(2026-11-15)**: 파투(`CANCELLED`) 스터디의 채팅방은 정상 종료와 같은 경로로 폐쇄하고 보관 기간 뒤 메시지를 삭제한다. PRD 4.12·4.14 문구 반영은 T-049 PR에서 한다 | PRD 4.12·4.14 | T-049, T-054 |
| D-17 | **결정됨(2026-11-22, PRD 4.6, DATA-MODEL 9장)**: 자동 감점·제재·판정 확정과 운영자 조치를 모두 감사 로그에 남기고, PRD 4.6 문구는 "운영자 정정"으로 고쳤다. 주최자는 출석을 바꿀 수 없으므로 주최자 행위는 기록 대상이 아니다. T-019에서 함께 정한 것: 기록은 `common.audit.AuditLogger`(트랜잭션 필수), `action`은 `AuditAction` 이름이고 DB `CHECK`는 두지 않는다 | PRD 4.6·8장, DATA-MODEL 9장 | T-019 |
| D-18 | **결정됨(2026-11-22, 권장안 적용. DATA-MODEL 7장)**: 일정 알림은 다음 알림 시각(체크인 오픈은 체크인 마감)까지, 즉시 알림은 설정값 고정 기간. 원래 항목: 알림 종류별 `expires_at` 기준(예: 일정 알림은 해당 시점 이후 무의미, 대기 알림은 체크인 오픈까지). 권장안: 일정 알림은 다음 알림 시각 또는 체크인 마감, 대기 알림은 체크인 오픈, 그 밖의 즉시 알림은 고정 기간(설정값). 기술 파라미터 기본값(락 대기·백오프·최대 횟수·배치 상한)은 결정 항목이 아니라 진행 규칙 9로 처리 | ARCHITECTURE 4.6, ADR-0005 | T-040 |
| D-19 | **결정됨(2026-11-16, API 첫머리·ARCHITECTURE 9장)**: OpenAPI 문서(`/v3/api-docs`)와 Swagger UI(`/swagger-ui/index.html`)는 개발용이라 운영(`prod`)에서는 끈다. `application-prod.yml`에서 끄므로 배포할 때 `SPRING_PROFILES_ACTIVE=prod`를 지정해야 한다 | API 첫머리, ARCHITECTURE 9장 | T-013 |
| D-20 | **결정됨(2026-11-17, PRD 4.1~4.3 v0.7, DATA-MODEL 1장)**: 직군은 대분류 → 세부 직무 2단계다. 회원은 대분류 필수·세부 선택, 스터디 희망 직군은 대분류를 여러 개 고를 수 있고 선택이며 고르지 않으면 직군 무관(`ANY` 코드 없음). 희망 직군을 대분류로만 고르는 것과 참여 조건이라는 점은 X-18에서 정했다. 지역은 시·도 → 시 → 구이고 회원·스터디는 구로 저장, 탐색은 어느 단계로든 거르며 "내 지역"은 내 구가 속한 시. 주제는 PRD 4.2의 6개. 목록은 `common/code`의 enum | PRD 4.1~4.3, DATA-MODEL 1장 | T-006, T-008, T-009 |
| D-21 | **결정됨(2026-11-17, API 1·4장)**: PRD에 없는 개설 입력 규칙. 제목 2~40자, 소개는 선택이고 2000자 이하(2026-11-18 변경, PRD 4.2 v0.11). 시작 시각은 10분 단위, 진행 시간은 30분 이상 10분 단위(자정에 끝나는 것까지). 최소 출석률은 없음 또는 50~100% 중 10% 단위. 단위·최소값·선택지는 설정값이고 `/meta/options`로 내려준다. 온보딩 전 회원의 개설은 `403 ONBOARDING_REQUIRED` | API 1·4장, `StudyProperties` | T-008, T-010 |
| D-22 | **결정됨(2026-11-17, PRD 4.3 v0.9, API 4장)**: 탐색 목록은 기본이 모집 중만이고, `includeClosed=true`("마감된 스터디도 보기")면 시작 전 마감 스터디도 함께 보여 준다. 이미 시작했거나 끝난 스터디는 목록에 없다. 직군 필터(`jobCode`)는 명시된 직군만, `myJob=true`는 내 대분류가 신청할 수 있는 스터디만. 마감임박순은 시작이 빠른 순. 체크인 시간창(시작 10분 전~30분 뒤)은 `study` 설정값 | PRD 4.3, API 4장 | T-009, T-011 |
| D-23 | **결정됨(2026-11-18, PRD 4.7·4.8 v0.10, DATA-MODEL 6장)**: 지각도 감점한다. 출석률에서 지각은 출석 0.5회(분자 0.5, 분모 1)로 계산한다. 제재 판정은 환산 노쇼(노쇼 + ⌊지각 ÷ 3⌋)로 같은 단계표를 쓰고, 지각만 최근 5회 중 3회면 경고까지만 받는다. 노쇼 제재는 그대로 | PRD 4.7·4.8 | T-032, T-035a |

### 7.2 문서 간 불일치 (X) — PLAN에서 임의로 고치지 않음

| 번호 | 내용 | 권장안 | 막는 작업 |
|---|---|---|---|
| X-1 | ADR-0002~0005 본문은 "확정(2026-11-13)"인데 머리말 `status`가 `draft`. ARCHITECTURE 3장·4.1·4.6~4.8 제목에 "(제안, ADR-000x)"가 남아 있음 | 머리말을 `confirmed`로, 제목의 "제안"을 "확정"으로 (ADR 오탈자 수준 수정) | 없음 |
| X-2 | **결정됨(2026-11-22, 사용자 확인, 권장안대로. ARCHITECTURE 4.6·4.7).** ARCHITECTURE 4.7 작업 목록에 "체크인 오픈 알림" 작업이 따로 있으나, 4.6과 ADR-0005는 개설 시 아웃박스 예약으로 처리. 또 4.6은 "개설 시 예약"이라 하는데 알림 대상인 참여자는 개설 뒤에 생김 | 참여 확정 시 그 참여자의 일정 알림을 예약하고(나가기 시 취소), 4.7의 별도 작업은 지운다 | T-040 |
| X-3 | **결정됨(해소)**: docs/README.md 1장 표와 계층도에 PLAN을 추가함 (PR #12) | - | - |
| X-4 | PRD 4.8 경고 알림, 4.13C 주최자 경고, 4.12 새 메시지 알림이 API 8장 알림 목록에 없음. **일부 반영(T-035a)**: `SANCTION_WARNING`·`SANCTION_APPLIED`(참여자)는 API 8장에 추가됨. `HOST_WARNING`(주최자)도 T-035b에서 추가됨. `CHAT_MESSAGE`도 T-050에서 추가됨. X-4 의 알림 type 은 모두 반영(문구는 T-039 가 점검) | API 8장에 `SANCTION_WARNING`, `HOST_WARNING`, `CHAT_MESSAGE`(이름은 구현 시 확정) 추가 | T-035a, T-035b, T-039, T-050 |
| X-5 | **결정됨(2026-11-17, 오른쪽 제안대로. API 4장 상세 응답 필드 표)**: API 4장 "상세 응답 주요 필드"에 `scheduleEditable`, `checkinOpensAt`, `checkinClosesAt`이 없음 (다른 절에서만 언급) | 상세 필드 목록에 추가 | T-009 |
| X-6 | **결정됨(2026-11-17, 오른쪽 제안대로. API 1장)**: 닉네임 중복 불허(DATA-MODEL 3장 확정)에 대응하는 오류 코드가 API 1장 표에 없음. 함께 정한 것: 닉네임은 2~20자의 한글(완성형)·영문·숫자·밑줄, 영문 대소문자는 구분하지 않고 중복으로 본다(API 3장) | `409 NICKNAME_TAKEN` 추가 | T-006 |
| X-7 | **결정됨(2026-11-22, 사용자 확인, 권장안대로. API 7장).** 운영자 개별 출석 정정은 `POST /api/v1/admin/attendance-events/{eventId}/corrections`(`result` `ATTENDED`·`CLEARED`, `note` 필수)로 정정 이벤트를 추가한다 | 운영자 개별 출석 정정(PRD 9장, DATA-MODEL 9장 `ATTENDANCE_CORRECT`)에 해당하는 API 경로가 없었음 | T-057 |
| X-8 | **결정됨(2026-11-16, 오른쪽 제안대로. ARCHITECTURE 3장, ADR-0003 후속 결정).** **이벤트→알림 트랜잭션 경계와 모듈 방향.** ARCHITECTURE 3장은 모듈 간 리스너를 `AFTER_COMMIT`으로 실행한다고 하나, 4.6과 ADR-0005는 아웃박스를 비즈니스 트랜잭션 안에서 기록한다고 함(`AFTER_COMMIT`에서 기록하면 커밋 후 실패 시 알림 유실). 또 `study`가 개설 시 `chat_room`을 만들고(PRD 4.12, API 4장) 종료 시 폐쇄하면 `study → chat` 의존이 생겨 3장의 `chat → study` 방향과 순환 | 알림은 `BEFORE_COMMIT` 동기 리스너 또는 `notification` 공개 서비스 직접 호출로 같은 트랜잭션에 기록하고, `AFTER_COMMIT`은 유실돼도 되는 반응(STOMP 전달, 구독 종료)에만 쓴다. `chat`은 `StudyCreated`·`StudyEnded`·`StudyCancelled`를 동기 리스너(같은 트랜잭션)로 받아 방 생성·폐쇄를 하고, `study`는 `chat`을 참조하지 않는다. ARCHITECTURE 3장과 ADR-0003에 반영 | T-003, T-008, T-014, T-015 |
| X-9 | DATA-MODEL 5장의 `UNIQUE (study_id, user_id, type)`와 재신청 허용(같은 행 재활성화, DATA-MODEL 11장 확정)이 충돌: 나가기→재신청→다시 나가기에서 `EARLY_CANCEL`·`LATE_CANCEL`이 중복되어 거부됨. MySQL은 부분 유일 인덱스가 없음 | 생성 컬럼(ADR-0001 "생성 컬럼으로 우회")으로 판정 종류(`ATTENDED`, `LATE`, `NO_SHOW_CANDIDATE`, `NO_SHOW`, `VOIDED`)일 때만 값을 갖고 취소 종류는 `NULL`인 키를 만들어 `UNIQUE (study_id, user_id, 그 키)`로 둔다. 취소 이벤트의 중복 방지는 `study` 락으로 충분 | T-018 |
| X-10 | **결정됨(2026-11-19, 오른쪽 첫 제안 `visible_at`. DATA-MODEL 7장, API 3장).** 일정 알림을 미리 예약하면(ARCHITECTURE 4.6) 아웃박스가 `notification` 행을 FK로 참조하므로, 발송 전 예약 알림이 알림 센터(`/me/notifications`)에 미리 보임 | `notification`에 `visible_at`을 두고 알림 센터는 `visible_at <= now`만 보여 준다 (또는 센터는 발송된 건만) | T-014, T-040 |
| X-11 | **결정됨(2026-11-16, 오른쪽 제안대로, 감사 로그는 `common`. ARCHITECTURE 3장, ADR-0003 후속 결정).** 모듈 소속: ARCHITECTURE 3장에 `schedule` 모듈(일정 변경, 세션 무효)이 있으나 일정은 `study` 테이블에 있음(ARCHITECTURE 5장). `attendance_event`는 `trust` 소속인데 체크인·자동 판정은 `attendance` 소속. 감사 로그 소속 모듈도 없음 | 일정 수정은 `study`, 세션 무효·노쇼 후보 판정은 `attendance`, 이벤트 기록·집계는 `trust`(공개 서비스), 감사 로그는 `common`(여러 모듈이 기록하므로 `admin`에 두면 모두 `admin`에 의존하게 됨)으로 정하고 `schedule` 모듈을 지운다. ARCHITECTURE 3장 갱신 | T-003, T-022, T-031 |
| X-12 | **결정됨(2026-11-22, 사용자 확인, 권장안대로. DATA-MODEL 6장).** 지각 판정에 대한 이의가 인정되면(`NO_SHOW_CLEARED`, "직전 판정을 무효화") 출석으로 볼지 집계에서 뺄지 정의가 없음 | 이의 인정은 "판정이 틀렸음"이므로 출석으로 집계하는 정정 이벤트(`ATTENDED`)를 추가 | T-032, T-056 |
| X-13 | **결정됨(2026-11-22, 사용자 확인. ARCHITECTURE 4.3, DATA-MODEL 4장, API 4장).** 주최자가 시작 후 나가면(`LEFT_AFTER_START`) `study_member` 상태, `host_id`, 이미 한 체크인과 출석 집계 처리가 정의되지 않았음 | `study_member`는 `LEFT`, `host_id`와 체크인은 그대로 두어 세션은 진행되고(PRD 4.13A 마지막 항목), 취소 이벤트는 만들지 않는다. 체크인 전이었다면 미도착 규칙(T-031)으로 처리. **T-020에서 함께 정한 세부:** ① 주최자의 나가기 응답은 `outcome`만 담고 `cancelType`은 없다(주최 포기는 참여 취소가 아니라 `study_member.cancel_type`도 `NULL`). ② 종료·파투된 스터디에서는 주최자도 `409 SESSION_STARTED`, 진행 중(`ACTIVE`)이면 시작 후에도 나갈 수 있다. ③ 감점은 **승계로 이어질 때만**(남은 참여자가 있을 때) 부과한다. 기준 시각은 참여자 직전 취소와 같게 시작 3시간 전 정각까지는 감점 없음, 1초라도 넘기면 감점. 기준 시간·점수는 설정값(`everystudy.trust.host-leave-window-minutes` 180, `host-leave-penalty-points` 1). ④ 감점의 감사 로그는 `HOST_PENALTY`, 대상 `STUDY`, 행위자 `NULL`. ⑤ 알림: 새 주최자에게 `HOST_ASSIGNED`(만료는 일정 종료 시각), 파투면 대기자에게만 `STUDY_CANCELLED`(만료는 시작 시각)를 보내고 대기 행은 지운다. 승계 뒤에는 대기 행을 그대로 두고, 정원이 찼던 스터디면 T-018과 같은 규칙으로 자격이 남은 대기자에게 자리 알림을 보낸다. ⑥ 파투는 같은 트랜잭션에서 `StudyCancelled` 이벤트를 발행하고 채팅방 폐쇄는 T-049가 한다 | T-020, T-046 |
| X-14 | **결정됨(2026-11-22, 자동 진행 중 표 한 줄 추가라 권장안대로. API 1장).** `APPEAL_CLOSED`(API 5장)가 API 1장 오류 표에 없음 | API 1장 표에 `409 APPEAL_CLOSED` 추가 | T-034 |
| X-15 | **결정됨(2026-11-22, 사용자 확인. ADR-0004 결정 1, ARCHITECTURE 4.4).** **체크인과 자동 판정의 경쟁.** ADR-0004 결정 1은 멤버 구성을 바꾸는 작업만 `study` 행 락으로 직렬화한다. 체크인(T-027)과 주최자 미도착 무효·노쇼 후보 판정(T-031)은 락 대상이 아니어서, 마감 직전 체크인이 판정보다 늦게 커밋되면 출석과 노쇼 후보가 함께 남거나, 주최자가 체크인했는데 세션이 무효가 될 수 있음 | ADR-0004 결정 1의 대상에 "체크인, 일회용 코드 체크인, 세션 무효·노쇼 후보 판정"을 추가한다(보강 ADR 또는 ADR-0004 후속 결정). 체크인은 락을 잡은 뒤 서버 시각으로 시간창을 검사하고, 판정은 락을 잡은 뒤 대상 조건(`judged_at IS NULL`, 마감 경과)을 다시 확인한다 | T-027, T-031 |
| X-16 | **결정됨(2026-11-22, 사용자 확인. DATA-MODEL 4장은 T-017에서 이미 반영, 종료 시 삭제는 T-030).** **체크인 오픈 시 대기 정리.** PRD 4.14와 DATA-MODEL 4장(`study_waiting`)은 체크인이 열리면 대기를 자동 해제(행 삭제)한다고 하나, ADR-0005·ARCHITECTURE 4.7 작업 목록과 DATA-MODEL 10장에는 그 시점에 도는 작업이 없음 | 체크인 오픈 이후의 대기 행은 조회(`myState`, `waitingCount`)와 알림 대상에서 걸러 내고, 행 삭제는 스터디 종료·파투 처리 때 한다. DATA-MODEL 4장 문구를 "체크인 오픈 이후에는 무효로 보고 종료 시 삭제"로 고친다 | T-017, T-030 |
| X-17 | **결정됨(2026-11-16, API 1장)**: 서버 내부 오류는 `500 INTERNAL_ERROR`, 잘못된 본문·파라미터는 `VALIDATION_FAILED`, 405·415 등 HTTP 표준 오류는 `code` 없이 `status`로 구분한다. T-001에서 구현 | API 1장 | T-004 |
| X-18 | **결정됨(2026-11-17, PRD 4.2·4.4 v0.8)**: 희망 직군은 안내용이 아니라 **참여 조건**이고, 지금은 대분류로만 고른다. 희망 직군이 없거나 내 직군의 대분류가 희망 목록에 있으면 신청할 수 있고, 아니면 `409 JOB_NOT_ALLOWED`. 세부 직무 단위 스터디(그 세부 직무만 신청 가능)는 PRD 9장 운영 후 과제. 직군을 바꿔 들어오는 것은 막지 않는다 | (처음 권장안: 일치하지 않으면 안내만) | T-009, T-015, T-024 |
| X-19 | **결정됨(2026-11-19, 오른쪽 제안대로. API 4장, ARCHITECTURE 4.2).** 온보딩(닉네임·직군·지역)을 마치지 않은 회원의 참여 신청 처리가 없음. 개설은 `403 ONBOARDING_REQUIRED`인데 API 4장의 참여 신청 오류 목록에는 없어, 희망 직군이 없는 스터디에 닉네임 없는 참여자가 들어갈 수 있음 | 개설과 같이 `403 ONBOARDING_REQUIRED`. 스터디 규칙이 아니라 신청자의 전제 조건이라 PRD 4.4 검증보다 먼저(락을 잡기 전에) 확인한다 | T-015, T-024 |
| X-20 | **결정됨(2026-11-21, 사용자 지시와 T-017 구현 중 정한 세부. PRD 4.14 v0.12, API 4장).** PRD 4.14는 대기의 자격 검사를 말하지 않았고 API 4장은 대기 오류를 3개만 적어, 입장 기준을 못 맞추는 사람이 대기해 알림만 받을 수 있었음 | ① 대기도 참여 신청과 같은 자격 검사(이용 제한→입장 기준→희망 직군)를 받는다. 대기 검사 순서는 스터디 상태→이미 참여→정원 여유→이용 제한→입장 기준→희망 직군→이미 대기. 정원 여유가 먼저라 여유 있는 스터디는 자격과 무관하게 `STUDY_NOT_FULL`. ② 이미 참여 중인 사람의 대기는 `ALREADY_JOINED`. ③ 대기 취소는 대기 중이 아니어도 `204`(멱등). ④ 대기 등록 뒤에 지표·제재가 바뀌어 자격을 잃어도 대기 행은 지우지 않는다(실제 신청 때도 다시 검사). ⑤ **자리 알림은 알림을 만드는 시점에 대기자마다 같은 자격 검사(`StudyJoinRules.checkApplicant`)를 다시 해서, 통과한 대기자에게만 아웃박스에 기록한다.** 체크인 오픈 이후 행은 X-16대로 제외. 대기자가 소수라 조회 비용은 문제 삼지 않는다(사용자 확인 2026-11-21) | T-017, T-018, T-022, T-024 |
| X-21 | **결정됨(2026-11-22, 사용자 확인과 T-018 구현 중 정한 세부. PRD 4.6 v0.13, API 4장).** PRD는 시작 이후 참여자의 나가기, 주최자의 나가기 응답, 대기 알림을 보낼 시점을 정하지 않았음 | ① 시작 시각 이후이거나 스터디가 종료·파투(`ENDED`/`CANCELLED`)면 일반 참여자의 나가기는 `409 SESSION_STARTED`(새 오류 코드). ② 주최자의 나가기와 사전 조회는 T-018에서는 `403 FORBIDDEN`으로 임시 거절했고, T-020에서 승계·파투·시작 후 처리로 바꿨다(X-13). 응답의 `hostOutcome`은 주최자에게만 담긴다. ③ 나가기 검사 순서는 스터디 없음 `404` → 주최자 `403` → 참여 중이 아님 `403` → 시작 후·종료·파투 `409`. ④ 사전/직전 판정은 `now`가 (시작 − 기준 시간)보다 **엄격히 뒤**면 직전, 정확히 3시간 전은 사전. 기준 시간은 설정 `everystudy.study.late-cancel-within-minutes`(180). ⑤ 나가기→재신청→재나가기는 X-9대로 `attendance_event`의 `verdict_key` 생성 컬럼(V7)으로 취소 이벤트만 중복을 허용해 가능. ⑥ **대기 알림은 "새로 자리가 났을 때만"** 기록한다: 나가기 직전 `ACTIVE` 수가 정원 이상이던 경우만, 체크인 오픈 이후에는 보내지 않음(X-16). 대기자마다 `ApplicantEligibility`로 자격을 다시 검사해(X-20) 통과한 사람에게만 `WAITING_OPENED` 알림을 같은 트랜잭션에서 기록한다. 중복 방지 키는 `WAITING_OPENED:<스터디>:<대기자>:<나간 사람>:<시각(ms)>`, 알림 만료 시각은 체크인 오픈 시각 | T-018, T-020, T-022 |
| X-22 | **결정됨(2026-11-22, T-022 구현 중 정한 세부. API 4장).** PRD·API는 수정·정원 변경의 응답, 종료·파투 후 동작, 시작 후 일정 수정, 정원 알림을 보낼 시점을 정하지 않았음 | ① 수정·정원 변경은 고친 뒤의 상세를 `200`으로 돌려준다. ② 수정 권한은 `host_id` 일치 **그리고** `ACTIVE`(시작 후 나간 주최자 제외, X-13), 검사 순서는 없음 `404` → 주최자 아님 `403` → 종료·파투 `409 STUDY_CLOSED`(새 오류 코드, 제목·소개와 정원 모두). ③ 일정·장소는 시작 전이고 주최자 혼자일 때만, 아니면 `SCHEDULE_LOCKED`(이미 시작한 스터디 포함, 메시지에 반영). 같이 보낸 제목도 바뀌지 않는다(한 트랜잭션). ④ 일정 검사는 개설 규칙을 그대로 재사용하고(`StudyCreationRules.checkSchedule`), 보내지 않은 항목은 지금 값과 합쳐 검사한다. 일정이 바뀌면 `session_date`도 다시 계산. ⑤ 보낸 항목이 하나도 없는 `PATCH {}`는 바꾸는 것 없이 `200`. ⑥ 정원 증액 알림은 **정원이 찼던 스터디(정원 ≤ 참여자 수)에서 자리가 새로 생길 때만**, 감소·원래 여유 있던 스터디는 알리지 않는다. 체크인 오픈 뒤에는 알리지 않고(X-16) 대기 행은 지우지 않는다. 알림 로직은 `WaitingNotifier`로 빼 나가기(T-018)와 공유한다. ⑦ 정원 값은 범위 밖 `400`을 먼저, 참여자 수 미만 `409`를 그 다음에 판정 | T-022 |
| X-23 | **결정됨(2026-11-22, T-023 구현 중 정한 세부. API 3장).** PRD 4.10·API 3장은 내 스터디 목록의 포함 범위, 기본값, 정렬, 응답 형식을 정하지 않았음 | ① 내가 `ACTIVE` 참여자인 스터디만 나온다(나간 것·대기 중인 것 제외). 그래서 승계 뒤 전 주최자는 빠지고 새 주최자는 `HOST`로 나온다. 시작 후 나간 주최자의 스터디도 빠진다(X-13). ② `role` 생략은 주최·참여 모두, `state` 생략은 `upcoming`. ③ `upcoming` = 저장 상태 `ACTIVE`(시작했어도 종료 처리 전이면 포함), `past` = `ENDED`·`CANCELLED`. ④ 정렬은 다가오는 것은 시작이 빠른 순, 지난 것은 시작이 늦은 순, 같으면 `id`. 커서는 `state` 태그를 담아 다른 `state`의 커서는 `400`. ⑤ 항목은 `{ id, title, topicCode, districtCode, startAt, durationMin, capacity, memberCount, status, role }`(주최자 지표·대기 인원은 제외, 상세에서 본다) | T-023 |
| X-24 | **결정됨(2026-11-22, T-024 구현 중 정한 세부. DESIGN 6장 상세).** 상세의 버튼 조합, 문구, 확인 창, 시작 뒤 주최자의 나가기가 정해지지 않았음 | ① 버튼 규칙은 DESIGN 6장 표대로 `studyActions.ts`에 두고 단위 테스트로 조합을 검증한다. ② 주최자의 나가기 버튼 이름은 "주최 그만두기"(참여자는 "참여 취소"), 시작 뒤에도 보인다(X-13). ③ 참여자는 시작 뒤 나가기 버튼이 없다(X-21). ④ 직군 불일치는 내 직군(세부 직무면 그 대분류)이 희망 직군에 없으면 신청 전에 이유를 보이고 버튼은 숨긴다. 입장 기준은 내 지표를 모르므로 서버의 `ENTRY_CRITERIA_NOT_MET`로 안내한다. ⑤ 성공 문구는 "참여했어요.", "대기를 등록했어요.", "대기를 취소했어요.", "참여를 취소했어요." 등이고 신청·대기·나가기 뒤 상세와 목록 캐시를 다시 읽는다. ⑥ 새 오류 코드 `STUDY_CLOSED`를 오류 코드 목록에 더했다 | T-024 |
| X-25 | **결정됨(2026-11-22, T-025 구현 중 정한 세부. API 3장, DESIGN 5장 상단 헤더).** 알림 센터 화면의 진입 방법, 읽음 시점, 읽지 않음 표시, 안 읽은 수 표시가 정해지지 않았음 | ① 진입은 헤더 오른쪽의 "알림" 링크(회원에게만), 주소는 `/notifications`. ② 알림을 열면(스터디가 있으면 상세로 이동하는 링크, 없으면 버튼) 읽음으로 바꾸고, 화면에 먼저 반영한 뒤 서버에 보낸다(실패하면 되돌림). ③ 읽지 않음은 점·굵은 제목에 더해 화면 낭독용 "읽지 않음" 글자를 둔다(색만으로 구분하지 않는다). ④ "모두 읽음"은 읽지 않은 알림이 없으면 꺼진다. ⑤ **헤더에 안 읽은 수 배지는 두지 않는다**: 안 읽은 수를 주는 API가 없고, 목록 한 페이지로는 정확한 수를 알 수 없다. 필요해지면 API 3장에 `unreadCount`를 더하는 새 작업으로 한다. ⑥ 다음 페이지는 끝에 닿으면 자동으로, 관측을 못 하는 환경을 위해 "더 보기" 버튼도 둔다 | T-025 |
| X-26 | **결정됨(2026-11-22, T-026 구현 중 정한 세부. PRD 5장 화면 6·10, API 3·4장).** 내 스터디 관리와 목록의 주소, 진입 경로, 항목 구성이 정해지지 않았음 | ① 목록은 `/me/studies`(헤더 "내 스터디"), 관리는 `/studies/{id}/manage`. 관리 진입은 목록 카드의 "관리"(주최하는 진행 중인 스터디만)와 상세의 "스터디 관리"(주최자). ② 목록은 "다가오는 모임/지난 모임"과 "전체/주최/참여"로 거르고 더 보기로 이어 받는다. 카드는 역할·상태(진행 중/종료/취소됨)·일시·제목·지역·좌석 줄이다. ③ 관리 화면은 기본 정보(제목·소개, 바뀐 항목만 전송) → 일정·장소 → 정원 → 대기 인원 → 참여자 순이다. ④ 일정·장소는 `scheduleEditable=false`면 입력란을 만들지 않고 현재 값과 "새 스터디를 만들어 주세요" 안내·링크만 둔다. 서버의 `SCHEDULE_LOCKED`도 같은 안내로 보인다. ⑤ 정원 Stepper 범위는 설정값(`capacityMin`~`capacityMax`)이고 참여자 수보다 적게는 서버의 `CAPACITY_BELOW_MEMBERS`로 거절해 안내한다(화면이 막지 않는다. 대기자 때문에 참여자 수가 화면과 어긋날 수 있어 서버 판단을 따른다). ⑥ 주최자가 아니거나 종료·파투면 관리 화면 대신 안내와 상세 링크를 보인다. ⑦ 새 주최자 알림 전용 화면(PRD 화면 9)은 알림 센터의 알림에서 상세로 가는 것으로 갈음한다 | T-026 |
| X-27 | **결정됨(2026-11-22, T-027 구현 중 정한 세부. API 5장, ARCHITECTURE 4.4).** 체크인의 검사 순서, 이미 체크인한 사용자의 응답, 출석·지각 경계의 설정 위치, 요청 필드 형식이 정해지지 않았음 | ① 검사 순서: 스터디 없음 `404` → 참여자(`ACTIVE`)가 아님 `403` → **이미 체크인했으면 처음 결과를 `200`으로**(시간창·반경을 다시 보지 않음) → 종료·파투 `409 STUDY_CLOSED` → 시간창 `422 CHECKIN_WINDOW_CLOSED` → 반경 `422 CHECKIN_OUT_OF_RANGE`. ② 시간창은 양 끝 시각을 **포함**한다(시작 10분 전 정각부터 30분 뒤 정각까지). 출석은 시작 후 `everystudy.attendance.attended-within-minutes`(10)분 **정각까지**, 그 뒤는 지각이고 시작 전 체크인은 출석이다. 반경도 경계(150m)를 포함하고 거리는 반올림한 미터다. ③ `accuracyM`은 필수(0 이상, 소수 가능)이고 반올림해 저장하며 판정에는 쓰지 않는다. 위도·경도는 범위(±90, ±180)만 검사한다. ④ `GET attendance` 응답은 `{ mine: { checkedIn, result?, method?, checkedInAt? }, members?: [{ userId, nickname, checkedIn }] }`이고 `members`는 주최자에게만, 판정 결과 없이 담긴다. 주최자 본인의 체크인 결과는 `mine`에 있다. ⑤ 출석·지각 `attendance_event`는 체크인과 같은 트랜잭션에서 `trust` 기록 서비스로 남긴다 | T-027 |
| X-28 | **결정됨(2026-11-22, T-028 구현 중 정한 세부. API 5장, ARCHITECTURE 4.4).** 코드 발급·코드 체크인의 검사 순서, 코드가 아직 발급되지 않았을 때의 응답, 실패 횟수를 세는 단위, 코드를 다시 불러오는 방법이 정해지지 않았음 | ① 발급·코드 체크인도 `study` 행 락 안에서 한다(X-15와 같은 이유). ② 발급 검사 순서: 없음 `404` → 주최자 아님 `403` → 종료·파투 `409 STUDY_CLOSED` → 시간창 `422 CHECKIN_WINDOW_CLOSED` → 주최자 미체크인 `422 HOST_NOT_CHECKED_IN`. 코드 체크인은 위치 체크인과 같은 순서에서 반경 대신 `HOST_NOT_CHECKED_IN` → `CHECKIN_CODE_INVALID`를 본다(이미 체크인했으면 코드가 틀려도 처음 결과 `200`). ③ 주최자가 체크인했지만 코드를 아직 발급하지 않았을 때의 입력은 `CHECKIN_CODE_INVALID`이고 실패로 센다. ④ 실패 횟수는 (스터디, 사용자) 단위로 메모리에서 세고 성공해도 0으로 되돌리지 않는다(성공하면 더는 입력할 일이 없다). 재발급해도 횟수는 유지한다. 6자리 숫자가 아닌 입력은 `400`이고 세지 않는다. ⑤ 코드를 다시 불러오는 API는 만들지 않고 `PUT`을 다시 부르면 새 코드가 된다(주최자 화면은 T-029에서 이에 맞춘다). ⑥ 코드 체크인의 `checkin` 행은 `distance_m`·`accuracy_m`이 `NULL`이다 | T-028 |
| X-29 | **결정됨(2026-11-22, T-030 구현 중 정한 세부. ARCHITECTURE 4.7).** 스케줄 작업의 주기·배치 상한·예외 처리·종료 대상이 정해지지 않았음 | ① 배치 상한 기본 100건(`everystudy.scheduler.batch-limit`), 남은 건은 다음 실행. ② 주기는 `fixedDelay`로 스터디 종료 1분, 토큰 정리 1시간(설정으로 바꿈). ③ 작업 예외는 `JobRunner`가 삼키고 로그로 남기며, 종료 처리는 스터디별 트랜잭션이라 한 건 실패가 다음 건을 막지 않는다. ④ 종료 대상은 `ACTIVE AND session_date < 오늘(KST)`. 파투(`CANCELLED`)는 건드리지 않고 파투 시점에 이미 대기 행을 지웠다. ⑤ 토큰은 `expires_at <= now`면 지운다(만료 시각부터 이미 쓸 수 없음). ⑥ `StudyEnded` 이벤트는 만들었고 채팅방 폐쇄는 T-049 | T-030 |
| X-30 | **결정됨(2026-11-22, T-031 구현 중 정한 세부. ARCHITECTURE 4.4·4.7, DATA-MODEL 10장).** 무효 처리와 노쇼 후보 판정의 시점·대상, 파투 스터디의 `VOIDED`, 주최자의 이벤트, 통지의 유효 기한이 정해지지 않았음 | ① 미도착 기준(시작 + 30분)이 체크인 마감과 같아 **두 작업을 하나의 판정 작업으로 합친다**(`attendance` 모듈, 1분 주기). 대상은 `judged_at IS NULL AND 체크인 마감 < now`(마감 정각까지는 체크인할 수 있으므로 1초라도 지나야 판정), 상태는 가리지 않는다(`ACTIVE`·`ENDED`·`CANCELLED`). ② 파투면 `ACTIVE` 참여자 전원(주최자 포함)에게 `VOIDED`, 감점 없음, `voided_at`은 비움(미도착 전용). ③ 주최자 체크인이 없으면 `voided_at` 기록, **주최자를 뺀** `ACTIVE` 참여자 전원에게 `VOIDED`(주최자보다 먼저 체크인한 참여자도), 주최자는 `attendance_event`를 남기지 않고 `ABSENT` 감점은 그런 참여자가 있을 때만, 감사 로그 `SESSION_VOID`. 감점 감사 로그는 `HOST_PENALTY`. ④ 주최자 체크인이 있으면 체크인하지 않은 `ACTIVE` 참여자에게 `NO_SHOW_CANDIDATE`와 `NO_SHOW_NOTICE` 알림(중복 키 `NO_SHOW_NOTICE:{studyId}:{userId}`, 유효 기한은 이의 기간 `everystudy.attendance.appeal-window-hours` 48시간). ⑤ 이미 같은 판정 이벤트가 있으면 새로 남기지 않고 건너뛰며, 유일 제약 위반은 "이미 처리됨"으로 로그만 남기고 다음 스터디를 계속 처리한다. 스터디별 `READ COMMITTED` 트랜잭션에서 `study` 행 락을 잡고 조건을 다시 확인한다(X-15) | T-031 |
