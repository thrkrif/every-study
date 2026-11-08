#!/usr/bin/env bash
# PreToolUse(Read|Edit|Write|NotebookEdit|Grep|Glob)
#  - .env / 키 / 자격증명 파일 접근 차단
#  - 보안 정책 파일(.claude/hooks, settings 등) 수정 차단 (읽기는 허용)
HOOK_NAME="guard-files"
. "$(cd "$(dirname "$0")" && pwd)/_lib.sh"

read_input
tool="$(jget '.tool_name | select(type=="string")')" || exit 70
paths="$(printf '%s' "$INPUT" | jq -r '[.tool_input.file_path?, .tool_input.path?, .tool_input.notebook_path?] | map(select(type=="string")) | .[]')" || internal "경로 추출 실패"

case "$tool" in Edit|Write|NotebookEdit) writing=1 ;; *) writing=0 ;; esac

while IFS= read -r p; do
  [ -z "$p" ] && continue
  if is_secret_path "$p"; then
    deny "시크릿/자격증명 파일 접근 차단: $p" \
         "설정값이 필요하면 .env.example 에 키 이름만 적고, 실제 값은 사용자가 직접 관리하세요."
  fi
  if [ "$writing" -eq 1 ] && is_protected_path "$p"; then
    deny "보안 정책 파일 수정 차단: $p" "$PROTECTED_MSG"
  fi
done <<< "$paths"

# Glob 은 pattern, Grep 은 glob 필드가 파일명 패턴
case "$tool" in
  Glob) globs="$(printf '%s' "$INPUT" | jq -r '.tool_input.pattern? | select(type=="string")')" ;;
  Grep) globs="$(printf '%s' "$INPUT" | jq -r '.tool_input.glob? | select(type=="string")')" ;;
  *)    globs="" ;;
esac
if [ -n "$globs" ] && is_secret_glob "$globs"; then
  deny "시크릿 파일을 겨냥한 검색 패턴 차단: $globs"
fi

# Grep 이 내용(content)을 출력할 때, 검색 범위(path 또는 cwd)에 시크릿 파일이 있으면 차단
if [ "$tool" = Grep ]; then
  mode="$(printf '%s' "$INPUT" | jq -r '.tool_input.output_mode? | select(type=="string")')" || internal "output_mode 추출 실패"
  if [ "$mode" = content ]; then
    gcwd="$(printf '%s' "$INPUT" | jq -r '.cwd? | select(type=="string")')" || internal "cwd 추출 실패"
    gpath="$(printf '%s' "$INPUT" | jq -r '.tool_input.path? | select(type=="string")')" || internal "path 추출 실패"
    [ -z "$gpath" ] && gpath="${gcwd:-.}"
    case "$gpath" in /*) ;; *) gpath="${gcwd:-.}/$gpath" ;; esac   # 상대경로는 입력의 cwd 기준
    if [ -d "$gpath" ] && dir_has_secret "$gpath"; then
      deny "시크릿 파일이 있는 범위의 내용 검색 차단: $gpath" "path/glob 으로 시크릿이 없는 하위 디렉터리(예: src/)로 좁히거나, files_with_matches 모드를 쓰세요."
    fi
  fi
fi

exit 0
