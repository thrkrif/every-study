# 프론트 운영 이미지 (PLAN T-013, ARCHITECTURE 9장). 저장소 루트에서:
#   docker build -t every-study-frontend \
#     --build-arg NEXT_PUBLIC_API_BASE_URL=https://api.<도메인> frontend
# NEXT_PUBLIC_ 값은 빌드할 때 화면 코드에 들어간다. 그래서 백엔드 주소가 바뀌면 이미지를 다시 만든다.
# 공개되는 값만 받는다(비밀 값을 NEXT_PUBLIC_ 으로 만들지 않는다).

FROM node:24-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_KAKAO_MAP_APP_KEY=""
ARG NEXT_PUBLIC_SAMPLE_PLACES=""
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL \
    NEXT_PUBLIC_KAKAO_MAP_APP_KEY=$NEXT_PUBLIC_KAKAO_MAP_APP_KEY \
    NEXT_PUBLIC_SAMPLE_PLACES=$NEXT_PUBLIC_SAMPLE_PLACES \
    NEXT_TELEMETRY_DISABLED=1
RUN test -n "$NEXT_PUBLIC_API_BASE_URL" || (echo "NEXT_PUBLIC_API_BASE_URL 빌드 인자가 필요합니다" >&2; exit 1)
RUN npm run build

# next.config 의 output: "standalone" 이 만든 최소 서버만 옮긴다
FROM node:24-slim
WORKDIR /app
ENV NODE_ENV=production PORT=3000 HOSTNAME=0.0.0.0 NEXT_TELEMETRY_DISABLED=1
COPY --from=build --chown=node:node /app/public ./public
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
USER node
EXPOSE 3000
CMD ["node", "server.js"]
