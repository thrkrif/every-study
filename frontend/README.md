# frontend

Next.js(App Router) 웹 앱. 구조와 규칙은 [ARCHITECTURE 6장](../docs/ARCHITECTURE.md), 버전은 [ADR-0001](../docs/adr/0001-tech-stack.md), 화면 디자인은 [DESIGN](../docs/DESIGN.md)이 원본이다.

## 준비

- Node 24 (24.15 이상). `.nvmrc`에 적혀 있다. 테스트 환경(jsdom)이 이 버전을 요구해서 더 낮으면 `npm test`가 시작되지 않는다.
  - nvm을 쓰면 이 디렉토리에서 `nvm install`(처음 한 번), 이후에는 `nvm use`로 맞춘다. `node -v`로 확인한다.
- 저장소 루트의 `.env.example`을 `.env`로 복사한다. 프론트는 `NEXT_PUBLIC_API_BASE_URL`(백엔드 주소)을 쓴다.

MySQL·백엔드까지 한 번에 띄우려면 저장소 루트에서 `bash tools/dev.sh` (루트 README "로컬에서 전체 실행").

## 명령 (이 디렉토리에서)

| 하려는 일                           | 명령                 |
| ----------------------------------- | -------------------- |
| 의존성 설치 (잠금 파일 그대로)      | `npm ci`             |
| 개발 서버 (`http://localhost:3000`) | `npm run dev`        |
| 린트와 포맷 검사                    | `npm run lint`       |
| 포맷 자동 수정                      | `npm run format`     |
| 타입 검사                           | `npm run typecheck`  |
| 테스트 (한 번 실행)                 | `npm test`           |
| 테스트 (파일 변경 감시)             | `npm run test:watch` |
| 운영 빌드                           | `npm run build`      |

CI는 `npm ci` → `npm run lint` → `npm test` → `npm run build` 순서로 돈다.

## 환경변수

환경 파일은 저장소 루트에 하나만 둔다. `next.config.ts`가 루트의 `.env`를 읽으므로 `frontend/` 안에 따로 만들지 않는다.

- 브라우저 코드에는 `NEXT_PUBLIC_`으로 시작하는 값만 들어간다. 이 값은 누구나 볼 수 있으므로 비밀 값을 `NEXT_PUBLIC_`으로 만들지 않는다.
- `NEXT_PUBLIC_` 값은 **빌드할 때** 코드에 박힌다. 값을 바꾸면 다시 빌드해야 한다.

## 구조

```
src/
  app/            화면(라우트). layout.tsx 가 Providers(TanStack Query)로 감싼다
  app/design/     토큰·공통 컴포넌트 확인 화면 (개발 서버에서만 /design)
  components/     공통 컴포넌트 (좌석 줄, 출석 기록 줄, 버튼, 상태 칩)
  lib/api/        백엔드 호출. client.ts(apiFetch), errors.ts(ApiError, 오류 코드), me.ts, meta.ts
  lib/session.ts  로그인 상태(useSession)와 메타 옵션(useMetaOptions) 조회
  lib/studyFormat.ts 스터디 목록·상세의 표시 규칙 (KST 시각, 상태 칩, 지표 문장)
  lib/kakaoMap.ts 카카오맵 SDK 로드와 장소 검색 (키 이름과 준비 방법은 루트의 키 목록 파일)
  lib/devPlaces.ts 개발 서버 전용 예시 장소. 지도를 불러오지 못하면 장소 검색이 이것을 준다 (PLAN H-2 보류)
public/auth/      카카오·구글 로그인 버튼 로고 (각 공급자 배포 리소스, 바꾸지 않는다)
  styles/         tokens.css(디자인 토큰), globals.css(기본 스타일)
```

## 스타일 규칙

- 색·글자·간격은 `src/styles/tokens.css`의 CSS 변수만 쓴다. 원본은 [DESIGN 4장](../docs/DESIGN.md)이고, `tokens.test.ts`가 두 곳의 값이 같은지 확인한다.
- 컴포넌트 스타일은 CSS Modules(`*.module.css`)로 대상 옆에 둔다.
- 화면 PR마다 DESIGN 8장 점검 목록을 확인한다.

## API 호출 규칙

- 화면 코드에서 `fetch`를 직접 부르지 않고 `apiFetch`(`src/lib/api/client.ts`)만 쓴다. 쿠키 포함, CSRF 헤더, 오류 변환이 여기 한 곳에 있다.
- 토큰은 HttpOnly 쿠키라 JS에서 읽거나 `localStorage`에 저장하지 않는다 (ADR-0002).
- 실패는 `ApiError`로 온다. `isApiError(error, "STUDY_FULL")`처럼 `code`로 분기하고, 사용자 문구는 화면이 정한다 (API 1장).
- 오류 코드 목록(`errors.ts`)의 원본은 API 명세 1장의 표다. 표가 바뀌면 함께 고친다.

## 테스트 규칙

- 테스트 파일은 대상 옆에 `<대상>.test.ts(x)`로 둔다.
- 테스트 이름은 한국어로 기대 동작을 한 문장으로 적는다.
- 컴포넌트는 Testing Library로 사용자가 보는 것(글자, 역할)을 기준으로 검증한다.
