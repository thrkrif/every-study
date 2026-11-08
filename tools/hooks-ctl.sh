#!/usr/bin/env bash
# 보안 훅 설치 · 검증 · 진단 · 복구 도구 — 반드시 "사람이" 실행한다.
#
#   ! bash tools/hooks-ctl.sh verify     원본을 임시 경로에 빌드하고 자체 테스트만 수행 (활성화 안 함)
#   ! bash tools/hooks-ctl.sh install    빌드 → 자체 테스트 → 원자적 교체 → 설정 반영 → 스모크 테스트 (실패 시 자동 롤백)
#   ! bash tools/hooks-ctl.sh check      현재 설치 상태 진단 (원인과 조치 출력)
#   ! bash tools/hooks-ctl.sh fix        진단 후 필요하면 재설치, 그래도 실패하면 이전 버전으로 롤백
#   ! bash tools/hooks-ctl.sh rollback   직전 버전(.prev)으로 즉시 되돌림
#
# 설계 원칙
#   - Fail-Closed 유지: 이 도구는 검증을 우회하는 모드를 제공하지 않는다.
#   - 활성화는 항상 마지막: 훅 파일 검증 → 훅 교체 → settings.json 교체 순서.
#   - 에이전트(Claude)는 가드에 의해 이 파일을 수정/실행할 수 없다. 사람은 '!' 접두사로 실행한다(훅을 거치지 않음).

set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/tools/hooks-src"
CDIR="$ROOT/.claude"
LIVE="$CDIR/hooks";            PREV="$CDIR/hooks.prev"
SETTINGS="$CDIR/settings.json"; SETTINGS_PREV="$CDIR/settings.json.prev"
LOG="$CDIR/hooks.log"
STAGE_DIR=""
cleanup() { [ -n "$STAGE_DIR" ] && rm -rf "$STAGE_DIR"; return 0; }
trap cleanup EXIT
FILES=(_lib.sh guard-bash.sh guard-files.sh run-guard.sh)

