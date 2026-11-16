#!/usr/bin/env bash
# tools/ci-changes.sh 테스트
#   bash tools/tests/ci-changes.sh
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT="${CI_CHANGES:-$ROOT/tools/ci-changes.sh}"

pass=0; fail=0
ok()  { pass=$((pass+1)); }
bad() { fail=$((fail+1)); echo "  ✗ FAIL: $1"; }
# paths <라벨> <backend 기대> <frontend 기대> <경로...>   (경로 목록을 --stdin 으로 전달)
paths() { local label="$1" b="$2" f="$3"; shift 3; local out
  out="$(printf '%s\n' "$@" | bash "$SCRIPT" --stdin 2>/dev/null | paste -sd ' ' -)"
  if [ "$out" = "backend=$b frontend=$f" ]; then ok; else bad "$label (기대: backend=$b frontend=$f, 실제: $out)"; fi; }
# run <라벨> <종료 코드 기대> <출력 기대> <인자...>
run() { local label="$1" rc_want="$2" want="$3"; shift 3; local out rc
  out="$(bash "$SCRIPT" "$@" 2>/dev/null | paste -sd ' ' -)"; bash "$SCRIPT" "$@" >/dev/null 2>&1; rc=$?
  if [ "$rc" = "$rc_want" ] && [ "$out" = "$want" ]; then ok; else bad "$label (기대: $rc_want '$want', 실제: $rc '$out')"; fi; }

ALL="backend=true frontend=true"

echo "== ① 경로 규칙"
paths "백엔드만" true false backend/build.gradle backend/src/main/java/A.java
paths "프론트만" false true frontend/src/app/page.tsx
paths "둘 다" true true backend/README.md frontend/package.json
paths "문서만" false false docs/PLAN.md AGENTS.md README.md
paths "도구만" false false tools/docs-check.sh tools/tests/docs-check.sh
paths "변경 없음" false false ""
paths "CI 워크플로가 바뀌면 전부" true true .github/workflows/ci.yml
paths "이 스크립트가 바뀌면 전부" true true tools/ci-changes.sh
paths "PR 템플릿은 CI 가 아님" false false .github/pull_request_template.md
paths "이름이 비슷한 다른 디렉토리" false false backend-notes/x.md frontendish/y.ts docs/backend/z.md
paths "공백이 있는 경로" true false "backend/src/a b.java"

echo "== ② 비교 대상"
run "인자가 없으면 전부 실행" 0 "$ALL"

T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
d="$T/repo"; mkdir -p "$d/tools" "$d/backend" "$d/frontend" "$d/docs"
cp "$SCRIPT" "$d/tools/ci-changes.sh"; SCRIPT="$d/tools/ci-changes.sh"
g() { git -C "$d" -c user.name=test -c user.email=test@example.com -c commit.gpgsign=false -c core.hooksPath=/dev/null "$@"; }
put() { printf 'x\n' >> "$d/$1"; }
commit() { g add -A; g commit -q -m "$1"; }
put backend/a; put frontend/b; put docs/c
git -C "$d" init -q -b develop; commit "chore: base"; g checkout -q -b feature/x

put docs/c; commit "docs: only"
run "문서만 바꾼 브랜치" 0 "backend=false frontend=false" --base develop
put frontend/b; commit "feat: front"
run "프론트를 바꾼 브랜치" 0 "backend=false frontend=true" --base develop
g checkout -q develop; put backend/a; commit "feat: develop moved"; g checkout -q feature/x
run "기준 브랜치가 앞서가도 갈라진 뒤의 변경만" 0 "backend=false frontend=true" --base develop
g merge -q develop
run "기준 브랜치를 병합해 온 변경은 내 변경이 아님" 0 "backend=false frontend=true" --base develop
put backend/a; commit "feat: back"
run "둘 다 바꾼 브랜치" 0 "$ALL" --base develop
run "없는 참조면 건너뛰지 않고 전부 실행" 0 "$ALL" --base nope

echo "== ③ 옵션"
run "알 수 없는 옵션" 2 "" --nope
run "--base 에 값이 없음" 2 "" --base

echo
echo "통과 $pass · 실패 $fail"
[ "$fail" = 0 ]
