# every-study

모두의 스터디 — 동네에서 열리는 오프라인 스터디를 찾고, 출석 기록으로 믿을 수 있는 사람과 만나는 서비스. 무엇을 만드는지는 [PRD](docs/PRD.md), 문서 전체 안내는 [docs/README.md](docs/README.md)에 있다.

| 디렉토리    | 내용                                                          |
| ----------- | ------------------------------------------------------------- |
| `backend/`  | Spring Boot REST API ([backend/README.md](backend/README.md)) |
| `frontend/` | Next.js 웹 앱 ([frontend/README.md](frontend/README.md))      |
| `docs/`     | 기능 명세, 설계, 작업 계획                                    |
| `tools/`    | 로컬 실행과 검사 스크립트                                     |

## 로컬에서 전체 실행 (PLAN T-012)

### 준비 (처음 한 번)

1. Docker Desktop, JDK 21, Node 24를 설치한다. Node는 nvm을 쓰면 `frontend/`에서 `nvm install` 한 번이면 된다.
2. 저장소 루트의 `.env.example`을 `.env`로 복사하고 값을 채운다. 값은 사람이 직접 발급해 이 파일에만 적고 커밋하지 않는다.
   - DB 계정: `MYSQL_USER`, `MYSQL_PASSWORD`
   - 소셜 로그인과 JWT 서명 키: [backend/README.md "소셜 로그인 준비"](backend/README.md#소셜-로그인-준비-plan-h-1). 리다이렉트 URI는 `http://localhost:8090/login/oauth2/code/{kakao|google}`
   - 카카오맵 키(`NEXT_PUBLIC_KAKAO_MAP_APP_KEY`)는 비워도 된다. 지도를 불러오지 못하면 개발 서버에서는 장소 검색이 예시 장소를 준다 (PLAN H-2).

### 실행

```bash
bash tools/dev.sh
```

- MySQL(Docker)을 켜고, 백엔드와 프론트를 함께 띄운다. 준비되면 주소를 출력한다.
  - 프론트 <http://localhost:3000> · 백엔드 <http://localhost:8090> · Swagger <http://localhost:8090/swagger-ui/index.html>
- 프론트와 백엔드가 모두 `localhost`라 같은 사이트다. 로그인 토큰 쿠키(`SameSite=Lax`)가 그대로 오간다 ([ADR-0002](docs/adr/0002-auth-token-cookie.md)).
- `Ctrl+C`로 백엔드와 프론트를 함께 끈다. MySQL은 켜 두므로 데이터가 남는다. MySQL까지 끄려면 `docker compose down`.
- 백엔드 기록은 `backend/build/dev-logs/backend.log`, 프론트 기록은 터미널에 `[frontend]`를 붙여 보여 준다.
- 3000·8090 포트를 이미 쓰고 있으면 시작하지 않는다. 켜 둔 개발 서버를 끄고 다시 실행한다.

### 확인해 볼 흐름 (M1 시연의 로컬 부분)

1. 비회원으로 목록(`/`)을 본다.
2. 헤더의 "로그인"에서 카카오나 구글로 로그인하고, 온보딩에서 닉네임·직군·지역을 입력한다.
3. "스터디 만들기"에서 장소를 고르고 날짜·시간·정원을 입력해 개설하면 상세로 간다.
4. "로그아웃" 뒤 비회원으로 목록과 상세에서 그 스터디를 본다.

## 검사

각 디렉토리의 README에 명령이 있다. 문서 검사는 `bash tools/docs-check.sh --base origin/develop`.
