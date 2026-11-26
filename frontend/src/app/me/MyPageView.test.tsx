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
    { code: "SEOUL", name: "서울", children: [] },
    { code: "GYEONGGI", name: "경기", children: [] },
  ],
  study: {},
  checkin: { radiusMeters: 150 },
};

const ME = {
  id: 7,
  nickname: "나",
  jobCode: "IT",
  districtCode: "GYEONGGI",
  role: "USER",
  onboarded: true,
  joinedAt: "2026-11-01T00:00:00Z",
};

const PROFILE = {
  id: 7,
  nickname: "나",
  jobCode: "IT",
  districtCode: "GYEONGGI",
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
    appealResponse?: () => Response,
  ) {
    fetchMock.mockImplementation(async (url, init) => {
      const parsed = new URL(String(url));
      const path = parsed.pathname.replace("/api/v1", "");
      if (init?.method === "POST" && path.endsWith("/appeals")) {
        return (
          appealResponse?.() ?? json(404, { status: 404, code: "NOT_FOUND" })
        );
      }
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

  describe("이의 신청", () => {
    const due = "2026-11-18T05:00:00Z";
    const appealable = (id: number) =>
      record(id, "NO_SHOW_CANDIDATE", { appealable: true, appealDueAt: due });

    const posts = () =>
      fetchMock.mock.calls.filter(([, init]) => init?.method === "POST");

    it("기한이 지났거나 이미 낸 판정에는 신청 버튼이 없다", async () => {
      mock({
        first: {
          items: [
            record(3, "NO_SHOW_CANDIDATE"),
            record(2, "LATE", { appealId: 5 }),
            appealable(1),
          ],
          nextCursor: null,
        },
      });
      renderView();

      await screen.findByText("스터디 3");
      expect(screen.getAllByRole("button", { name: "이의 신청" })).toHaveLength(
        1,
      );
      expect(screen.getByRole("link", { name: "이의 내역" })).toHaveAttribute(
        "href",
        "/me/appeals",
      );
      expect(
        screen.getByText(/11월 18일 \(수\) 오후 2:00까지/),
      ).toBeInTheDocument();
    });

    it("사유를 적어 신청하면 서버에 보내고 입력칸을 닫는다", async () => {
      mock({ first: { items: [appealable(1)], nextCursor: null } }, () =>
        json(201, { id: 9, status: "OPEN" }),
      );
      renderView();
      const user = userEvent.setup();

      await user.click(
        await screen.findByRole("button", { name: "이의 신청" }),
      );
      await user.type(
        screen.getByLabelText(/이의 사유/),
        "  지하철이 멈췄어요  ",
      );
      await user.click(screen.getByRole("button", { name: "이의 신청" }));

      await vi.waitFor(() => expect(posts()).toHaveLength(1));
      const [url, init] = posts()[0];
      expect(String(url)).toContain("/attendance-events/1/appeals");
      expect(JSON.parse(String(init?.body))).toEqual({
        reason: "지하철이 멈췄어요",
      });
      await vi.waitFor(() =>
        expect(screen.queryByLabelText(/이의 사유/)).not.toBeInTheDocument(),
      );
    });

    it("사유가 비어 있으면 보내지 않고, 기한이 지났다는 응답(APPEAL_CLOSED)은 안내한다", async () => {
      mock({ first: { items: [appealable(1)], nextCursor: null } }, () =>
        json(409, { status: 409, code: "APPEAL_CLOSED" }),
      );
      renderView();
      const user = userEvent.setup();

      await user.click(
        await screen.findByRole("button", { name: "이의 신청" }),
      );
      await user.click(screen.getByRole("button", { name: "이의 신청" }));
      expect(
        await screen.findByText("이의 사유를 입력해 주세요."),
      ).toBeInTheDocument();
      expect(posts()).toHaveLength(0);

      await user.type(screen.getByLabelText(/이의 사유/), "늦었어요");
      await user.click(screen.getByRole("button", { name: "이의 신청" }));

      expect(await screen.findByRole("alert")).toHaveTextContent(
        "이의를 받는 기간이 지났",
      );
    });
  });
});
