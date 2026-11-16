# backend

Spring Boot REST API. 구조와 규칙은 [ARCHITECTURE](../docs/ARCHITECTURE.md), 버전은 [ADR-0001](../docs/adr/0001-tech-stack.md)이 원본이다.

## 준비

- JDK 21, Docker (로컬 MySQL과 통합 테스트에 필요)
- 저장소 루트의 `.env.example`을 `.env`로 복사하고 `MYSQL_USER`, `MYSQL_PASSWORD`를 직접 채운다.

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
