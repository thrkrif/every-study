#!/usr/bin/env bash
# tools/docs-check.sh 테스트
#   bash tools/tests/docs-check.sh
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CHECK="${DOCS_CHECK:-$ROOT/tools/docs-check.sh}"

pass=0; fail=0
ok()  { pass=$((pass+1)); }
bad() { fail=$((fail+1)); echo "  ✗ FAIL: $1"; }
# expect <0|1> <라벨> <인자...>   (인자는 docs-check.sh 로 전달)
expect() { local want="$1" label="$2"; shift 2; bash "$CHECK" "$@" >/dev/null 2>&1; local rc=$?
  if [ "$rc" = "$want" ]; then ok; else bad "$label (기대=$want 실제=$rc)"; fi; }
# expect_msg <라벨> <패턴> <인자...>   (stderr 에 패턴이 있어야 함)
expect_msg() { local label="$1" pat="$2"; shift 2; local out; out="$(bash "$CHECK" "$@" 2>&1 >/dev/null)"
  if printf '%s' "$out" | grep -q -- "$pat"; then ok; else bad "$label (메시지에 '$pat' 없음: $out)"; fi; }

T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT

# 정상 문서 세트를 만든다: <디렉토리>
good() {
  mkdir -p "$1/docs/adr"
  printf -- '---\nstatus: confirmed\nversion: "0.3"\n---\n\n# PRD\n' > "$1/docs/PRD.md"
  printf -- '---\nstatus: draft\nversion: "0.1"\nbased_on:\n  - docs/PRD.md@0.3\n---\n\n# ARCH\n[PRD](PRD.md)\n' > "$1/docs/ARCH.md"
  printf -- '---\nstatus: draft\nbased_on:\n  - docs/PRD.md@0.3\n  - docs/ARCH.md@0.1\n---\n\n# ADR\n[arch](../ARCH.md) [dir](./)\n' > "$1/docs/adr/0001.md"
}
fresh() { local d; d="$(mktemp -d "$T/r.XXXXXX")"; good "$d"; printf '%s' "$d"; }

echo "== ① 정상"
d="$(fresh)"; expect 0 "정상 문서 세트" --root "$d"
expect 0 "실제 저장소 문서"
d="$(mktemp -d "$T/empty.XXXXXX")"; expect 0 "docs 디렉토리가 없으면 통과" --root "$d"

echo "== ② 머리말"
d="$(fresh)"; printf '# 머리말 없음\n' > "$d/docs/NOFM.md"
expect 1 "머리말 없음" --root "$d"; expect_msg "머리말 없음 메시지" "머리말이 없음" --root "$d"
d="$(fresh)"; printf -- '---\nstatus: draft\n\n# 닫힘 없음\n' > "$d/docs/OPEN.md"
expect 1 "머리말이 닫히지 않음" --root "$d"; expect_msg "닫힘 없음 메시지" "닫히지 않음" --root "$d"
d="$(fresh)"; printf -- '---\nversion: "1"\n---\n# status 없음\n' > "$d/docs/NOSTATUS.md"
expect 1 "status 없음" --root "$d"
d="$(fresh)"; printf -- '---\nstatus: done\n---\n# 잘못된 status\n' > "$d/docs/BADSTATUS.md"
expect 1 "status 값 오류" --root "$d"
d="$(fresh)"; printf -- '---\nstatus: draft\nversion: v1\n---\n# 잘못된 version\n' > "$d/docs/BADVER.md"
expect 1 "version 형식 오류" --root "$d"
d="$(fresh)"; printf -- '---\nstatus: superseded # 주석\nversion: '"'"'2.0'"'"'\n---\n# 주석과 작은따옴표\n' > "$d/docs/OK.md"
expect 0 "주석·작은따옴표 허용" --root "$d"
d="$(fresh)"; mkdir -p "$d/docs/guides"; printf -- '---\nstatus: confirmed\ncovers:\n  - tools/**\n---\n# 하위 디렉토리\n' > "$d/docs/guides/g.md"
expect 0 "하위 디렉토리 문서" --root "$d"

