#!/usr/bin/env bash
# 로컬 git 훅(.githooks)과 공용 규칙(tools/git-policy.sh) 테스트
#   bash tools/tests/git-policy.sh
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
. "$ROOT/tools/git-policy.sh"

pass=0; fail=0
ok()   { pass=$((pass+1)); }
bad()  { fail=$((fail+1)); echo "  ✗ FAIL: $1"; }
# expect <0|1> <라벨> <명령...>
expect() { local want="$1" label="$2"; shift 2; "$@" >/dev/null 2>&1; local rc=$?
  if { [ "$want" = 0 ] && [ "$rc" = 0 ]; } || { [ "$want" = 1 ] && [ "$rc" != 0 ]; }; then ok; else bad "$label (기대=$want 실제=$rc)"; fi; }

T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT

echo "== ① 공용 규칙"
for s in 'feat: 스터디 생성' 'fix(backend): 락 처리' 'docs(tools)!: 규칙 변경' 'chore(ci): x' 'Merge pull request #1 from a/b' 'Revert "feat: x"' 'perf: x'; do
  expect 0 "커밋 허용 [$s]" check_commit_subject "$s"; done
for s in 'update' 'Feat: x' 'feat:x' 'feat: x.' 'wip' '' 'feat(Backend): x' 'feature: x' "feat: $(printf 'a%.0s' $(seq 1 101))"; do
  expect 1 "커밋 차단 [$s]" check_commit_subject "$s"; done
for b in feature/login bugfix/a-b release/0.1.0 hotfix/x develop main; do expect 0 "브랜치 허용 [$b]" check_branch_name "$b"; done
for b in Feature/x feat/x fix-login 'feature/' 'feature/A' wip; do expect 1 "브랜치 차단 [$b]" check_branch_name "$b"; done
expect 0 "PR feature->develop"  check_pr_base feature/x develop
expect 0 "PR bugfix->develop"   check_pr_base bugfix/x develop
expect 0 "PR release->main"     check_pr_base release/0.1.0 main
expect 0 "PR hotfix->main"      check_pr_base hotfix/x main
expect 1 "PR feature->main"     check_pr_base feature/x main
expect 1 "PR develop->main"     check_pr_base develop main
expect 1 "PR release->develop"  check_pr_base release/0.1.0 develop
expect 1 "PR feature->feature"  check_pr_base feature/x feature/y
expect 0 "시크릿 파일 없음"       bash -c ". '$ROOT/tools/git-policy.sh'; printf 'src/A.java\n.env.example\nREADME.md\n' | check_no_secret_files"
expect 1 "시크릿 파일 있음"       bash -c ". '$ROOT/tools/git-policy.sh'; printf 'src/A.java\nbackend/.env\n' | check_no_secret_files"

echo "== ② 훅 동작 (임시 저장소)"
HP="$ROOT/.githooks"
mk() { # <dir> <branch>
  git init -q -b "$2" "$1" 2>/dev/null || { git init -q "$1"; git -C "$1" symbolic-ref HEAD "refs/heads/$2"; }
  git -C "$1" config user.email t@t; git -C "$1" config user.name t
  git -C "$1" config core.hooksPath "$HP"
}
mk "$T/r" main
echo a > "$T/r/a.txt"; git -C "$T/r" add a.txt

# commit-msg 를 실제 git commit 으로
git -C "$T/r" switch -q -c feature/ok
expect 1 "git commit: 잘못된 메시지 차단"  git -C "$T/r" commit -q -m "update stuff"
expect 0 "git commit: 올바른 메시지 허용"  git -C "$T/r" commit -q -m "feat: add a"

# pre-commit: main 직접 커밋
git -C "$T/r" switch -q -c scratch-tmp 2>/dev/null; git -C "$T/r" switch -q feature/ok
echo b > "$T/r/b.txt"; git -C "$T/r" add b.txt
git -C "$T/r" switch -q main 2>/dev/null || git -C "$T/r" checkout -q -B main feature/ok
echo c > "$T/r/c.txt"; git -C "$T/r" add c.txt
expect 1 "main 에서 커밋 차단" git -C "$T/r" commit -q -m "feat: on main"
git -C "$T/r" switch -q feature/ok

# pre-commit: 브랜치 이름 위반
git -C "$T/r" switch -q -c BadName
expect 1 "잘못된 브랜치 이름에서 커밋 차단" git -C "$T/r" commit -q -m "feat: x"
git -C "$T/r" switch -q feature/ok

# pre-commit: 시크릿 파일
echo S=1 > "$T/r/.env"; git -C "$T/r" add -f .env
expect 1 "시크릿 파일 커밋 차단" git -C "$T/r" commit -q -m "feat: add env"
git -C "$T/r" rm -q --cached .env; rm -f "$T/r/.env"
echo K= > "$T/r/.env.example"; git -C "$T/r" add .env.example
expect 0 "예시 파일 커밋 허용" git -C "$T/r" commit -q -m "chore: add example"

# pre-push (stdin 직접 입력)
Z=0000000000000000000000000000000000000000
A="$(git -C "$T/r" rev-parse HEAD~1)"; B="$(git -C "$T/r" rev-parse HEAD)"
push() { printf '%s\n' "$1" | (cd "$T/r" && bash "$HP/pre-push" origin url); }
expect 1 "main 직접 push 차단"      push "refs/heads/feature/ok $B refs/heads/main $Z"
expect 1 "master 직접 push 차단"    push "refs/heads/feature/ok $B refs/heads/master $Z"
expect 1 "develop 삭제 차단"        push "(delete) $Z refs/heads/develop $B"
expect 1 "main 삭제 차단"           push "(delete) $Z refs/heads/main $B"
expect 0 "feature 삭제 허용"        push "(delete) $Z refs/heads/feature/old $B"
expect 0 "feature push 허용"        push "refs/heads/feature/ok $B refs/heads/feature/ok $Z"
expect 0 "develop fast-forward 허용" push "refs/heads/feature/ok $B refs/heads/develop $A"
expect 1 "develop force push 차단"  push "refs/heads/feature/ok $A refs/heads/develop $B"
expect 0 "develop 신규 생성 허용"    push "refs/heads/feature/ok $B refs/heads/develop $Z"

echo "== 결과: pass=$pass fail=$fail"
[ "$fail" -eq 0 ]
