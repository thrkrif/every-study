#!/usr/bin/env bash
# 문서 머리말·링크 검사. 규칙은 docs/README.md 3~4장.
#   bash tools/docs-check.sh                  # 오류가 있으면 1 (CI)
#   bash tools/docs-check.sh --warn           # 같은 검사를 하되 항상 0 (로컬 pre-commit)
#   bash tools/docs-check.sh --root <저장소>  # 다른 저장소 루트를 검사 (테스트용)
#   bash tools/docs-check.sh --base <git 참조> # covers 검사도 한다 (5번). 예: --base origin/develop
#
# 검사 항목 (docs/**/*.md 전부)
#   1. 맨 위에 닫힌 YAML 머리말이 있고 status 가 draft|confirmed|superseded 인가
#   2. version 이 있으면 숫자와 점으로 된 형식인가
#   3. based_on 의 각 항목이 "경로@버전" 형식이고, 대상 문서가 있고, 그 버전과 같은가 (다르면 "오래됨")
#   4. 상대 경로 링크의 대상이 실제로 있는가 (코드 블록·인라인 코드·http 링크는 제외)
#   5. (--base 가 있을 때) <참조>에서 갈라진 뒤 바뀐 파일(커밋 + 스테이징)이 어떤 문서의 covers 에
#      해당하는데 그 문서는 바뀌지 않았는가.
#      문서를 고칠 내용이 없으면 커밋 메시지에 "Docs-Unchanged: <사유>" 줄을 넣어 건너뛴다.
#      covers 패턴: ** 는 / 를 포함한 아무 문자열, * 는 / 를 뺀 문자열, ? 는 / 를 뺀 한 글자.
#
# 한계: 문서 내용이 바뀌었는데 version 을 올리지 않은 경우는 알 수 없다 (사람이 판단).
#       covers 검사는 문서 파일이 바뀌었는지만 본다. 맞게 고쳤는지는 리뷰가 본다.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WARN=0
BASE=""
while [ $# -gt 0 ]; do
  case "$1" in
    --warn) WARN=1 ;;
    --root) [ -n "${2:-}" ] && [ -d "$2" ] || { echo "--root 에 존재하는 디렉토리가 필요합니다" >&2; exit 2; }
            ROOT="$(cd "$2" && pwd)"; shift ;;
    --base) [ -n "${2:-}" ] || { echo "--base 에 git 참조가 필요합니다 (예: origin/develop)" >&2; exit 2; }
            BASE="$2"; shift ;;
    -h|--help) sed -n '2,19p' "$0"; exit 0 ;;
    *) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
  esac
  shift
done

[ -d "$ROOT/docs" ] || exit 0   # 문서가 없는 저장소는 검사할 것이 없다

MERGE_BASE=""
if [ -n "$BASE" ]; then
  MERGE_BASE="$(git -C "$ROOT" merge-base "$BASE" HEAD 2>/dev/null)" \
    || { echo "--base 참조를 찾을 수 없음: $BASE (git 저장소가 아니거나 참조가 없습니다)" >&2; exit 2; }
fi

if [ "$WARN" = 1 ]; then MARK='⚠'; else MARK='✖'; fi
ERR=0
report() { # <파일> <사유> [<조치>]
  ERR=$((ERR + 1))
  printf '%s %s: %s\n' "$MARK" "$1" "$2" >&2
  [ -n "${3:-}" ] && printf '  → %s\n' "$3" >&2
  return 0
}

# 머리말 본문을 출력한다. 반환: 0 정상, 2 머리말 없음, 3 닫히지 않음
fm_block() {
  awk 'BEGIN { st = 0 }
       NR == 1 { if ($0 != "---") { st = 2; exit } next }
       /^---[[:space:]]*$/ { st = 1; exit }
       { print }
       END { exit (st == 1 ? 0 : (st == 2 ? 2 : 3)) }' "$1"
}

strip_quotes() {
  local v="$1"
  v="${v#\"}"; v="${v%\"}"; v="${v#\'}"; v="${v%\'}"
  printf '%s' "$v"
}

