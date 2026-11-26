import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ProfileView } from "./ProfileView";

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

const PROFILE = {
  id: 9,
  nickname: "준비생",
  jobCode: "IT",
  districtCode: "GYEONGGI",
  joinedAt: "2026-11-17T05:00:00Z",
  attendance: {
    attendanceRate: 92,
    windowCount: 10,
    noShow: 0,
    late: 1,
    lateCancel: 0,
    isNewcomer: false,
  },
  hostStats: { hostedCount: 7, leftWithinWindowCount: 1, showCountOnly: false },
};

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const fetchMock = vi.fn<typeof fetch>();

function renderView(id: number) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ProfileView id={id} />
    </QueryClientProvider>,
  );
}

describe("ProfileView", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE_URL);
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  function mock(profile: () => Response) {
    fetchMock.mockImplementation(async (url) => {
      const path = new URL(String(url)).pathname.replace("/api/v1", "");
      if (path === "/meta/options") return json(200, META);
      if (path.startsWith("/users/")) return profile();
      return json(404, { status: 404, code: "NOT_FOUND" });
    });
  }

  it("닉네임, 직군·지역·가입일, 지표를 보여 준다", async () => {
    mock(() => json(200, PROFILE));
    renderView(9);

    expect(
      await screen.findByRole("heading", { name: "준비생" }),
    ).toBeInTheDocument();
    expect(await screen.findByText(/IT·개발 · 경기/)).toHaveTextContent(
      "11월 17일 (화) 가입",
    );
    expect(
      screen.getByText("출석률 92% · 최근 10회 중 노쇼 0회 · 지각 1회"),
    ).toBeInTheDocument();
  });

  it("탈퇴한 사용자는 이름 대신 안내를 쓰고 지표는 그대로 보인다", async () => {
    mock(() =>
      json(200, {
        ...PROFILE,
        nickname: null,
        jobCode: null,
        districtCode: null,
      }),
    );
    renderView(9);

    expect(
      await screen.findByRole("heading", { name: "탈퇴한 사용자" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("출석률 92% · 최근 10회 중 노쇼 0회 · 지각 1회"),
    ).toBeInTheDocument();
  });

  it("없는 회원이면 찾을 수 없다고 안내한다", async () => {
    mock(() => json(404, { status: 404, code: "NOT_FOUND" }));
    renderView(404);

    expect(
      await screen.findByText("찾을 수 없는 사용자예요."),
    ).toBeInTheDocument();
  });
});
