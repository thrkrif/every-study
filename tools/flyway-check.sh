#!/usr/bin/env bash
# Flyway 마이그레이션 파일명 검사. 규칙은 AGENTS.md 6장 "DB 마이그레이션"과 ADR-0001.
#   bash tools/flyway-check.sh                # 오류가 있으면 1 (CI)
#   bash tools/flyway-check.sh --dir <경로>   # 다른 디렉토리를 검사 (테스트용)
#
# 검사 항목 (디렉토리 아래 모든 파일, 하위 디렉토리 포함 — Flyway 도 하위까지 읽는다)
#   1. 파일명이 V<번호>__<설명>.sql 인가 (번호는 0으로 시작하지 않는 정수, 설명은 영문·숫자·밑줄)
#   2. 같은 번호가 두 번 쓰이지 않았는가
#   3. 번호가 1부터 빠짐없이 이어지는가
#
# 한계: 이미 병합된 파일의 내용을 고쳤는지는 보지 않는다 (운영 DB 에서는 Flyway 체크섬 검증이 막는다).
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR="$ROOT/backend/src/main/resources/db/migration"
while [ $# -gt 0 ]; do
  case "$1" in
    --dir) [ -n "${2:-}" ] && [ -d "$2" ] || { echo "--dir 에 존재하는 디렉토리가 필요합니다" >&2; exit 2; }
           DIR="$(cd "$2" && pwd)"; shift ;;
    -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
    *) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
  esac
  shift
done

[ -d "$DIR" ] || { echo "flyway-check: 마이그레이션 디렉토리가 없어 건너뜀"; exit 0; }

ERR=0
report() { # <사유> [<조치>]
  ERR=$((ERR + 1))
  printf '✖ %s\n' "$1" >&2
  [ -n "${2:-}" ] && printf '  → %s\n' "$2" >&2
  return 0
}

count=0
numbers=""   # 형식이 맞는 파일의 번호 (한 줄에 하나)
while IFS= read -r f; do
  [ -n "$f" ] || continue
  count=$((count + 1))
  name="${f#"$DIR"/}"
  base="$(basename "$f")"
  if [[ "$base" =~ ^V([1-9][0-9]*)__[A-Za-z0-9_]+\.sql$ ]]; then
    numbers="${numbers}${BASH_REMATCH[1]}"$'\n'
  else
    report "파일명 형식이 잘못됨: $name" "V<번호>__<설명>.sql 로 쓰세요 (예: V2__create_user.sql). 번호는 0으로 시작하지 않는 정수, 설명은 영문·숫자·밑줄입니다."
  fi
done < <(find "$DIR" -type f | sort)

sorted="$(printf '%s' "$numbers" | sort -n)"

while IFS= read -r n; do
  [ -n "$n" ] || continue
  report "번호가 중복됨: V$n" "다른 브랜치가 먼저 쓴 번호입니다. 새 파일의 번호를 다음 번호로 바꾸세요 (병합된 파일은 고치지 않습니다)."
done < <(printf '%s\n' "$sorted" | uniq -d)

expected=1
while IFS= read -r n; do
  [ -n "$n" ] || continue
  while [ "$expected" -lt "$n" ]; do
    report "번호가 빠짐: V$expected" "번호는 1부터 빠짐없이 이어져야 합니다."
    expected=$((expected + 1))
  done
  expected=$((n + 1))
done < <(printf '%s\n' "$sorted" | uniq)

if [ "$ERR" -gt 0 ]; then
  echo "flyway-check: 오류 ${ERR}건 (파일 ${count}개)" >&2
  exit 1
fi
echo "flyway-check: 파일 ${count}개 통과"
