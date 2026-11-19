#!/usr/bin/env bash
# 배포 (PLAN T-013). 내 컴퓨터에서 이미지를 빌드해 서버로 보내고 다시 띄운다. 레지스트리를 쓰지 않는다.
#
#   DEPLOY_HOST=<ssh 대상> APP_DOMAIN=<도메인> bash deploy/deploy.sh [backend|frontend|all]
#
# - backend / frontend 만 고르면 그것만 다시 배포한다 (백엔드·프론트 따로 배포, PLAN T-013).
# - DEPLOY_HOST: ~/.ssh/config 의 호스트 이름이나 user@IP. 서버에는 Docker 와 Compose 가 있어야 한다.
# - APP_DOMAIN: 등록 도메인(예: example.com). 프론트는 https://api.<APP_DOMAIN> 을 바라보게 빌드된다.
# - 선택: DEPLOY_DIR(서버의 배포 디렉토리, 기본 every-study), DEPLOY_PLATFORM(기본: 서버 CPU 에 맞춤),
#         NEXT_PUBLIC_KAKAO_MAP_APP_KEY, NEXT_PUBLIC_SAMPLE_PLACES(true 면 지도 대신 예시 장소, 임시 환경용)
# 서버의 시크릿은 서버의 환경 파일에만 둔다. 이 스크립트는 시크릿을 보내거나 읽지 않는다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-all}"
DEPLOY_DIR="${DEPLOY_DIR:-every-study}"

say() { printf '\033[1m[deploy]\033[0m %s\n' "$*"; }
fail() { printf '\033[31m[deploy] %s\033[0m\n' "$*" >&2; exit 1; }

case "$TARGET" in
  backend | frontend) targets=("$TARGET") ;;
  all) targets=(backend frontend) ;;
  *) fail "대상은 backend, frontend, all 중 하나입니다 (지금: $TARGET)" ;;
esac
[ -n "${DEPLOY_HOST:-}" ] || fail "DEPLOY_HOST(ssh 대상)를 주세요. 예: DEPLOY_HOST=every-study-vm"
[ -n "${APP_DOMAIN:-}" ] || fail "APP_DOMAIN(등록 도메인)을 주세요. 예: APP_DOMAIN=example.com"
docker info >/dev/null 2>&1 || fail "Docker 에 연결하지 못했습니다. Docker Desktop 을 켜 주세요."

say "서버에 연결합니다: $DEPLOY_HOST"
ssh "$DEPLOY_HOST" "docker compose version >/dev/null" \
  || fail "서버에 접속하지 못했거나 Docker Compose 가 없습니다 (deploy/README.md 서버 준비)."

if [ -z "${DEPLOY_PLATFORM:-}" ]; then
  case "$(ssh "$DEPLOY_HOST" uname -m)" in
    x86_64 | amd64) DEPLOY_PLATFORM=linux/amd64 ;;
    aarch64 | arm64) DEPLOY_PLATFORM=linux/arm64 ;;
    *) fail "서버 CPU 를 알 수 없습니다. DEPLOY_PLATFORM 을 직접 주세요 (linux/amd64 또는 linux/arm64)." ;;
  esac
fi
say "이미지 플랫폼: $DEPLOY_PLATFORM"

# 배포 설정(compose·Caddyfile)을 올린다. 환경 파일은 서버에서 사람이 직접 만든다.
ssh "$DEPLOY_HOST" "mkdir -p '$DEPLOY_DIR'"
scp -q "$ROOT/deploy/compose.yaml" "$ROOT/deploy/Caddyfile" "$DEPLOY_HOST:$DEPLOY_DIR/"
ssh "$DEPLOY_HOST" "test -f '$DEPLOY_DIR/.env'" \
  || fail "서버의 $DEPLOY_DIR 에 환경 파일(.env)이 없습니다. deploy/README.md '서버 환경 파일'을 보고 만든 뒤 다시 실행하세요."

for target in "${targets[@]}"; do
  image="every-study-$target:latest"
  build_args=()
  if [ "$target" = frontend ]; then
    build_args+=(--build-arg "NEXT_PUBLIC_API_BASE_URL=https://api.$APP_DOMAIN")
    build_args+=(--build-arg "NEXT_PUBLIC_KAKAO_MAP_APP_KEY=${NEXT_PUBLIC_KAKAO_MAP_APP_KEY:-}")
    build_args+=(--build-arg "NEXT_PUBLIC_SAMPLE_PLACES=${NEXT_PUBLIC_SAMPLE_PLACES:-}")
  fi
  say "$target 이미지를 빌드합니다"
  docker buildx build --platform "$DEPLOY_PLATFORM" --load -t "$image" ${build_args[@]+"${build_args[@]}"} "$ROOT/$target"
  say "$target 이미지를 서버로 보냅니다"
  docker save "$image" | gzip | ssh "$DEPLOY_HOST" "gunzip | docker load"
done

say "서버에서 다시 띄웁니다"
if [ "$TARGET" = all ]; then
  ssh "$DEPLOY_HOST" "cd '$DEPLOY_DIR' && docker compose up -d"
else
  # 고른 서비스만 새 이미지로 바꾼다. 처음 배포는 all 로 한다(다른 이미지가 서버에 아직 없다)
  ssh "$DEPLOY_HOST" "cd '$DEPLOY_DIR' && docker compose up -d --no-deps $TARGET"
fi
ssh "$DEPLOY_HOST" "docker image prune -f >/dev/null"
say "끝났습니다: https://app.$APP_DOMAIN · https://api.$APP_DOMAIN (처음에는 인증서 발급에 1~2분 걸릴 수 있습니다)"
