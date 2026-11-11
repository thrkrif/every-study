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

echo "== ⑤ 옵션"
expect 2 "알 수 없는 옵션" --nope
expect 2 "--root 에 없는 디렉토리" --root "$T/없음"

echo
echo "통과 $pass · 실패 $fail"
[ "$fail" = 0 ]
