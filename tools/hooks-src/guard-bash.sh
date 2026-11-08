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

# ── 0. 문자열 전체 검사 ───────────────────────────────────
shopt -s nocasematch
if [[ "$cmd" =~ drop[[:space:]]+(database|schema|table) ]]; then
  deny "DROP DATABASE/SCHEMA/TABLE 차단" "DB 파괴 작업은 사용자가 직접 실행하세요."
fi
shopt -u nocasematch

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
    if is_secret_path "$t" || is_secret_path "$v"; then
      deny "시크릿/자격증명 파일 접근 차단: $a" "설정값이 필요하면 .env.example 에 키 이름만 적으세요."
    fi
    if is_protected_path "$t" || is_protected_path "$v"; then prot="$a"; fi
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
done < <(printf '%s\n' "$cmd" | tr ';&|' '\n\n\n')   # 끝의 개행 필수: 없으면 마지막 줄을 읽고도 루프가 돌지 않음

exit 0
