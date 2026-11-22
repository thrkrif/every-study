import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ManageStudyView } from "./ManageStudyView";

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
  regions: [],
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
  nickname: "주최자",
  jobCode: "IT",
  districtCode: "SUWON_PALDAL",
  role: "USER",
  onboarded: true,
  joinedAt: "2026-11-01T00:00:00Z",
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
  description: "소개",
  topicCode: "JOB_PREP",
  districtCode: "SUWON_PALDAL",
  place: {
    name: "예시 장소",
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
  memberCount: 3,
  targetJobCodes: [],
  entryCriteria: { minAttendanceRate: null, allowNewcomer: true },
  host: {
    userId: 7,
    nickname: "주최자",
    stats: { hostedCount: 0, leftWithinWindowCount: 0, showCountOnly: true },
  },
  members: [
    { userId: 7, nickname: "주최자", trust: newcomer },
    { userId: 8, nickname: "참여자", trust: newcomer },
    { userId: 9, nickname: null, trust: newcomer },
  ],
  myState: "HOST",
  scheduleEditable: false,
  checkinOpensAt: "2026-11-20T04:50:00Z",
  checkinClosesAt: "2026-11-20T05:20:00Z",
  waitingCount: 2,
};

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const fetchMock = vi.fn<typeof fetch>();

function renderManage(id = "1") {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ManageStudyView id={id} now={() => NOW} />
    </QueryClientProvider>,
  );
}

const calls = (method: string, path: string) =>
  fetchMock.mock.calls.filter(
    ([url, init]) =>
      new URL(String(url)).pathname === `/api/v1${path}` &&
      (init?.method ?? "GET") === method,
  );
const bodyOf = (call: Parameters<typeof fetch>) =>
  JSON.parse(String(call[1]?.body));

