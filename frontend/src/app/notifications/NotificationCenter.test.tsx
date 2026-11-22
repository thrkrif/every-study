import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NotificationCenter } from "./NotificationCenter";

const BASE_URL = "http://api.test";
// KST 2026-11-18(수) 10:00
const NOW = new Date("2026-11-18T01:00:00Z");

const ME = {
  id: 9,
  nickname: "나",
  jobCode: "IT",
  districtCode: "SUWON_PALDAL",
  role: "USER",
  onboarded: true,
  joinedAt: "2026-11-01T00:00:00Z",
};

const item = (id: number, over: Record<string, unknown> = {}) => ({
  id,
  type: "CHECKIN_OPEN",
  studyId: 3,
  title: `알림 ${id}`,
  body: `내용 ${id}`,
  read: false,
  notifiedAt: "2026-11-18T00:30:00Z",
  ...over,
});

const json = (status: number, body?: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const fetchMock = vi.fn<typeof fetch>();

function renderCenter() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <NotificationCenter now={() => NOW} />
    </QueryClientProvider>,
  );
}

const calls = (method: string, path: string) =>
  fetchMock.mock.calls.filter(
    ([url, init]) =>
      new URL(String(url)).pathname === `/api/v1${path}` &&
      (init?.method ?? "GET") === method,
  );

describe("NotificationCenter", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE_URL);
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  /** 로그인한 회원으로 응답한다. `pages` 는 커서 순서대로 준다 */
  function mockMember(
    pages: { items: unknown[]; nextCursor: string | null }[],
    extra: (path: string, method: string) => Response | undefined = () =>
      undefined,
  ) {
    fetchMock.mockImplementation(async (url, init) => {
      const parsed = new URL(String(url));
      const path = parsed.pathname.replace("/api/v1", "");
      const method = init?.method ?? "GET";
      const custom = extra(path, method);
      if (custom) return custom;
      if (path === "/me") return json(200, ME);
      if (path === "/me/notifications" && method === "GET") {
        const cursor = parsed.searchParams.get("cursor");
        return json(200, pages[cursor ? Number(cursor) : 0]);
      }
      if (path.startsWith("/me/notifications/") && method === "POST")
        return new Response(null, { status: 204 });
      return json(404, { status: 404, code: "NOT_FOUND" });
    });
  }

  it("비회원이면 로그인을 안내한다", async () => {
    fetchMock.mockImplementation(async () =>
      json(401, { status: 401, code: "UNAUTHENTICATED" }),
    );
    renderCenter();

    expect(await screen.findByRole("link", { name: "로그인" })).toHaveAttribute(
      "href",
      "/login",
    );
  });

  it("알림을 최근 것부터 보여 주고, 읽지 않은 알림을 구분한다", async () => {
    mockMember([
      {
        items: [
          item(2),
          item(1, { read: true, notifiedAt: "2026-11-17T03:00:00Z" }),
        ],
        nextCursor: null,
      },
    ]);
    renderCenter();

    expect(await screen.findByText("알림 2")).toBeInTheDocument();
    expect(screen.getByText("내용 2")).toBeInTheDocument();
    expect(screen.getByText("오늘 오전 9:30")).toBeInTheDocument();
    expect(
      screen.getByText("11월 17일 (화) 오후 12:00", { exact: false }),
    ).toBeInTheDocument();
    // 읽지 않은 알림에만 "읽지 않음" 이 붙는다
    expect(screen.getAllByText("읽지 않음", { exact: false })).toHaveLength(1);
  });

  it("스터디가 연결된 알림을 열면 읽음으로 바꾸고 상세로 이동한다", async () => {
    mockMember([{ items: [item(7, { studyId: 3 })], nextCursor: null }]);
    renderCenter();

    const link = await screen.findByRole("link", { name: /알림 7/ });
    expect(link).toHaveAttribute("href", "/studies/3");
    await userEvent.click(link);

    // 읽지 않음 표시가 사라지고 서버에 읽음이 간다
    await waitFor(() =>
      expect(screen.queryByText("읽지 않음", { exact: false })).toBeNull(),
    );
    expect(calls("POST", "/me/notifications/7/read")).toHaveLength(1);
  });

  it("스터디가 없는 알림은 버튼이고 누르면 읽음만 바뀐다", async () => {
    mockMember([{ items: [item(5, { studyId: null })], nextCursor: null }]);
    renderCenter();

    await userEvent.click(
      await screen.findByRole("button", { name: /알림 5/ }),
    );

    await waitFor(() =>
      expect(screen.queryByText("읽지 않음", { exact: false })).toBeNull(),
    );
    expect(calls("POST", "/me/notifications/5/read")).toHaveLength(1);
  });

  it("읽음 요청이 실패하면 읽지 않음으로 되돌린다", async () => {
    mockMember(
      [{ items: [item(5, { studyId: null })], nextCursor: null }],
      (path, method) =>
        path === "/me/notifications/5/read" && method === "POST"
          ? json(500, { status: 500, code: "INTERNAL_ERROR" })
          : undefined,
    );
    renderCenter();

    await userEvent.click(
      await screen.findByRole("button", { name: /알림 5/ }),
    );

    await waitFor(() =>
      expect(calls("POST", "/me/notifications/5/read")).toHaveLength(1),
    );
    expect(
      await screen.findByText("읽지 않음", { exact: false }),
    ).toBeInTheDocument();
  });

  it("모두 읽음을 누르면 전부 읽음이 되고 버튼이 꺼진다", async () => {
    mockMember([{ items: [item(2), item(1)], nextCursor: null }]);
    renderCenter();
    await screen.findByText("알림 2");
    expect(screen.getAllByText("읽지 않음", { exact: false })).toHaveLength(2);

    await userEvent.click(screen.getByRole("button", { name: "모두 읽음" }));

    await waitFor(() =>
      expect(screen.queryByText("읽지 않음", { exact: false })).toBeNull(),
    );
    expect(calls("POST", "/me/notifications/read-all")).toHaveLength(1);
    expect(screen.getByRole("button", { name: "모두 읽음" })).toBeDisabled();
  });

  it("다음 페이지가 있으면 더 보기로 이어 받는다", async () => {
    mockMember([
      { items: [item(3)], nextCursor: "1" },
      { items: [item(2)], nextCursor: null },
    ]);
    renderCenter();

    await userEvent.click(
      await screen.findByRole("button", { name: "더 보기" }),
    );

    expect(await screen.findByText("알림 2")).toBeInTheDocument();
    expect(screen.getByText("알림 3")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "더 보기" })).toBeNull();
  });

  it("알림이 없으면 빈 상태를 보여 준다", async () => {
    mockMember([{ items: [], nextCursor: null }]);
    renderCenter();

    expect(
      await screen.findByText("아직 받은 알림이 없어요."),
    ).toBeInTheDocument();
    expect(
      within(document.body).getByRole("button", { name: "모두 읽음" }),
    ).toBeDisabled();
  });

  it("목록을 불러오지 못하면 다시 시도할 수 있다", async () => {
    mockMember([], (path, method) =>
      path === "/me/notifications" && method === "GET"
        ? json(500, { status: 500, code: "INTERNAL_ERROR" })
        : undefined,
    );
    renderCenter();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "알림을 불러오지 못했어요.",
    );
    expect(
      screen.getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
  });
});
