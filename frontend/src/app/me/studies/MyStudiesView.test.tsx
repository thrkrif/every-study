import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MyStudiesView } from "./MyStudiesView";

const BASE_URL = "http://api.test";
// KST 2026-11-18(수) 10:00
const NOW = new Date("2026-11-18T01:00:00Z");

const META = {
  topics: [],
  jobs: [],
  regions: [
    {
      code: "GYEONGGI",
      name: "경기",
      children: [
        {
          code: "SUWON",
          name: "수원시",
          children: [{ code: "SUWON_PALDAL", name: "팔달구", children: [] }],
        },
      ],
    },
  ],
  study: {
    capacityMin: 2,
    capacityMax: 6,
    openableDays: 14,
    startMinuteUnit: 10,
    durationMinuteUnit: 10,
    durationMinMinutes: 30,
    minAttendanceRates: [50],
  },
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

const study = (id: number, over: Record<string, unknown> = {}) => ({
  id,
  title: `스터디 ${id}`,
  topicCode: "JOB_PREP",
  districtCode: "SUWON_PALDAL",
  startAt: "2026-11-20T05:00:00Z",
  durationMin: 120,
  capacity: 4,
  memberCount: 2,
  status: "ACTIVE",
  role: "MEMBER",
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
      <MyStudiesView now={() => NOW} />
    </QueryClientProvider>,
  );
}

const listCalls = () =>
  fetchMock.mock.calls
    .map(([url]) => new URL(String(url)))
    .filter((url) => url.pathname === "/api/v1/me/studies");

describe("MyStudiesView", () => {
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
      if (path === "/me/studies") {
        const key = [
          parsed.searchParams.get("state"),
          parsed.searchParams.get("role") ?? "all",
          parsed.searchParams.get("cursor") ?? "first",
        ].join("/");
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

  it("내 역할과 일시를 보이고, 주최하는 진행 중 스터디에만 관리 링크가 있다", async () => {
    mock({
      "upcoming/all/first": {
        items: [
          study(1, { role: "HOST" }),
          study(2),
          study(3, { role: "HOST", startAt: "2026-11-18T00:00:00Z" }),
        ],
        nextCursor: null,
      },
    });
    renderView();

    expect(await screen.findByText("스터디 1")).toBeInTheDocument();
    // 역할 필터 칩의 "주최"와 구분하려고 배지만 센다
    expect(
      screen.getAllByText("주최", { selector: '[data-role="HOST"]' }),
    ).toHaveLength(2);
    expect(
      screen.getByText("참여", { selector: '[data-role="MEMBER"]' }),
    ).toBeInTheDocument();
    expect(screen.getByText("진행 중")).toBeInTheDocument();
    expect(screen.getAllByText("수원시 팔달구").length).toBe(3);
    expect(screen.getAllByRole("link", { name: "관리" })[0]).toHaveAttribute(
      "href",
      "/studies/1/manage",
    );
    expect(screen.getByRole("link", { name: /스터디 2/ })).toHaveAttribute(
      "href",
      "/studies/2",
    );
    // 참여자 스터디에는 관리 링크가 없다: 주최 2건에만 있다
    expect(screen.getAllByRole("link", { name: "관리" })).toHaveLength(2);
  });

  it("역할과 지난 모임으로 바꾸면 그 조건으로 다시 받는다", async () => {
    mock({
      "upcoming/all/first": { items: [study(1)], nextCursor: null },
      "upcoming/host/first": {
        items: [study(5, { role: "HOST" })],
        nextCursor: null,
      },
      "past/all/first": {
        items: [
          study(9, { status: "ENDED" }),
          study(8, { status: "CANCELLED", role: "HOST" }),
        ],
        nextCursor: null,
      },
    });
    renderView();
    await screen.findByText("스터디 1");

    await userEvent.click(screen.getByRole("radio", { name: "주최" }));
    expect(await screen.findByText("스터디 5")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("radio", { name: "전체" }));
    await userEvent.click(screen.getByRole("radio", { name: "지난 모임" }));
    expect(await screen.findByText("스터디 9")).toBeInTheDocument();
    expect(screen.getByText("종료")).toBeInTheDocument();
    expect(screen.getByText("취소됨")).toBeInTheDocument();
    // 끝난 스터디는 주최여도 관리할 수 없다
    expect(
      screen.queryByRole("link", { name: "관리" }),
    ).not.toBeInTheDocument();
    const last = listCalls().at(-1)!;
    expect(last.searchParams.get("state")).toBe("past");
    expect(last.searchParams.get("role")).toBeNull();
  });

  it("비어 있으면 안내하고, 다음 페이지는 더 보기로 이어 받는다", async () => {
    mock({
      "upcoming/all/first": { items: [study(1)], nextCursor: "c1" },
      "upcoming/all/c1": { items: [study(2)], nextCursor: null },
      "past/all/first": { items: [], nextCursor: null },
    });
    renderView();

    await userEvent.click(
      await screen.findByRole("button", { name: "더 보기" }),
    );
    expect(await screen.findByText("스터디 2")).toBeInTheDocument();
    expect(screen.getByText("스터디 1")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("radio", { name: "지난 모임" }));
    expect(
      await screen.findByText("지난 스터디가 없어요."),
    ).toBeInTheDocument();
  });

  it("불러오지 못하면 다시 시도할 수 있다", async () => {
    fetchMock.mockImplementation(async (url) => {
      const path = new URL(String(url)).pathname.replace("/api/v1", "");
      if (path === "/me") return json(200, ME);
      if (path === "/meta/options") return json(200, META);
      return json(500, { status: 500, code: "INTERNAL_ERROR" });
    });
    renderView();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "내 스터디를 불러오지 못했어요.",
    );
    expect(
      screen.getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
  });
});