describe("ManageStudyView", () => {
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
      if (path === "/studies/1" && method === "GET") return json(200, detail);
      return json(404, { status: 404, code: "NOT_FOUND" });
    });
  }

  it("참여자 목록과 대기 인원을 보여 준다", async () => {
    mock(DETAIL);
    renderManage();

    expect(
      await screen.findByRole("heading", { name: "참여자 3명" }),
    ).toBeInTheDocument();
    expect(screen.getByText("참여자")).toBeInTheDocument();
    expect(screen.getByText("탈퇴한 사용자")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "대기 2명" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/정원을 늘리면 대기 중인 분들에게/),
    ).toBeInTheDocument();
  });

  it("일정 수정이 잠겨 있으면 입력란 대신 새 스터디 안내를 보여 준다", async () => {
    mock({ ...DETAIL, scheduleEditable: false });
    renderManage();

    expect(
      await screen.findByText(/일정·장소는 바꿀 수 없어요/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "새 스터디 만들기" }),
    ).toHaveAttribute("href", "/studies/new");
    expect(
      screen.queryByRole("button", { name: "일정·장소 저장" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("group", { name: "날짜" }),
    ).not.toBeInTheDocument();
  });

  it("주최자 혼자라 일정을 고칠 수 있으면 입력란과 저장 버튼이 나온다", async () => {
    mock({ ...DETAIL, scheduleEditable: true, memberCount: 1, members: [] });
    renderManage();

    expect(
      await screen.findByRole("button", { name: "일정·장소 저장" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "날짜" })).toBeInTheDocument();
    expect(
      screen.queryByText(/일정·장소는 바꿀 수 없어요/),
    ).not.toBeInTheDocument();
  });

  it("일정을 바꿔 저장하면 시작 시각·진행 시간·장소를 보낸다", async () => {
    mock(
      { ...DETAIL, scheduleEditable: true, memberCount: 1, members: [] },
      (path, method) =>
        path === "/studies/1" && method === "PATCH"
          ? json(200, { ...DETAIL, scheduleEditable: true })
          : undefined,
    );
    renderManage();

    // 11월 21일(토) 오전 10시 30분으로
    await userEvent.click(
      await screen.findByRole("radio", { name: "21일 토" }),
    );
    await userEvent.click(screen.getByRole("radio", { name: "오전" }));
    await userEvent.click(screen.getByRole("radio", { name: "10시" }));
    await userEvent.click(screen.getByRole("radio", { name: "30분" }));
    await userEvent.click(
      screen.getByRole("button", { name: "일정·장소 저장" }),
    );

    expect(
      await screen.findByText("일정과 장소를 저장했어요."),
    ).toBeInTheDocument();
    const [call] = calls("PATCH", "/studies/1");
    expect(bodyOf(call)).toMatchObject({
      startAt: "2026-11-21T01:30:00Z",
      durationMin: 120,
      place: { name: "예시 장소", address: "경기 수원시 팔달구" },
    });
  });

  it("지금보다 이전 시각이면 보내지 않고 안내한다", async () => {
    mock({ ...DETAIL, scheduleEditable: true, memberCount: 1, members: [] });
    renderManage();

    await userEvent.click(
      await screen.findByRole("radio", { name: /18일 수/ }),
    );
    await userEvent.click(screen.getByRole("radio", { name: "오전" }));
    await userEvent.click(screen.getByRole("radio", { name: "9시" }));
    await userEvent.click(
      screen.getByRole("button", { name: "일정·장소 저장" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("이후 시각");
    expect(calls("PATCH", "/studies/1")).toHaveLength(0);
  });

  it("서버가 SCHEDULE_LOCKED 로 거절하면 새 스터디를 안내한다", async () => {
    mock(
      { ...DETAIL, scheduleEditable: true, memberCount: 1, members: [] },
      (path, method) =>
        path === "/studies/1" && method === "PATCH"
          ? json(409, { status: 409, code: "SCHEDULE_LOCKED" })
          : undefined,
    );
    renderManage();

    await userEvent.click(
      await screen.findByRole("button", { name: "일정·장소 저장" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "새 스터디를 만들어 주세요",
    );
  });

  it("제목과 소개를 고치면 바뀐 항목만 보낸다", async () => {
    mock(DETAIL, (path, method) =>
      path === "/studies/1" && method === "PATCH"
        ? json(200, { ...DETAIL, title: "새 제목" })
        : undefined,
    );
    renderManage();

    const title = await screen.findByLabelText("제목");
    await userEvent.clear(title);
    await userEvent.type(title, "새 제목");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByText("저장했어요.")).toBeInTheDocument();
    expect(bodyOf(calls("PATCH", "/studies/1")[0])).toEqual({
      title: "새 제목",
    });
  });

  it("제목이 너무 짧으면 보내지 않는다", async () => {
    mock(DETAIL);
    renderManage();

    const title = await screen.findByLabelText("제목");
    await userEvent.clear(title);
    await userEvent.type(title, "가");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(
      await screen.findByText("2자 이상 입력해 주세요."),
    ).toBeInTheDocument();
    expect(calls("PATCH", "/studies/1")).toHaveLength(0);
  });

  it("정원을 늘려 바꾼다", async () => {
    mock(DETAIL, (path, method) =>
      path === "/studies/1/capacity" && method === "PUT"
        ? json(200, { ...DETAIL, capacity: 5 })
        : undefined,
    );
    renderManage();

    await userEvent.click(
      await screen.findByRole("button", { name: "정원 (나를 포함) 늘리기" }),
    );
    await userEvent.click(screen.getByRole("button", { name: "정원 바꾸기" }));

    expect(await screen.findByText("정원을 바꿨어요.")).toBeInTheDocument();
    expect(bodyOf(calls("PUT", "/studies/1/capacity")[0])).toEqual({
      capacity: 5,
    });
  });

  it("참여자 수보다 줄이면 CAPACITY_BELOW_MEMBERS 안내를 보여 준다", async () => {
    mock(DETAIL, (path, method) =>
      path === "/studies/1/capacity" && method === "PUT"
        ? json(409, { status: 409, code: "CAPACITY_BELOW_MEMBERS" })
        : undefined,
    );
    renderManage();

    await userEvent.click(
      await screen.findByRole("button", { name: "정원 (나를 포함) 줄이기" }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "정원 (나를 포함) 줄이기" }),
    );
    await userEvent.click(screen.getByRole("button", { name: "정원 바꾸기" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "지금 참여자가 3명이라 그보다 적게 줄일 수 없어요.",
    );
  });

  it("주최자가 아니면 관리할 수 없다는 안내와 상세로 가는 링크를 보여 준다", async () => {
    mock({ ...DETAIL, myState: "MEMBER" });
    renderManage();

    expect(
      await screen.findByText("주최자만 관리할 수 있어요."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "스터디 보기" })).toHaveAttribute(
      "href",
      "/studies/1",
    );
  });

  it("끝났거나 열리지 않은 스터디는 바꿀 수 없다", async () => {
    mock({ ...DETAIL, status: "CANCELLED" });
    renderManage();

    expect(
      await screen.findByText(/끝났거나 열리지 않은 스터디는 바꿀 수 없어요/),
    ).toBeInTheDocument();
  });

  it("비회원이면 로그인을, 없는 스터디면 찾을 수 없다는 안내를 보여 준다", async () => {
    fetchMock.mockImplementation(async (url) => {
      const path = new URL(String(url)).pathname.replace("/api/v1", "");
      if (path === "/meta/options") return json(200, META);
      return json(401, { status: 401, code: "UNAUTHENTICATED" });
    });
    const first = renderManage();
    expect(await screen.findByRole("link", { name: "로그인" })).toHaveAttribute(
      "href",
      "/login",
    );
    first.unmount();

    renderManage("abc");
    await waitFor(() =>
      expect(screen.getByText("스터디를 찾을 수 없어요.")).toBeInTheDocument(),
    );
  });
});
