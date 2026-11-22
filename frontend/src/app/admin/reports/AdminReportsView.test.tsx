import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AdminReportsView } from "./AdminReportsView";

const BASE_URL = "http://api.test";

const ME = {
  id: 1,
  nickname: "운영자",
  jobCode: "IT",
  districtCode: "X",
  role: "ADMIN",
  onboarded: true,
  joinedAt: "2026-11-01T00:00:00Z",
};

const report = (id: number, over: Record<string, unknown> = {}) => ({
  id,
  reporterId: 5,
  reporterNickname: "신고자",
  targetType: "STUDY",
  targetId: 30 + id,
  targetSummary: `대상 ${id}`,
  reason: "SPAM_AD",
  detail: null,
  status: "OPEN",
  handledBy: null,
  handleNote: null,
  createdAt: "2026-11-23T05:00:00Z",
  handledAt: null,
  ...over,
});

const sanction = (id: number, over: Record<string, unknown> = {}) => ({
  id,
  userId: 9,
  kind: "HOST_RESTRICT",
  source: "AUTO",
  reason: "주최 감점 합계 5점, 운영자 검토",
  startsAt: "2026-11-20T00:00:00Z",
  endsAt: "2026-12-20T00:00:00Z",
  revokedAt: null,
  createdBy: null,
  reviewRequired: true,
  active: true,
  ...over,
});

