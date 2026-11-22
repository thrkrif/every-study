import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MyAppealsView } from "./MyAppealsView";

const BASE_URL = "http://api.test";

const ME = {
  id: 7,
  nickname: "나",
  jobCode: "IT",
  districtCode: "X",
  role: "USER",
  onboarded: true,
  joinedAt: "2026-11-01T00:00:00Z",
};

const appeal = (id: number, over = {}) => ({
  id,
  attendanceEventId: id * 10,
  studyId: id * 100,
  studyTitle: `스터디 ${id}`,
  verdictType: "NO_SHOW_CANDIDATE",
  reason: `사유 ${id}`,
  hostOpinion: null,
  status: "OPEN",
  dueAt: "2026-11-20T05:00:00Z",
  createdAt: "2026-11-18T05:00:00Z",
  decidedAt: null,
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
      <MyAppealsView />
    </QueryClientProvider>,
  );
}

describe("MyAppealsView", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE_URL);
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("사유·주최자 의견·처리 상태를 보여 준다", async () => {
    fetchMock.mockImplementation(async (url) => {
      const path = new URL(String(url)).pathname.replace("/api/v1", "");
      if (path === "/me") return json(200, ME);
      if (path === "/me/appeals")
        return json(200, {
          items: [
            appeal(2, { status: "ACCEPTED", hostOpinion: "사정이 확인돼요" }),
            appeal(1),
          ],
          nextCursor: null,
        });
      return json(404, { status: 404, code: "NOT_FOUND" });
    });
    renderView();

    expect(await screen.findByText("인정됨")).toBeInTheDocument();
    expect(screen.getByText("검토 중")).toBeInTheDocument();
    expect(screen.getByText("사정이 확인돼요")).toBeInTheDocument();
    expect(screen.getByText("아직 의견이 없어요.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "스터디 2" })).toHaveAttribute(
      "href",
      "/studies/200",
    );
  });

  it("신청한 이의가 없으면 안내한다", async () => {
    fetchMock.mockImplementation(async (url) => {
      const path = new URL(String(url)).pathname.replace("/api/v1", "");
      if (path === "/me") return json(200, ME);
      return json(200, { items: [], nextCursor: null });
    });
    renderView();

    expect(
      await screen.findByText("신청한 이의가 없어요."),
    ).toBeInTheDocument();
  });
});