say()  { printf '%s\n' "$*"; }
ok()   { printf '  ✅ %s\n' "$*"; }
bad()  { printf '  ❌ %s\n' "$*"; }
note() { printf '     ↳ %s\n' "$*"; }
log()  { mkdir -p "$CDIR"; printf '%s CTL %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*" >> "$LOG" 2>/dev/null; return 0; }

if command -v shasum >/dev/null 2>&1; then SUM="shasum -a 256"; elif command -v sha256sum >/dev/null 2>&1; then SUM="sha256sum"; else SUM=""; fi

# ── 공통 ──────────────────────────────────────────────────
preflight() {
  local fail=0
  for c in bash jq git mktemp mv cp; do
    command -v "$c" >/dev/null 2>&1 && ok "의존성: $c" || { bad "의존성 없음: $c"; [ "$c" = jq ] && note "brew install jq"; fail=1; }
  done
  [ -n "$SUM" ] && ok "의존성: ${SUM%% *}" || { bad "shasum/sha256sum 없음"; fail=1; }
  for f in "${FILES[@]}" settings.template.json tests/run.sh; do
    [ -f "$SRC/$f" ] && ok "원본: $f" || { bad "원본 없음: tools/hooks-src/$f"; fail=1; }
  done
  jq -e . "$SRC/settings.template.json" >/dev/null 2>&1 && ok "settings.template.json 유효" || { bad "settings.template.json JSON 오류"; fail=1; }
  return $fail
}

build_stage() { # $1=stage 디렉터리 → $1/hooks 생성
  mkdir -p "$1/hooks" || return 1
  for f in "${FILES[@]}"; do cp "$SRC/$f" "$1/hooks/$f" || return 1; done
  chmod +x "$1/hooks/"*.sh
  (cd "$1/hooks" && $SUM "${FILES[@]}" > MANIFEST.sha256) || return 1
  for f in "${FILES[@]}"; do bash -n "$1/hooks/$f" 2>/dev/null && ok "문법 OK: $f" || { bad "문법 오류: $f"; bash -n "$1/hooks/$f"; return 1; }; done
}

selftest() { # $1=훅 디렉터리
  HOOKS_DIR="$1" bash "$SRC/tests/run.sh"
}

merge_settings() { # $1=출력 파일
  local base='{}'
  if [ -f "$SETTINGS" ]; then
    jq -e . "$SETTINGS" >/dev/null 2>&1 || { bad "기존 .claude/settings.json 이 유효한 JSON 이 아님 — 덮어쓰지 않고 중단"; return 1; }
    base="$(cat "$SETTINGS")"
  fi
  jq -n --argjson cur "$base" --slurpfile tpl "$SRC/settings.template.json" '
    $tpl[0] as $t
    | $cur
    | .permissions.deny = (((.permissions.deny // []) + $t.permissions.deny) | unique)
    | .hooks.PreToolUse = ((((.hooks.PreToolUse // []) | map(select((tojson | contains("run-guard.sh") or contains("/hooks/guard-")) | not))) + $t.hooks.PreToolUse))
  ' > "$1" && jq -e . "$1" >/dev/null 2>&1
}

smoke() { # 설치본 스모크: 정책 차단 / 정책 허용 / 파일 차단 / 파일 허용
  local d="${1:-$LIVE}" r=0 out rc
  run() { out="$(printf '%s' "$2" | HOOKS_LOG=/dev/null bash "$d/run-guard.sh" "$1" 2>&1 >/dev/null)"; rc=$?; }
  run guard-bash  '{"tool_name":"Bash","cwd":"/tmp","tool_input":{"command":"rm -rf /tmp/x"}}'
  [ $rc -eq 2 ] && [[ "$out" != *hook-error* ]] && ok "스모크: rm -rf 차단" || { bad "스모크: rm -rf 가 차단되지 않음 (rc=$rc) $out"; r=1; }
  run guard-bash  '{"tool_name":"Bash","cwd":"/tmp","tool_input":{"command":"ls"}}'
  [ $rc -eq 0 ] && ok "스모크: ls 허용" || { bad "스모크: ls 가 허용되지 않음 (rc=$rc) $out"; r=1; }
  run guard-files '{"tool_name":"Read","tool_input":{"file_path":"/x/.env"}}'
  [ $rc -eq 2 ] && [[ "$out" != *hook-error* ]] && ok "스모크: .env 읽기 차단" || { bad "스모크: .env 읽기가 차단되지 않음 (rc=$rc) $out"; r=1; }
  run guard-files '{"tool_name":"Read","tool_input":{"file_path":"/x/README.md"}}'
  [ $rc -eq 0 ] && ok "스모크: README 읽기 허용" || { bad "스모크: README 읽기가 허용되지 않음 (rc=$rc) $out"; r=1; }
  return $r
}

# 훅 디렉터리가 존재하고 MANIFEST 무결성 + 문법을 통과하면 0
live_healthy() {
  local d="$1" f
  [ -d "$d" ] && [ -f "$d/MANIFEST.sha256" ] && [ -n "$SUM" ] || return 1
  (cd "$d" && $SUM -c MANIFEST.sha256 >/dev/null 2>&1) || return 1
  for f in "${FILES[@]}"; do [ -f "$d/$f" ] && bash -n "$d/$f" 2>/dev/null || return 1; done
}

rollback_live() {
  local did=0
  if [ -d "$PREV" ] && ! live_healthy "$PREV"; then bad ".claude/hooks.prev 도 무결성 검증을 통과하지 못해 롤백하지 않음"; return 1; fi
  if [ -d "$PREV" ]; then
    rm -rf "$LIVE.bad"; [ -d "$LIVE" ] && mv "$LIVE" "$LIVE.bad"
    mv "$PREV" "$LIVE" && did=1
  fi
  if [ -f "$SETTINGS_PREV" ]; then cp -p "$SETTINGS_PREV" "$CDIR/.settings.rb" && mv "$CDIR/.settings.rb" "$SETTINGS" && did=1; fi
  [ $did -eq 1 ] && log "ROLLBACK ok" || log "ROLLBACK nothing-to-restore"
  [ $did -eq 1 ]
}

# ── 명령 ──────────────────────────────────────────────────
cmd_verify() {
  say "== 사전 점검"; preflight || return 1
  local stage; stage="$(mktemp -d)"; STAGE_DIR="$stage"
  say "== 빌드(임시 경로)"; build_stage "$stage" || return 1
  say "== 자체 테스트"; selftest "$stage/hooks" || { bad "자체 테스트 실패 — 활성화하지 않음"; return 1; }
  ok "검증 통과 (아직 활성화되지 않았습니다. 활성화: install)"
}

cmd_install() {
  say "== 1/6 사전 점검"; preflight || { bad "사전 점검 실패 — 아무것도 변경하지 않았습니다"; return 1; }
  mkdir -p "$CDIR"
  local stage; stage="$(mktemp -d "$CDIR/.stage.XXXXXX")"; STAGE_DIR="$stage"
  say "== 2/6 빌드(임시 경로, 아직 비활성)"; build_stage "$stage" || { bad "빌드 실패 — 변경 없음"; return 1; }
  say "== 3/6 자체 테스트 (정책 + 고장 주입)"; selftest "$stage/hooks" || { bad "자체 테스트 실패 — 변경 없음(기존 훅 유지)"; return 1; }
  say "== 4/6 설정 병합 준비"; merge_settings "$stage/settings.json" || { bad "설정 병합 실패 — 변경 없음"; return 1; }; ok "settings.json 병합본 생성/검증"

  say "== 5/6 활성화 (훅 교체 → settings 교체)"
  # 롤백 대상(.prev)은 "검증을 통과하는(건강한) 설치본"만 된다.
  # 고장났거나 MANIFEST 없는 구버전은 hooks.bad 로 치워 두고, 기존의 좋은 .prev 는 덮어쓰지 않는다.
  local was_healthy=0
  live_healthy "$LIVE" && was_healthy=1
  if [ -d "$LIVE" ]; then
    if [ $was_healthy -eq 1 ]; then rm -rf "$PREV"; mv "$LIVE" "$PREV"
    else rm -rf "$LIVE.bad"; mv "$LIVE" "$LIVE.bad"; fi
  fi
  if ! mv "$stage/hooks" "$LIVE"; then
    bad "훅 교체 실패 — 이전 상태 복원"
    if [ $was_healthy -eq 1 ]; then mv "$PREV" "$LIVE"; elif [ -d "$LIVE.bad" ]; then mv "$LIVE.bad" "$LIVE"; fi
    return 1
  fi
  if [ $was_healthy -eq 1 ]; then ok "hooks 교체 완료 (이전 버전: .claude/hooks.prev)"
  else ok "hooks 교체 완료 (기존 설치본은 검증 불가 → .claude/hooks.bad 로 이동, 롤백 대상 아님)"; fi
  if [ $was_healthy -eq 1 ] && [ -f "$SETTINGS" ]; then cp -p "$SETTINGS" "$SETTINGS_PREV"; fi
  cp "$stage/settings.json" "$CDIR/.settings.new" && mv "$CDIR/.settings.new" "$SETTINGS" \
    || { bad "settings 교체 실패 — 롤백"; rollback_live; return 1; }
  ok "settings.json 교체 완료 (이전 버전: settings.json.prev)"

  say "== 6/6 설치본 스모크 테스트"
  if ! smoke "$LIVE"; then bad "스모크 실패 — 자동 롤백"; rollback_live && ok "이전 상태로 복원됨" ; log "INSTALL failed-smoke"; return 1; fi
  log "INSTALL ok"
  say ""; ok "설치 완료. 새 정책이 활성화되었습니다. (Claude Code 가 설정을 못 읽으면 /hooks 한 번 열거나 세션 재시작)"
}

cmd_check() {
  local fail=0
  say "== 의존성"
  for c in bash jq git; do command -v "$c" >/dev/null 2>&1 && ok "$c" || { bad "$c 없음"; fail=1; }; done
  [ -n "$SUM" ] && ok "${SUM%% *}" || { bad "shasum/sha256sum 없음"; fail=1; }
  say "== 설치본 ($LIVE)"
  if [ ! -d "$LIVE" ]; then bad "훅 디렉터리 없음"; note "조치: ! bash tools/hooks-ctl.sh fix"; fail=1
  else
    for f in "${FILES[@]}" MANIFEST.sha256; do [ -r "$LIVE/$f" ] && ok "존재: $f" || { bad "없음/읽기 불가: $f"; fail=1; }; done
    [ -n "$SUM" ] && (cd "$LIVE" && $SUM -c MANIFEST.sha256 >/dev/null 2>&1) && ok "무결성(MANIFEST) 일치" || { bad "무결성 불일치 또는 MANIFEST 없음"; note "조치: ! bash tools/hooks-ctl.sh fix"; fail=1; }
    for f in "${FILES[@]}"; do [ -f "$LIVE/$f" ] && { bash -n "$LIVE/$f" 2>/dev/null || { bad "문법 오류: $f"; fail=1; }; }; done
    [ -f "$LIVE/run-guard.sh" ] && smoke "$LIVE" || fail=1
  fi
  say "== settings.json"
  if [ ! -f "$SETTINGS" ]; then bad "settings.json 없음 (훅이 등록되지 않음 = 보호 꺼짐)"; note "조치: ! bash tools/hooks-ctl.sh install"; fail=1
  elif ! jq -e . "$SETTINGS" >/dev/null 2>&1; then bad "settings.json JSON 오류 — 모든 설정이 무시될 수 있음"; note "조치: ! bash tools/hooks-ctl.sh rollback  (또는 직접 수정 후 install)"; fail=1
  else
    jq -e '[.hooks.PreToolUse[]? | select(.hooks[]?.command | tostring | contains("run-guard.sh"))] | length >= 2' "$SETTINGS" >/dev/null 2>&1 \
      && ok "가드 훅 2종 등록됨" || { bad "가드 훅이 settings.json 에 등록돼 있지 않음 (보호 꺼짐)"; note "조치: ! bash tools/hooks-ctl.sh install"; fail=1; }
    jq -e '[.hooks.PreToolUse[]? | select(.hooks[]?.command | tostring | contains("run-guard.sh")) | .hooks[] | .onFailure] | all(. == "block")' "$SETTINGS" >/dev/null 2>&1 \
      && ok "onFailure=block (fail-closed)" || { bad "onFailure=block 이 아님 (fail-open 위험)"; fail=1; }
    jq -e '.disableAllHooks != true' "$SETTINGS" >/dev/null 2>&1 && ok "disableAllHooks 꺼짐" || { bad "disableAllHooks=true — 모든 훅이 비활성"; fail=1; }
  fi
  if [ -f "$CDIR/settings.local.json" ] && jq -e '.disableAllHooks == true' "$CDIR/settings.local.json" >/dev/null 2>&1; then bad "settings.local.json 에서 disableAllHooks=true"; fail=1; fi
  say "== 원본 대비 드리프트"
  local drift=0
  for f in "${FILES[@]}"; do cmp -s "$SRC/$f" "$LIVE/$f" 2>/dev/null || drift=1; done
  [ $drift -eq 0 ] && ok "원본(tools/hooks-src)과 설치본 동일" || { say "  ⚠️  원본과 설치본이 다름"; note "원본 변경을 반영하려면: ! bash tools/hooks-ctl.sh install"; }
  say ""; [ $fail -eq 0 ] && ok "정상" || bad "문제 발견 — 위 '조치' 참고"
  return $fail
}

cmd_fix() {
  say "== 진단"; if cmd_check >/dev/null 2>&1; then ok "이미 정상입니다. 변경 없음."; return 0; fi
  cmd_check || true
  say ""; say "== 복구 시도 1: 원본으로 재설치(검증 포함)"
  if cmd_install; then ok "복구 완료"; log "FIX via-install"; return 0; fi
  say ""; say "== 복구 시도 2: 이전 버전으로 롤백"
  if rollback_live && smoke "$LIVE"; then ok "이전 버전으로 복구됨"; log "FIX via-rollback"; return 0; fi
  bad "자동 복구 실패. 수동 조치:"
  note "1) tools/hooks-src/ 의 오류를 고치고 ! bash tools/hooks-ctl.sh verify 로 검증"
  note "2) 통과하면 ! bash tools/hooks-ctl.sh install"
  note "3) 급하면 .claude/settings.json 의 hooks 블록을 직접 제거(= 보호 꺼짐을 인지한 채로) 후 위 1~2 진행"
  return 1
}

cmd_rollback() {
  [ -d "$PREV" ] || [ -f "$SETTINGS_PREV" ] || { bad "되돌릴 이전 버전이 없습니다"; return 1; }
  rollback_live && smoke "$LIVE" && ok "롤백 완료" || { bad "롤백 후 스모크 실패 — check 로 상태 확인"; return 1; }
}

case "${1:-}" in
  verify)   cmd_verify ;;
  install)  cmd_install ;;
  check)    cmd_check ;;
  fix)      cmd_fix ;;
  rollback) cmd_rollback ;;
  *) sed -n '2,12p' "$0"; exit 1 ;;
esac
