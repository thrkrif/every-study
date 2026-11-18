import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { StudyDetailView } from "./StudyDetailView";

// 지도 표시는 브라우저 SDK 가 필요하므로 테스트에서는 불러오지 못한 것으로 둔다
vi.mock("@/lib/kakaoMap", async (original) => ({
  ...(await original<typeof import("@/lib/kakaoMap")>()),
  loadKakaoMaps: () => Promise.reject(new Error("테스트에는 지도가 없다")),
}));

const BASE_URL = "http://api.test";
// KST 2026-11-18(수) 10:00
const NOW = new Date("2026-11-18T01:00:00Z");

const META = {
  topics: [{ code: "JOB_PREP", name: "취업준비", children: [] }],
  jobs: [{ code: "IT", name: "IT·개발", children: [] }],
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

const newcomer = {
  attendanceRate: null,
  windowCount: 0,
  noShow: 0,
  late: 0,
  lateCancel: 0,
  isNewcomer: true,
};

const DETAIL = {
  id: 1,
  title: "면접 스터디",
  description: "모의 면접을 서로 해 줘요",
  topicCode: "JOB_PREP",
  districtCode: "SUWON_PALDAL",
  place: {
    name: "예시 장소 (팔달구)",
    address: "경기 수원시 팔달구",
    latitude: 37.2826,
    longitude: 127.0196,
  },
  startAt: "2026-11-20T05:00:00Z",
  durationMin: 120,
  sessionDate: "2026-11-20",
  status: "ACTIVE",
  recruiting: true,
  capacity: 4,
  memberCount: 2,
  targetJobCodes: [],
  entryCriteria: { minAttendanceRate: null, allowNewcomer: true },
  host: {
    userId: 7,
    nickname: "주최자",
    stats: { hostedCount: 0, leftWithinWindowCount: 0, showCountOnly: true },
  },
  members: [
    { userId: 7, nickname: "주최자", trust: newcomer },
    {
      userId: 8,
      nickname: null,
      trust: { ...newcomer, isNewcomer: false, attendanceRate: 92 },
    },
  ],
  myState: "NONE",
  scheduleEditable: false,
  checkinOpensAt: "2026-11-20T04:50:00Z",
  checkinClosesAt: "2026-11-20T05:20:00Z",
};

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const fetchMock = vi.fn<typeof fetch>();

function renderDetail(id: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <StudyDetailView id={id} now={() => NOW} />
    </QueryClientProvider>,
  );
}

describe("StudyDetailView", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE_URL);
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockImplementation(async (url) => {
      const path = new URL(String(url)).pathname.replace("/api/v1", "");
      if (path === "/me" || path === "/auth/refresh")
        return json(401, { status: 401, code: "UNAUTHENTICATED" });
      if (path === "/meta/options") return json(200, META);
      if (path === "/studies/1") return json(200, DETAIL);
      return json(404, { status: 404, code: "NOT_FOUND" });
    });
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("비회원도 상세를 보고, 참여는 로그인 안내로 바뀐다", async () => {
    renderDetail("1");

    expect(
      await screen.findByRole("heading", { name: "면접 스터디" }),
    ).toBeInTheDocument();
    expect(
      screen.getAllByText("11월 20일 금요일 오후 2:00").length,
    ).toBeGreaterThan(0);
    expect(screen.getByText(/신청 마감 오후 1:50/)).toBeInTheDocument();
    expect(screen.getByText("모의 면접을 서로 해 줘요")).toBeInTheDocument();
    expect(screen.getByText("입장 기준 없음")).toBeInTheDocument();
    expect(screen.getByText("직군 무관")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "카카오맵에서 보기" }),
    ).toHaveAttribute(
      "href",
      expect.stringContaining("map.kakao.com/link/map/"),
    );
    // 참여자: 주최 표시, 탈퇴한 사용자, 출석 지표
    expect(
      screen.getByRole("heading", { name: "참여자 2명" }),
    ).toBeInTheDocument();
    expect(screen.getByText("주최")).toBeInTheDocument();
    expect(screen.getByText("탈퇴한 사용자")).toBeInTheDocument();
    expect(screen.getByText("출석률 92%")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "로그인하고 참여하기" }),
    ).toHaveAttribute("href", "/login");
  });

  it("없는 스터디면 목록으로 돌아가는 안내를 보여 준다", async () => {
    renderDetail("999");

    expect(
      await screen.findByText(/스터디를 찾을 수 없어요/),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "목록으로" })).toHaveAttribute(
      "href",
      "/",
    );
  });

  it("주소의 id 가 숫자가 아니면 요청하지 않고 없는 스터디로 본다", async () => {
    renderDetail("abc");

    expect(screen.getByText(/스터디를 찾을 수 없어요/)).toBeInTheDocument();
    expect(
      fetchMock.mock.calls.some(([url]) => String(url).includes("/studies/")),
    ).toBe(false);
  });
});
