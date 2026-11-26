import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { StudyListView } from "./StudyListView";

const BASE_URL = "http://api.test";
// KST 2026-11-18(수) 10:00
const NOW = new Date("2026-11-18T01:00:00Z");

const META = {
  topics: [
    { code: "JOB_PREP", name: "취업준비", children: [] },
    { code: "LANGUAGE", name: "어학", children: [] },
  ],
  jobs: [{ code: "IT", name: "IT·개발", children: [] }],
  regions: [
    { code: "SEOUL", name: "서울", children: [] },
    { code: "GYEONGGI", name: "경기", children: [] },
  ],
  study: {
    capacityMin: 2,
    capacityMax: 6,
    openableDays: 14,
    startMinuteUnit: 10,
    durationMinuteUnit: 10,
    durationMinMinutes: 30,
    minAttendanceRates: [50, 60, 70, 80, 90, 100],
  },
  checkin: { radiusMeters: 150 },
};

const study = (id: number, title: string) => ({
  id,
  title,
  topicCode: "JOB_PREP",
  districtCode: "GYEONGGI",
  startAt: "2026-11-18T05:00:00Z",
  durationMin: 120,
  capacity: 4,
  memberCount: 1,
  recruiting: true,
  targetJobCodes: ["IT"],
  entryCriteria: { minAttendanceRate: 70, allowNewcomer: true },
  host: {
    userId: 7,
    nickname: "주최자",
    stats: { hostedCount: 0, leftWithinWindowCount: 0, showCountOnly: true },
  },
});

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const fetchMock = vi.fn<typeof fetch>();
/** 목록 요청의 쿼리 문자열들 */
let listQueries: URLSearchParams[];

function renderList() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <StudyListView now={() => NOW} />
    </QueryClientProvider>,
  );
}

describe("StudyListView", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE_URL);
    vi.stubGlobal("fetch", fetchMock);
    listQueries = [];
    fetchMock.mockImplementation(async (url) => {
      const full = new URL(String(url));
      const path = full.pathname.replace("/api/v1", "");
      if (path === "/me")
        return json(401, { status: 401, code: "UNAUTHENTICATED" });
      if (path === "/auth/refresh")
        return json(401, { status: 401, code: "UNAUTHENTICATED" });
      if (path === "/meta/options") return json(200, META);
      if (path === "/studies") {
        listQueries.push(full.searchParams);
        if (full.searchParams.get("cursor") === "next-1")
          return json(200, {
            items: [study(2, "두 번째 스터디")],
            nextCursor: null,
          });
        if (full.searchParams.get("topicCode") === "LANGUAGE")
          return json(200, {
            items: [study(3, "영어 회화")],
            nextCursor: null,
          });
        return json(200, {
          items: [study(1, "면접 스터디")],
          nextCursor: "next-1",
        });
      }
      return json(404, { status: 404, code: "NOT_FOUND" });
    });
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("비회원도 카드를 보고, 카드에 시각·정원·입장 기준·희망 직군·주최 지표가 있다", async () => {
    renderList();

    const card = await screen.findByRole("link", { name: /면접 스터디/ });
    expect(card).toHaveAttribute("href", "/studies/1");
    expect(within(card).getByText("오늘 오후 2:00")).toBeInTheDocument();
    expect(within(card).getByText("출석률 70% 이상")).toBeInTheDocument();
    expect(within(card).getByText("IT·개발")).toBeInTheDocument();
    expect(
      within(card).getByRole("img", { name: /정원 4명 중 1명/ }),
    ).toBeInTheDocument();
    expect(within(card).getByText("주최 주최자 · 첫 주최")).toBeInTheDocument();
    // 내 직군 필터는 로그인한 회원에게만 보인다
    expect(
      screen.queryByRole("checkbox", { name: "내가 신청할 수 있는 스터디" }),
    ).not.toBeInTheDocument();
  });

  it("더 보기는 직전 응답의 커서로 다음 페이지를 붙인다", async () => {
    const user = userEvent.setup();
    renderList();

    await user.click(await screen.findByRole("button", { name: "더 보기" }));

    expect(
      await screen.findByRole("link", { name: /두 번째 스터디/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /면접 스터디/ }),
    ).toBeInTheDocument();
    expect(listQueries.at(-1)?.get("cursor")).toBe("next-1");
  });

  it("필터를 바꾸면 커서 없이 첫 페이지부터 다시 조회한다", async () => {
    const user = userEvent.setup();
    renderList();
    await user.click(await screen.findByRole("button", { name: "더 보기" }));
    await screen.findByRole("link", { name: /두 번째 스터디/ });

    await user.click(screen.getByRole("radio", { name: "어학" }));

    expect(
      await screen.findByRole("link", { name: /영어 회화/ }),
    ).toBeInTheDocument();
    const last = listQueries.at(-1)!;
    expect(last.get("topicCode")).toBe("LANGUAGE");
    expect(last.has("cursor")).toBe(false);
    expect(
      screen.queryByRole("link", { name: /두 번째 스터디/ }),
    ).not.toBeInTheDocument();
  });

  it("정렬과 마감 포함을 요청에 담는다", async () => {
    const user = userEvent.setup();
    renderList();
    await screen.findByRole("link", { name: /면접 스터디/ });

    await user.click(screen.getByRole("radio", { name: "마감임박순" }));
    await user.click(screen.getByRole("checkbox", { name: "마감 포함" }));

    await waitFor(() => {
      const last = listQueries.at(-1)!;
      expect(last.get("sort")).toBe("deadline");
      expect(last.get("includeClosed")).toBe("true");
    });
  });
});
