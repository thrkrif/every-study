import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch, socialLoginUrl } from "./client";
import { ApiError, isApiError } from "./errors";

const BASE_URL = "http://api.test";

function jsonResponse(
  status: number,
  body: unknown,
  contentType = "application/json",
) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": contentType },
  });
}

function problemResponse(status: number, body: Record<string, unknown>) {
  return jsonResponse(status, { status, ...body }, "application/problem+json");
}

function clearCookies() {
  for (const cookie of document.cookie.split(";")) {
    const name = cookie.split("=")[0].trim();
    if (name)
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
  }
}

describe("apiFetch", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE_URL);
    vi.stubGlobal("fetch", fetchMock);
    // 응답 본문은 한 번만 읽을 수 있으므로 호출마다 새로 만든다.
    fetchMock.mockImplementation(async () => jsonResponse(200, {}));
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
    clearCookies();
  });

  function lastRequest() {
    const [url, init] = fetchMock.mock.calls.at(-1)!;
    return {
      url: String(url),
      init: init!,
      headers: new Headers(init!.headers),
    };
  }

  describe("요청", () => {
    it("기본 주소와 /api/v1 을 붙이고 쿠키를 포함해 보낸다", async () => {
      await apiFetch("/studies");

      const { url, init } = lastRequest();
      expect(url).toBe("http://api.test/api/v1/studies");
      expect(init.credentials).toBe("include");
      expect(init.method).toBe("GET");
    });

    it("상태를 바꾸는 요청에는 CSRF 쿠키 값을 X-XSRF-TOKEN 헤더로 붙인다", async () => {
      document.cookie = "XSRF-TOKEN=csrf-abc; path=/";

      for (const method of ["POST", "PUT", "PATCH", "DELETE"] as const) {
        await apiFetch("/studies", { method });

        expect(lastRequest().headers.get("X-XSRF-TOKEN")).toBe("csrf-abc");
      }
    });

    it("URL 인코딩된 CSRF 쿠키 값은 풀어서 붙인다", async () => {
      document.cookie = "XSRF-TOKEN=a%2Bb%3D; path=/";

      await apiFetch("/studies", { method: "POST" });

      expect(lastRequest().headers.get("X-XSRF-TOKEN")).toBe("a+b=");
    });

    it("조회 요청에는 CSRF 헤더를 붙이지 않는다", async () => {
      document.cookie = "XSRF-TOKEN=csrf-abc; path=/";

      await apiFetch("/studies");

      expect(lastRequest().headers.has("X-XSRF-TOKEN")).toBe(false);
    });

    it("CSRF 쿠키가 없으면 헤더 없이 보낸다", async () => {
      await apiFetch("/studies", { method: "POST" });

      expect(lastRequest().headers.has("X-XSRF-TOKEN")).toBe(false);
    });

    it("본문은 JSON 으로 보낸다", async () => {
      await apiFetch("/studies", { method: "POST", body: { title: "스터디" } });

      const { init, headers } = lastRequest();
      expect(headers.get("Content-Type")).toBe("application/json");
      expect(init.body).toBe('{"title":"스터디"}');
    });

    it("Authorization 헤더를 쓰지 않고 토큰을 저장소에 두지 않는다", async () => {
      document.cookie = "XSRF-TOKEN=csrf-abc; path=/";

      await apiFetch("/studies", { method: "POST", body: {} });

      expect(lastRequest().headers.has("Authorization")).toBe(false);
      expect(localStorage.length).toBe(0);
      expect(sessionStorage.length).toBe(0);
    });

    it("기본 주소가 설정되지 않았으면 요청하지 않고 실패한다", async () => {
      vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "");

      await expect(apiFetch("/studies")).rejects.toThrow(
        "NEXT_PUBLIC_API_BASE_URL",
      );
      expect(fetchMock).not.toHaveBeenCalled();
    });
  });

  describe("응답", () => {
    it("성공하면 JSON 본문을 돌려준다", async () => {
      fetchMock.mockResolvedValue(
        jsonResponse(200, { items: [], nextCursor: null }),
      );

      await expect(apiFetch("/studies")).resolves.toEqual({
        items: [],
        nextCursor: null,
      });
    });

    it("본문이 없는 성공 응답은 undefined 를 돌려준다", async () => {
      fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
      await expect(
        apiFetch("/me", { method: "DELETE" }),
      ).resolves.toBeUndefined();

      fetchMock.mockResolvedValue(new Response("", { status: 200 }));
      await expect(
        apiFetch("/auth/logout", { method: "POST" }),
      ).resolves.toBeUndefined();
    });
  });

  describe("오류 변환", () => {
    async function failWith(response: Response) {
      fetchMock.mockResolvedValue(response);
      const error = await apiFetch("/studies/1/join", { method: "POST" }).catch(
        (e) => e,
      );
      expect(error).toBeInstanceOf(ApiError);
      return error as ApiError;
    }

    it("problem details 의 code 로 분기할 수 있다", async () => {
      const error = await failWith(
        problemResponse(409, {
          code: "STUDY_FULL",
          title: "정원이 찼습니다",
          traceId: "t-1",
        }),
      );

      expect(error.status).toBe(409);
      expect(error.code).toBe("STUDY_FULL");
      expect(error.traceId).toBe("t-1");
      expect(isApiError(error, "STUDY_FULL")).toBe(true);
      expect(isApiError(error, "ALREADY_JOINED")).toBe(false);
    });

    it("VALIDATION_FAILED 는 필드 오류 목록을 담는다", async () => {
      const error = await failWith(
        problemResponse(400, {
          code: "VALIDATION_FAILED",
          errors: [{ field: "nickname", message: "필수 값입니다" }],
        }),
      );

      expect(error.code).toBe("VALIDATION_FAILED");
      expect(error.fieldErrors).toEqual([
        { field: "nickname", message: "필수 값입니다" },
      ]);
    });

    it("USER_RESTRICTED 는 제한 종료 시각을 담는다", async () => {
      const error = await failWith(
        problemResponse(403, {
          code: "USER_RESTRICTED",
          until: "2026-12-01T00:00:00Z",
        }),
      );

      expect(error.code).toBe("USER_RESTRICTED");
      expect(error.until).toBe("2026-12-01T00:00:00Z");
    });

    it("code 가 없는 HTTP 표준 오류는 status 로만 구분한다", async () => {
      const error = await failWith(
        problemResponse(405, { title: "Method Not Allowed" }),
      );

      expect(error.status).toBe(405);
      expect(error.code).toBeUndefined();
      expect(error.fieldErrors).toEqual([]);
    });

    it("JSON 이 아닌 오류 응답도 status 를 가진 ApiError 로 바꾼다", async () => {
      const error = await failWith(
        new Response("<html>Bad Gateway</html>", { status: 502 }),
      );

      expect(error.status).toBe(502);
      expect(error.code).toBeUndefined();
    });

    it("네트워크 실패는 NETWORK_ERROR 로 바꾼다", async () => {
      fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));

      const error = (await apiFetch("/studies").catch((e) => e)) as ApiError;

      expect(error).toBeInstanceOf(ApiError);
      expect(error.code).toBe("NETWORK_ERROR");
      expect(error.status).toBe(0);
    });

    it("호출한 쪽이 취소한 요청은 변환하지 않고 그대로 던진다", async () => {
      const abort = new DOMException("Aborted", "AbortError");
      fetchMock.mockRejectedValue(abort);

      await expect(apiFetch("/studies")).rejects.toBe(abort);
    });

    it("isApiError 는 ApiError 가 아닌 값에 false 를 돌려준다", () => {
      expect(isApiError(new Error("x"))).toBe(false);
      expect(isApiError(undefined, "STUDY_FULL")).toBe(false);
    });
  });

  describe("인증 갱신 (API 1장: 401 후 refresh 1회 재시도)", () => {
    const unauthenticated = () =>
      problemResponse(401, { code: "UNAUTHENTICATED" });
    const paths = () =>
      fetchMock.mock.calls.map(([url, init]) => [
        init?.method,
        String(url).replace(`${BASE_URL}/api/v1`, ""),
      ]);

    it("401 이면 갱신을 한 번 보내고, 성공하면 원래 요청을 다시 보낸다", async () => {
      let meCalls = 0;
      fetchMock.mockImplementation(async (url) => {
        const path = String(url);
        if (path.endsWith("/auth/refresh"))
          return new Response(null, { status: 204 });
        meCalls += 1;
        return meCalls === 1 ? unauthenticated() : jsonResponse(200, { id: 1 });
      });

      await expect(apiFetch("/me")).resolves.toEqual({ id: 1 });
      expect(paths()).toEqual([
        ["GET", "/me"],
        ["POST", "/auth/refresh"],
        ["GET", "/me"],
      ]);
    });

    it("갱신이 실패하면 다시 보내지 않고 UNAUTHENTICATED 를 던진다", async () => {
      fetchMock.mockImplementation(async () => unauthenticated());

      const error = (await apiFetch("/me").catch((e) => e)) as ApiError;

      expect(error.code).toBe("UNAUTHENTICATED");
      expect(paths()).toEqual([
        ["GET", "/me"],
        ["POST", "/auth/refresh"],
      ]);
    });

    it("다시 보낸 요청도 401 이면 갱신을 또 하지 않는다", async () => {
      fetchMock.mockImplementation(async (url) =>
        String(url).endsWith("/auth/refresh")
          ? new Response(null, { status: 204 })
          : unauthenticated(),
      );

      await expect(apiFetch("/me")).rejects.toMatchObject({
        code: "UNAUTHENTICATED",
      });
      expect(
        paths().filter(([, path]) => path === "/auth/refresh"),
      ).toHaveLength(1);
    });

    it("동시에 여러 요청이 401 을 받아도 갱신은 한 번만 보낸다", async () => {
      let refreshed = false;
      fetchMock.mockImplementation(async (url) => {
        if (String(url).endsWith("/auth/refresh")) {
          await new Promise((resolve) => setTimeout(resolve, 10));
          refreshed = true;
          return new Response(null, { status: 204 });
        }
        return refreshed ? jsonResponse(200, {}) : unauthenticated();
      });

      await Promise.all([
        apiFetch("/me"),
        apiFetch("/meta/options"),
        apiFetch("/studies"),
      ]);

      expect(
        paths().filter(([, path]) => path === "/auth/refresh"),
      ).toHaveLength(1);
    });

    it("갱신·로그아웃 요청 자체의 401 에는 갱신하지 않는다", async () => {
      fetchMock.mockImplementation(async () => unauthenticated());

      await expect(
        apiFetch("/auth/logout", { method: "POST" }),
      ).rejects.toMatchObject({
        code: "UNAUTHENTICATED",
      });
      expect(paths()).toEqual([["POST", "/auth/logout"]]);
    });

    it("403 CSRF_INVALID 이면 새 CSRF 쿠키를 다시 읽어 한 번 다시 보낸다", async () => {
      let calls = 0;
      fetchMock.mockImplementation(async () => {
        calls += 1;
        if (calls === 1) {
          document.cookie = "XSRF-TOKEN=fresh; path=/";
          return problemResponse(403, { code: "CSRF_INVALID" });
        }
        return new Response(null, { status: 204 });
      });

      await apiFetch("/me/profile", { method: "PUT", body: {} });

      expect(fetchMock).toHaveBeenCalledTimes(2);
      expect(lastRequest().headers.get("X-XSRF-TOKEN")).toBe("fresh");
    });
  });

  it("소셜 로그인 주소는 /api/v1 이 아니라 /oauth2 아래다", () => {
    expect(socialLoginUrl("kakao")).toBe(
      `${BASE_URL}/oauth2/authorization/kakao`,
    );
  });
});