# <머리말 본문> <키> → 한 줄 값 (주석·따옴표 제거)
fm_scalar() {
  local raw
  raw="$(printf '%s\n' "$1" | awk -v k="$2" '
    $0 ~ "^" k ":" { sub("^" k ":[[:space:]]*", ""); sub("[[:space:]]+#.*$", ""); sub("[[:space:]]+$", ""); print; exit }')"
  strip_quotes "$raw"
}

# <머리말 본문> <키> → "- 항목" 목록의 항목들 (한 줄에 하나)
fm_list() {
  printf '%s\n' "$1" | awk -v k="$2" '
    $0 ~ "^" k ":" { on = 1; next }
    on && /^[^[:space:]#-]/ { on = 0 }
    on && /^[[:space:]]*-[[:space:]]/ {
      sub(/^[[:space:]]*-[[:space:]]+/, ""); sub(/[[:space:]]+#.*$/, ""); sub(/[[:space:]]+$/, ""); print
    }' | while IFS= read -r item; do strip_quotes "$item"; printf '\n'; done
}

check_links() { # <절대경로> <표시 이름>
  local file="$1" name="$2" dir link path
  dir="$(dirname "$file")"
  while IFS= read -r link; do
    case "$link" in
      http://*|https://*|mailto:*|'#'*|/*) continue ;;
    esac
    path="${link%%#*}"; path="${path%%\?*}"
    [ -n "$path" ] || continue
    [ -e "$dir/$path" ] || report "$name" "끊어진 링크: $link" "대상 파일이 있는지, 상대 경로가 맞는지 확인하세요."
  done < <(awk '/^```/ { fence = !fence; next } !fence { print }' "$file" \
             | sed -E 's/`[^`]*`//g' \
             | grep -oE '\]\([^)[:space:]]+\)' \
             | sed -E 's/^\]\(//; s/\)$//')
}

# covers 패턴을 정규식으로 바꾼다 (** → .*, * → [^/]*, ? → [^/], 나머지 특수문자는 글자 그대로)
glob_to_regex() {
  local glob="$1" re="" i c
  for ((i = 0; i < ${#glob}; i++)); do
    c="${glob:i:1}"
    case "$c" in
      '*') if [ "${glob:i+1:1}" = '*' ]; then re="$re.*"; i=$((i + 1)); else re="$re[^/]*"; fi ;;
      '?') re="$re[^/]" ;;
      '.'|'+'|'('|')'|'['|']'|'{'|'}'|'^'|'$'|'|'|'\\') re="$re\\$c" ;;
      *) re="$re$c" ;;
    esac
  done
  printf '^%s$' "$re"
}

COVERS=""   # "<문서 경로><탭><covers 패턴>" 을 한 줄에 하나씩 (5번 검사용)
count=0
while IFS= read -r f; do
  [ -n "$f" ] || continue
  count=$((count + 1))
  name="${f#"$ROOT"/}"

  block="$(fm_block "$f")"; rc=$?
  if [ "$rc" = 2 ]; then
    report "$name" "머리말이 없음 (첫 줄이 --- 가 아님)" "docs/README.md 4장의 머리말을 맨 위에 추가하세요."
    check_links "$f" "$name"; continue
  elif [ "$rc" != 0 ]; then
    report "$name" "머리말이 닫히지 않음 (두 번째 --- 가 없음)"
    check_links "$f" "$name"; continue
  fi

  status="$(fm_scalar "$block" status)"
  case "$status" in
    draft|confirmed|superseded) ;;
    '') report "$name" "status 가 없음" "draft | confirmed | superseded 중 하나를 쓰세요." ;;
    *)  report "$name" "status 값이 잘못됨: $status" "draft | confirmed | superseded 중 하나를 쓰세요." ;;
  esac

  version="$(fm_scalar "$block" version)"
  if [ -n "$version" ] && ! [[ "$version" =~ ^[0-9]+(\.[0-9]+)*$ ]]; then
    report "$name" "version 형식이 잘못됨: $version" '숫자와 점만 쓰세요 (예: "0.3").'
  fi

  if [ -n "$(fm_scalar "$block" based_on)" ]; then
    report "$name" "based_on 은 한 줄에 하나씩 \"- 경로@버전\" 목록으로 써야 함"
  fi
  while IFS= read -r ref; do
    [ -n "$ref" ] || continue
    if ! [[ "$ref" == *@* ]]; then
      report "$name" "based_on 항목에 @버전이 없음: $ref" "예: docs/PRD.md@0.3"; continue
    fi
    rpath="${ref%@*}"; rver="${ref##*@}"
    target="$ROOT/$rpath"
    if [ ! -f "$target" ]; then
      report "$name" "based_on 대상이 없음: $rpath"; continue
    fi
    tblock="$(fm_block "$target")" || { report "$name" "based_on 대상의 머리말을 읽을 수 없음: $rpath"; continue; }
    tver="$(fm_scalar "$tblock" version)"
    if [ -z "$tver" ]; then
      report "$name" "based_on 대상에 version 이 없어 고정할 수 없음: $rpath"
    elif [ "$tver" != "$rver" ]; then
      report "$name" "오래됨: $rpath 는 $tver 인데 이 문서는 @$rver 를 근거로 함" \
        "$rpath 의 변경을 확인해 이 문서를 고친 뒤 based_on 의 버전을 @$tver 로 올리세요."
    fi
  done < <(fm_list "$block" based_on)

  while IFS= read -r pattern; do
    [ -n "$pattern" ] && COVERS="${COVERS}${name}"$'\t'"${pattern}"$'\n'
  done < <(fm_list "$block" covers)

  check_links "$f" "$name"