echo "== ③ based_on"
d="$(fresh)"; printf -- '---\nstatus: confirmed\nversion: "0.4"\n---\n# PRD\n' > "$d/docs/PRD.md"
expect 1 "상위 버전이 올라가면 오래됨" --root "$d"; expect_msg "오래됨 메시지" "오래됨" --root "$d"
expect 0 "--warn 은 오래됨이어도 0" --warn --root "$d"; expect_msg "--warn 메시지" "경고" --warn --root "$d"
d="$(fresh)"; printf -- '---\nstatus: draft\nbased_on:\n  - docs/NOPE.md@1\n---\n# x\n' > "$d/docs/X.md"
expect 1 "대상 파일 없음" --root "$d"
d="$(fresh)"; printf -- '---\nstatus: draft\nbased_on:\n  - docs/PRD.md\n---\n# x\n' > "$d/docs/X.md"
expect 1 "@버전 누락" --root "$d"
d="$(fresh)"; printf -- '---\nstatus: draft\n---\n# 버전 없는 문서\n' > "$d/docs/NOVER.md"
printf -- '---\nstatus: draft\nbased_on:\n  - docs/NOVER.md@1\n---\n# x\n' > "$d/docs/X.md"
expect 1 "대상에 version 없음" --root "$d"
d="$(fresh)"; printf -- '---\nstatus: draft\nbased_on: [docs/PRD.md@0.3]\n---\n# x\n' > "$d/docs/X.md"
expect 1 "한 줄 목록 형식은 거부" --root "$d"
d="$(fresh)"; printf -- '---\nstatus: draft\nbased_on:\n  - "docs/PRD.md@0.3"  # 주석\n---\n# x\n' > "$d/docs/X.md"
expect 0 "따옴표·주석 있는 based_on" --root "$d"

echo "== ④ 링크"
d="$(fresh)"; printf -- '---\nstatus: draft\n---\n[끊김](NOPE.md)\n' > "$d/docs/L.md"
expect 1 "끊어진 링크" --root "$d"; expect_msg "끊어진 링크 메시지" "끊어진 링크" --root "$d"
d="$(fresh)"; printf -- '---\nstatus: draft\n---\n[끊김](adr/NOPE.md#앵커)\n' > "$d/docs/L.md"
expect 1 "앵커 붙은 끊어진 링크" --root "$d"
d="$(fresh)"; printf -- '---\nstatus: draft\n---\n[ok](PRD.md#절) [web](https://example.com/x.md) [m](mailto:a@b) [a](#top) [ok2](../docs/PRD.md)\n' > "$d/docs/L.md"
expect 0 "앵커·외부·상대 링크 허용" --root "$d"
d="$(fresh)"; printf -- '---\nstatus: draft\n---\n```\n[예시](NOPE.md)\n```\n본문 `[코드](NOPE2.md)` 끝\n' > "$d/docs/L.md"
expect 0 "코드 블록·인라인 코드의 링크는 무시" --root "$d"
d="$(fresh)"; printf '# 머리말 없는 문서\n[끊김](NOPE.md)\n' > "$d/docs/L.md"
expect_msg "머리말 없는 문서도 링크 검사" "끊어진 링크" --root "$d"

echo "== ⑤ covers (--base)"
# git 저장소를 만든다: develop 에 기준 커밋을 두고 feature 브랜치로 옮긴 상태. 문서 C.md 가 코드 경로를 covers 로 가진다.
g() { git -C "$d" -c user.name=test -c user.email=test@example.com -c commit.gpgsign=false -c core.hooksPath=/dev/null "$@"; }
put() { mkdir -p "$d/$(dirname "$1")"; printf '%s\n' "${2:-x}" >> "$d/$1"; }   # put <경로> [내용]
commit() { g add -A; g commit -q --allow-empty -m "$1" ${2:+-m "$2"}; }          # commit <제목> [본문]
repo() {
  d="$(fresh)"
  printf -- '---\nstatus: draft\ncovers:\n  - src/app/**\n  - conf/app.yml\n  - lib/*.sh\n---\n# C\n' > "$d/docs/C.md"
  put src/app/A.java; put src/app/deep/B.java; put src/other/O.java; put conf/app.yml; put conf/other.yml
  put lib/a.sh; put lib/sub/b.sh
  git -C "$d" init -q -b develop; commit "chore: base"; g checkout -q -b feature/x
}

