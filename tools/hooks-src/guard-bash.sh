#!/usr/bin/env bash
# PreToolUse(Bash) — git flow 보호 / 파괴적 명령 / 시크릿 접근 / 정책 파일 보호
#   exit 2 = 정책 위반(차단), exit 70 = 내부 오류(런처가 훅 고장으로 처리)
# 한계: 셸 문자열을 단순 토큰화하므로 변수 치환·eval·bash -c·스크립트 안의 명령 같은
#       우회까지는 막지 못한다. 실제 보호는 GitHub Branch Protection 과 병행해야 한다.
HOOK_NAME="guard-bash"
. "$(cd "$(dirname "$0")" && pwd)/_lib.sh"

PROTECTED_RE='^(main|master)$'                # 직접 push/commit 금지
DELETE_PROTECTED_RE='^(main|master|develop)$' # 원격 삭제 금지
BRANCH_NAME_RE='^((feature|bugfix|release|hotfix)/[a-z0-9][a-z0-9._-]*|develop)$'

read_input
cmd="$(jget '.tool_input.command | select(type=="string")')" || exit 70
cwd="$(printf '%s' "$INPUT" | jq -r '.cwd? | select(type=="string")')" || internal "cwd 추출 실패"
[ -z "$cmd" ] && exit 0
[ -n "$cwd" ] && cd "$cwd" 2>/dev/null

sim_br=""; prev_br=""; cdir=""   # cdir: 앞선 cd 로 바뀐 작업 디렉터리(상대 누적)   # 앞선 세그먼트의 git switch/checkout 으로 바뀔 브랜치 (예: git switch main && git commit)
current_branch() { git -C "${1:-.}" symbolic-ref --short -q HEAD 2>/dev/null; }
# 토큰에서 따옴표/괄호/리다이렉션 문자 제거. (문자 집합을 변수로 둔다: 패턴 안의 '}' 가 ${...} 를 닫아버리는 것을 방지)
JUNK=$'"\'`(){}<>'
clean_tok() { local t="${1//[$JUNK]/}"; printf '%s' "$t"; }

# ── 인자 역할 구분 ────────────────────────────────────────
# 같은 단어라도 경로(cat secrets/x)일 때와 글자(커밋 메시지, 검색어, 출력 문구)일 때가 다르다.
# 글자 자리의 값만 TEXT 로 바꾼 사본을 만들어 아래 경로 검사에 쓴다.
#   · 치환 대상은 확실한 자리뿐: echo/printf 인자, git commit/tag/merge/stash/notes 의 -m 값,
#     gh pr|issue|release 의 --title/--body/--notes 값, grep 계열의 검색 패턴(-e 값),
#     cat/tee/git commit|tag/gh 로 흘러가는 heredoc 본문
#   · 파일 경로를 받는 자리(-F, --body-file, -f, 리다이렉션 대상, 일반 인자)는 절대 치환하지 않는다
#   · 명령 치환 $(..), 백틱, 리다이렉션, 서브셸, 파이프로 이어지는 heredoc 은 치환하지 않는다 (실행될 수 있으므로)
MT_Q=$'"\'\\'
MT_NEUTRAL_RE='^([0-9]*>&[0-9-]+|&?[0-9]*>>?/dev/null)$'
MT_REDIR_RE='^[0-9&]*>>?$'
HD_OPEN_RE=$'<<(-?)[[:space:]]*([\'"]?)([A-Za-z_][A-Za-z0-9_]*)([\'"]?)([[:space:];&|()<>]|$)'
MT_HEREDOC_RE=$'^<<-?[\'"]?[A-Za-z_][A-Za-z0-9_]*[\'"]?$|^<<-?$'   #<<'EOF', <<-EOF, << (heredoc 표시는 실행 효과가 없음)
mt_plain() { local t="${1//[$MT_Q]/}"; printf '%s' "$t"; }

