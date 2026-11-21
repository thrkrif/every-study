#!/usr/bin/env bash
# git flow / 커밋 규칙의 단일 출처. 로컬 git 훅(.githooks)과 CI(.github/workflows/ci.yml)가 함께 source 한다.
# 규칙을 바꿀 때는 이 파일만 고치고 tools/tests/git-policy.sh 로 확인한다.
#
# 모든 check_* 함수는 위반 시 사유를 stderr 에 출력하고 1 을 반환한다 (통과 0).

GP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 시크릿 파일명 규칙은 Claude 훅과 같은 정의를 쓴다 (is_secret_path).
# shellcheck source=hooks-src/_lib.sh
. "$GP_DIR/hooks-src/_lib.sh"

# ── 규칙 ────────────────────────────────────────────────
CC_TYPES='feat|fix|docs|refactor|test|chore|build|ci|perf|revert'
CC_RE="^(${CC_TYPES})(\\([a-z0-9._/-]+\\))?!?: .+"
CC_MAX_SUBJECT=100
BRANCH_RE='^((feature|bugfix|release|hotfix)/[a-z0-9][a-z0-9._-]*|develop|main)$'
PROTECTED_BRANCH_RE='^(main|master)$'
REMOTE_PROTECTED_RE='^(main|master|develop)$'   # 삭제·비-fast-forward 금지

gp_err() { printf '✖ %s\n' "$1" >&2; [ -n "${2:-}" ] && printf '  → %s\n' "$2" >&2; return 1; }

# 커밋 제목(첫 줄) 검사
check_commit_subject() {
  local s="$1"
  case "$s" in
    "Merge "*|'Revert "'*) return 0 ;;   # git 이 만드는 병합/되돌리기 메시지
  esac
  if ! [[ "$s" =~ $CC_RE ]]; then
    gp_err "커밋 메시지가 Conventional Commits 형식이 아님: $s" \
           "<type>(<scope>): <제목>   type: ${CC_TYPES//|/ }   예) feat(backend): 스터디 생성 API 추가"
    return 1
  fi
  if [[ "$s" == *. ]]; then
    gp_err "커밋 제목은 마침표로 끝내지 않는다: $s"; return 1
  fi
  if [ "${#s}" -gt "$CC_MAX_SUBJECT" ]; then
    gp_err "커밋 제목이 ${CC_MAX_SUBJECT}자를 넘음 (${#s}자)"; return 1
  fi
  return 0
}

# 브랜치 이름 검사
check_branch_name() {
  local b="$1"
  [[ "$b" =~ $BRANCH_RE ]] && return 0
  gp_err "브랜치 이름 규칙 위반: $b" \
         "feature/<이름>, bugfix/<이름>, release/<버전>, hotfix/<이름> 또는 develop (소문자·숫자·.-_)"
}

# PR 의 head/base 조합 검사
#   develop <- feature/*, bugfix/*      main <- release/*, hotfix/*
check_pr_base() {
  local head="$1" base="$2"
  case "$base" in
    develop)
      [[ "$head" =~ ^(feature|bugfix)/ ]] && return 0
      gp_err "develop 대상 PR 은 feature/* 또는 bugfix/* 브랜치에서만 만든다: $head -> $base" ;;
    main)
      [[ "$head" =~ ^(release|hotfix)/ ]] && return 0
      gp_err "main 대상 PR 은 release/* 또는 hotfix/* 브랜치에서만 만든다: $head -> $base" ;;
    *)
      gp_err "허용되지 않는 PR 대상 브랜치: $base" "develop 또는 main(release/hotfix 만)" ;;
  esac
}

# 파일 목록(한 줄에 하나, stdin)에서 시크릿 파일명을 찾는다
check_no_secret_files() {
  local f bad=0
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    if is_secret_path "$f"; then gp_err "시크릿으로 보이는 파일이 포함됨: $f" "커밋하지 말고 gitignore 에 추가하세요. 키 이름만 환경변수 예시 파일에 적습니다."; bad=1; fi
  done
  return "$bad"
}

# main 대상 PR 에 MVP 용 에이전트 훅(guard-bash-mvp)이 남아 있으면 막는다.
#   MVP 동안만 에이전트의 PR 병합을 허용하는 훅이라, 배포 PR 이 되기 전에 운영용으로 돌려놔야 한다.
#   인자: <설정 파일 경로> <PR 대상 브랜치>
check_no_mvp_hook() {
  local settings="$1" base="$2"
  [ "$base" = main ] || return 0
  [ -f "$settings" ] || return 0
  if grep -q 'guard-bash-mvp' "$settings"; then
    gp_err "MVP 용 에이전트 훅(guard-bash-mvp)이 설치된 채로 main 대상 PR 을 만들 수 없음" \
           "사람이 '! bash tools/hooks-ctl.sh install' 로 운영용 훅으로 되돌리고 .claude/ 변경을 커밋하세요."
    return 1
  fi
  return 0
}