repo; put src/app/A.java; commit "feat: code"
expect 0 "--base 가 없으면 covers 를 검사하지 않음" --root "$d"
expect 1 "코드만 바뀌고 문서는 그대로" --root "$d" --base develop
expect_msg "covers 메시지에 문서와 파일" "docs/C.md: covers 에 해당하는 코드가 바뀌었는데 이 문서는 바뀌지 않음: src/app/A.java" --root "$d" --base develop
expect_msg "예외 방법 안내" "Docs-Unchanged: <사유>" --root "$d" --base develop
expect 0 "--warn 은 covers 위반이어도 0" --warn --root "$d" --base develop
expect_msg "--warn covers 메시지" "경고" --warn --root "$d" --base develop
put docs/C.md "갱신"; commit "docs: update"
expect 0 "코드와 문서가 함께 바뀜 (다른 커밋이어도 됨)" --root "$d" --base develop

repo; put docs/C.md "갱신"; put src/other/O.java; put conf/other.yml; put lib/sub/b.sh; commit "feat: unrelated"
expect 0 "covers 밖의 파일과 문서만 바뀜 (* 는 / 를 넘지 않음)" --root "$d" --base develop
repo; put src/other/O.java; commit "feat: unrelated"
expect 0 "covers 밖의 파일만 바뀜" --root "$d" --base develop
repo; put src/app/deep/B.java; commit "feat: deep"
expect 1 "** 는 하위 디렉토리까지" --root "$d" --base develop
repo; put conf/app.yml; commit "feat: conf"
expect 1 "파일 하나를 가리키는 패턴" --root "$d" --base develop
repo; put lib/a.sh; commit "feat: lib"
expect 1 "* 패턴" --root "$d" --base develop
repo; put src/app/New.java; commit "feat: new file"
expect 1 "새 파일" --root "$d" --base develop
repo; rm "$d/src/app/A.java"; commit "feat: delete"
expect 1 "삭제된 파일" --root "$d" --base develop
repo; put src/app/A.java; g add -A
expect 1 "스테이징만 한 변경도 본다 (pre-commit)" --root "$d" --base develop
repo; put src/app/A.java; put src/app/deep/B.java; put src/app/C.java; put src/app/D.java; put conf/app.yml; commit "feat: many"
expect_msg "파일이 많으면 3개와 나머지 수" "외 2개" --root "$d" --base develop
repo; put src/app/A.java; commit "feat: code"; g checkout -q develop; put src/other/O.java; commit "feat: develop moved"; g checkout -q feature/x
expect_msg "develop 이 앞서가도 갈라진 뒤의 변경만 본다" "src/app/A.java" --root "$d" --base develop
repo; g checkout -q develop; put src/app/A.java; commit "feat: on develop"; g checkout -q feature/x; g merge -q develop; put src/other/O.java; commit "feat: mine"
expect 0 "기준 브랜치에서 온 변경은 내 변경이 아님" --root "$d" --base develop

repo; printf -- '---\nstatus: draft\ncovers:\n  - src/**\n---\n# D\n' > "$d/docs/D.md"; commit "docs: second doc"
g checkout -q develop; g merge -q feature/x; g checkout -q feature/x
put src/app/A.java; put docs/C.md "갱신"; commit "feat: one doc updated"
expect 1 "같은 코드를 가리키는 두 문서 중 하나만 바뀜" --root "$d" --base develop
out="$(bash "$CHECK" --root "$d" --base develop 2>&1 >/dev/null)"
if printf '%s' "$out" | grep -q '^✖ docs/D.md' && ! printf '%s' "$out" | grep -q '^✖ docs/C.md'; then ok; else bad "바뀌지 않은 문서만 보고 ($out)"; fi

