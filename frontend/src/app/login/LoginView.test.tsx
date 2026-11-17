import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LoginView } from "./LoginView";

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn() }),
}));

const BASE_URL = "http://api.test";
const fetchMock = vi.fn<typeof fetch>();

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

function renderView(error?: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <LoginView error={error} />
    </QueryClientProvider>,
  );
}

describe("LoginView", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE_URL);
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
    replace.mockReset();
  });

  it("비회원에게 카카오·구글 로그인 버튼을 보여 주고 서버의 로그인 시작 주소로 연결한다", async () => {
    fetchMock.mockImplementation(async () =>
      json(401, { status: 401, code: "UNAUTHENTICATED" }),
    );

    renderView();

    expect(
      await screen.findByRole("link", { name: "카카오 로그인" }),
    ).toHaveAttribute("href", `${BASE_URL}/oauth2/authorization/kakao`);
    expect(
      screen.getByRole("link", { name: "Google 계정으로 로그인" }),
    ).toHaveAttribute("href", `${BASE_URL}/oauth2/authorization/google`);
  });

  it("소셜 로그인이 실패해 돌아오면 이유를 알려 준다", async () => {
    fetchMock.mockImplementation(async () =>
      json(401, { status: 401, code: "UNAUTHENTICATED" }),
    );

    renderView("social_login_failed");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "로그인을 마치지 못했어요.",
    );
  });

  it("로그인했지만 온보딩 전이면 온보딩으로 보낸다", async () => {
    fetchMock.mockImplementation(async () =>
      json(200, {
        id: 1,
        nickname: null,
        jobCode: null,
        districtCode: null,
        role: "USER",
        onboarded: false,
        joinedAt: "2026-11-18T00:00:00Z",
      }),
    );

    renderView();

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/onboarding"));
  });
});
