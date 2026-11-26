import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { HeaderMenu } from "./HeaderMenu";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace: vi.fn() }),
}));

const BASE_URL = "http://api.test";
const ME = {
  id: 1,
  nickname: "준비생",
  jobCode: "IT",
  districtCode: "GYEONGGI",
  role: "USER",
  onboarded: true,
  joinedAt: "2026-11-17T00:00:00Z",
};

const json = (status: number, body?: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const fetchMock = vi.fn<typeof fetch>();
let loggedIn: boolean;
let role: "USER" | "ADMIN";

function renderMenu() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <HeaderMenu />
    </QueryClientProvider>,
  );
}

describe("HeaderMenu", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE_URL);
    vi.stubGlobal("fetch", fetchMock);
    loggedIn = true;
    role = "USER";
    fetchMock.mockImplementation(async (url, init) => {
      const path = new URL(String(url)).pathname.replace("/api/v1", "");
      if (path === "/auth/logout" && init?.method === "POST") {
        loggedIn = false;
        return new Response(null, { status: 204 });
      }
      if (path === "/me" && loggedIn) return json(200, { ...ME, role });
      return json(401, { status: 401, code: "UNAUTHENTICATED" });
    });
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
    push.mockReset();
  });

  it("비회원에게는 로그인을 보여 준다", async () => {
    loggedIn = false;
    renderMenu();

    expect(await screen.findByRole("link", { name: "로그인" })).toHaveAttribute(
      "href",
      "/login",
    );
  });

  it("회원이 로그아웃하면 서버에 알리고 비회원 메뉴로 바뀐 뒤 목록으로 간다", async () => {
    const user = userEvent.setup();
    renderMenu();
    expect(
      await screen.findByRole("link", { name: "스터디 만들기" }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "로그아웃" }));

    expect(
      await screen.findByRole("link", { name: "로그인" }),
    ).toBeInTheDocument();
    expect(
      fetchMock.mock.calls.some(([url]) =>
        String(url).endsWith("/auth/logout"),
      ),
    ).toBe(true);
    expect(push).toHaveBeenCalledWith("/");
  });

  it("운영자에게만 운영 링크를 보여 준다", async () => {
    const first = renderMenu();
    await screen.findByRole("link", { name: "마이페이지" });
    expect(
      screen.queryByRole("link", { name: "운영" }),
    ).not.toBeInTheDocument();
    first.unmount();

    role = "ADMIN";
    renderMenu();
    expect(await screen.findByRole("link", { name: "운영" })).toHaveAttribute(
      "href",
      "/admin",
    );
  });
});