echo "== ⑥ covers 예외 (Docs-Unchanged)"
repo; put src/app/A.java; commit "refactor: rename" "Docs-Unchanged: 이름만 바꿔 설명이 달라지지 않음"
expect 0 "사유가 있는 예외" --root "$d" --base develop
expect_msg "건너뛴 문서와 사유를 보여 줌" "docs/C.md: covers 검사를 건너뜀 (Docs-Unchanged: 이름만 바꿔 설명이 달라지지 않음)" --root "$d" --base develop
repo; put src/app/A.java; commit "refactor: rename" "Docs-Unchanged:   "
expect 1 "사유가 없는 예외는 인정하지 않음" --root "$d" --base develop
repo; put src/app/A.java; commit "refactor: rename" "본문에 Docs-Unchanged: 라고 쓰기만 함"
expect 1 "줄 맨 앞이 아니면 예외가 아님" --root "$d" --base develop
repo; g checkout -q develop; commit "chore: old" "Docs-Unchanged: 예전 커밋"; g checkout -q feature/x; g merge -q develop; put src/app/A.java; commit "feat: code"
expect 1 "기준 브랜치에 있던 예외는 내 변경에 적용되지 않음" --root "$d" --base develop
repo; put src/app/A.java; commit "refactor: rename" "Docs-Unchanged: 사유"; printf '# 머리말 없음\n' > "$d/docs/NOFM.md"; commit "docs: bad"
expect 1 "예외는 covers 만 건너뛰고 다른 검사는 그대로" --root "$d" --base develop

echo "== ⑦ 옵션"
expect 2 "알 수 없는 옵션" --nope
expect 2 "--root 에 없는 디렉토리" --root "$T/없음"
expect 2 "--base 에 값이 없음" --base
repo; expect 2 "--base 에 없는 참조" --root "$d" --base nope
d="$(fresh)"; expect 2 "git 저장소가 아닌데 --base" --root "$d" --base develop

echo "== ⑧ 화면 추적 (API 화면 열)"
# PRD 5장 화면 목록과 API 엔드포인트 표를 만든다: <디렉토리> <API 표 본문 행들>
screens() {
  printf -- '---\nstatus: confirmed\nversion: "0.3"\n---\n\n# PRD\n\n## 5. 화면 목록\n\n1. 로그인\n2. 온보딩\n10. 마이페이지\n\n## 6. 다음\n\n1. 화면 아님\n' > "$1/docs/PRD.md"
  printf -- '---\nstatus: draft\n---\n\n# API\n\n| 메서드 경로 | 권한 | 화면 | 설명 |\n|---|---|---|---|\n%s\n\n본문\n' "$2" > "$1/docs/API.md"
}
d="$(fresh)"; screens "$d" '| `PUT /me/profile` | 회원 | 2, 10 | 온보딩·수정 |
| `GET /me` | 회원 | 공통 | 내 정보 |
| `GET /a\|b` | 운영자 | 없음: API 로만 조회 | 셀 안의 \| 는 구분자가 아님 |'
expect 0 "번호·공통·없음: 이유" --root "$d"
d="$(fresh)"; screens "$d" '| `GET /x` | 회원 | 14 | PRD 에 없는 화면 |'
expect 1 "PRD 5장에 없는 번호" --root "$d"; expect_msg "없는 번호 메시지" "PRD 5장에 없는 화면" --root "$d"
d="$(fresh)"; screens "$d" '| `GET /x` | 회원 |  | 빈 칸 |'
expect 1 "빈 화면 칸" --root "$d"; expect_msg "빈 칸 메시지" "비었음" --root "$d"
d="$(fresh)"; screens "$d" '| `GET /x` | 회원 | 없음: | 이유 없음 |'
expect 1 "이유 없는 없음" --root "$d"
d="$(fresh)"; screens "$d" '| `GET /x` | 회원 | 1 | 설명 |'
printf '\n| 메서드 경로 | 권한 | 설명 |\n|---|---|---|\n| `GET /y` | 회원 | 화면 열 없음 |\n' >> "$d/docs/API.md"
expect 1 "화면 열이 없는 표" --root "$d"; expect_msg "열 없음 메시지" "\"화면\" 열이 없음" --root "$d"
d="$(fresh)"; screens "$d" '| `GET /x` | 회원 | 1 | 설명 |'
printf -- '---\nstatus: confirmed\nversion: "0.3"\n---\n\n# PRD\n' > "$d/docs/PRD.md"
expect 1 "PRD 에 화면 목록이 없음" --root "$d"
d="$(fresh)"; expect 0 "API.md 가 없으면 건너뜀" --root "$d"
d="$(fresh)"; screens "$d" '| `PUT /me/profile` | 회원 | 2(온보딩), 10(수정) | 쓰임새 표기 |'
expect 0 "번호(쓰임새) 표기" --root "$d"
d="$(fresh)"; screens "$d" '| `PUT /me/profile` | 회원 | 10( | 괄호가 닫히지 않음 |'
expect 1 "화면 항목 형식" --root "$d"; expect_msg "형식 메시지" "형식이 틀림" --root "$d"
d="$(fresh)"; screens "$d" '| `PUT /me/profile` | 회원 | 14(수정) | 없는 번호 |'
expect 1 "쓰임새 붙은 없는 번호" --root "$d"

