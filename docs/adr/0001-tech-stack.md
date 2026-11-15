---
status: draft
based_on:
  - docs/PRD.md@0.6
---

# ADR-0001: 기술 스택

- 상태: 확정 (버전·도구는 2026-11-16 확정. 호스팅 업체·도메인은 ARCHITECTURE 11장에서 별도 결정)
- 일자: 2026-11-09
- 관련: [PRD](../PRD.md) (v0.6), [ARCHITECTURE](../ARCHITECTURE.md)

## 배경

PRD 는 백엔드 Spring Boot REST API, 프론트 Next.js(React) 분리 구조를 전제한다.
아키텍처(ARCHITECTURE.md) 작성 전에 도구 체인을 정해야 컨벤션·로컬 훅·CI 를 만들 수 있다.

## 결정

### 확정

| 영역 | 결정 | 비고 |
|---|---|---|
| 백엔드 언어 | Java | |
| 빌드 | Gradle (Groovy DSL) | |
| DB | MySQL 8 | 문자셋 `utf8mb4` 명시 |
| DB 마이그레이션 | Flyway (처음부터 도입) | MySQL 은 `flyway-mysql` 모듈 필요 |
| 인증 | JWT | 브라우저 보관은 **httpOnly 쿠키** (XSS 노출 최소화) |
| 프론트 | Next.js (React), TypeScript | PRD 확정 사항 |

### 버전과 도구 (확정, 2026-11-16)

주 버전 계열만 정한다. 패치 버전은 처음 쓰는 작업(T-001, T-002)에서 최신 안정 버전을 공식 문서와 레지스트리로 확인해 잠금 파일(Gradle 의존성, `package-lock.json`)에 맡긴다. 새 라이브러리 API를 쓸 때는 이 버전을 고정해 공식 문서를 조회한다 (AGENTS.md 6장).

| 영역 | 결정 | 근거 |
|---|---|---|
| Spring Boot | 4.1.x | 3.5는 OSS 지원이 2026-06-30에 끝났고 4.0은 2026-12-31에 끝난다. 4.1 문서 기준 Java 17 이상(27까지), Gradle 8.14 이상 또는 9.x 요구. 4.2는 안정화 후 후속 작업으로 검토 |
| JDK | 21 (LTS) | Boot 요구(17 이상)를 충족하는 보수적 선택. 25(LTS)도 Boot 4.1이 지원하므로 필요해지면 올린다 |
| Gradle | 9.x (Wrapper 로 고정) | Boot 4.1 요구 범위의 최신 계열 |
| Next.js | 16.x, TypeScript 5.x | 문서 기준 Node 20.9 이상, TypeScript 5.1 이상 |
| Node | 24 (LTS) | Node 20은 2026-04-30에 지원이 끝났다. Vitest 5가 22.12 이상을 요구한다. 26은 LTS 전환 시점이 확인되지 않아 제외 |
| 린트·포맷 (프론트) | ESLint(flat config), Prettier | Next.js 16은 `next build`에서 린트를 실행하지 않으므로 npm 스크립트로 실행한다 |
| 테스트 (백엔드) | JUnit, Testcontainers(MySQL) | 버전은 Boot 4.1이 관리하는 값을 따른다 (T-001에서 확정). 실제 DB 로 정원 마감 동시성 검증 |
| 테스트 (프론트) | Vitest 5, Testing Library | E2E(Playwright)는 후속 검토 |
| 로컬 실행 | Docker Compose (MySQL) | Redis는 소규모에서 불필요 (ADR-0005) |

## 결과와 영향

### MySQL 사용 시 주의
- 기본 격리 수준이 `REPEATABLE READ` 라 갭 락이 생긴다. 정원 마감처럼 동시 변경이 몰리는 곳은 **스터디 행 단위 락**으로 직렬화하는 구조로 설계하고 동시성 테스트를 처음부터 둔다.
- 부분 인덱스가 없다. 소프트 삭제와 유일성 제약이 함께 필요하면 생성 컬럼으로 우회한다.
- 좌표는 위도·경도 컬럼 또는 `POINT`(SRID 4326)로 저장한다. 체크인 반경 판정 정도는 이것으로 충분하다 (PostGIS 수준의 쿼리는 MVP 범위 밖).

### Flyway 를 처음부터 쓰는 규칙
- 마이그레이션은 `backend/src/main/resources/db/migration/` 에 `V<번호>__<설명>.sql` 로 둔다.
- **한 번 병합된 마이그레이션 파일은 수정하지 않는다.** 변경은 새 버전 파일로 추가한다.
- JPA 는 `spring.jpa.hibernate.ddl-auto=validate`. 스키마 생성은 Flyway 만 한다.
- 번호 충돌을 막기 위해 PR 마다 번호를 확인한다 (CI 에서 중복·누락 검사 예정).
- 테스트는 Testcontainers MySQL 에 실제 마이그레이션을 적용해 실행한다.

### JWT 를 쿠키로 보관할 때 필요한 대응
- 쿠키 속성: `HttpOnly`, `Secure`, `SameSite` 지정.
- CSRF 대응 (상태 변경 요청 보호)과 CORS 허용 오리진 설정이 필요하다.
- 구체 설계(액세스/리프레시 수명, 갱신 흐름, 폐기)는 ARCHITECTURE.md 와 별도 ADR 에서 정한다.

## 후속 결정 (2026-11-13)

[ARCHITECTURE](../ARCHITECTURE.md)와 ADR-0002~0005에서 아래를 확정했다.

- 패키지 매니저 npm, 프론트 서버 상태 TanStack Query
- JWT 수명·쿠키 정책 (ADR-0002), 백엔드 패키지 구조와 루트 패키지 `com.everystudy` (ADR-0003)
- 배포는 Docker (VM 1대 + Compose), Redis는 소규모에서 불필요 (ADR-0005)
- 호스팅 업체·도메인은 아직 미정 (ARCHITECTURE 11장)