# heredoc 소비 명령이 "데이터로만 쓰는" 경우인가
hd_consumer_ok() {
  local -a ws; read -ra ws <<< "$1"; local k=0 c sub
  while [ "$k" -lt "${#ws[@]}" ]; do
    case "${ws[$k]}" in sudo|command|env|nohup|time|exec|[A-Za-z_]*=*) k=$((k+1)) ;; *) break ;; esac
  done
  c="${ws[$k]:-}"; c="${c##*/}"; sub="${ws[$((k+1))]:-}"
  case "$c" in
    cat|tee) return 0 ;;
    git) case "$sub" in commit|tag) return 0 ;; esac ;;
    gh)  case "$sub" in pr|issue|release) return 0 ;; esac ;;
  esac
  return 1
}

# '<<' 앞부분($1)이 따옴표/주석/백틱 안이 아닌가. 아니면 heredoc 이 아니므로 본문을 숨기면 안 된다.
#   (예: cat x "<<EOF" 다음 줄의 실제 명령을 본문으로 착각해 숨기는 우회 방지)
#   "$(cat <<'EOF'" 처럼 명령 치환 안의 heredoc 은 정상으로 본다.
hd_unquoted() {
  local s="$1" stack="C" i=0 n=${#1} ch top prev=" "
  HD_START=0   # heredoc 을 소비하는 "마지막 명령"이 시작되는 위치 (따옴표 밖의 ; & | ( 기준)
  while [ "$i" -lt "$n" ]; do
    ch="${s:$i:1}"; top="${stack: -1}"
    case "$top" in
      S) [ "$ch" = "'" ] && stack="${stack%?}" ;;
      D) case "$ch" in
           '\') i=$((i+1)) ;;
           '"') stack="${stack%?}" ;;
           '`') return 1 ;;
           '$') if [ "${s:$((i+1)):1}" = "(" ]; then stack+="C"; i=$((i+1)); HD_START=$((i+1)); fi ;;
         esac ;;
      *) case "$ch" in
           "'") stack+="S" ;;
           '"') stack+="D" ;;
           '\') i=$((i+1)) ;;
           '`') return 1 ;;
           '#') [[ "$prev" == [[:space:]] ]] && return 1 ;;
           '(') stack+="C"; HD_START=$((i+1)) ;;
           ')') [ "${#stack}" -gt 1 ] && stack="${stack%?}" ;;
           ';'|'|') HD_START=$((i+1)) ;;
           '&') if [ "$prev" != ">" ] && [ "${s:$((i+1)):1}" != ">" ]; then HD_START=$((i+1)); fi ;;   # 2>&1, &> 는 구분자가 아님
         esac ;;
    esac
    prev="$ch"; i=$((i+1))
  done
  top="${stack: -1}"
  [ "$top" = "C" ]
}

