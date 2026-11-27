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
#   6. (docs/PRD.md 와 docs/API.md 가 있을 때) API 엔드포인트 표마다 "화면" 열이 있고, 값이 PRD 5장의
#      화면 번호·"공통"·"없음: <이유>" 중 하나인가 (화면 추적)
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
    -h|--help) sed -n '2,21p' "$0"; exit 0 ;;
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

# ── 6. 화면 추적: API 의 쓰임새마다 그것을 쓰는 PRD 화면이 있는가 ──
# 하위 문서(API)가 상위 문서(PRD)에 없는 기능을 조용히 더하는 것을 막는다 (PLAN T-073).
# 엔드포인트 표("| 메서드 경로 |" 로 시작하는 머리 행)마다 "화면" 열이 있어야 하고, 값은 다음 중 하나다.
#   - PRD "## 5. 화면 목록" 에 있는 번호들 (쉼표로 구분. 예: 2, 10)
#     같은 화면에서 쓰임새가 둘 이상이거나 구분해 두고 싶으면 번호(쓰임새)로 적는다. 예: 2(온보딩), 10(수정)
#   - 공통 (모든 화면이 쓰는 인증·메타 등)
#   - 없음: <이유> (화면이 쓰지 않는 API. 이유를 반드시 적는다)
PRD_DOC="$ROOT/docs/PRD.md"; API_DOC="$ROOT/docs/API.md"
if [ -f "$PRD_DOC" ] && [ -f "$API_DOC" ]; then
  screens="$(LC_ALL=C awk '/^## 5\. 화면 목록/ { f = 1; next } /^## / { f = 0 }
                  f && /^[0-9]+\. / { n = $0; sub(/\..*/, "", n); printf "%s ", n }' "$PRD_DOC")"
  if [ -z "$screens" ]; then
    report "PRD.md" "화면 추적: '## 5. 화면 목록' 에서 번호 목록(1. 2. …)을 찾지 못함" \
      "API.md 의 화면 열을 검사하려면 PRD 5장의 화면 번호가 필요합니다."
  else
    while IFS=$'\t' read -r line msg; do
      [ -n "$line" ] && report "API.md" "화면 추적 ${line}행: $msg" \
        "PRD 5장의 화면 번호(쉼표 구분), '공통', '없음: <이유>' 중 하나를 적으세요. PRD 에 없는 쓰임새라면 PRD 를 먼저 고치거나 PLAN 7장에 X 로 등록하세요."
    done < <(LC_ALL=C awk -v screens="$screens" '
      BEGIN { n = split(screens, s, " "); for (i = 1; i <= n; i++) ok[s[i]] = 1 }
      function trim(x) { gsub(/^[ \t]+|[ \t]+$/, "", x); return x }
      {
        row = $0; gsub(/\\\|/, "\001", row)          # 셀 안의 \| 는 구분자가 아니다
      }
      index($0, "| 메서드 경로 |") == 1 {
        intable = 1; col = 0
        m = split(row, h, "|")
        for (i = 2; i < m; i++) if (trim(h[i]) == "화면") col = i
        if (!col) printf "%d\t엔드포인트 표에 \"화면\" 열이 없음\n", NR
        next
      }
      intable && $0 ~ /^[|][ :|-]+[|][ \t]*$/ { next }   # 구분 행
      intable && substr($0, 1, 1) == "|" {
        if (!col) next
        m = split(row, c, "|"); v = trim(c[col])
        if (v == "공통") next
        if (v ~ /^없음: *[^ ]/) next
        if (v == "" ) { printf "%d\t화면 칸이 비었음\n", NR; next }
        k = split(v, nums, /, */)
        for (i = 1; i <= k; i++) {
          x = trim(nums[i])
          if (x !~ /^[0-9]+([(][^()]+[)])?$/) { printf "%d\t화면 항목 형식이 틀림: \"%s\" (번호 또는 번호(쓰임새))\n", NR, x; continue }
          sub(/[(].*$/, "", x)
          if (!(x in ok)) printf "%d\tPRD 5장에 없는 화면: \"%s\"\n", NR, x
        }
        next
      }
      { intable = 0 }
    ' "$API_DOC")
  fi
fi

# ── 7. 쓰임새 추적: API 의 (엔드포인트, 화면) 쌍마다 그것을 화면으로 만드는 [프론트] 작업이 있는가 ──
# PLAN 의 "선행" 은 순서만 정하고, 백엔드가 만든 기능을 어느 프론트 작업이 받는지는 정하지 않는다.
# 그래서 한 엔드포인트의 두 번째 쓰임새(PUT /me/profile 의 수정)가 어느 작업에도 없이 지나갔다 (PLAN T-074).
# [프론트] 작업의 "  - 쓰임새:" 줄에 `메서드 경로` 화면항목 을 " · " 로 이어 적는다.
#   예: - 쓰임새: `PUT /me/profile` 10(수정) · `GET /users/{id}` 10, 14
# 경로의 /api/v1 과 ?쿼리 는 비교할 때 뺀다. 화면 항목은 API 화면 열에 적힌 그대로 써야 한다.
# '공통' · '없음: …' 행은 짝지을 필요가 없다.
PLAN_DOC="$ROOT/docs/PLAN.md"
if [ -f "$API_DOC" ] && [ -f "$PLAN_DOC" ]; then
  while IFS=$'\t' read -r doc line msg; do
    [ -n "$doc" ] && report "$doc" "쓰임새 추적 ${line}행: $msg" \
      "API 화면 열의 (엔드포인트, 화면 항목)마다 그것을 만드는 [프론트] 작업의 '쓰임새' 줄에 적으세요. 맡을 작업이 없으면 PLAN 에 작업을 추가하세요."
  done < <(LC_ALL=C awk '
    function trim(x) { gsub(/^[ \t]+|[ \t]+$/, "", x); return x }
    function norm(x) { sub(/\/api\/v1/, "", x); sub(/[?].*$/, "", x); return trim(x) }
    FNR == 1 { file++ }
    file == 1 {
      row = $0; gsub(/\\\|/, "\001", row)
      if (index($0, "| 메서드 경로 |") == 1) {
        intable = 1; col = 0
        m = split(row, h, "|")
        for (i = 2; i < m; i++) if (trim(h[i]) == "화면") col = i
        next
      }
      if (intable && $0 ~ /^[|][ :|-]+[|][ \t]*$/) next
      if (intable && substr($0, 1, 1) == "|") {
        if (!col) next
        m = split(row, c, "|")
        if (!match(c[2], /`[^`]+`/)) next
        k = norm(substr(c[2], RSTART + 1, RLENGTH - 2)); known[k] = 1
        v = trim(c[col])
        if (v == "" || v == "공통" || v ~ /^없음:/) next
        n = split(v, it, /, */)
        for (i = 1; i <= n; i++) { q = k SUBSEP trim(it[i]); if (!(q in need)) { need[q] = FNR; order[++cnt] = q } }
        next
      }
      intable = 0; next
    }
    /^- \[.\] T-/ { fe = index($0, "[프론트]") > 0; next }
    /^  - 쓰임새:/ {
      if (!fe) { printf "PLAN.md\t%d\t쓰임새 줄은 [프론트] 작업에만 둔다\n", FNR; next }
      rest = $0; sub(/^  - 쓰임새:[ ]*/, "", rest)
      n = split(rest, parts, / · /)
      for (i = 1; i <= n; i++) {
        p = trim(parts[i])
        if (!match(p, /^`[^`]+`/)) { printf "PLAN.md\t%d\t형식이 틀림: \"%s\" (`메서드 경로` 화면항목)\n", FNR, p; continue }
        k = norm(substr(p, 2, RLENGTH - 2)); items = trim(substr(p, RLENGTH + 1))
        if (!(k in known)) { printf "PLAN.md\t%d\tAPI 표에 없는 엔드포인트: \"%s\"\n", FNR, k; continue }
        if (items == "") { printf "PLAN.md\t%d\t화면 항목이 없음: \"%s\"\n", FNR, k; continue }
        m = split(items, it, /, */)
        for (j = 1; j <= m; j++) {
          q = k SUBSEP trim(it[j])
          if (q in need) got[q] = 1
          else printf "PLAN.md\t%d\tAPI 화면 열에 없는 쓰임새: \"%s %s\"\n", FNR, k, trim(it[j])
        }
      }
    }
    END {
      for (i = 1; i <= cnt; i++) if (!(order[i] in got)) {
        split(order[i], z, SUBSEP)
        printf "API.md\t%d\t맡은 [프론트] 작업이 없는 쓰임새: \"%s %s\"\n", need[order[i]], z[1], z[2]
      }
    }
  ' "$API_DOC" "$PLAN_DOC")
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
