#!/usr/bin/env bash
# 보안 훅 자체 테스트
#   ① 정책 테스트  : 차단돼야 할 입력은 exit 2 (hook-error 표시 없이), 허용돼야 할 입력은 exit 0
#   ② 고장 주입    : 훅이 망가진 모든 경우에 exit 2 + 'hook-error' 안내가 나오는지 (fail-closed + 복구 안내)
# 사용: HOOKS_DIR=<검증할 훅 디렉터리> bash tools/hooks-src/tests/run.sh
#       (기본값: <repo>/.claude/hooks)

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
HOOKS_DIR="${HOOKS_DIR:-$ROOT/.claude/hooks}"
TEMPLATE="$HERE/../settings.template.json"
[ -f "$HOOKS_DIR/run-guard.sh" ] || { echo "run-guard.sh 없음: $HOOKS_DIR" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq 미설치" >&2; exit 1; }

T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
export HOOKS_LOG="$T/test.log"
BASH_BIN="$(command -v bash)"
if command -v shasum >/dev/null; then SUM="shasum -a 256"; else SUM="sha256sum"; fi
pass=0; fail=0

mkrepo() { git init -q -b "$2" "$1" 2>/dev/null || { git init -q "$1"; git -C "$1" symbolic-ref HEAD "refs/heads/$2"; }; }
mkrepo "$T/main" main
mkrepo "$T/feat" feature/x

rc=0; err=""
guard() { # <훅디렉터리> <가드이름> <stdin>
  err="$(printf '%s' "$3" | "$BASH_BIN" "$1/run-guard.sh" "$2" 2>&1 >/dev/null)"; rc=$?
}
report() { # <기대> <라벨> <ok(0/1)>
  if [ "$3" -eq 1 ]; then pass=$((pass+1)); else fail=$((fail+1)); echo "  ✗ FAIL: $2 (기대=$1 실제=$rc) :: $(printf '%s' "$err" | head -n2 | tr '\n' ' ')"; fi
}
expect() { # <0|2> <라벨>   : 정책 판정 (차단 시 hook-error 가 섞이면 안 됨)
  ok=0
  if [ "$rc" = "$1" ]; then ok=1; fi
  if [ "$1" = 2 ] && [[ "$err" == *hook-error* ]]; then ok=0; fi
  report "$1" "$2" "$ok"
}
expect_broken() { # <라벨>  : 훅 고장 → 반드시 exit 2 + hook-error + 복구 명령 안내
  ok=0
  if [ "$rc" = 2 ] && [[ "$err" == *hook-error* ]] && [[ "$err" == *hooks-ctl.sh* ]]; then ok=1; fi
  report "2(hook-error)" "$1" "$ok"
}
b() { # <기대> <cwd> <명령>
  guard "$HOOKS_DIR" guard-bash "$(jq -nc --arg c "$3" --arg d "$2" '{tool_name:"Bash",cwd:$d,tool_input:{command:$c}}')"
  expect "$1" "bash[$(basename "$2")] $3"
}
f() { # <기대> <도구> <tool_input JSON>
  guard "$HOOKS_DIR" guard-files "$(jq -nc --arg t "$2" --argjson i "$3" '{tool_name:$t,tool_input:$i}')"
  expect "$1" "$2 $3"
}

echo "== ① 정책 테스트 (대상: $HOOKS_DIR)"
M="$T/main"; F="$T/feat"
# git flow
b 2 $M 'git push origin main'
b 2 $M 'git push'
b 2 $M 'git push -u origin HEAD'
b 2 $F 'git push origin feature/a:main'
b 2 $F 'git push origin HEAD:refs/heads/main'
b 2 $F 'git push --force origin feature/a'
b 2 $F 'git push -fu origin feature/a'
b 2 $F 'git push --force-with-lease origin feature/a'
b 2 $F 'git push origin +feature/a'
b 2 $F 'git push --all'
b 2 $F 'git push origin --delete develop'
b 2 $F 'git push origin :main'
b 0 $F 'git push -u origin feature/a'
b 0 $F 'git push'
b 0 $F 'git push origin --delete feature/a'
b 0 $F 'git push --follow-tags origin feature/a'
b 2 $M 'git commit -m "x"'
b 2 $M 'git merge develop'
b 2 $M 'git -C . rebase develop'
b 0 $F 'git commit -m "x"'
b 0 $M 'git status && git log --oneline'
b 0 $M 'git help commit'
b 2 $F 'gh pr create --title x'
b 2 $F 'gh pr create --base main'
b 2 $F 'gh pr create -B main --fill'
b 2 $F 'gh pr create --base=master'
b 0 $F 'gh pr create --base develop --fill'
b 2 $F 'gh pr merge 3'
b 2 $F 'git checkout -b fix-login'
b 2 $F 'git switch -c Feature/Foo'
b 0 $F 'git checkout -b feature/login'
b 0 $F 'git checkout -b release/1.0.0'
b 0 $F 'git switch -c develop'
b 0 $F 'git checkout develop'
# 파괴적 명령
b 2 $M 'rm -rf build'
b 2 $M 'rm -fr build'
b 2 $M 'rm -r build'
b 2 $M 'sudo rm -rf /'
b 2 $M 'ls && rm -rf x'
b 2 $M 'echo a | rm -rf x'
b 0 $M 'rm foo.txt'
b 2 $M 'find . -name x -delete'
b 2 $M 'find . -exec rm {} \;'
b 0 $M 'find . -name "*.java"'
b 2 $M 'git reset --hard HEAD~1'
b 0 $M 'git reset --soft HEAD~1'
b 2 $M 'git clean -fd'
b 0 $M 'git clean -n'
b 2 $M 'psql -c "DROP DATABASE x"'
# 시크릿
b 2 $M 'cat .env'
b 2 $M 'cat backend/.env.local'
b 2 $M 'source .env'
b 2 $M 'docker run --env-file=.env x'
b 2 $M 'cp .env.example .env'
b 0 $M 'cat .env.example'
b 2 $M 'printenv'
b 2 $M 'cat ~/.ssh/id_rsa'
b 2 $M 'cat server.pem'
b 0 $M './gradlew test'
b 0 $M 'npm run build'
f 2 Read '{"file_path":"/a/b/.env"}'
f 2 Read '{"file_path":"/a/b/.env.production"}'
f 0 Read '{"file_path":"/a/b/.env.example"}'
f 2 Write '{"file_path":"/a/b/server.pem","content":"x"}'
f 2 Edit '{"file_path":"/a/secrets/db.txt"}'
f 2 Glob '{"pattern":"**/.env*"}'
f 2 Grep '{"pattern":"x","glob":".env*"}'
f 0 Grep '{"pattern":"password","path":"/a/src"}'
f 0 Read '{"file_path":"/a/src/SecretService.java"}'
f 0 Read '{"file_path":"/a/docs/PRD.md"}'
# 정책 파일 자기 보호 (읽기는 허용, 수정/실행은 차단)
f 2 Edit  '{"file_path":"/p/.claude/hooks/guard-bash.sh"}'
f 2 Write '{"file_path":"/p/.claude/settings.json","content":"{}"}'
f 2 Write '{"file_path":"/p/.claude/settings.local.json","content":"{}"}'
f 2 Edit  '{"file_path":"/p/tools/hooks-ctl.sh"}'
f 0 Read  '{"file_path":"/p/.claude/hooks/guard-bash.sh"}'
f 0 Read  '{"file_path":"/p/.claude/settings.json"}'
f 0 Edit  '{"file_path":"/p/tools/hooks-src/guard-bash.sh"}'
f 0 Write '{"file_path":"/p/docs/ARCHITECTURE.md","content":"x"}'
b 2 $M 'echo x > .claude/settings.json'
b 2 $M 'cat a > .claude/hooks/guard-bash.sh'
b 2 $M 'sed -i s/a/b/ .claude/hooks/guard-bash.sh'
b 2 $M 'chmod -x .claude/hooks/run-guard.sh'
b 2 $M 'rm .claude/hooks/_lib.sh'
b 2 $M 'mv .claude/hooks .claude/hooks.off'
b 2 $M 'bash tools/hooks-ctl.sh install'
b 2 $M './tools/hooks-ctl.sh fix'
b 2 $M 'git checkout -- .claude/settings.json'
b 0 $M 'cat .claude/settings.json'
b 0 $M 'ls -l .claude/hooks'
b 0 $M 'git add .claude/settings.json'
b 0 $M 'git diff .claude/hooks'
b 0 $M 'bash tools/hooks-src/tests/run.sh'
b 0 $M 'bash -n tools/hooks-src/guard-bash.sh'

# ── 리뷰 지적 회귀 테스트 ─────────────────────────────────
mkdir -p "$T/sec/src" "$T/clean/src" "$T/sec/secrets"
echo 'PW=1' > "$T/sec/.env"; echo 'k' > "$T/sec/secrets/.env.example"; echo x > "$T/sec/src/a.txt"; echo x > "$T/clean/src/a.txt"; echo K= > "$T/clean/.env.example"
fc() { # <기대> <cwd> <도구> <tool_input JSON>
  guard "$HOOKS_DIR" guard-files "$(jq -nc --arg t "$3" --arg d "$2" --argjson i "$4" '{tool_name:$t,cwd:$d,tool_input:$i}')"
  expect "$1" "$3@$(basename "$2") $4"
}
fc 2 "$T/sec"   Grep '{"pattern":"PW","output_mode":"content"}'
fc 2 "$T/sec"   Grep '{"pattern":"PW","path":".","output_mode":"content"}'
fc 2 "$T/sec"   Grep '{"pattern":"PW","path":"'"$T"'/sec","output_mode":"content"}'
fc 0 "$T/sec"   Grep '{"pattern":"PW","path":"'"$T"'/sec/src","output_mode":"content"}'
fc 0 "$T/sec"   Grep '{"pattern":"PW","output_mode":"files_with_matches"}'
fc 0 "$T/clean" Grep '{"pattern":"x","output_mode":"content"}'
b 2 "$T/sec"   'rg password .'
b 2 "$T/sec"   'rg password'
b 2 "$T/sec"   'grep -rn PW .'
b 2 "$T/sec"   'grep -R PW'
b 0 "$T/sec"   'rg password src'
b 0 "$T/sec"   'grep -rn x src/'
b 0 "$T/sec"   'grep PW src/a.txt'
b 0 "$T/clean" 'rg x .'
b 0 "$T/clean" 'grep -rn x .'
f 2 Read '{"file_path":"/a/secrets/.env.example"}'
b 2 $M 'cat secrets/.env.example'
f 0 Read '{"file_path":"/a/.env.example"}'
b 2 $F 'git switch main && git commit -m example'
b 2 $F 'git checkout main; git commit -m x'
b 2 $F 'git checkout main && git push'
b 2 $F 'git push origin "main"'
b 2 $F "git push origin 'HEAD:main'"
b 2 $F 'git push "origin" "main"'
b 0 $F 'git switch develop && git commit -m x'
b 0 $F 'git checkout -- file.txt && git commit -m x'
b 0 $M 'git switch -c feature/y && git commit -m x'
b 2 $M 'git switch -c Bad && git commit -m x'
b 2 $M 'find .claude/hooks -type f -exec chmod -x {} +'
b 2 $M 'find .claude -name x -delete'
b 2 $M 'find .claude/hooks -type f -execdir sh -c x {} ;'
b 0 $M 'find .claude/hooks -type f'
b 0 $M 'find .claude -name "*.sh"'
b 2 $M 'mv .claude archive-claude'
b 2 $M 'mv .claude/ archive-claude'
b 2 $M 'rm -r .claude'
b 2 $M 'cp -r .claude /tmp/x/.claude'
b 0 $M 'ls .claude'
b 0 $M 'cd .claude && ls'
b 0 $M 'git add .claude'
b 2 $F 'git branch BadName'
b 2 $F 'git branch fix-login'
b 2 $F 'git branch main2 develop'
b 0 $F 'git branch feature/ok'
b 0 $F 'git branch develop'
b 0 $F 'git branch'
b 0 $F 'git branch -a'
b 0 $F 'git branch --show-current'
b 0 $F 'git branch --list "feature/*"'
b 2 $F 'git branch -D main'
b 2 $F 'git branch -d develop'
b 0 $F 'git branch -d feature/old'
b 2 $F 'git branch -m BadName'
b 0 $F 'git branch -m feature/renamed'
b 2 $F 'git branch -f main HEAD~1'
b 2 $F 'git checkout --orphan Bad'
b 2 $F 'gh pr create --base feature/other --fill'
b 2 $F 'gh pr create --base release/1.0.0 --fill'
b 2 $F 'gh pr create --base=hotfix/a'
b 0 $F 'gh pr create --base=develop --title x'

# ── 2차 리뷰 회귀: cd 이후 경로 / git switch - / 탐색 한도 ──
b 2 $M 'cd .claude && chmod -x hooks/guard-bash.sh'
b 2 $M 'cd .claude/hooks && rm guard-bash.sh'
b 2 $M 'cd .claude; cd hooks; chmod -x run-guard.sh'
b 2 $M 'cd .claude && echo x > settings.json'
b 2 "$T/sec" 'cd secrets && cat .env.example'
b 0 $M 'cd .claude && ls hooks'
b 0 $M 'cd tools && ls'
b 0 $M 'cd docs && rm old.md'
b 2 $F 'git switch main && git switch -c feature/test && git switch - && git commit -m example'
b 0 $F 'git switch main && git switch - && git commit -m x'
b 0 $F 'git switch develop && git switch - && git commit -m x'
mkdir -p "$T/big/src" "$T/big/deep"
( cd "$T/big" && d=deep; for i in 1 2 3 4 5 6 7 8 9 10 11 12; do d="$d/d$i"; done; mkdir -p "$d"; echo K > "$d/.env" )
b 2 "$T/big" 'rg x .'
b 2 "$T/big" 'grep -rn x .'
b 0 "$T/big" 'rg x src'
# 탐색 한도 초과 → 확인 불가 = 차단
mkdir -p "$T/many"; for i in 1 2 3 4 5 6; do : > "$T/many/f$i"; done
MANYJ="$(jq -nc --arg d "$T/many" '{tool_name:"Bash",cwd:$d,tool_input:{command:"rg x ."}}')"
guard "$HOOKS_DIR" guard-bash "$MANYJ"; expect 0 "탐색 한도 미만이면 허용"
DIR_SCAN_MAX=3 guard "$HOOKS_DIR" guard-bash "$MANYJ"; expect 2 "탐색 한도 초과(확인 불가) 시 차단"
# ── /dev/null · fd 복제 리다이렉션 오탐 ──
b 0 $M 'ls -A .claude 2>/dev/null'
b 0 $M 'cat .claude/settings.json >/dev/null 2>&1'
b 0 $M 'ls .claude 2>&1 | head'
b 0 $M 'cat .claude/settings.json &>/dev/null'
b 0 $M 'ls .claude > /dev/null'
b 2 $M 'echo x > .claude/settings.json 2>/dev/null'
b 2 $M 'echo x >> .claude/hooks/_lib.sh 2>&1'
b 2 $M 'echo x 2>&1 > .claude/settings.json'
b 2 $M 'cat a > .claude/hooks/x.sh; ls 2>/dev/null'
b 2 $M 'echo x > /dev/null; echo y > .claude/settings.json'

# ── 문장부호가 붙은 인자 / ':' ',' 로 결합된 인자 ──
b 0 $M 'echo see .env.example: keys only'
b 0 $M 'echo "files: .env.example, README.md"'
b 0 $M 'cat .env.example,'
b 2 $M 'docker run -v .env:/app/.env x'
b 2 $M 'scp host:.env .'
b 2 $M 'cat a,.env,b'
b 0 $M 'echo a,.env,b'   # echo 는 출력만 하는 글자 자리
b 2 $M 'cat .env:'
b 2 $M 'rsync -a .env.example:server.pem /tmp'
b 2 $M 'docker run -v ./.claude/hooks:/x -w /x y rm a'
b 0 $M 'docker run -v ./src:/app/src x'

# ── 로컬 git 훅 우회 차단 ──
b 2 $F 'git commit --no-verify -m "feat: x"'
b 2 $F 'git commit -n -m "feat: x"'
b 2 $F 'git commit -nm "feat: x"'
b 2 $F 'git commit -an -m "feat: x"'
b 2 $F 'git push --no-verify'
b 2 $F 'git merge --no-verify develop'
b 2 $F 'git config core.hooksPath /dev/null'
b 2 $F 'git config --local core.hookspath x'
b 2 $F 'git -c core.hooksPath=/dev/null commit -m "feat: x"'
b 0 $F 'git config user.name x'
b 0 $F 'git config --get core.hooksPath'
b 0 $F 'git commit -m "feat: x"'
b 0 $F 'git commit -m "feat: use -n flag in docs"'
b 0 $F 'git commit -am "feat: x"'
b 0 $F 'git push -n origin feature/x'

# ── 인자 역할 구분: 글자 자리는 허용, 경로 자리는 계속 차단 ──
# 허용: 메시지·제목·본문·검색어·출력 문구
b 0 $F 'git commit -m "docs: secrets: gitleaks 설명"'
b 0 $F 'git commit -m "docs: update .env.example and credentials notes"'
b 0 $F 'git commit -am "fix: secrets 처리"'
b 0 $F 'git commit --message="feat: .env 안내"'
b 0 $F 'git tag -a v1 -m "release secrets: none"'
b 0 $M 'echo secrets'
b 0 $M 'echo "secrets: credentials .env id_rsa"'
b 0 $M 'printf "%s\n" "secrets: x"'
b 0 $M 'grep secrets README.md'
b 0 $M 'grep -n credentials docs/PRD.md'
b 0 $M 'grep -e secrets -e .env README.md'
b 0 $M 'grep -A 3 secrets README.md'
b 0 $M 'rg -g "*.md" secrets docs'
b 0 $F 'gh pr create --base develop --title "docs: secrets: x" --body "secrets: gitleaks"'
b 0 $F 'gh pr create --base develop --title x --body-file - <<'"'"'EOF'"'"'
- secrets: gitleaks
- .env.example: 키 이름
- cat credentials
EOF'
b 0 $F 'git commit -q -F - <<'"'"'EOF'"'"'
feat: x

secrets: 설명, .env 와 credentials 언급
EOF'
b 0 $F 'git commit -m "$(cat <<'"'"'EOF'"'"'
fix: y

secrets: 본문에 단어만 있음
EOF
)"'
b 0 $M 'cat <<'"'"'EOF'"'"' > notes.txt
secrets: 메모, .env 언급
EOF'
# 차단: 경로로 쓰이는 경우
b 2 $M 'cat secrets/db.txt'
b 2 $M 'cat secrets'
b 2 $M 'cat credentials'
b 2 $M 'cat .env'
b 2 $M 'mv secrets /tmp/x'
b 2 $M 'cp -r secrets /tmp/x'
b 2 $M 'tar czf a.tgz secrets'
b 2 $M 'cat server.pem'
b 2 $F 'git commit -F .env'
b 2 $F 'git commit -m "ok" -F credentials'
b 2 $F 'gh pr create --base develop --title x --body-file .env'
b 2 $M 'grep -f .env README.md'
b 2 $M 'grep password .env'
b 2 $M 'grep -e password .env'
b 2 $M 'grep -A 3 password .env'
b 2 $M 'echo hi > .env'
b 2 $M 'echo hi >.env'
b 2 $M 'echo hi>.env'
b 2 $M 'echo "x" >> secrets/a.txt'
b 2 $M 'echo $(cat .env)'
b 2 $M 'echo `cat .env`'
b 2 $M 'echo "$(cat .env)"'
b 2 $M 'git commit -m "$(cat .env)"'
b 2 $M 'echo <(cat .env)'
b 2 $M 'echo ok; cat .env'
b 2 $M 'echo ok && cat .env'
b 2 $M 'echo ok | cat .env'
# heredoc: 해석기로 실행되는 본문과 파이프로 이어지는 본문은 검사 대상
b 2 $M 'bash <<'"'"'EOF'"'"'
cat .env
EOF'
b 2 $M 'sh <<EOF
rm -rf x
EOF'
b 2 $M 'cat <<'"'"'EOF'"'"' | bash
rm -rf x
EOF'
b 2 $M 'cat <<'"'"'EOF'"'"' > .claude/settings.json
{}
EOF'
b 2 $M 'git apply <<'"'"'EOF'"'"'
+++ b/.claude/settings.json
EOF'
b 2 $F 'git commit -m "$(cat <<'"'"'EOF'"'"'
x
$(cat .env)
EOF
)"'
# heredoc 위장: 따옴표·주석 안의 '<<' 는 heredoc 이 아니므로 다음 줄의 실제 명령을 숨기면 안 된다
b 2 $M 'cat x "<<EOF"
cat .env
EOF'
b 2 $M "cat x '<<EOF'
cat .env
EOF"
b 2 $M 'cat x # <<EOF
cat .env
EOF'
b 2 $M 'cat x `echo <<EOF`
cat .env
EOF'
b 2 $M 'cat <<<"x" <<EOF
cat .env
EOF'
b 2 $M 'cat <<'"'"'EOF'"'"'
ok
EOF
cat .env'
b 0 $M 'cat <<'"'"'EOF'"'"'
secrets: 본문
EOF'
# 제목·메시지 안의 괄호/세미콜론/파이프가 heredoc 소비 명령 판정을 흐리지 않아야 한다
b 0 $F 'git push -u origin feature/x 2>&1 | tail -2 && gh pr create --base develop --title "fix(tools): secrets: x" --body-file - <<'"'"'EOF'"'"'
- secrets: gitleaks
- cat credentials
EOF'
b 0 $F 'gh pr create --base develop --title "feat(a); b | c" --body-file - <<'"'"'EOF'"'"'
secrets: 본문
EOF'
b 0 $F 'git commit -q -F - <<'"'"'EOF'"'"'
fix(tools): secrets: x
EOF'
b 2 $M 'echo "a(b" && bash <<'"'"'EOF'"'"'
rm -rf x
EOF'
b 2 $M 'echo "x; y" | bash <<'"'"'EOF'"'"'
cat .env
EOF'
b 2 $M 'gh pr create --title "fix(x): y" && sh <<'"'"'EOF'"'"'
cat .env
EOF'
# 따옴표 안의 구분자가 있어도 /dev/null 리다이렉션 처리는 유지
b 0 $F 'git commit -m "docs: a" 2>&1'
b 0 $M 'echo "secrets: x" 2>/dev/null'
b 2 $M 'echo "secrets: x"; cat .env 2>/dev/null'