strip_heredocs() {
  local line t tag="" dash="" pre full_pre post body="" mask=0 in_hd=0 out="" TAB=$'\t' hd_tmp hd_cnt hd_bs
  while IFS= read -r line || [ -n "$line" ]; do
    if [ "$in_hd" -eq 1 ]; then
      t="$line"
      [ "$dash" = "-" ] && t="${t#"${t%%[!$TAB]*}"}"
      t="${t%"${t##*[![:space:]]}"}"
      if [ "$t" = "$tag" ]; then
        in_hd=0
        if [ "$mask" -eq 1 ] && ! [[ "$body" == *'$('* || "$body" == *'`'* ]]; then out+="$line"$'\n'; else out+="$body$line"$'\n'; fi
      else
        body+="$line"$'\n'
      fi
      continue
    fi
    out+="$line"$'\n'
    # heredoc 으로 인정하는 형식만 처리한다. 애매하면 heredoc 모드에 들어가지 않아
    # 이후 줄이 일반 명령으로 검사된다 (= 안전한 쪽으로만 틀림).
    #   · 종료자(구분 단어) 전체가 [A-Za-z0-9_]+ 이고 그 뒤가 공백/메타문자/줄끝일 것 (EOF-X 같은 형식은 제외)
    #   · 여는 따옴표와 닫는 따옴표가 같을 것
    #   · 한 줄에 '<<' 가 정확히 하나일 것, '<<<' 는 제외
    #   · '<<' 앞에 홀수 개의 백슬래시가 없을 것 (\<<EOF 는 heredoc 이 아님)
    if [[ "$line" != *"<<<"* && "$line" =~ $HD_OPEN_RE && "${BASH_REMATCH[2]}" == "${BASH_REMATCH[4]}" ]]; then
      dash="${BASH_REMATCH[1]}"; tag="${BASH_REMATCH[3]}"
      full_pre="${line%%<<*}"
      post="${line#*<<}"
      hd_tmp="${line//<</}"; hd_cnt=$(( (${#line} - ${#hd_tmp}) / 2 ))
      hd_bs="${full_pre##*[!\\]}"
      if [ "$hd_cnt" -ne 1 ] || [ $(( ${#hd_bs} % 2 )) -eq 1 ]; then continue; fi
      mask=0
      if hd_unquoted "$full_pre"; then
        pre="${full_pre:$HD_START}"
        if hd_consumer_ok "$pre" && [[ "$post" != *"|"* ]]; then mask=1; fi
      fi
      in_hd=1; body=""
    fi
  done <<< "$1"
  [ "$in_hd" -eq 1 ] && out+="$body"    # 끝나지 않은 heredoc 은 그대로 둔다
  printf '%s' "$out"
  return 0
}

mt_flush_tok() { if [ "$have" -eq 1 ]; then mt_toks+=("$buf"); mt_uns+=("$unsafe"); fi; buf=""; unsafe=0; have=0; }
mt_flush_cmd() { out+="$(mask_one_cmd)"; mt_toks=(); mt_uns=(); }

mask_git() {
  local j=$((idx0+1)) p sub="" nxt=0
  while [ "$j" -lt "$n" ]; do
    p="$(mt_plain "${mt_toks[$j]}")"
    case "$p" in
      -C|-c|--git-dir|--work-tree|--namespace) j=$((j+2)) ;;
      -*) j=$((j+1)) ;;
      *) sub="$p"; break ;;
    esac
  done
  case "$sub" in commit|tag|merge|stash|notes) ;; *) return 0 ;; esac
  for ((j=j+1; j<n; j++)); do
    p="$(mt_plain "${mt_toks[$j]}")"
    if [ "$nxt" -eq 1 ]; then mt_toks[$j]=TEXT; nxt=0; continue; fi
    case "$p" in
      --message) nxt=1 ;;
      --message=*) mt_toks[$j]="--message=TEXT" ;;
      --*) ;;
      -m|-[a-zA-Z]*m) nxt=1 ;;   # -m 단독과 -am 같은 묶음 (패턴의 [a-zA-Z] 는 문자 1개를 요구하므로 -m 은 따로)
    esac
  done
}

mask_gh() {
  local j p g1="" nxt=0
  for ((j=idx0+1; j<n; j++)); do
    p="$(mt_plain "${mt_toks[$j]}")"
    if [[ "$p" != -* ]]; then g1="$p"; break; fi
  done
  case "$g1" in pr|issue|release) ;; *) return 0 ;; esac
  for ((j=j+1; j<n; j++)); do
    p="$(mt_plain "${mt_toks[$j]}")"
    if [ "$nxt" -eq 1 ]; then mt_toks[$j]=TEXT; nxt=0; continue; fi
    case "$p" in
      --title|-t|--body|-b|--notes) nxt=1 ;;
      --title=*) mt_toks[$j]="--title=TEXT" ;;
      --body=*)  mt_toks[$j]="--body=TEXT" ;;
      --notes=*) mt_toks[$j]="--notes=TEXT" ;;
    esac
  done
}

