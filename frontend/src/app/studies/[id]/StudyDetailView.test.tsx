import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
    // 참여자 이름은 공개 프로필로 이동한다 (T-036)
    expect(screen.getByRole("link", { name: "주최자" })).toHaveAttribute(
      "href",
      "/users/7",
    );
    expect(screen.getByRole("link", { name: "탈퇴한 사용자" })).toHaveAttribute(
      "href",
      "/users/8",
    );
    // 로그인 상태를 확인한 뒤에 버튼이 정해진다
    expect(
      await screen.findByRole("link", { name: "로그인하고 참여하기" }),
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

  describe("참여·대기·나가기", () => {
    const ME = {
      id: 9,
      nickname: "나",
      jobCode: "IT",
      districtCode: "SUWON_PALDAL",
      role: "USER",
      onboarded: true,
      joinedAt: "2026-11-01T00:00:00Z",
    };

    /** 로그인한 회원으로 응답하고, 주어진 상세·추가 경로를 돌려준다 */
    function mockMember(
      detail: Record<string, unknown>,
      extra: (path: string, method: string) => Response | undefined = () =>
        undefined,
    ) {
      fetchMock.mockImplementation(async (url, init) => {
        const path = new URL(String(url)).pathname.replace("/api/v1", "");
        const method = init?.method ?? "GET";
        const custom = extra(path, method);
        if (custom) return custom;
        if (path === "/me") return json(200, ME);
        if (path === "/meta/options") return json(200, META);
        if (path === "/studies/1") return json(200, detail);
        return json(404, { status: 404, code: "NOT_FOUND" });
      });
    }

    const calls = (method: string, path: string) =>
      fetchMock.mock.calls.filter(
        ([url, init]) =>
          new URL(String(url)).pathname === `/api/v1${path}` &&
          (init?.method ?? "GET") === method,
      );

    it("모집 중이면 참여하기를 눌러 신청하고 결과를 알린다", async () => {
      mockMember(DETAIL, (path, method) =>
        path === "/studies/1/members" && method === "POST"
          ? new Response(null, { status: 201 })
          : undefined,
      );
      renderDetail("1");

      await userEvent.click(
        await screen.findByRole("button", { name: "참여하기" }),
      );

      expect(await screen.findByText("참여했어요.")).toBeInTheDocument();
      expect(calls("POST", "/studies/1/members")).toHaveLength(1);
      // 신청 뒤 상세를 다시 읽는다
      await waitFor(() =>
        expect(calls("GET", "/studies/1").length).toBeGreaterThan(1),
      );
    });

    it("정원이 찼으면 대기하기로 바뀌고, 대기 중이면 대기 취소가 나온다", async () => {
      mockMember({ ...DETAIL, memberCount: 4, recruiting: false });
      const first = renderDetail("1");
      expect(
        await screen.findByRole("button", { name: "대기하기" }),
      ).toBeInTheDocument();
      expect(screen.getByText(/정원이 찼어요/)).toBeInTheDocument();
      first.unmount();

      mockMember({
        ...DETAIL,
        memberCount: 4,
        recruiting: false,
        myState: "WAITING",
      });
      renderDetail("1");
      expect(
        await screen.findByRole("button", { name: "대기 취소" }),
      ).toBeInTheDocument();
    });

    it("신청이 STUDY_FULL 로 거절되면 안내하고 상세를 다시 읽는다", async () => {
      mockMember(DETAIL, (path, method) =>
        path === "/studies/1/members" && method === "POST"
          ? json(409, { status: 409, code: "STUDY_FULL" })
          : undefined,
      );
      renderDetail("1");

      await userEvent.click(
        await screen.findByRole("button", { name: "참여하기" }),
      );

      expect(await screen.findByRole("alert")).toHaveTextContent(
        /정원이 찼어요/,
      );
      await waitFor(() =>
        expect(calls("GET", "/studies/1").length).toBeGreaterThan(1),
      );
    });

    it.each([
      ["ENTRY_CRITERIA_NOT_MET", /입장 기준/],
      ["LOCK_TIMEOUT", /한 번 더 눌러/],
    ])("신청이 %s 로 거절되면 그 이유를 알려 준다", async (code, text) => {
      mockMember(DETAIL, (path, method) =>
        path === "/studies/1/members" && method === "POST"
          ? json(409, { status: 409, code })
          : undefined,
      );
      renderDetail("1");

      await userEvent.click(
        await screen.findByRole("button", { name: "참여하기" }),
      );

      expect(await screen.findByRole("alert")).toHaveTextContent(text);
      // 다시 눌러 볼 수 있다
      expect(screen.getByRole("button", { name: "참여하기" })).toBeEnabled();
    });

    it("이용 제한이면 끝나는 날을 알려 준다", async () => {
      mockMember(DETAIL, (path, method) =>
        path === "/studies/1/members" && method === "POST"
          ? json(403, {
              status: 403,
              code: "USER_RESTRICTED",
              until: "2026-11-25T05:00:00Z",
            })
          : undefined,
      );
      renderDetail("1");

      await userEvent.click(
        await screen.findByRole("button", { name: "참여하기" }),
      );

      expect(await screen.findByRole("alert")).toHaveTextContent(
        "11월 25일 오후 2:00까지",
      );
    });

    it("희망 직군이 아니면 신청 버튼 대신 이유를 보여 준다", async () => {
      mockMember({ ...DETAIL, targetJobCodes: ["DESIGN"] });
      renderDetail("1");

      expect(
        await screen.findByText(
          /희망 직군\(DESIGN\)이 아니라 참여할 수 없어요/,
        ),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "참여하기" }),
      ).not.toBeInTheDocument();
    });

    it("참여자가 나가려면 사전 조회 결과를 확인한 뒤에 나간다", async () => {
      mockMember({ ...DETAIL, myState: "MEMBER" }, (path, method) => {
        if (path === "/studies/1/leave-preview")
          return json(200, { cancelType: "LATE", penaltyApplies: true });
        if (path === "/studies/1/members/me" && method === "DELETE")
          return json(200, { cancelType: "LATE" });
        return undefined;
      });
      renderDetail("1");

      await userEvent.click(
        await screen.findByRole("button", { name: "참여 취소" }),
      );
      const dialog = await screen.findByRole("alertdialog");
      expect(dialog).toHaveTextContent("직전 취소로 기록돼요");
      expect(calls("DELETE", "/studies/1/members/me")).toHaveLength(0);

      await userEvent.click(
        screen.getAllByRole("button", { name: "참여 취소" })[0],
      );

      expect(await screen.findByText("참여를 취소했어요.")).toBeInTheDocument();
      expect(calls("DELETE", "/studies/1/members/me")).toHaveLength(1);
    });

    it("돌아가기를 누르면 나가지 않는다", async () => {
      mockMember({ ...DETAIL, myState: "MEMBER" }, (path) =>
        path === "/studies/1/leave-preview"
          ? json(200, { cancelType: "EARLY", penaltyApplies: false })
          : undefined,
      );
      renderDetail("1");

      await userEvent.click(
        await screen.findByRole("button", { name: "참여 취소" }),
      );
      await userEvent.click(
        await screen.findByRole("button", { name: "돌아가기" }),
      );

      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
      expect(calls("DELETE", "/studies/1/members/me")).toHaveLength(0);
    });

    it("주최자는 남은 참여자가 없으면 취소된다는 안내를 확인한 뒤 나간다", async () => {
      mockMember({ ...DETAIL, myState: "HOST" }, (path, method) => {
        if (path === "/studies/1/leave-preview")
          return json(200, { hostOutcome: "CANCELLED", penaltyApplies: false });
        if (path === "/studies/1/members/me" && method === "DELETE")
          return json(200, { outcome: "CANCELLED" });
        return undefined;
      });
      renderDetail("1");

      await userEvent.click(
        await screen.findByRole("button", { name: "주최 그만두기" }),
      );
      expect(await screen.findByRole("alertdialog")).toHaveTextContent(
        "스터디가 취소돼요",
      );
      await userEvent.click(
        screen.getByRole("button", { name: "스터디 취소" }),
      );

      expect(
        await screen.findByText("스터디를 취소했어요."),
      ).toBeInTheDocument();
    });
  });
});
