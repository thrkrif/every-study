# backend

Spring Boot REST API. 구조와 규칙은 [ARCHITECTURE](../docs/ARCHITECTURE.md), 버전은 [ADR-0001](../docs/adr/0001-tech-stack.md)이 원본이다.

## 준비

- JDK 21, Docker (로컬 MySQL과 통합 테스트에 필요)
- 저장소 루트의 `.env.example`을 `.env`로 복사하고 `MYSQL_USER`, `MYSQL_PASSWORD`를 직접 채운다.

### 소셜 로그인 준비 (PLAN H-1)

앱은 아래 값이 없으면 기동하지 않는다. 값은 사람이 직접 발급해 `.env`에만 적는다. 테스트는 지어낸 값(`src/test/resources/config/application.yml`)을 쓰므로 이 준비 없이도 돈다.

| `.env` 키 | 어디서 얻나 |
|---|---|
| `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | 카카오 개발자 콘솔: 앱의 REST API 키와 Client Secret(사용함으로 설정) |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google Cloud 콘솔: OAuth 클라이언트 ID(웹 애플리케이션) |
| `JWT_SECRET` | 직접 만든 32자 이상의 랜덤 값 (예: `openssl rand -base64 48`의 출력) |

공급자 콘솔에 등록할 값:

- 리다이렉트 URI: `http://localhost:8080/login/oauth2/code/kakao`, `http://localhost:8080/login/oauth2/code/google`
- 카카오 동의 항목: 카카오계정(이메일) `account_email`을 **선택 동의**로 켠다. 켜지 않으면 카카오 로그인이 `KOE205` 오류로 실패한다. 이 항목을 쓰려면 앱을 비즈 앱으로 전환해야 할 수 있다(콘솔 안내를 따른다).
- 구글 범위: `email` (`.../auth/userinfo.email`)

로그인은 브라우저에서 `http://localhost:8080/oauth2/authorization/kakao`(또는 `/google`)로 시작한다. 성공하면 토큰 쿠키를 받고 `FRONTEND_URL`(기본 `http://localhost:3000`)로 이동한다. 쿠키 이름과 이동 경로는 [API 2장](../docs/API.md).

## 명령 (이 디렉토리에서)

| 하려는 일 | 명령 |
|---|---|
| 단위·슬라이스 테스트와 모듈 경계 검사 (Docker 불필요) | `./gradlew test` |
| 통합 테스트 (Testcontainers MySQL) | `./gradlew integrationTest` |
| 전체 빌드와 모든 테스트 | `./gradlew build` |
| 의존성을 바꾼 뒤 잠금 파일 갱신 | `./gradlew dependencies --write-locks` |
| 마이그레이션 파일명·번호 검사 | `bash ../tools/flyway-check.sh` |

모듈 경계 검사(`com.everystudy.architecture`)가 실패하면 메시지에 나온 의존을 없애는 쪽으로 코드를 옮긴다. 규칙은 [ARCHITECTURE 3장](../docs/ARCHITECTURE.md)에 있다.

## 로컬 실행

```bash
docker compose up -d                 # 저장소 루트에서. MySQL 시작
cd backend
./gradlew bootRun
```

앱은 `backend/`에서 실행하면 저장소 루트의 `.env`를 직접 읽는다(`application.yml`의 `spring.config.import`). 알아 둘 것:

- IDE에서 실행할 때는 작업 디렉토리(Working directory)를 `backend/`로 맞춘다. 다르면 `.env`를 찾지 못해 `Access denied for user '${MYSQL_USER}'`가 난다.
- `.env`의 값에는 따옴표를 쓰지 않는다 (`KEY=value`). Spring은 따옴표를 값의 일부로 읽는다.
- 같은 이름의 환경변수가 있으면 환경변수가 우선한다. `.env`가 없는 환경(운영, CI)에서는 환경변수만 쓴다.

실행 후 API 문서를 보는 주소:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI 문서(JSON): `http://localhost:8080/v3/api-docs`

둘 다 개발용이다. 운영(`SPRING_PROFILES_ACTIVE=prod`)에서는 꺼져서 `404`가 된다.