mask_grep() {
  local j p nxt=0 skip=0 haspat=0 donepat=0
  for ((j=idx0+1; j<n; j++)); do
    p="$(mt_plain "${mt_toks[$j]}")"
    case "$p" in -e|--regexp|-f|--file|--regexp=*|--file=*) haspat=1 ;; --*) ;; -[a-zA-Z]*[ef]) haspat=1 ;; esac
  done
  for ((j=idx0+1; j<n; j++)); do
    p="$(mt_plain "${mt_toks[$j]}")"
    if [ "$nxt" -eq 1 ]; then mt_toks[$j]=TEXT; nxt=0; continue; fi
    if [ "$skip" -eq 1 ]; then skip=0; continue; fi
    case "$p" in
      -e|--regexp) nxt=1 ;;
      --regexp=*) mt_toks[$j]="--regexp=TEXT" ;;
      -f|--file) skip=1 ;;                                  # 패턴 "파일" 경로: 치환하지 않음
      -A|-B|-C|-m|-g|-t|-T|-d|-D|-j|-M|--include|--exclude|--exclude-dir|--glob|--type|--max-count|--context|--after-context|--before-context|--max-depth|--max-columns) skip=1 ;;
      --*) ;;
      -[a-zA-Z]*e) nxt=1 ;;
      -*) ;;
      *) if [ "$haspat" -eq 0 ] && [ "$donepat" -eq 0 ]; then mt_toks[$j]=TEXT; donepat=1; fi ;;
    esac
  done
}

mask_one_cmd() {
  local n=${#mt_toks[@]} j p c0="" idx0=-1 cmdunsafe=0
  [ "$n" -eq 0 ] && return 0
  for ((j=0; j<n; j++)); do
    [ "${mt_uns[$j]}" = 1 ] || continue
    p="${mt_toks[$j]}"
    if [[ "$p" =~ $MT_NEUTRAL_RE ]]; then continue; fi                                         # 2>&1, >/dev/null, &>/dev/null
    if [[ "$p" =~ $MT_HEREDOC_RE ]]; then continue; fi                                         # <<'EOF'
    if [[ "$p" =~ $MT_REDIR_RE && "${mt_toks[$((j+1))]:-}" = "/dev/null" ]]; then continue; fi  # > /dev/null
    cmdunsafe=1
  done
  if [ "$cmdunsafe" -eq 0 ]; then
    for ((j=0; j<n; j++)); do
      p="$(mt_plain "${mt_toks[$j]}")"
      case "$p" in
        sudo|command|env|nohup|time|exec|[A-Za-z_]*=*) ;;
        *) idx0=$j; c0="${p##*/}"; break ;;
      esac
    done
    if [ "$idx0" -ge 0 ]; then
      case "$c0" in
        echo|printf) for ((j=idx0+1; j<n; j++)); do mt_toks[$j]=TEXT; done ;;
        git) mask_git ;;
        gh)  mask_gh ;;
        grep|egrep|fgrep|rg|ag|ack) mask_grep ;;
      esac
    fi
  fi
  printf '%s' "${mt_toks[*]}"
}

mask_text_args() {
  local LC_ALL=C
  local s="$1" n=${#1} i=0 ch buf="" inS=0 inD=0 unsafe=0 have=0 out=""
  mt_toks=(); mt_uns=()
  while [ "$i" -lt "$n" ]; do
    ch="${s:$i:1}"
    if [ "$inS" -eq 1 ]; then buf+="$ch"; [ "$ch" = "'" ] && inS=0; i=$((i+1)); continue; fi
    if [ "$inD" -eq 1 ]; then
      case "$ch" in
        '\') buf+="${s:$i:2}"; i=$((i+2)); continue ;;
        '"') inD=0 ;;
        '`') unsafe=1 ;;
        '$') [ "${s:$((i+1)):1}" = "(" ] && unsafe=1 ;;
      esac
      buf+="$ch"; i=$((i+1)); continue
    fi
    case "$ch" in
      "'") inS=1; have=1; buf+="$ch" ;;
      '"') inD=1; have=1; buf+="$ch" ;;
      '\') buf+="${s:$i:2}"; have=1; i=$((i+1)) ;;
      '`'|'<'|'>'|'('|')') unsafe=1; have=1; buf+="$ch" ;;
      '$') [ "${s:$((i+1)):1}" = "(" ] && unsafe=1; have=1; buf+="$ch" ;;
      '&') if [[ "$buf" == *'>' || "${s:$((i+1)):1}" == '>' ]]; then unsafe=1; have=1; buf+="$ch"; else mt_flush_tok; mt_flush_cmd; out+="$ch"; fi ;;
      ' '|$'\t') mt_flush_tok ;;
      ';'|'|'|$'\n') mt_flush_tok; mt_flush_cmd; out+="$ch" ;;
      *) have=1; buf+="$ch" ;;
    esac
    i=$((i+1))
  done
  mt_flush_tok; mt_flush_cmd
  printf '%s' "$out"
  return 0
}

