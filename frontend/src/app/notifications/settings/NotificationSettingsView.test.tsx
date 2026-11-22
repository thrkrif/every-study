import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NotificationSettingsView } from "./NotificationSettingsView";

const ME = {
  id: 7,
  nickname: "준비생",
  jobCode: "IT",
  districtCode: "X",
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

describe("NotificationSettingsView", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://api.test");
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  function mock(
    put: (body: Record<string, boolean>) => Response,
    initial = { push: true, email: true },
  ) {
    fetchMock.mockImplementation(async (url, init) => {
      const path = new URL(String(url)).pathname.replace("/api/v1", "");
      if (path === "/me") return json(200, ME);
      if (path === "/me/notification-settings") {
        if (init?.method === "PUT") {
          return put(JSON.parse(String(init.body)));
        }
        return json(200, initial);
      }
      return json(404, { status: 404, code: "NOT_FOUND" });
    });
  }

  function renderView() {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <NotificationSettingsView />
      </QueryClientProvider>,
    );
  }

  it("채널별 현재 설정을 보여 준다", async () => {
    mock(() => json(200, {}), { push: true, email: false });
    renderView();

    expect(
      await screen.findByRole("switch", { name: "푸시 알림" }),
    ).toBeChecked();
    expect(screen.getByRole("switch", { name: "이메일" })).not.toBeChecked();
  });

  it("끄면 바로 바뀐 것으로 보이고 그 채널만 서버에 보낸다", async () => {
    mock((body) => json(200, { push: true, email: false, ...body }));
    renderView();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("switch", { name: "이메일" }));

    expect(screen.getByRole("switch", { name: "이메일" })).not.toBeChecked();
    await vi.waitFor(() => {
      const put = fetchMock.mock.calls.find(
        ([, init]) => init?.method === "PUT",
      );
      expect(JSON.parse(String(put?.[1]?.body))).toEqual({ email: false });
    });
    expect(screen.getByRole("switch", { name: "푸시 알림" })).toBeChecked();
  });

  it("저장에 실패하면 이전 값으로 되돌리고 안내한다", async () => {
    mock(() => json(500, { status: 500, code: "INTERNAL_ERROR" }));
    renderView();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("switch", { name: "푸시 알림" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "이전 설정으로 되돌렸어요",
    );
    expect(screen.getByRole("switch", { name: "푸시 알림" })).toBeChecked();
  });

  it("비회원에게는 로그인을 안내한다", async () => {
    fetchMock.mockImplementation(async () =>
      json(401, { status: 401, code: "UNAUTHENTICATED" }),
    );
    renderView();

    expect(
      await screen.findByText("로그인하면 알림 설정을 바꿀 수 있어요."),
    ).toBeInTheDocument();
  });
});
