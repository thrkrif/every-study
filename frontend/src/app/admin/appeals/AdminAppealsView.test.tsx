import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AdminAppealsView } from "./AdminAppealsView";

const BASE_URL = "http://api.test";

const me = (role: "USER" | "ADMIN") => ({
  id: 1,
  nickname: "운영자",
  jobCode: "IT",
  districtCode: "X",
  role,
  onboarded: true,
  joinedAt: "2026-11-01T00:00:00Z",
});

const appeal = (id: number, over: Record<string, unknown> = {}) => ({
  id,
  studyId: 3,
  studyTitle: "모각코",
  userId: 9,
  applicantNickname: "신청자",
  verdictType: "NO_SHOW_CANDIDATE",
  reason: `사유 ${id}`,
  hostOpinion: "의견 있음",
  status: "OPEN",
  dueAt: "2026-11-25T03:00:00Z",
  createdAt: "2026-11-23T05:00:00Z",
  decidedAt: null,
  ...over,
});

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const fetchMock = vi.fn<typeof fetch>();

describe("AdminAppealsView", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE_URL);
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  function renderView() {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    return render(
      <QueryClientProvider client={client}>
        <AdminAppealsView />
      </QueryClientProvider>,
    );
  }

  /** 큐 응답은 호출 순서대로 주고 마지막 것을 반복한다 */
  function mock(
    role: "USER" | "ADMIN",
    queue: unknown[] = [{ items: [], nextCursor: null }],
    extra?: (path: string, init?: RequestInit) => Response | undefined,
  ) {
    let calls = 0;
    fetchMock.mockImplementation(async (url, init) => {
      const path = new URL(String(url)).pathname.replace("/api/v1", "");
      if (path === "/me") return json(200, me(role));
      if (path === "/admin/appeals" && (init?.method ?? "GET") === "GET") {
        const page = queue[Math.min(calls, queue.length - 1)];
        calls += 1;
        return json(200, page);
      }
      return (
        extra?.(path, init) ?? json(404, { status: 404, code: "NOT_FOUND" })
      );
    });
  }

  it("운영자가 아니면 화면을 보여 주지 않고 큐도 부르지 않는다", async () => {
    mock("USER");
    renderView();

    expect(
      await screen.findByText("운영자만 볼 수 있어요."),
    ).toBeInTheDocument();
    expect(
      fetchMock.mock.calls.some(([url]) => String(url).includes("/admin/")),
    ).toBe(false);
  });

  it("열린 이의의 사유와 주최자 의견을 보여 준다", async () => {
    mock("ADMIN", [{ items: [appeal(5)], nextCursor: null }]);
    renderView();

    expect(await screen.findByText("사유 5")).toBeInTheDocument();
    expect(screen.getByText("의견 있음")).toBeInTheDocument();
    expect(screen.getByText(/신청자 · 모각코 · 노쇼 후보/)).toBeInTheDocument();
  });

  it("결정 사유가 없으면 결정을 보내지 않고, 사유를 적고 인정하면 큐에서 사라진다", async () => {
    mock(
      "ADMIN",
      [
        { items: [appeal(5)], nextCursor: null },
        { items: [], nextCursor: null },
      ],
      (path, init) =>
        path === "/admin/appeals/5/decision" && init?.method === "POST"
          ? json(200, appeal(5, { status: "ACCEPTED" }))
          : undefined,
    );
    renderView();
    const user = userEvent.setup();
    await screen.findByText("사유 5");

    await user.click(screen.getByRole("button", { name: "인정" }));
    expect(screen.getByText("결정 사유를 적어 주세요.")).toBeInTheDocument();
    expect(
      fetchMock.mock.calls.some(([url]) => String(url).includes("/decision")),
    ).toBe(false);

    await user.type(screen.getByLabelText(/결정 사유/), "증빙 확인");
    await user.click(screen.getByRole("button", { name: "인정" }));

    expect(
      await screen.findByText("처리할 이의가 없어요."),
    ).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([url]) =>
      String(url).includes("/decision"),
    );
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({
      accept: true,
      note: "증빙 확인",
    });
  });

  it("이미 결정된 이의면 안내하고 큐를 다시 받는다", async () => {
    mock(
      "ADMIN",
      [
        { items: [appeal(5)], nextCursor: null },
        { items: [], nextCursor: null },
      ],
      (path) =>
        path === "/admin/appeals/5/decision"
          ? json(409, { status: 409, code: "APPEAL_CLOSED" })
          : undefined,
    );
    renderView();
    const user = userEvent.setup();
    await screen.findByText("사유 5");

    await user.type(screen.getByLabelText(/결정 사유/), "기각 사유");
    await user.click(screen.getByRole("button", { name: "기각" }));

    expect(
      await screen.findByText("처리할 이의가 없어요."),
    ).toBeInTheDocument();
  });

  it("개별 출석 정정은 번호·결과·사유가 모두 있어야 보내고, 성공하면 안내한다", async () => {
    mock("ADMIN", undefined, (path, init) =>
      path === "/admin/attendance-events/77/corrections" &&
      init?.method === "POST"
        ? json(200, { eventId: 77, studyId: 3, userId: 9, verdict: "ATTENDED" })
        : undefined,
    );
    renderView();
    const user = userEvent.setup();
    const submit = await screen.findByRole("button", { name: "정정하기" });
    expect(submit).toBeDisabled();

    await user.type(screen.getByLabelText("판정 번호"), "77");
    await user.click(screen.getByLabelText("출석으로 정정"));
    expect(submit).toBeDisabled();
    await user.type(screen.getByLabelText("정정 사유"), "서버 장애");
    expect(submit).toBeEnabled();
    await user.click(submit);

    expect(await screen.findByText("출석으로 정정했어요.")).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([url]) =>
      String(url).includes("/corrections"),
    );
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({
      result: "ATTENDED",
      note: "서버 장애",
    });
  });

  it("정정할 수 없는 판정이면 이유를 안내한다", async () => {
    mock("ADMIN", undefined, (path) =>
      path.endsWith("/corrections")
        ? json(409, { status: 409, code: "CORRECTION_NOT_ALLOWED" })
        : undefined,
    );
    renderView();
    const user = userEvent.setup();
    await screen.findByRole("button", { name: "정정하기" });

    await user.type(screen.getByLabelText("판정 번호"), "5");
    await user.click(screen.getByLabelText("판정 무효화"));
    await user.type(screen.getByLabelText("정정 사유"), "오판정");
    await user.click(screen.getByRole("button", { name: "정정하기" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "정정할 수 없는 판정이에요",
    );
  });
});