# ── 0. 문자열 전체 검사 ───────────────────────────────────
shopt -s nocasematch
if [[ "$cmd" =~ drop[[:space:]]+(database|schema|table) ]]; then
  deny "DROP DATABASE/SCHEMA/TABLE 차단" "DB 파괴 작업은 사용자가 직접 실행하세요."
fi
shopt -u nocasematch

# 이후 검사는 "글자 자리"를 치환한 사본으로 한다 (DROP 검사는 위에서 원문으로 끝냄)
cmd_scan="$(strip_heredocs "$cmd")" || internal "heredoc 처리 실패"
cmd_scan="$(mask_text_args "$cmd_scan")" || internal "인자 역할 처리 실패"

# ── 1. 세그먼트(; & | 개행 기준)별 검사 ───────────────────
while IFS= read -r seg; do
  read -ra w <<< "$seg"
  [ "${#w[@]}" -eq 0 ] && continue
  for k in "${!w[@]}"; do w[$k]="$(clean_tok "${w[$k]}")"; done   # git push origin "main" 같은 따옴표 인자 대응

  # 선행 sudo / env / VAR=val / command 등 제거
  i=0
  while [ "$i" -lt "${#w[@]}" ]; do
    case "${w[$i]}" in
      sudo|command|env|nohup|time|exec) i=$((i+1)) ;;
      [A-Za-z_]*=*) i=$((i+1)) ;;
      *) break ;;
    esac
  done
  [ "$i" -ge "${#w[@]}" ] && continue
  c0="${w[$i]##*/}"
  args=("${w[@]:$((i+1))}")

  [ "$c0" = "printenv" ] && deny "printenv 차단 (환경변수에 시크릿이 있을 수 있음)"

  # ── 시크릿 / 정책 파일 접근 (모든 명령 공통) ───────────
  prot=""
  for a in "${w[@]:$i}"; do
    t="$(clean_tok "$a")"; v="${t#*=}"
    if [ -n "$cdir" ]; then   # cd 이후의 상대경로는 cdir 기준으로도 검사
      case "$t" in /*|-*) ;; *) tc="$cdir/$t"; vc="$cdir/$v"
        is_secret_path "$tc" && deny "시크릿/자격증명 파일 접근 차단: $a (cd $cdir 이후)"
        is_protected_path "$tc" && prot="$a" ;; esac
    fi
    # 토큰 전체, '=' 뒤, 그리고 ':' ',' 로 쪼갠 조각을 모두 검사한다.
    #   (문장부호가 붙은 예시 파일명 오탐 방지 + docker -v 호스트:컨테이너 같은 결합 인자 대응)
    while [[ "$t" == *[:,] ]]; do t="${t%?}"; done   # 끝에 붙은 문장부호 제거
    while [[ "$v" == *[:,] ]]; do v="${v%?}"; done
    IFS=':,' read -ra pieces <<< "$t"
    IFS=':,' read -ra vpieces <<< "$v"
    for x in "$t" "$v" "${pieces[@]}" "${vpieces[@]}"; do
      if is_secret_path "$x"; then
        deny "시크릿/자격증명 파일 접근 차단: $a" "설정값이 필요하면 .env.example 에 키 이름만 적으세요."
      fi
      if is_protected_path "$x"; then prot="$a"; fi
    done
  done
  if [ -n "$prot" ]; then
    if [[ "$seg" == *">"* ]] || ! ro_cmd "$c0" "${args[0]:-}" "$seg"; then
      deny "보안 정책 파일에 대한 수정/실행 차단: $prot" "$PROTECTED_MSG"
    fi
  fi

  # ── 재귀 검색 도구가 시크릿 파일을 포함한 디렉터리를 훑는 것 차단 ──
  case "$c0" in
    grep|egrep|fgrep|rg|ag|ack)
      recur=0; [ "$c0" = rg ] || [ "$c0" = ag ] || [ "$c0" = ack ] && recur=1
      dirs=()
      for a in "${args[@]}"; do
        if [[ "$a" =~ ^-[a-zA-Z]*[rR] && "$a" != --* ]] || [[ "$a" == --recursive || "$a" == --dereference-recursive ]]; then recur=1; fi
        [[ "$a" != -* ]] && [ -d "$a" ] && dirs+=("$a")
      done
      if [ "$recur" -eq 1 ]; then
        [ "${#dirs[@]}" -eq 0 ] && dirs=(.)
        for d in "${dirs[@]}"; do
          if dir_has_secret "$d"; then
            deny "시크릿 파일이 있는 디렉터리를 재귀 검색할 수 없음: $d" "검색 범위를 시크릿이 없는 하위 디렉터리(예: src/)로 좁히세요."
          fi
        done
      fi ;;
  esac

  if [ "$c0" = cd ]; then
    cd_t="${args[0]:-}"
    case "$cd_t" in
      "")  cdir="" ;;
      /*)  cdir="$cd_t" ;;
      -|~*) cdir="$cd_t" ;;
      *)   cdir="${cdir:+$cdir/}$cd_t" ;;
    esac
  fi

  case "$c0" in
    # ── 파괴적 명령 ────────────────────────────────────────
    rm)
      for a in "${args[@]}"; do
        if [[ "$a" =~ ^-[a-zA-Z]*[rR] || "$a" == "--recursive" ]]; then
          deny "재귀 삭제(rm -r/-rf) 차단" "삭제 대상을 사용자에게 알리고 직접 실행하게 하세요. 파일 단건은 'rm <file>' 만 가능합니다."
        fi
      done ;;
    find)
      for a in "${args[@]}"; do [ "$a" = "-delete" ] && deny "find -delete 차단"; done
      [[ "$seg" =~ -exec(dir)?[[:space:]]+rm ]] && deny "find -exec rm 차단" ;;
    shred|mkfs*) deny "디스크/파일 파괴 명령($c0) 차단" ;;
    dd) [[ "$seg" =~ of= ]] && deny "dd of= 차단" ;;

    # ── git ───────────────────────────────────────────────
    git)
      gdir="."; j=0
      while [ "$j" -lt "${#args[@]}" ] && [[ "${args[$j]}" == -* ]]; do
        case "${args[$j]}" in
          -C) gdir="${args[$((j+1))]:-.}"; j=$((j+2)) ;;
          -c|--git-dir|--work-tree|--namespace) j=$((j+2)) ;;
          *) j=$((j+1)) ;;
        esac
      done
      sub="${args[$j]:-}"
      rest=("${args[@]:$((j+1))}")
      br="${sim_br:-$(current_branch "$gdir")}"

      # 로컬 git 훅 우회 차단: --no-verify, commit -n, core.hooksPath 변경
      for a in "${args[@]}"; do
        case "$a" in
          --no-verify) deny "git 훅 우회(--no-verify) 차단" "훅이 막는 이유를 해결하세요. 정말 필요하면 사용자가 직접 실행합니다." ;;
          *hooksPath=*|*hookspath=*) deny "core.hooksPath 변경 차단" "로컬 git 훅 설정은 tools/setup-git-hooks.sh 로 사람이 관리합니다." ;;
        esac
      done
      if [ "$sub" = commit ]; then
        for a in "${rest[@]}"; do
          if [[ "$a" =~ ^-[a-zA-Z]*n && "$a" != --* ]]; then deny "git commit -n(--no-verify) 차단" "훅을 건너뛰지 말고 원인을 해결하세요."; fi
          # 메시지 본문(-m 이후)에 든 '-n' 같은 단어는 옵션이 아니므로 여기서 중단
          if [[ "$a" =~ ^-[a-zA-Z]*[mFC]$ || "$a" == --message* || "$a" == --file* ]]; then break; fi
        done
      fi
      if [ "$sub" = config ]; then
        cfg_read=0
        for a in "${rest[@]}"; do case "$a" in --get|--get-all|--get-regexp|--list|-l|--show-origin) cfg_read=1 ;; esac; done
        [ "$cfg_read" -eq 1 ] && rest=()
        for a in "${rest[@]}"; do
          shopt -s nocasematch; [[ "$a" == *hookspath* ]] && { shopt -u nocasematch; deny "core.hooksPath 변경 차단" "로컬 git 훅 설정은 tools/setup-git-hooks.sh 로 사람이 관리합니다."; }; shopt -u nocasematch
        done
      fi

      case "$sub" in
        push)
          refs=(); delflag=0; remote_seen=0
          for a in "${rest[@]}"; do
            if [[ "$a" == -* ]]; then
              case "$a" in
                --force|--force-with-lease*|--force-if-includes|--mirror|--all)
                  deny "force push / --all / --mirror 차단" ;;
              esac
              # 짧은 옵션 묶음(-f, -fu, -uf …) 안의 f  (case 의 글롭이 아니라 정규식으로)
              if [[ "$a" =~ ^-[a-zA-Z]*f ]]; then deny "force push(-f) 차단"; fi
              if [ "$a" = "--delete" ] || [ "$a" = "-d" ]; then delflag=1; fi
            elif [ "$remote_seen" -eq 0 ]; then
              remote_seen=1
            else
              refs+=("$a")
            fi
          done
          for r in "${refs[@]}"; do
            [[ "$r" == +* ]] && deny "force push(+refspec) 차단: $r"
            dst="${r##*:}"; dst="${dst#refs/heads/}"
            if [[ "$dst" =~ $PROTECTED_RE ]]; then
              deny "main 브랜치 직접 push 차단: $r" "feature/* 브랜치에서 작업 → develop 대상 PR 로 진행하세요 (git flow)."
            fi
            if [[ ( "$delflag" -eq 1 || "$r" == :* ) && "$dst" =~ $DELETE_PROTECTED_RE ]]; then
              deny "보호 브랜치 원격 삭제 차단: $r"
            fi
          done
          if { [ "${#refs[@]}" -eq 0 ] || [ "${refs[0]}" = "HEAD" ]; } && [[ "$br" =~ $PROTECTED_RE ]]; then
            deny "현재 브랜치($br)에서 push 차단" "feature/* 브랜치를 만들어 작업하세요."
          fi ;;
        commit|merge|rebase|cherry-pick|revert|am)
          if [[ "$br" =~ $PROTECTED_RE ]]; then
            deny "$br 브랜치에서 git $sub 차단" "git flow: main 은 release/hotfix 병합(PR)으로만 변경됩니다. feature/* 브랜치로 이동하세요."
          fi ;;
        reset)
          for a in "${rest[@]}"; do [ "$a" = "--hard" ] && deny "git reset --hard 차단"; done ;;
        clean)
          for a in "${rest[@]}"; do
            if [[ "$a" =~ ^-[a-zA-Z]*f ]] || [ "$a" = "--force" ]; then deny "git clean -f 차단"; fi
          done ;;
        checkout|switch)
          target=""; skip=0
          for k in "${!rest[@]}"; do
            a="${rest[$k]}"
            if [ "$skip" -eq 1 ]; then skip=0; continue; fi
            case "$a" in
              -b|-B|-c|-C|--orphan)
                name="${rest[$((k+1))]:-}"
                if ! [[ "$name" =~ $BRANCH_NAME_RE ]]; then
                  deny "브랜치 이름 규칙 위반: '$name'" "feature/<이름>, bugfix/<이름>, release/<버전>, hotfix/<이름> 형식 (소문자·숫자·.-_)."
                fi
                target="$name"; skip=1 ;;
              --) break ;;
              -) [ -z "$target" ] && target="-" ;;
              -*) ;;
              *) [ -z "$target" ] && target="$a" ;;
            esac
          done
          if [ "$target" = "-" ]; then
            target="${prev_br:-$(git -C "$gdir" rev-parse --abbrev-ref '@{-1}' 2>/dev/null)}"
            [ -z "$target" ] && target=main   # 이전 브랜치를 알 수 없으면 보수적으로 main 으로 간주
          fi
          # 이후 세그먼트의 판정에 반영 (checkout <파일> 과 구분하려고 switch 이거나 보호 브랜치일 때만)
          if [ -n "$target" ]; then
            if [ "$sub" = switch ] || [[ "$target" =~ $PROTECTED_RE ]] || [[ "$target" =~ $BRANCH_NAME_RE ]]; then prev_br="$br"; sim_br="$target"; fi
          fi ;;
        branch)
          mode=""; pos=()
          for a in "${rest[@]}"; do
            case "$a" in
              -d|-D|--delete) mode=del ;;
              -m|-M|--move|-c|-C|--copy) mode=mv ;;
              -l|--list|-a|--all|-r|--remotes|-v|-vv|--verbose|--show-current|--contains|--merged|--no-merged|-u|--set-upstream-to*|--unset-upstream|--edit-description) [ -z "$mode" ] && mode=ro ;;
              -*) ;;
              *) pos+=("$a") ;;
            esac
          done
          case "$mode" in
            del) for a in "${pos[@]}"; do
                   [[ "$a" =~ $DELETE_PROTECTED_RE ]] && deny "보호 브랜치 삭제 차단: $a"
                 done ;;
            mv)  if [ "${#pos[@]}" -gt 0 ]; then
                   last="${pos[$((${#pos[@]}-1))]}"
                   [[ "$last" =~ $BRANCH_NAME_RE ]] || deny "브랜치 이름 규칙 위반: '$last'" "feature/<이름>, bugfix/<이름>, release/<버전>, hotfix/<이름> 형식."
                 fi ;;
            ro)  ;;
            *)   if [ "${#pos[@]}" -gt 0 ] && ! [[ "${pos[0]}" =~ $BRANCH_NAME_RE ]]; then
                   deny "브랜치 이름 규칙 위반: '${pos[0]}'" "feature/<이름>, bugfix/<이름>, release/<버전>, hotfix/<이름> 형식 (소문자·숫자·.-_)."
                 fi ;;
          esac ;;
      esac ;;

    # ── GitHub CLI ─────────────────────────────────────────
    gh)
      g1="${args[0]:-}"; g2="${args[1]:-}"
      if [ "$g1" = "pr" ] && [ "$g2" = "create" ]; then
        base=""
        for k in "${!args[@]}"; do
          case "${args[$k]}" in
            --base|-B) base="${args[$((k+1))]:-}" ;;
            --base=*)  base="${args[$k]#--base=}" ;;
          esac
        done
        [ -z "$base" ] && deny "gh pr create 에 --base 가 없음 (기본값이 main 이라 차단)" "--base develop 을 명시하세요."
        [ "$base" != "develop" ] && deny "develop 이외 대상 PR 생성 차단: $base" "git flow: 에이전트의 PR 은 항상 --base develop. main 대상(release/hotfix)은 사용자가 직접 만듭니다."
      fi
      [ "$g1" = "pr" ] && [ "$g2" = "merge" ] && deny "gh pr merge 차단" "병합은 사용자가 직접 수행합니다."
      [ "$g1" = "repo" ] && [ "$g2" = "delete" ] && deny "gh repo delete 차단" ;;
  esac
done < <(printf '%s\n' "$cmd_scan" | sed -E 's#[0-9]*>&[0-9-]+##g; s#&?[0-9]*>{1,2}[[:space:]]*/dev/null##g' | tr ';&|' '\n\n\n')   # /dev/null·fd 복제(2>&1) 리다이렉션은 쓰기가 아니므로 제거 후 분할. 끝의 개행 필수: 없으면 마지막 줄을 읽고도 루프가 돌지 않음

exit 0
