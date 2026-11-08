# AGENTS.md — 모두의 스터디

AI 에이전트(Claude Code 등)와 사람 모두가 따르는 프로젝트 규칙. `CLAUDE.md` 는 이 파일을 참조한다.

## 1. 프로젝트

전 직군 취준생이 동네(구 단위)에서 **대면 스터디**를 만들고 참여하는 매칭 서비스. 사람 평가 없이 **출석 기록으로 만든 신뢰 지표(출석률)** 로 노쇼를 억제한다.
기능 명세: [`docs/PRD.md`](docs/PRD.md) (초안 — 확정 전까지 변경될 수 있음)

| 영역 | 경로 | 기술 (PRD 기준) |
|---|---|---|
| 백엔드 | `backend/` | Spring Boot REST API |
| 프론트엔드 | `frontend/` | Next.js (React), 모바일 웹 우선 |
| 문서 | `docs/` | PRD → ARCHITECTURE(+ADR) → API·데이터 명세 → PLAN |
| 보안 훅 | `.claude/`, `tools/` | 아래 §4 |

세부 기술 스택·빌드 도구·패키지 구조는 `docs/ARCHITECTURE.md` 확정 후 이 파일에 추가한다. **확정되지 않은 것은 추측해서 코드/설정에 넣지 않는다.**

## 2. 작업 순서

1. `docs/PRD.md` 완성 → 2. `docs/ARCHITECTURE.md` + `docs/adr/` → 3. API·데이터 명세 → 4. `/ralplan` 으로 `PLAN.md` → 5. 구현
상위 문서가 바뀌면 하위 문서를 함께 갱신한다.

## 3. Git flow

| 브랜치 | 용도 | 만드는 곳 → 병합 대상 |
|---|---|---|
| `main` | 배포 가능한 상태만 | (직접 push/commit 금지) |
| `develop` | 통합 | `feature/*`, `bugfix/*` 가 PR 로 병합 |
| `feature/<이름>` | 기능 개발 | `develop` → `develop` |
| `bugfix/<이름>` | develop 버그 수정 | `develop` → `develop` |
| `release/<버전>` | 릴리스 준비 | `develop` → `main`(사람이 병합) + `develop` |
| `hotfix/<이름>` | 운영 긴급 수정 | `main` → `main`(사람이 병합) + `develop` |

- 브랜치 이름: 소문자·숫자·`.`·`-`·`_` (예: `feature/study-create`, `release/0.1.0`)
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

## 4. 보안 가드 (훅)

`.claude/settings.json` 의 PreToolUse 훅이 아래를 **차단**한다 (원본: `tools/hooks-src/`).

- main 직접 commit/push, force push, main 대상 PR, PR 병합, 규칙 위반 브랜치명
- `.env`·키·인증서·`secrets/` 등 시크릿 파일의 읽기/쓰기 (`.env.example` 은 허용)
- `rm -r/-rf`, `find -delete`, `git reset --hard`, `git clean -f`, `DROP DATABASE/TABLE` 등
- `.claude/hooks`, `.claude/settings*.json`, `tools/hooks-ctl.sh` 수정/실행 (읽기는 허용)

**Fail-Closed**: 훅이 고장 나도 도구 호출은 통과되지 않는다. 대신 정책 위반(🚫)과 훅 고장(⛔ `hook-error`)을 구분해 원인과 복구 명령을 알려 준다.

### 에이전트 행동 규칙
- 차단되면 **우회하지 않는다** (다른 명령/도구로 같은 동작 시도 금지). 사유를 사용자에게 전달하고 사람이 할 일을 안내한다.
- `⛔ hook-error` 를 만나면 직접 고치려 하지 말고 사용자에게 `! bash tools/hooks-ctl.sh check` / `fix` 실행을 요청한다.
- 훅 로직을 바꾸려면 `tools/hooks-src/` 를 수정하고 `bash tools/hooks-src/tests/run.sh` 가 통과하는지 확인한 뒤, **사람이** `! bash tools/hooks-ctl.sh install` 로 반영한다.

### 사람용 명령 (`!` 접두사 = 훅을 거치지 않고 실행)

| 명령 | 동작 |
|---|---|
| `! bash tools/hooks-ctl.sh verify` | 원본 빌드 + 자체 테스트만 (활성화 안 함) |
| `! bash tools/hooks-ctl.sh install` | 빌드 → 테스트 → 원자적 교체 → 설정 반영 → 스모크 (실패 시 자동 롤백) |
| `! bash tools/hooks-ctl.sh check` | 설치 상태 진단 |
| `! bash tools/hooks-ctl.sh fix` | 진단 → 재설치 → 안 되면 이전 버전 롤백 |
| `! bash tools/hooks-ctl.sh rollback` | 직전 버전으로 즉시 복원 |

한계: 훅은 셸 문자열을 단순 분석하므로 `bash -c`, 변수 치환, 스크립트 내부 명령 같은 우회까지는 막지 못한다. 보조 방어선일 뿐이다.

## 5. 시크릿

- `.env` 등 시크릿은 커밋하지 않는다. 필요한 키 이름은 `.env.example` 에만 적는다.
- 실제 값은 사람이 직접 관리한다. 에이전트는 시크릿을 읽지도, 만들어 채워 넣지도 않는다.
- 로그·에러 메시지·테스트 픽스처에 토큰/개인정보를 남기지 않는다.

## 6. 코드 컨벤션

`docs/ARCHITECTURE.md` 확정 후 작성한다 (패키지 구조, 네이밍, 테스트 전략, 린트/포맷터). 그 전에는 `.editorconfig` 를 따른다.
