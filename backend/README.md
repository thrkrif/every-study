# backend

Spring Boot REST API. 구조와 규칙은 [ARCHITECTURE](../docs/ARCHITECTURE.md), 버전은 [ADR-0001](../docs/adr/0001-tech-stack.md)이 원본이다.

## 준비

- JDK 21, Docker (로컬 MySQL과 통합 테스트에 필요)
- 저장소 루트의 `.env.example`을 `.env`로 복사하고 `MYSQL_USER`, `MYSQL_PASSWORD`를 직접 채운다.

## 명령 (이 디렉토리에서)

| 하려는 일 | 명령 |
|---|---|
| 단위·슬라이스 테스트 (Docker 불필요) | `./gradlew test` |
| 통합 테스트 (Testcontainers MySQL) | `./gradlew integrationTest` |
| 전체 빌드와 모든 테스트 | `./gradlew build` |
| 의존성을 바꾼 뒤 잠금 파일 갱신 | `./gradlew dependencies --write-locks` |

## 로컬 실행

```bash
docker compose up -d                 # 저장소 루트에서. MySQL 시작
cd backend
set -a; . ../.env; set +a            # .env 값을 환경변수로 올린다
./gradlew bootRun
```

실행 후 API 문서를 보는 주소:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI 문서(JSON): `http://localhost:8080/v3/api-docs`

둘 다 개발용이다. 운영(`SPRING_PROFILES_ACTIVE=prod`)에서는 꺼져서 `404`가 된다.
