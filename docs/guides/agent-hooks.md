---
status: confirmed
covers:
  - tools/**
  - .claude/**
  - .githooks/**
  - .github/workflows/ci.yml
---

# 3겹 방어와 보안 가드(훅) 상세

`AGENTS.md`가 요약만 담고, 자세한 동작과 사람용 명령은 이 문서에 둔다. 훅·CI를 고치면 이 문서도 함께 고친다.

## 1. 3겹 방어

같은 규칙을 세 층에서 강제한다. 앞 층이 우회돼도 뒤 층이 막는다.

| 층 | 대상 | 수단 | 우회 가능성 |
|---|---|---|---|
| ① 에이전트 훅 | Claude Code 의 도구 호출 | `.claude/` 의 PreToolUse 훅 (2장) | 셸 문자열 우회 |
| ② 로컬 git 훅 | 사람·에이전트 모두의 commit/push | `.githooks/` (commit-msg, pre-commit, pre-push) | `--no-verify` (①이 에이전트의 사용을 차단) |
| ③ CI + Branch Protection | PR 병합 | `.github/workflows/ci.yml` | 없음 (최종 방어선) |

- 규칙의 단일 출처: `tools/git-policy.sh` (커밋 형식, 브랜치 이름, PR 대상, 시크릿 파일명). ②와 ③이 같은 파일을 쓴다.
- clone 후 한 번 실행: `bash tools/setup-git-hooks.sh` (상태 확인: `--check`). 권장: `gitleaks` 설치.
- CI 의 필수 상태 검사로는 **`CI 통과`** 하나만 지정한다 (건너뛴 job 은 통과, 실패는 실패).
- `backend`·`frontend` job 은 **자기 영역이 바뀐 PR 에서만** 돈다. `changes` job 이 `tools/ci-changes.sh` 로 바뀐 경로를 보고 정한다: `backend/**` 는 백엔드, `frontend/**` 는 프론트, `.github/workflows/**` 나 감지 스크립트가 바뀌면 둘 다. 문서만 바꾼 PR 은 둘 다 건너뛴다. develop·main 으로의 push(병합 직후)에서는 전부 실행한다. 건너뛴 job 은 `CI 통과` 에서 통과로 센다.
- 백엔드는 `test`(단위·슬라이스) → `integrationTest`(Testcontainers) → `build` 순서로 따로 실행한다.
- 문서 검사: `bash tools/docs-check.sh` (머리말·`based_on` 버전·링크, 자세한 내용은 [docs/README.md](../README.md)). CI 는 오류로, pre-commit(②)은 경고로만 처리한다.
- 마이그레이션 검사: `bash tools/flyway-check.sh` (Flyway 파일명 형식, 번호 중복·누락). CI `migrations` job 에서만 실행한다.
- 테스트: `bash tools/tests/git-policy.sh` (②), `bash tools/hooks-src/tests/run.sh` (①), `bash tools/tests/docs-check.sh` (문서 검사), `bash tools/tests/flyway-check.sh` (마이그레이션 검사), `bash tools/tests/ci-changes.sh` (CI 경로 감지).

## 2. 보안 가드 (에이전트 훅)

`.claude/settings.json` 의 PreToolUse 훅이 아래를 **차단**한다 (원본: `tools/hooks-src/`).

- main 직접 commit/push, force push, main 대상 PR, PR 병합, 규칙 위반 브랜치명
- `.env`·키·인증서·`secrets/` 등 시크릿 파일의 읽기/쓰기 (`.env.example` 은 허용)
- `rm -r/-rf`, `find -delete`, `git reset --hard`, `git clean -f`, `DROP DATABASE/TABLE` 등
- `.claude/hooks`, `.claude/settings*.json`, `tools/hooks-ctl.sh` 수정/실행 (읽기는 허용)

**Fail-Closed**: 훅이 고장 나도 도구 호출은 통과되지 않는다. 대신 정책 위반(🚫)과 훅 고장(⛔ `hook-error`)을 구분해 원인과 복구 명령을 알려 준다.

### 사람용 명령 (`!` 접두사 = 훅을 거치지 않고 실행)

| 명령 | 동작 |
|---|---|
| `! bash tools/hooks-ctl.sh verify` | 원본 빌드 + 자체 테스트만 (활성화 안 함) |
| `! bash tools/hooks-ctl.sh install` | 빌드 → 테스트 → 원자적 교체 → 설정 반영 → 스모크 (실패 시 자동 롤백) |
| `! bash tools/hooks-ctl.sh check` | 설치 상태 진단 |
| `! bash tools/hooks-ctl.sh fix` | 진단 → 재설치 → 안 되면 이전 버전 롤백 |
| `! bash tools/hooks-ctl.sh rollback` | 직전 버전으로 즉시 복원 |

### 원본과 설치본

- 원본 `tools/hooks-src/`(에이전트가 수정 가능), 설치본 `.claude/hooks/`(사람이 `install`로 반영).
- CI가 설치본과 원본이 같은지 검사하므로, 훅 원본을 바꾸면 사람의 `install` 후에 커밋한다.

### 인자 역할

같은 단어라도 경로(`cat secrets/x`)와 글자(커밋 메시지, 검색어, 출력 문구)를 구분한다.

- 글자 자리: `echo`/`printf` 인자, `git commit|tag` 의 `-m` 값, `gh pr|issue|release` 의 `--title`/`--body`, `grep` 계열의 검색 패턴, `cat`·`tee`·`git commit`·`gh` 로 가는 heredoc 본문
- 경로 자리: `-F`, `--body-file`, 리다이렉션 대상. `$(..)`·백틱·파이프가 섞인 명령은 치환하지 않고 그대로 검사한다.

### 한계

훅은 셸 문자열을 단순 분석하므로 `bash -c`, 변수 치환, 스크립트 내부 명령 같은 우회까지는 막지 못한다. 보조 방어선일 뿐이고, 최종 방어선은 GitHub Branch Protection(③)이다.
알려진 후속 과제: 따옴표 안의 `;`·`|` 분리로 인한 오탐, `git -C <dir> commit` heredoc 미처리.
