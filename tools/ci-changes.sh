#!/usr/bin/env bash
# CI 에서 어느 영역의 job 을 돌릴지 정한다. 바뀐 경로가 그 영역에 해당할 때만 true.
#   bash tools/ci-changes.sh --base <git 참조>   # <참조>에서 갈라진 뒤 바뀐 파일로 판단 (PR)
#   bash tools/ci-changes.sh --stdin             # 표준 입력의 경로 목록(한 줄에 하나)으로 판단 (테스트용)
#   bash tools/ci-changes.sh                     # 비교 대상이 없으면 전부 true (push)
#
# 출력 (GITHUB_OUTPUT 형식)
#   backend=true|false
#   frontend=true|false
#
# 규칙
#   backend  : backend/**
#   frontend : frontend/**
#   둘 다    : .github/workflows/** 또는 이 스크립트가 바뀌면 (CI 자체가 바뀐 것이므로 전부 확인)
#
# 판단할 수 없으면(참조 없음 등) 건너뛰지 않고 전부 true 로 둔다. 잘못 건너뛰는 것보다 한 번 더 도는 쪽이 안전하다.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE=all
BASE=""
while [ $# -gt 0 ]; do
  case "$1" in
    --base) [ -n "${2:-}" ] || { echo "--base 에 git 참조가 필요합니다 (예: origin/develop)" >&2; exit 2; }
            MODE=base; BASE="$2"; shift ;;
    --stdin) MODE=stdin ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
  esac
  shift
done

emit() { printf 'backend=%s\nfrontend=%s\n' "$1" "$2"; }

case "$MODE" in
  all) emit true true; exit 0 ;;
  stdin) paths="$(cat)" ;;
  base)
    merge_base="$(git -C "$ROOT" merge-base "$BASE" HEAD 2>/dev/null)" \
      && paths="$(git -C "$ROOT" diff --name-only --no-renames "$merge_base" HEAD 2>/dev/null)" \
      || { echo "ci-changes: $BASE 와 비교할 수 없어 전부 실행합니다" >&2; emit true true; exit 0; }
    ;;
esac

backend=false; frontend=false
while IFS= read -r path; do
  case "$path" in
    backend/*) backend=true ;;
    frontend/*) frontend=true ;;
    .github/workflows/*|tools/ci-changes.sh) backend=true; frontend=true ;;
  esac
done <<< "$paths"

emit "$backend" "$frontend"
