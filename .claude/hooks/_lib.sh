#!/usr/bin/env bash
# 가드 공용 함수. 직접 실행하지 않고 source 한다.
#
# 종료코드 규약 (run-guard.sh 가 해석)
#   0  허용
#   2  정책 위반 → 차단 (deny)
#   70 가드 내부 오류 → 런처가 "훅 고장"으로 분류하고 역시 차단 (fail-closed)

HOOK_NAME="${HOOK_NAME:-hook}"

deny() {
  echo "🚫 [$HOOK_NAME] $1" >&2
  [ -n "${2:-}" ] && echo "   → $2" >&2
  exit 2
}

internal() {
  echo "[$HOOK_NAME] 내부 오류: $1" >&2
  exit 70
}

# stdin 을 JSON 객체로 읽는다. 아니면 내부 오류(= fail-closed).
read_input() {
  INPUT="$(cat)"
  [ -n "$INPUT" ] || internal "입력(stdin)이 비어 있음"
  printf '%s' "$INPUT" | jq -e 'type=="object"' >/dev/null 2>&1 || internal "입력이 JSON 객체가 아님"
}

# jget '<jq filter>' : 필수 필드 추출. 값이 없으면 비어있지 않은 종료코드.
# 사용: var="$(jget '...')" || exit 70
jget() {
  printf '%s' "$INPUT" | jq -er "$1" 2>/dev/null || { echo "[$HOOK_NAME] 입력 필드 없음: $1" >&2; return 70; }
}

# ── 시크릿 ────────────────────────────────────────────────
is_secret_path() {
  local p="${1%/}" base
  [ -z "$p" ] && return 1
  base="${p##*/}"
  case "/$p/" in   # 비밀 디렉터리 아래는 예외 파일명(.env.example 등)이어도 차단
    */.ssh/*|*/.aws/*|*/.gnupg/*|*/.kube/*|*/secrets/*) return 0 ;;
  esac
  case "$base" in
    .env.example|.env.sample|.env.template|.env.dist|*.pub) return 1 ;;
    .env|.env.*|*.env) return 0 ;;
    *.pem|*.key|*.p12|*.pfx|*.jks|*.keystore|*.kdbx) return 0 ;;
    id_rsa*|id_ed25519*|id_ecdsa*|id_dsa*) return 0 ;;
    .netrc|.pgpass|.pypirc|.git-credentials) return 0 ;;
    credentials|credentials.*|secrets.*|secret.*|*.secret|*.secrets) return 0 ;;
    service-account*.json|*-sa-key.json) return 0 ;;
  esac
  return 1
}

# 디렉터리 하위(node_modules/.git 제외)에 시크릿 파일이 있으면 0.
# 깊이 제한 없음. 파일이 너무 많아 끝까지 못 보면 "확인 불가"로 보고 0(=위험)을 반환한다 (fail-closed).
DIR_SCAN_MAX="${DIR_SCAN_MAX:-50000}"
dir_has_secret() {
  local d="${1:-.}" f n=0
  [ -d "$d" ] || return 1
  while IFS= read -r f; do
    n=$((n+1))
    [ "$n" -gt "$DIR_SCAN_MAX" ] && return 0
    is_secret_path "$f" && return 0
  done < <(find "$d" \( -name node_modules -o -name .git \) -prune -o -type f -print 2>/dev/null | head -n $((DIR_SCAN_MAX+1)))
  return 1
}

# Glob/Grep 의 파일명 패턴용 느슨한 검사 (예: **/.env*)
is_secret_glob() {
  local base="${1##*/}"
  case "$base" in
    *example*|*sample*|*template*) return 1 ;;
    .env*|*.env|*.pem|*.key|*secret*) return 0 ;;
  esac
  return 1
}

# ── 보안 정책 파일 자기 보호 ─────────────────────────────
# 설치본 훅, Claude 설정 파일, 설치/복구 도구는 에이전트가 수정·실행할 수 없다.
# (원본 tools/hooks-src/ 는 수정 가능 — 반영은 사람이 hooks-ctl.sh install 로)
is_protected_path() {
  local p="${1#./}"
  while [[ "$p" == */ ]]; do p="${p%/}"; done   # 끝 슬래시 정규화 (.claude/ == .claude)
  [ -z "$p" ] && return 1
  case "/$p/" in
    */.claude/|*/.claude/hooks/*|*/.claude/hooks.*/*|*/.claude/settings.json*|*/.claude/settings.local.json*) return 0 ;;
    */tools/hooks-ctl.sh/) return 0 ;;
  esac
  return 1
}

# 읽기 전용으로 간주하는 명령 (보호 경로를 인자로 받아도 허용)
ro_cmd() { # $1=명령 $2=첫 인자 $3=세그먼트 전체
  case "$1" in
    cat|ls|head|tail|grep|egrep|fgrep|rg|diff|wc|file|stat|less|more|shasum|sha256sum|jq|bat|tree|test|cd|pwd|'[') return 0 ;;
    find) [[ "${3:-}" =~ [[:space:]]-(exec|execdir|ok|okdir|delete|fprint|fprint0|fprintf|fls)([[:space:]]|$) ]] && return 1; return 0 ;;
    git) case "${2:-}" in add|diff|log|show|status|ls-files|blame|check-ignore|rev-parse) return 0 ;; esac ;;
  esac
  return 1
}

PROTECTED_MSG="보안 정책 파일은 에이전트가 수정/실행할 수 없습니다. 원본(tools/hooks-src/)을 수정한 뒤 사람이 '! bash tools/hooks-ctl.sh install' 로 반영하세요."