echo "== ② 고장 주입 테스트 (훅이 망가져도 반드시 차단 + 복구 안내)"
H="$T/h"
fresh() { rm -rf "$H"; cp -R "$HOOKS_DIR" "$H"; }
remanifest() { (cd "$H" && $SUM _lib.sh guard-bash.sh guard-files.sh run-guard.sh > MANIFEST.sha256); }
OK_BASH="$(jq -nc --arg d "$T/main" '{tool_name:"Bash",cwd:$d,tool_input:{command:"ls"}}')"

fresh; rm "$H/guard-bash.sh";                      guard "$H" guard-bash "$OK_BASH"; expect_broken "가드 스크립트 삭제"
fresh; rm "$H/_lib.sh";                            guard "$H" guard-bash "$OK_BASH"; expect_broken "_lib.sh 삭제"
fresh; rm "$H/MANIFEST.sha256";                    guard "$H" guard-bash "$OK_BASH"; expect_broken "MANIFEST 삭제"
fresh; echo '# tamper' >> "$H/guard-bash.sh";      guard "$H" guard-bash "$OK_BASH"; expect_broken "파일 변조(무결성 불일치)"
fresh; echo 'if then' >> "$H/guard-bash.sh"; remanifest
                                                   guard "$H" guard-bash "$OK_BASH"; expect_broken "문법 오류"