done < <(find "$ROOT/docs" -type f -name '*.md' | sort)

# ── 5. covers: 코드가 바뀌었는데 그 코드를 설명하는 문서가 그대로인가 ──
if [ -n "$MERGE_BASE" ] && [ -n "$COVERS" ]; then
  # 갈라진 지점부터 인덱스까지. 커밋 전(pre-commit)에는 스테이징된 변경도 포함된다.
  changed="$(git -C "$ROOT" diff --cached --name-only --no-renames "$MERGE_BASE")"
  skip_reason="$(git -C "$ROOT" log --format=%B "$MERGE_BASE..HEAD" \
    | sed -n 's/^Docs-Unchanged:[[:space:]]*//p' | sed 's/[[:space:]]*$//' | grep -v '^$' | head -n 1)"

  while IFS= read -r doc; do
    [ -n "$doc" ] || continue
    printf '%s\n' "$changed" | grep -qxF -- "$doc" && continue   # 문서도 함께 바뀜

    hits=""; nhits=0
    while IFS=$'\t' read -r d pattern; do
      [ "$d" = "$doc" ] || continue
      re="$(glob_to_regex "$pattern")"
      while IFS= read -r path; do
        [ -n "$path" ] || continue
        if [[ "$path" =~ $re ]] && ! printf '%s\n' "$hits" | grep -qxF -- "$path"; then
          hits="${hits}${path}"$'\n'; nhits=$((nhits + 1))
        fi
      done <<< "$changed"
    done <<< "$COVERS"
    [ "$nhits" -gt 0 ] || continue

    shown="$(printf '%s' "$hits" | head -n 3 | paste -sd ' ' -)"
    [ "$nhits" -gt 3 ] && shown="$shown 외 $((nhits - 3))개"
    if [ -n "$skip_reason" ]; then
      printf 'ℹ %s: covers 검사를 건너뜀 (Docs-Unchanged: %s)\n' "$doc" "$skip_reason" >&2
    else
      report "$doc" "covers 에 해당하는 코드가 바뀌었는데 이 문서는 바뀌지 않음: $shown" \
        "문서가 코드와 같은지 확인해 고치세요. 고칠 내용이 없으면 커밋 메시지에 'Docs-Unchanged: <사유>' 줄을 넣으세요."
    fi
  done < <(printf '%s' "$COVERS" | cut -f1 | sort -u)
fi

if [ "$ERR" -gt 0 ]; then
  if [ "$WARN" = 1 ]; then
    echo "docs-check: 경고 ${ERR}건 (문서 ${count}개). CI 에서는 오류로 처리됩니다." >&2
    exit 0
  fi
  echo "docs-check: 오류 ${ERR}건 (문서 ${count}개)" >&2
  exit 1
fi
echo "docs-check: 문서 ${count}개 통과"