const json = (status: number, body?: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const fetchMock = vi.fn<typeof fetch>();

describe("AdminReportsView", () => {
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
        <AdminReportsView />
      </QueryClientProvider>,
    );
  }

  /** 신고 큐 응답은 호출 순서대로 주고 마지막 것을 반복한다 */
  function mock(
    reports: unknown[],
    sanctions: unknown[] = [{ items: [], nextCursor: null }],
    extra?: (path: string, init?: RequestInit) => Response | undefined,
  ) {
    let reportCalls = 0;
    let sanctionCalls = 0;
    fetchMock.mockImplementation(async (url, init) => {
      const path = new URL(String(url)).pathname.replace("/api/v1", "");
      const method = init?.method ?? "GET";
      if (path === "/me") return json(200, ME);
      if (path === "/admin/reports" && method === "GET") {
        return json(200, reports[Math.min(reportCalls++, reports.length - 1)]);
      }
      if (path === "/admin/sanctions" && method === "GET") {
        return json(
          200,
          sanctions[Math.min(sanctionCalls++, sanctions.length - 1)],
        );
      }
      return (
        extra?.(path, init) ?? json(404, { status: 404, code: "NOT_FOUND" })
      );
    });
  }

  const called = (fragment: string, method?: string) =>
    fetchMock.mock.calls.find(
      ([url, init]) =>
        String(url).includes(fragment) &&
        (method === undefined || init?.method === method),
    );

  it("신고의 사유·대상·상세를 보여 준다", async () => {
    mock([
      {
        items: [report(1, { detail: "홍보 글을 올림" })],
        nextCursor: null,
      },
    ]);
    renderView();

    expect(await screen.findByText("대상 1")).toBeInTheDocument();
    expect(screen.getByText("홍보 글을 올림")).toBeInTheDocument();
    expect(screen.getByText(/광고·홍보 · 신고자/)).toBeInTheDocument();
  });

  it("처리 사유가 없으면 반려·처리 완료를 보내지 않고, 사유를 적으면 큐에서 사라진다", async () => {
    mock(
      [
        { items: [report(1)], nextCursor: null },
        { items: [], nextCursor: null },
      ],
      undefined,
      (path, init) =>
        path === "/admin/reports/1/dismiss" && init?.method === "POST"
          ? json(200, report(1, { status: "DISMISSED" }))
          : undefined,
    );
    renderView();
    const user = userEvent.setup();
    await screen.findByText("대상 1");

    await user.click(screen.getByRole("button", { name: "반려" }));
    expect(screen.getByText("처리 사유를 적어 주세요.")).toBeInTheDocument();
    expect(called("/dismiss")).toBeUndefined();

    await user.type(screen.getByLabelText(/처리 사유/), "근거 없음");
    await user.click(screen.getByRole("button", { name: "반려" }));

    expect(
      await screen.findByText("처리할 신고가 없어요."),
    ).toBeInTheDocument();
    expect(JSON.parse(String(called("/dismiss")?.[1]?.body))).toEqual({
      note: "근거 없음",
    });
  });

  it("스터디 강제 종료는 사유가 있어야 하고 확인 창에서 한 번 더 묻는다", async () => {
    mock([{ items: [report(1)], nextCursor: null }], undefined, (path, init) =>
      path === "/admin/studies/31/force-end" && init?.method === "POST"
        ? new Response(null, { status: 204 })
        : undefined,
    );
    renderView();
    const user = userEvent.setup();
    await screen.findByText("대상 1");

    await user.click(screen.getByRole("button", { name: "강제 종료" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(screen.getByText("처리 사유를 적어 주세요.")).toBeInTheDocument();

    await user.type(screen.getByLabelText(/처리 사유/), "부적절한 모집");
    await user.click(screen.getByRole("button", { name: "강제 종료" }));
    const dialog = screen.getByRole("alertdialog");
    expect(called("/force-end")).toBeUndefined();

    await user.click(within(dialog).getByRole("button", { name: "취소" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(called("/force-end")).toBeUndefined();

    await user.click(screen.getByRole("button", { name: "강제 종료" }));
    await user.click(
      within(screen.getByRole("alertdialog")).getByRole("button", {
        name: "강제 종료 확정",
      }),
    );

    expect(
      await screen.findByText("스터디를 강제 종료했어요."),
    ).toBeInTheDocument();
    expect(JSON.parse(String(called("/force-end")?.[1]?.body))).toEqual({
      note: "부적절한 모집",
    });
  });

  it("메시지 신고는 메시지 삭제, 사용자 신고는 이용 정지를 사유와 함께 보낸다", async () => {
    mock(
      [
        {
          items: [
            report(1, { targetType: "MESSAGE", targetId: 70 }),
            report(2, { targetType: "USER", targetId: 9 }),
          ],
          nextCursor: null,
        },
      ],
      undefined,
      (path, init) => {
        if (path === "/admin/chat-messages/70" && init?.method === "DELETE") {
          return new Response(null, { status: 204 });
        }
        if (path === "/admin/users/9/suspensions" && init?.method === "POST") {
          return json(
            200,
            sanction(3, { kind: "SUSPEND", reviewRequired: false }),
          );
        }
        return undefined;
      },
    );
    renderView();
    const user = userEvent.setup();
    await screen.findByText("대상 1");
    const forms = screen.getAllByRole("form");
    const messageForm = forms.find(
      (f) => f.getAttribute("aria-label") === "신고 1 처리",
    )!;
    const userForm = forms.find(
      (f) => f.getAttribute("aria-label") === "신고 2 처리",
    )!;

    await user.type(within(messageForm).getByLabelText(/처리 사유/), "욕설");
    await user.click(
      within(messageForm).getByRole("button", { name: "메시지 삭제" }),
    );
    expect(await screen.findByText("메시지를 삭제했어요.")).toBeInTheDocument();
    expect(
      JSON.parse(String(called("/chat-messages/70", "DELETE")?.[1]?.body)),
    ).toEqual({
      note: "욕설",
    });

    await user.type(within(userForm).getByLabelText(/처리 사유/), "반복 위반");
    await user.clear(within(userForm).getByLabelText("정지 기간(일)"));
    await user.type(within(userForm).getByLabelText("정지 기간(일)"), "14");
    await user.click(
      within(userForm).getByRole("button", { name: "이용 정지" }),
    );
    expect(
      await screen.findByText("이용 정지를 걸었어요."),
    ).toBeInTheDocument();
    expect(
      JSON.parse(String(called("/users/9/suspensions")?.[1]?.body)),
    ).toEqual({
      days: 14,
      note: "반복 위반",
    });
  });

  it("이미 처리된 신고면 안내하고 큐를 다시 받는다", async () => {
    mock(
      [
        { items: [report(1)], nextCursor: null },
        { items: [], nextCursor: null },
      ],
      undefined,
      (path) =>
        path === "/admin/reports/1/resolve"
          ? json(409, { status: 409, code: "REPORT_ALREADY_HANDLED" })
          : undefined,
    );
    renderView();
    const user = userEvent.setup();
    await screen.findByText("대상 1");

    await user.type(screen.getByLabelText(/처리 사유/), "처리함");
    await user.click(screen.getByRole("button", { name: "처리 완료" }));

    expect(
      await screen.findByText("처리할 신고가 없어요."),
    ).toBeInTheDocument();
  });

  it("제재를 보여 주고 운영자 검토 대상은 표시하며, 해제는 사유가 있어야 한다", async () => {
    mock(
      [{ items: [], nextCursor: null }],
      [
        { items: [sanction(4)], nextCursor: null },
        { items: [], nextCursor: null },
      ],
      (path, init) =>
        path === "/admin/sanctions/4" && init?.method === "DELETE"
          ? new Response(null, { status: 204 })
          : undefined,
    );
    renderView();
    const user = userEvent.setup();

    expect(await screen.findByText("운영자 검토 대상")).toBeInTheDocument();
    expect(screen.getByText("스터디 개설 제한")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "제재 해제" }));
    const release = screen.getByRole("button", { name: "해제하기" });
    expect(release).toBeDisabled();
    await user.type(screen.getByLabelText("해제 사유"), "검토 끝");
    await user.click(release);

    expect(
      await screen.findByText("유효한 제재가 없어요."),
    ).toBeInTheDocument();
    expect(
      JSON.parse(String(called("/sanctions/4", "DELETE")?.[1]?.body)),
    ).toEqual({
      note: "검토 끝",
    });
  });

  it("운영자 검토 대상만 보기를 켜면 그 조건으로 다시 받는다", async () => {
    mock([{ items: [], nextCursor: null }]);
    renderView();
    const user = userEvent.setup();
    await screen.findByText("유효한 제재가 없어요.");

    await user.click(screen.getByLabelText("운영자 검토 대상만 보기"));

    await vi.waitFor(() =>
      expect(
        fetchMock.mock.calls.some(([url]) =>
          String(url).includes("reviewRequired=true"),
        ),
      ).toBe(true),
    );
  });

  it("이용 정지를 직접 걸려면 회원 번호·기간·사유가 모두 있어야 한다", async () => {
    mock([{ items: [], nextCursor: null }], undefined, (path, init) =>
      path === "/admin/users/12/suspensions" && init?.method === "POST"
        ? json(200, sanction(8, { kind: "SUSPEND", reviewRequired: false }))
        : undefined,
    );
    renderView();
    const user = userEvent.setup();
    await screen.findByText("처리할 신고가 없어요.");
    const form = screen.getByRole("form", { name: "이용 정지 부여" });
    const submit = within(form).getByRole("button", { name: "이용 정지" });
    expect(submit).toBeDisabled();

    await user.type(within(form).getByLabelText("회원 번호"), "12");
    await user.type(within(form).getByLabelText("정지 사유"), "반복 위반");
    expect(submit).toBeEnabled();
    await user.click(submit);

    expect(
      await screen.findByText("이용 정지를 걸었어요."),
    ).toBeInTheDocument();
    expect(
      JSON.parse(String(called("/users/12/suspensions")?.[1]?.body)),
    ).toEqual({
      days: 7,
      note: "반복 위반",
    });
  });
});
