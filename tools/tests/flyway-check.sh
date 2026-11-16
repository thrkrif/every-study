#!/usr/bin/env bash
# tools/flyway-check.sh 테스트
#   bash tools/tests/flyway-check.sh
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CHECK="${FLYWAY_CHECK:-$ROOT/tools/flyway-check.sh}"

pass=0; fail=0
ok()  { pass=$((pass+1)); }
bad() { fail=$((fail+1)); echo "  ✗ FAIL: $1"; }
# expect <0|1|2> <라벨> <인자...>   (인자는 flyway-check.sh 로 전달)
expect() { local want="$1" label="$2"; shift 2; bash "$CHECK" "$@" >/dev/null 2>&1; local rc=$?
  if [ "$rc" = "$want" ]; then ok; else bad "$label (기대=$want 실제=$rc)"; fi; }
# expect_msg <라벨> <패턴> <인자...>   (stderr 에 패턴이 있어야 함)
expect_msg() { local label="$1" pat="$2"; shift 2; local out; out="$(bash "$CHECK" "$@" 2>&1 >/dev/null)"
  if printf '%s' "$out" | grep -q -- "$pat"; then ok; else bad "$label (메시지에 '$pat' 없음: $out)"; fi; }
# expect_count <라벨> <오류 건수> <인자...>   (✖ 줄의 수)
expect_count() { local label="$1" want="$2"; shift 2; local n; n="$(bash "$CHECK" "$@" 2>&1 >/dev/null | grep -c '^✖')"
  if [ "$n" = "$want" ]; then ok; else bad "$label (오류 기대=$want 실제=$n)"; fi; }

T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT

# 마이그레이션 디렉토리를 만든다: mk <파일명...> → 디렉토리 경로
mk() { local d f; d="$(mktemp -d "$T/m.XXXXXX")"
  for f in "$@"; do mkdir -p "$d/$(dirname "$f")"; : > "$d/$f"; done
  printf '%s' "$d"; }

echo "== ① 정상"
expect 0 "실제 저장소 마이그레이션"
d="$(mk V1__init.sql V2__create_user.sql V3__add_Index_2.sql)"; expect 0 "1부터 이어진 번호" --dir "$d"
d="$(mk V1__a.sql V2__b.sql V3__c.sql V4__d.sql V5__e.sql V6__f.sql V7__g.sql V8__h.sql V9__i.sql V10__j.sql V11__k.sql)"
expect 0 "두 자리 번호는 숫자로 정렬" --dir "$d"
d="$(mk V1__a.sql sub/V2__b.sql)"; expect 0 "하위 디렉토리도 같은 번호 흐름" --dir "$d"
d="$(mk)"; expect 0 "빈 디렉토리" --dir "$d"

echo "== ② 파일명 형식"
for name in v1__lower.sql V1_one_underscore.sql V1__.sql V1__desc.SQL V1__de-sc.sql "V1__de sc.sql" \
            V01__zero.sql V0__zero.sql V1.1__dotted.sql R__repeatable.sql U1__undo.sql V1__desc.sql.bak notes.txt; do
  d="$(mk "$name")"; expect 1 "형식 오류: $name" --dir "$d"
done
d="$(mk V1__a.sql README.md)"; expect_msg "형식 오류 메시지" "파일명 형식이 잘못됨: README.md" --dir "$d"
d="$(mk V1__a.sql sub/bad.sql)"; expect_msg "하위 디렉토리 파일은 상대 경로로 표시" "sub/bad.sql" --dir "$d"

echo "== ③ 번호 중복"
d="$(mk V1__a.sql V2__b.sql V2__c.sql)"
expect 1 "같은 번호 두 개" --dir "$d"; expect_msg "중복 메시지" "번호가 중복됨: V2" --dir "$d"
expect_count "중복은 번호당 한 번만 보고" 1 --dir "$d"
d="$(mk V1__a.sql sub/V1__b.sql)"; expect 1 "하위 디렉토리와 번호 중복" --dir "$d"

echo "== ④ 번호 누락"
d="$(mk V1__a.sql V3__c.sql)"
expect 1 "중간 번호 빠짐" --dir "$d"; expect_msg "누락 메시지" "번호가 빠짐: V2" --dir "$d"
d="$(mk V2__b.sql)"; expect_msg "1부터 시작하지 않음" "번호가 빠짐: V1" --dir "$d"
d="$(mk V1__a.sql V4__d.sql)"; expect_count "빠진 번호마다 보고" 2 --dir "$d"
d="$(mk V1__a.sql V3__c.sql V3__d.sql bad.sql)"; expect_count "형식·중복·누락을 한 번에 보고" 3 --dir "$d"

echo "== ⑤ 옵션"
expect 2 "알 수 없는 옵션" --nope
expect 2 "--dir 에 없는 디렉토리" --dir "$T/없음"

echo
echo "통과 $pass · 실패 $fail"
[ "$fail" = 0 ]