fresh; sed -i.bak '2i\
exit 1' "$H/guard-bash.sh"; rm -f "$H/guard-bash.sh.bak"; remanifest
                                                   guard "$H" guard-bash "$OK_BASH"; expect_broken "가드 비정상 종료(exit 1)"
fresh; sed -i.bak '2i\
exit 0' "$H/guard-bash.sh"; rm -f "$H/guard-bash.sh.bak"; remanifest
                                                   guard "$H" guard-bash "$(jq -nc --arg d "$T/main" '{tool_name:"Bash",cwd:$d,tool_input:{command:"rm -rf x"}}')"
                                                   # exit 0 으로 바꿔치기한 가드는 정책 위반을 못 잡음 → 무결성(MANIFEST 재생성 공격)은 설치본 보호 + 사람 설치로만 방어됨을 기록
                                                   [ "$rc" = 0 ] && echo "  ℹ 참고(점수 제외): MANIFEST 까지 재생성하면 변조가 통과함 → 그래서 .claude/hooks 수정은 에이전트에게 차단됨(위 ① 보호 테스트)"
fresh;                                             guard "$H" guard-bash 'not json';  expect_broken "입력이 JSON 아님"
fresh;                                             guard "$H" guard-bash '';          expect_broken "stdin 비어있음"
fresh;                                             guard "$H" guard-bash '{"tool_name":"Bash","tool_input":{}}'; expect_broken "필수 필드(command) 없음"
fresh;                                             guard "$H" guard-files '{"tool_input":{}}';                   expect_broken "필수 필드(tool_name) 없음"
fresh;                                             guard "$H" guard-evil "$OK_BASH";  expect_broken "알 수 없는 가드 이름"
fresh; chmod -x "$H"/*.sh;                         guard "$H" guard-bash "$OK_BASH";  expect 0 "실행권한 없어도 정상 동작(bash 로 호출)"
fresh; chmod -x "$H"/*.sh;                         guard "$H" guard-bash "$(jq -nc --arg d "$T/main" '{tool_name:"Bash",cwd:$d,tool_input:{command:"rm -rf x"}}')"; expect 2 "실행권한 없어도 정책 차단 유지"
# jq 없음: jq 만 빠진 최소 PATH
mkdir -p "$T/fakebin"
for t in cat dirname date shasum sha256sum tr head cut sed mkdir bash git; do p="$(command -v $t 2>/dev/null)" && ln -sf "$p" "$T/fakebin/$t"; done
fresh; err="$(printf '%s' "$OK_BASH" | PATH="$T/fakebin" "$BASH_BIN" "$H/run-guard.sh" guard-bash 2>&1 >/dev/null)"; rc=$?
                                                   expect_broken "jq 미설치"

echo "== ③ 설정 템플릿 정적 검사"
jq -e '.hooks.PreToolUse | length == 2 and all(.[].hooks[]; .onFailure == "block" and (.command | contains("run-guard.sh")))' "$TEMPLATE" >/dev/null 2>&1; rc=$?
report "onFailure=block 전부" "settings 템플릿" "$([ $rc -eq 0 ] && echo 1 || echo 0)"

echo "== 결과: pass=$pass fail=$fail"
[ "$fail" -eq 0 ]
