# AGENTS.md — 모두의 스터디

AI 에이전트(Claude Code 등)와 사람 모두가 따르는 프로젝트 규칙의 **입구**. `CLAUDE.md` 는 이 파일을 참조한다.
세부 내용은 아래 문서 지도를 따라 필요한 문서만 읽는다.

## 1. 프로젝트

전 직군 취준생이 동네(구 단위)에서 **대면 스터디**를 만들고 참여하는 매칭 서비스. 사람 평가 없이 **출석 기록으로 만든 신뢰 지표(출석률)** 로 노쇼를 억제한다.

| 영역 | 경로 | 기술 |
|---|---|---|
| 백엔드 | `backend/` | Java, Spring Boot REST API, Gradle(Groovy), MySQL, Flyway, JWT(httpOnly 쿠키) |
| 프론트엔드 | `frontend/` | Next.js (React), TypeScript, 모바일 웹 우선 |
| 문서 | `docs/` | 아래 §2 |
| 보안 훅·CI | `.claude/`, `.githooks/`, `tools/`, `.github/` | 아래 §4 |

**확정되지 않은 것은 추측해서 코드/설정에 넣지 않는다.** 문서 머리말의 `status`가 `draft`이거나 본문에 "제안"이라고 된 항목은 확정 전이다.

## 2. 문서 지도

전체 지도와 작성 규칙은 [`docs/README.md`](docs/README.md). 상황별 진입점만 적는다.

| 하려는 일 | 읽을 문서 |
|---|---|
| 기능·정책 수치 | [`docs/PRD.md`](docs/PRD.md) (정책 수치의 유일한 원본) |
| 구조·모듈 경계·설계 원칙 | [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) |
| 결정 근거(스택, 인증, 동시성, 채팅·알림) | [`docs/adr/`](docs/adr/) |
| 보안 훅·3겹 방어·사람용 명령 | [`docs/guides/agent-hooks.md`](docs/guides/agent-hooks.md) |

작업 순서: PRD → ARCHITECTURE(+ADR) → API·데이터 명세 → `/ralplan` 으로 PLAN → 구현.

- **상위 문서가 바뀌면 하위 문서를 함께 확인한다.** 문서 머리말의 `based_on` 이 관계를 나타내고, 버전이 어긋나면 CI(`bash tools/docs-check.sh`)가 "오래됨"으로 막는다. 의미 있는 변경이면 그 문서의 `version` 을 올린다.
- **한 사실은 한 곳에만 쓴다.** 값을 복사하지 않고 원본 위치를 가리킨다 (`docs/README.md` 3장).
- 코드가 바뀌면 그 코드를 설명하는 문서(머리말 `covers`)도 같은 PR 에서 갱신한다.

## 3. Git flow

| 브랜치 | 용도 | 만드는 곳 → 병합 대상 |
|---|---|---|
| `main` | 배포 가능한 상태만 | (직접 push/commit 금지) |
| `develop` | 통합 | `feature/*`, `bugfix/*` 가 PR 로 병합 |
| `feature/<이름>` | 기능 개발 | `develop` → `develop` |
| `bugfix/<이름>` | develop 버그 수정 | `develop` → `develop` |
| `release/<버전>` | 릴리스 준비 | `develop` → `main`(사람이 병합) + `develop` |
| `hotfix/<이름>` | 운영 긴급 수정 | `main` → `main`(사람이 병합) + `develop` |

- 브랜치 이름: 소문자·숫자·`.`·`-`·`_` (예: `feature/study-create`, `release/0.1.0`). 문서 작업도 `feature/` 를 쓴다.
- **에이전트는** main 에 직접 commit/push 하지 않고, main 대상 PR 을 만들지 않으며, PR 을 병합하지 않는다. PR 은 항상 `--base develop`.
- force push, `git reset --hard`, `git clean -f` 는 사용하지 않는다.
- GitHub 쪽 **Branch Protection**(main/develop: PR 필수, 직접 push 금지)을 반드시 함께 설정한다. 로컬 훅은 이를 대체하지 못한다.

### 커밋 메시지 (Conventional Commits)

```
<type>(<scope>): <제목>

<본문: 무엇보다 "왜">
```
- type: `feat` `fix` `docs` `refactor` `test` `chore` `build` `ci` `perf`
- scope(선택): `backend` `frontend` `docs` `tools`
- 제목은 한 줄, 마침표 없음. 하나의 커밋 = 하나의 논리적 변경.

## 4. 3겹 방어와 보안 가드

같은 규칙을 ① 에이전트 훅(`.claude/`) ② 로컬 git 훅(`.githooks/`) ③ CI + Branch Protection 세 층에서 강제한다. 상세·사람용 명령·한계는 [`docs/guides/agent-hooks.md`](docs/guides/agent-hooks.md).

- 규칙의 단일 출처: `tools/git-policy.sh`. clone 후 한 번 `bash tools/setup-git-hooks.sh`.
- 테스트: `bash tools/tests/git-policy.sh` (②), `bash tools/hooks-src/tests/run.sh` (①).
- 차단 대상: main 직접 commit/push·PR·병합, 시크릿 파일 접근(`.env.example` 제외), `rm -r`·`git reset --hard` 등 파괴 명령, 훅·설정 파일 수정.

### 에이전트 행동 규칙
- 차단되면 **우회하지 않는다** (다른 명령/도구로 같은 동작 시도 금지). 사유를 사용자에게 전달하고 사람이 할 일을 안내한다.
- `⛔ hook-error` 를 만나면 직접 고치려 하지 말고 사용자에게 `! bash tools/hooks-ctl.sh check` / `fix` 실행을 요청한다.
- 훅 로직을 바꾸려면 `tools/hooks-src/` 를 수정하고 테스트가 통과하는지 확인한 뒤, **사람이** `! bash tools/hooks-ctl.sh install` 로 반영한다.

## 5. 시크릿

- `.env` 등 시크릿은 커밋하지 않는다. 필요한 키 이름은 `.env.example` 에만 적는다.
- 실제 값은 사람이 직접 관리한다. 에이전트는 시크릿을 읽지도, 만들어 채워 넣지도 않는다.
- 로그·에러 메시지·테스트 픽스처에 토큰/개인정보를 남기지 않는다.

## 6. 코드 컨벤션

패키지 구조·네이밍·테스트·린트는 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) 가 확정되면 이곳에 추가한다. 그 전에는 `.editorconfig` 를 따른다.

확정된 규칙만 먼저 적는다.

### DB 마이그레이션 (Flyway)
- 위치 `backend/src/main/resources/db/migration/`, 파일명 `V<번호>__<설명>.sql`.
- **병합된 마이그레이션은 수정하지 않는다.** 변경은 새 버전 파일로 추가한다.
- JPA 는 `ddl-auto=validate`. 스키마 생성·변경은 Flyway 로만 한다.
- MySQL 은 `utf8mb4` 를 명시한다.

### 인증 (JWT)
- 토큰은 `localStorage` 에 두지 않고 **httpOnly 쿠키**로 다룬다. CSRF·CORS 대응을 함께 설계한다.
