import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { PlaceResult } from "@/lib/kakaoMap";
import { CreateStudyFlow } from "./CreateStudyFlow";

const push = vi.fn();
const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace }),
}));
// 지도 표시는 브라우저 SDK 가 필요하므로 테스트에서는 불러오지 못한 것으로 둔다
vi.mock("@/lib/kakaoMap", async (original) => ({
  ...(await original<typeof import("@/lib/kakaoMap")>()),
  loadKakaoMaps: () => Promise.reject(new Error("테스트에는 지도가 없다")),
}));

const BASE_URL = "http://api.test";
// KST 2026-11-18(수) 10:00
const NOW = new Date("2026-11-18T01:00:00Z");

const META = {
  topics: [
    { code: "JOB_PREP", name: "취업준비", children: [] },
    { code: "LANGUAGE", name: "어학", children: [] },
  ],
  jobs: [
    { code: "IT", name: "IT·개발", children: [] },
    { code: "PUBLIC", name: "공무원·공공", children: [] },
  ],
  regions: [
    {
      code: "GYEONGGI",
      name: "경기",
      children: [
        {
          code: "SUWON",
          name: "수원시",
          children: [
            { code: "SUWON_PALDAL", name: "팔달구", children: [] },
            { code: "SUWON_JANGAN", name: "장안구", children: [] },
          ],
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
    minAttendanceRates: [50, 60, 70, 80, 90, 100],
  },
  checkin: { radiusMeters: 150 },
};

const ME = {
  id: 1,
  nickname: "준비생",
  jobCode: "IT",
  districtCode: "SUWON_PALDAL",
  role: "USER",
  onboarded: true,
  joinedAt: "2026-11-17T00:00:00Z",
};

const CAFE: PlaceResult = {
  id: "1",
  name: "수원역 스터디카페",
  address: "경기 수원시 팔달구 매산로1가 18",
  roadAddress: "경기 수원시 팔달구 덕영대로 924",
  latitude: 37.266,
  longitude: 127.0,
};
const OUTSIDE: PlaceResult = {
  id: "2",
  name: "기흥 카페",
  address: "경기 용인시 기흥구 보정동 1",
  roadAddress: "",
  latitude: 37.3,
  longitude: 127.1,
};

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const fetchMock = vi.fn<typeof fetch>();
let createResponse: () => Response;
let sentBody: Record<string, unknown> | undefined;

function renderFlow() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <CreateStudyFlow
        now={() => NOW}
        searchPlaces={async (keyword) =>
          keyword.includes("기흥") ? [OUTSIDE] : [CAFE]
        }
      />
    </QueryClientProvider>,
  );
}

const next = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole("button", { name: "다음" }));

async function fillUntilReview(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByLabelText("제목"), "면접 스터디");
  await user.type(
    screen.getByLabelText("소개 (선택)"),
    "모의 면접을 서로 해 주고 피드백해요",
  );
  await next(user);

  await user.click(screen.getByRole("radio", { name: "취업준비" }));
  await user.click(screen.getByRole("checkbox", { name: "IT·개발" }));
  await next(user);

  await user.click(screen.getByRole("radio", { name: "20일 금" }));
  await user.click(screen.getByRole("radio", { name: "오후" }));
  await user.click(screen.getByRole("radio", { name: "2시" }));
  await user.click(screen.getByRole("radio", { name: "30분" }));
  await next(user);

  await user.type(screen.getByLabelText("장소 검색"), "수원역");
  await user.click(screen.getByRole("button", { name: "찾기" }));
  await user.click(
    await screen.findByRole("button", { name: /수원역 스터디카페/ }),
  );
  await next(user);

  await user.click(
    screen.getByRole("button", { name: "정원 (나를 포함) 늘리기" }),
  );
  await user.click(screen.getByRole("radio", { name: "70% 이상" }));
  await next(user);
}

