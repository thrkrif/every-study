import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MyPageView } from "./MyPageView";

const BASE_URL = "http://api.test";

const META = {
  topics: [],
  jobs: [{ code: "IT", name: "IT·개발", children: [] }],
  regions: [
    {
      code: "GYEONGGI",
      name: "경기",
      children: [{ code: "SUWON_PALDAL", name: "팔달구", children: [] }],
    },
  ],
  study: {},
  checkin: { radiusMeters: 150 },
};

const ME = {
  id: 7,
  nickname: "나",
  jobCode: "IT",
  districtCode: "SUWON_PALDAL",
  role: "USER",
  onboarded: true,
  joinedAt: "2026-11-01T00:00:00Z",
};

const PROFILE = {
  id: 7,
  nickname: "나",
  jobCode: "IT",
  districtCode: "SUWON_PALDAL",
  joinedAt: "2026-11-01T00:00:00Z",
  attendance: {
    attendanceRate: 90,
    windowCount: 10,
    noShow: 1,
    late: 2,
    lateCancel: 0,
    isNewcomer: false,
  },
  hostStats: { hostedCount: 2, leftWithinWindowCount: 0, showCountOnly: true },
};

const record = (id: number, type: string, over = {}) => ({
  eventId: id,
  studyId: id * 10,
  studyTitle: `스터디 ${id}`,
  type,
  occurredAt: "2026-11-16T05:00:00Z",
  appealable: false,
  appealDueAt: null,
  appealId: null,
  ...over,
});

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const fetchMock = vi.fn<typeof fetch>();

function renderView() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MyPageView />
    </QueryClientProvider>,
  );
}

describe("MyPageView", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE_URL);
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  function mock(
    pages: Record<string, { items: unknown[]; nextCursor: string | null }>,
  ) {
    fetchMock.mockImplementation(async (url) => {
      const parsed = new URL(String(url));
      const path = parsed.pathname.replace("/api/v1", "");
      if (path === "/me") return json(200, ME);
      if (path === "/meta/options") return json(200, META);
      if (path === "/users/7") return json(200, PROFILE);
      if (path === "/me/attendance") {
        const key = parsed.searchParams.get("cursor") ?? "first";
        return json(200, pages[key] ?? { items: [], nextCursor: null });
      }
      return json(404, { status: 404, code: "NOT_FOUND" });
    });
  }

  it("비회원이면 로그인을 안내한다", async () => {
    fetchMock.mockImplementation(async () =>
      json(401, { status: 401, code: "UNAUTHENTICATED" }),
    );
    renderView();

    expect(await screen.findByRole("link", { name: "로그인" })).toHaveAttribute(
      "href",
      "/login",
    );
  });

  it("내 지표와 지난 출석 기록을 보여 주고, 기록은 그 스터디로 이어진다", async () => {
    mock({
      first: {
        items: [record(2, "NO_SHOW_CANDIDATE"), record(1, "ATTENDED")],
        nextCursor: null,
      },
    });
    renderView();

    expect(
      await screen.findByRole("heading", { name: "나" }),
    ).toBeInTheDocument();
    expect(
      await screen.findByText("출석률 90% · 최근 10회 중 노쇼 1회 · 지각 2회"),
    ).toBeInTheDocument();
    expect(screen.getByText("주최 2회 중 직전 이탈 0회")).toBeInTheDocument();

    const list = await screen.findByRole("list");
    const rows = within(list).getAllByRole("link");
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("노쇼 후보");
    expect(rows[0]).toHaveTextContent("스터디 2");
    expect(rows[0]).toHaveAttribute("href", "/studies/20");
    expect(rows[1]).toHaveTextContent("출석");
    expect(screen.getByRole("link", { name: "내 스터디" })).toHaveAttribute(
      "href",
      "/me/studies",
    );
  });

  it("기록이 없으면 안내하고, 다음 페이지가 있으면 더 보기로 이어 받는다", async () => {
    mock({ first: { items: [], nextCursor: null } });
    const first = renderView();
    expect(
      await screen.findByText("아직 출석 기록이 없어요."),
    ).toBeInTheDocument();
    first.unmount();

    mock({
      first: { items: [record(2, "LATE")], nextCursor: "c1" },
      c1: { items: [record(1, "ATTENDED")], nextCursor: null },
    });
    renderView();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "더 보기" }));

    expect(await screen.findByText("스터디 1")).toBeInTheDocument();
    expect(screen.getByText("스터디 2")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "더 보기" }),
    ).not.toBeInTheDocument();
  });
});
