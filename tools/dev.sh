#!/usr/bin/env bash
# 로컬 통합 실행 (PLAN T-012, ARCHITECTURE 9장): MySQL·백엔드·프론트를 명령 하나로 띄운다.
#   bash tools/dev.sh        # 저장소 어디서든. Ctrl+C 로 백엔드·프론트를 함께 끈다 (MySQL 은 켜 둔다)
#
# 프론트 http://localhost:3000, 백엔드 http://localhost:8090. 둘 다 localhost 라 같은 사이트이고
# 토큰 쿠키(SameSite=Lax)가 그대로 오간다 (ADR-0002). 값은 저장소 루트의 환경 파일에서 각 프로그램이 직접 읽는다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_PORT=3000
BACKEND_PORT="${SERVER_PORT:-8090}"
LOG_DIR="$ROOT/backend/build/dev-logs"

say() { printf '\033[1m[dev]\033[0m %s\n' "$*"; }
fail() { printf '\033[31m[dev] %s\033[0m\n' "$*" >&2; exit 1; }

# ── 준비물 확인 ──
[ -f "$ROOT/.env" ] || fail "저장소 루트에 환경 파일이 없습니다. .env.example 을 복사해 값을 채우세요 (backend/README.md 소셜 로그인 준비)."
command -v docker >/dev/null || fail "docker 가 없습니다. Docker Desktop 을 설치하고 켜 주세요."
docker info >/dev/null 2>&1 || fail "Docker 에 연결하지 못했습니다. Docker Desktop 을 켜 주세요."

# 프론트는 frontend/.nvmrc 의 Node 주 버전을 쓴다. nvm 이 있으면 맞춰 쓴다.
want_node="$(tr -d '[:space:]' < "$ROOT/frontend/.nvmrc")"
have_node="$(node -v 2>/dev/null | sed 's/^v\([0-9]*\).*/\1/' || true)"
if [ "$have_node" != "$want_node" ] && [ -s "${NVM_DIR:-$HOME/.nvm}/nvm.sh" ]; then
  # shellcheck disable=SC1091
  . "${NVM_DIR:-$HOME/.nvm}/nvm.sh"
  nvm use "$want_node" >/dev/null || fail "nvm 으로 Node $want_node 을 쓰지 못했습니다. 'nvm install $want_node' 뒤 다시 실행하세요."
  have_node="$(node -v | sed 's/^v\([0-9]*\).*/\1/')"
fi
[ "$have_node" = "$want_node" ] || fail "Node $want_node 이 필요합니다 (지금: ${have_node:-없음})."

for port in "$FRONTEND_PORT" "$BACKEND_PORT"; do
  if lsof -iTCP:"$port" -sTCP:LISTEN -n -P >/dev/null 2>&1; then
    fail "포트 $port 를 이미 쓰고 있습니다. 켜 둔 개발 서버를 끈 뒤 다시 실행하세요 (lsof -iTCP:$port -sTCP:LISTEN 으로 확인)."
  fi
done

# ── MySQL ──
say "MySQL 을 켭니다 (docker compose)"
(cd "$ROOT" && docker compose up -d --wait mysql) || fail "MySQL 을 켜지 못했습니다. 'docker compose logs mysql' 을 확인하세요."

# ── 프론트 의존성 ──
if [ ! -d "$ROOT/frontend/node_modules" ]; then
  say "프론트 의존성을 설치합니다 (npm ci)"
  (cd "$ROOT/frontend" && npm ci)
fi

# ── 백엔드·프론트 ──
# 각 백그라운드 작업을 자기 프로세스 그룹으로 띄워(set -m), 끝날 때(Ctrl+C 포함) 그룹째 끈다.
# 백엔드는 --no-daemon 이라 Gradle 과 앱 JVM 이 같은 그룹에 있어 함께 꺼진다.
set -m
pids=()
cleanup() {
  trap - EXIT INT TERM
  say "백엔드와 프론트를 끕니다 (MySQL 은 켜 둡니다. 끄려면 docker compose down)"
  for pid in "${pids[@]}"; do
    kill -TERM -- "-$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
}
trap cleanup EXIT
trap 'cleanup; exit 130' INT
trap 'cleanup; exit 143' TERM

mkdir -p "$LOG_DIR"
say "백엔드를 켭니다 (기록: backend/build/dev-logs/backend.log)"
(cd "$ROOT/backend" && exec ./gradlew bootRun --no-daemon --console=plain >"$LOG_DIR/backend.log" 2>&1) &
pids+=($!)

say "프론트를 켭니다"
(cd "$ROOT/frontend" && exec npm run dev -- --port "$FRONTEND_PORT" 2>&1 | sed -u 's/^/[frontend] /') &
pids+=($!)

# 백엔드가 응답할 때까지 기다린다 (첫 실행은 컴파일 때문에 1~2분 걸릴 수 있다)
ready=false
for _ in $(seq 1 180); do
  if curl -fs -o /dev/null "http://localhost:$BACKEND_PORT/api/v1/meta/options"; then
    say "준비됐습니다: 프론트 http://localhost:$FRONTEND_PORT · 백엔드 http://localhost:$BACKEND_PORT · Swagger http://localhost:$BACKEND_PORT/swagger-ui/index.html"
    say "끄려면 Ctrl+C"
    ready=true
    break
  fi
  if ! kill -0 "${pids[0]}" 2>/dev/null; then
    tail -n 30 "$LOG_DIR/backend.log" >&2
    fail "백엔드가 켜지지 못했습니다. 위 기록이나 backend/build/dev-logs/backend.log 를 확인하세요."
  fi
  sleep 1
done

$ready || say "백엔드가 3분 안에 응답하지 않았습니다. backend/build/dev-logs/backend.log 를 확인하세요."

# 둘 중 하나가 멈추면 나머지도 끈다 (macOS 기본 bash 3.2 에는 wait -n 이 없어 직접 살핀다)
while kill -0 "${pids[0]}" 2>/dev/null && kill -0 "${pids[1]}" 2>/dev/null; do
  sleep 2
done
say "백엔드나 프론트 하나가 멈췄습니다. 백엔드 기록: backend/build/dev-logs/backend.log"