echo "== ⑨ 쓰임새 추적 (API 화면 열 ↔ PLAN [프론트] 작업)"
# 화면 열이 있는 API 와 PLAN 을 만든다: <디렉토리> <PLAN 작업 줄들>
usage() {
  screens "$1" '| `PUT /api/v1/me/profile` | 회원 | 2(온보딩), 10(수정) | 온보딩·수정 |
| `GET /api/v1/me/studies?role=host\|member` | 회원 | 10 | 쿼리는 비교에서 뺀다 |
| `GET /api/v1/me` | 회원 | 공통 | 짝 불필요 |
| `GET /api/v1/x` | 운영자 | 없음: API 로만 | 짝 불필요 |'
  printf -- '---\nstatus: draft\n---\n\n# PLAN\n\n%s\n' "$2" > "$1/docs/PLAN.md"
}
d="$(fresh)"; usage "$d" '- [x] T-007 [프론트] 온보딩
  - 쓰임새: `PUT /me/profile` 2(온보딩)
- [ ] T-036 [프론트] 마이페이지
  - 쓰임새: `PUT /api/v1/me/profile` 10(수정) · `GET /me/studies` 10'
expect 0 "모든 쓰임새를 맡음 (/api/v1 생략·포함 모두)" --root "$d"
d="$(fresh)"; usage "$d" '- [x] T-007 [프론트] 온보딩
  - 쓰임새: `PUT /me/profile` 2(온보딩) · `GET /me/studies` 10'
expect 1 "수정 쓰임새를 맡은 작업 없음 (T-072 사례)" --root "$d"
expect_msg "맡지 않음 메시지" "맡은 \[프론트\] 작업이 없는 쓰임새: \"PUT /me/profile 10(수정)\"" --root "$d"
d="$(fresh)"; usage "$d" '- [x] T-007 [프론트] 온보딩
  - 쓰임새: `PUT /me/profile` 2(온보딩), 10(수정) · `GET /me/studies` 10 · `GET /nope` 3'
expect 1 "API 에 없는 엔드포인트" --root "$d"; expect_msg "없는 엔드포인트 메시지" "API 표에 없는 엔드포인트" --root "$d"
d="$(fresh)"; usage "$d" '- [x] T-007 [프론트] 온보딩
  - 쓰임새: `PUT /me/profile` 2, 10(수정) · `GET /me/studies` 10'
expect 1 "API 와 다르게 적은 화면 항목" --root "$d"; expect_msg "다른 항목 메시지" "API 화면 열에 없는 쓰임새" --root "$d"
d="$(fresh)"; usage "$d" '- [x] T-006 내 정보 API
  - 쓰임새: `PUT /me/profile` 2(온보딩), 10(수정) · `GET /me/studies` 10'
expect 1 "백엔드 작업의 쓰임새 줄" --root "$d"; expect_msg "프론트 전용 메시지" "\[프론트\] 작업에만" --root "$d"
d="$(fresh)"; usage "$d" '- [x] T-007 [프론트] 온보딩
  - 쓰임새: PUT /me/profile 2(온보딩)'
expect 1 "백틱 없는 쓰임새" --root "$d"

echo
echo "통과 $pass · 실패 $fail"
[ "$fail" = 0 ]