describe("CreateStudyFlow", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE_URL);
    vi.stubGlobal("fetch", fetchMock);
    sentBody = undefined;
    createResponse = () => json(201, { id: 42 });
    fetchMock.mockImplementation(async (url, init) => {
      const path = String(url).replace(`${BASE_URL}/api/v1`, "");
      if (path === "/me") return json(200, ME);
      if (path === "/meta/options") return json(200, META);
      if (path === "/studies" && init?.method === "POST") {
        sentBody = JSON.parse(init.body as string);
        return createResponse();
      }
      return json(404, { status: 404, code: "NOT_FOUND" });
    });
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
    push.mockReset();
    replace.mockReset();
  });

  it("입력을 차례로 받아 개설하고 상세로 이동한다", async () => {
    const user = userEvent.setup();
    renderFlow();

    await fillUntilReview(user);
    expect(
      screen.getByRole("heading", { name: "이대로 만들까요?" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "만들기" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/studies/42"));
    expect(sentBody).toEqual({
      title: "면접 스터디",
      description: "모의 면접을 서로 해 주고 피드백해요",
      topicCode: "JOB_PREP",
      districtCode: "SUWON_PALDAL",
      targetJobCodes: ["IT"],
      place: {
        name: "수원역 스터디카페",
        address: "경기 수원시 팔달구 덕영대로 924",
        latitude: 37.266,
        longitude: 127.0,
      },
      startAt: "2026-11-20T05:30:00Z",
      durationMin: 120,
      capacity: 5,
      minAttendanceRate: 70,
      allowNewcomer: true,
    });
  });

  it("서버의 VALIDATION_FAILED 필드 오류를 해당 단계의 입력 아래에 보여 준다", async () => {
    const user = userEvent.setup();
    createResponse = () =>
      json(400, {
        status: 400,
        code: "VALIDATION_FAILED",
        errors: [{ field: "title", message: "이미 쓰인 표현은 쓸 수 없어요" }],
      });
    renderFlow();

    await fillUntilReview(user);
    await user.click(screen.getByRole("button", { name: "만들기" }));

    const title = await screen.findByLabelText("제목");
    expect(title).toHaveAttribute("aria-invalid", "true");
    expect(
      screen.getByText("이미 쓰인 표현은 쓸 수 없어요"),
    ).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });

  it("개설 가능 범위(오늘부터 14일 뒤까지) 밖의 날짜는 고를 수 없다", async () => {
    const user = userEvent.setup();
    renderFlow();

    // 소개는 선택이라 비워 두고 넘어간다
    await user.type(await screen.findByLabelText("제목"), "면접 스터디");
    await next(user);
    await user.click(screen.getByRole("radio", { name: "취업준비" }));
    await next(user);

    const dates = within(
      screen.getByRole("group", { name: "날짜" }),
    ).getAllByRole("radio");
    expect(dates).toHaveLength(15);
    expect(
      screen.getByRole("radio", { name: "18일 수 · 오늘" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "2일 수" })).toBeInTheDocument();
    expect(
      screen.queryByRole("radio", { name: "3일 목" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("radio", { name: "17일 화" }),
    ).not.toBeInTheDocument();
  });

  it("오늘 이미 지난 시각이면 다음으로 넘어가지 않는다", async () => {
    const user = userEvent.setup();
    renderFlow();

    await user.type(await screen.findByLabelText("제목"), "면접 스터디");
    await user.type(
      screen.getByLabelText("소개 (선택)"),
      "모의 면접을 서로 해요",
    );
    await next(user);
    await user.click(screen.getByRole("radio", { name: "취업준비" }));
    await next(user);
    await user.click(screen.getByRole("radio", { name: "18일 수 · 오늘" }));
    await user.click(screen.getByRole("radio", { name: "오전" }));
    await user.click(screen.getByRole("radio", { name: "9시" }));
    await next(user);

    expect(
      screen.getAllByText("지금보다 뒤의 시각을 골라 주세요.").length,
    ).toBeGreaterThan(0);
    expect(
      screen.getByRole("heading", { name: "언제 만날까요?" }),
    ).toBeInTheDocument();
  });

  it("지원하지 않는 지역의 장소는 고를 수 없다고 안내한다", async () => {
    const user = userEvent.setup();
    renderFlow();

    await user.type(await screen.findByLabelText("제목"), "면접 스터디");
    await user.type(
      screen.getByLabelText("소개 (선택)"),
      "모의 면접을 서로 해요",
    );
    await next(user);
    await user.click(screen.getByRole("radio", { name: "취업준비" }));
    await next(user);
    await user.click(screen.getByRole("radio", { name: "20일 금" }));
    await user.click(screen.getByRole("radio", { name: "2시" }));
    await next(user);
    await user.type(screen.getByLabelText("장소 검색"), "기흥");
    await user.click(screen.getByRole("button", { name: "찾기" }));
    await user.click(await screen.findByRole("button", { name: /기흥 카페/ }));
    await next(user);

    expect(
      screen.getAllByText(
        "지금은 수원시 팔달구, 수원시 장안구 안의 장소만 고를 수 있어요.",
      ).length,
    ).toBeGreaterThan(0);
  });

  it("온보딩 전 회원은 온보딩으로 보낸다", async () => {
    fetchMock.mockImplementation(async (url) =>
      String(url).endsWith("/me")
        ? json(200, { ...ME, onboarded: false })
        : json(200, META),
    );
    renderFlow();

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/onboarding"));
  });
});
