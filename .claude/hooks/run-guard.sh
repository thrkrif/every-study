#!/usr/bin/env bash
# 보안 훅 런처 — Fail-Closed 를 유지하면서 "훅 고장"과 "정책 위반"을 구분해 알린다.
#   사용: bash run-guard.sh <guard-bash|guard-files>   (stdin: 훅 입력 JSON)
#   종료코드는 0(허용) 또는 2(차단)만 반환한다.
#     - 정책 위반      → 가드의 메시지 그대로 출력, exit 2
#     - 훅 자체 고장   → 원인 + 사람용 복구 명령 출력, exit 2  (절대 조용히 통과시키지 않음)
# 이 파일은 'bash <경로>' 로 호출되므로 실행 권한 비트에 의존하지 않는다.

name="${1:-}"
dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)" || dir=""
logf="${HOOKS_LOG:-$dir/../hooks.log}"

log() { printf '%s %s %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$1" "$2" >> "$logf" 2>/dev/null; return 0; }

broken() {
  log "HOOK-ERROR[$name]" "$1"
  {
    echo "⛔ [hook-error:$name] 보안 훅 자체가 고장났습니다 (정책 위반이 아닙니다)."
    echo "   원인: $1"
    echo "   → 안전을 위해(fail-closed) 이 도구 호출은 차단됩니다. 에이전트는 이 문제를 직접 고칠 수 없습니다."
    echo "   → 사람이 직접 실행:  ! bash tools/hooks-ctl.sh check   (진단)"
    echo "                         ! bash tools/hooks-ctl.sh fix     (자동 복구)"
  } >&2
  exit 2
}

case "$name" in
  guard-bash|guard-files) ;;
  *) broken "알 수 없는 가드 이름: '$name'" ;;
esac

[ -n "$dir" ] || broken "훅 디렉터리를 찾을 수 없음"
command -v jq >/dev/null 2>&1 || broken "jq 미설치 (brew install jq)"

for f in _lib.sh "$name.sh" MANIFEST.sha256; do
  [ -r "$dir/$f" ] || broken "파일 없음 또는 읽기 불가: $f"
done

if command -v shasum >/dev/null 2>&1; then SUM="shasum -a 256"
elif command -v sha256sum >/dev/null 2>&1; then SUM="sha256sum"
else broken "shasum/sha256sum 없음"; fi

(cd "$dir" && $SUM -c MANIFEST.sha256) >/dev/null 2>&1 \
  || broken "무결성 검사 실패 — MANIFEST 와 불일치 (훅 파일이 변경/손상됨)"

bash -n "$dir/_lib.sh" 2>/dev/null || broken "문법 오류: _lib.sh"
bash -n "$dir/$name.sh" 2>/dev/null || broken "문법 오류: $name.sh"

input="$(cat)"
[ -n "$input" ] || broken "훅 입력(stdin)이 비어 있음"
printf '%s' "$input" | jq -e 'type=="object"' >/dev/null 2>&1 || broken "훅 입력이 JSON 객체가 아님 (stdin 비었거나 형식 오류)"

err="$(printf '%s' "$input" | bash "$dir/$name.sh" 2>&1 >/dev/null)"
rc=$?

case "$rc" in
  0)  exit 0 ;;
  2)  log "DENY[$name]" "$(printf '%s' "$err" | head -n1)"
      printf '%s\n' "$err" >&2
      exit 2 ;;
  *)  broken "$name 내부 오류 (exit $rc): $(printf '%s' "$err" | head -n3 | tr '\n' ' ')" ;;
esac
