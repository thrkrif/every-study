import { ApiError, toApiError } from "./errors";

/**
 * 백엔드 API 를 부르는 유일한 통로 (ARCHITECTURE 6장, API 1장).
 *
 * - 인증 쿠키를 함께 보낸다 (`credentials: "include"`). 토큰은 HttpOnly 쿠키라 JS 로 읽거나 저장하지 않는다.
 * - 상태를 바꾸는 요청에는 CSRF 쿠키 값을 `X-XSRF-TOKEN` 헤더로 붙인다 (ADR-0002 이중 제출 쿠키).
 * - 실패는 `ApiError` 로 바꿔 던진다. 화면은 `error.code` 로 분기한다.
 *
 * 화면 코드에서 `fetch` 를 직접 부르지 않는다.
 */

const API_PREFIX = "/api/v1";
// Spring Security 의 CSRF 쿠키·헤더 기본 이름이다. 백엔드 설정(T-005)과 같아야 한다.
const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

export type ApiRequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  /** JSON 으로 보낼 본문. */
  body?: unknown;
  signal?: AbortSignal;
};

function baseUrl(): string {
  // Next.js 가 빌드할 때 값으로 바꿔 넣으므로 이 이름 그대로 참조해야 한다.
  const value = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!value) {
    throw new Error(
      "NEXT_PUBLIC_API_BASE_URL 이 설정되지 않았습니다 (저장소 루트의 .env.example 참고)",
    );
  }
  return value.replace(/\/+$/, "");
}

function readCookie(name: string): string | undefined {
  if (typeof document === "undefined") return undefined; // 서버 렌더링 중
  for (const pair of document.cookie.split("; ")) {
    const separator = pair.indexOf("=");
    if (separator !== -1 && pair.slice(0, separator) === name) {
      return decodeURIComponent(pair.slice(separator + 1));
    }
  }
  return undefined;
}

/**
 * `/api/v1` 아래 경로를 호출한다. 성공하면 JSON 본문을, 본문이 없으면 `undefined` 를 돌려준다.
 *
 * @param path `/api/v1` 뒤의 경로. 예: `/studies/42`
 * @throws {ApiError} 실패 응답 또는 네트워크 실패(`NETWORK_ERROR`)
 */
export async function apiFetch<T = unknown>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const method = options.method ?? "GET";
  const headers = new Headers({ Accept: "application/json" });

  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  if (!SAFE_METHODS.has(method)) {
    const csrfToken = readCookie(CSRF_COOKIE);
    if (csrfToken) headers.set(CSRF_HEADER, csrfToken);
  }

  const url = `${baseUrl()}${API_PREFIX}${path}`;
  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers,
      credentials: "include",
      body:
        options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (cause) {
    // 호출한 쪽이 취소한 것은 오류가 아니다 (TanStack Query 가 화면 이탈 시 취소한다).
    if (cause instanceof DOMException && cause.name === "AbortError")
      throw cause;
    throw new ApiError({ status: 0, code: "NETWORK_ERROR", cause });
  }

  if (!response.ok) {
    throw await toApiError(response);
  }
  const text = await response.text();
  return (text === "" ? undefined : JSON.parse(text)) as T;
}
