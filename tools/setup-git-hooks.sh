#!/usr/bin/env bash
# 저장소를 clone 한 뒤 한 번 실행한다: 로컬 git 훅(.githooks)을 활성화한다.
#   bash tools/setup-git-hooks.sh          활성화
#   bash tools/setup-git-hooks.sh --check  상태만 확인 (종료코드 0=활성)
set -eu
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

if [ "${1:-}" = "--check" ]; then
  cur="$(git config --local --get core.hooksPath || true)"
  if [ "$cur" = ".githooks" ]; then echo "✅ git 훅 활성: core.hooksPath=.githooks"; exit 0; fi
  echo "✖ git 훅이 비활성입니다 (core.hooksPath='${cur}'). 실행: bash tools/setup-git-hooks.sh" >&2
  exit 1
fi

command -v git >/dev/null || { echo "✖ git 이 필요합니다" >&2; exit 1; }
chmod +x .githooks/commit-msg .githooks/pre-commit .githooks/pre-push
git config --local core.hooksPath .githooks
echo "✅ git 훅 활성화: core.hooksPath=.githooks"
command -v gitleaks >/dev/null 2>&1 || echo "ℹ 권장: gitleaks 설치 (brew install gitleaks) — pre-commit 에서 시크릿 내용도 검사합니다."
