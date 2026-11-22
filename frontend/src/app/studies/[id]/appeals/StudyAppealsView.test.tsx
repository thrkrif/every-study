import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { StudyAppealsView } from "./StudyAppealsView";

const BASE_URL = "http://api.test";

const ME = {
  id: 7,
  nickname: "주최자",
  jobCode: "IT",
  districtCode: "X",
  role: "USER",
  onboarded: true,
  joinedAt: "2026-11-01T00:00:00Z",
};

const hostAppeal = (id: number, over = {}) => ({
  id,
  applicantNickname: "참여자",
  verdictType: "NO_SHOW_CANDIDATE",
  reason: "지하철이 멈췄어요",
  hostOpinion: null,
  status: "OPEN",
  dueAt: "2026-11-20T05:00:00Z",
  createdAt: "2026-11-18T05:00:00Z",
  ...over,
});

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const fetchMock = vi.fn<typeof fetch>();

function renderView(id = "1") {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <StudyAppealsView id={id} />
    </QueryClientProvider>,
  );
}

const puts = () =>
  fetchMock.mock.calls.filter(([, init]) => init?.method === "PUT");

describe("StudyAppealsView", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE_URL);
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  function mock(list: () => Response, save?: () => Response) {
    fetchMock.mockImplementation(async (url, init) => {
      const path = new URL(String(url)).pathname.replace("/api/v1", "");
      if (path === "/me") return json(200, ME);
      if (path === "/studies/1/appeals") return list();
      if (init?.method === "PUT" && path.endsWith("/host-opinion"))
        return save?.() ?? json(200, {});
      return json(404, { status: 404, code: "NOT_FOUND" });
    });
  }

  it("주최자가 아니면 안내만 보이고 의견 입력은 없다", async () => {
    mock(() => json(403, { status: 403, code: "FORBIDDEN" }));
    renderView();

    expect(
      await screen.findByText("주최자만 볼 수 있어요."),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText(/주최자 의견/)).not.toBeInTheDocument();
  });

  it("없는 스터디면 찾을 수 없다고 안내한다", async () => {
    mock(() => json(404, { status: 404, code: "NOT_FOUND" }));
    renderView();

    expect(
      await screen.findByText("스터디를 찾을 수 없어요."),
    ).toBeInTheDocument();
  });

  it("들어온 이의의 사유를 보고 의견을 적어 저장한다", async () => {
    mock(() => json(200, [hostAppeal(5)]));
    renderView();
    const user = userEvent.setup();

    expect(await screen.findByText("지하철이 멈췄어요")).toBeInTheDocument();
    expect(screen.getByText(/참여자 · 노쇼 후보/)).toBeInTheDocument();

    await user.type(
      screen.getByLabelText(/주최자 의견/),
      "  그날 지연이 있었어요  ",
    );
    await user.click(screen.getByRole("button", { name: "의견 저장" }));

    await vi.waitFor(() => expect(puts()).toHaveLength(1));
    const [url, init] = puts()[0];
    expect(String(url)).toContain("/appeals/5/host-opinion");
    expect(JSON.parse(String(init?.body))).toEqual({
      opinion: "그날 지연이 있었어요",
    });
    expect(await screen.findByText("의견을 저장했어요.")).toBeInTheDocument();
  });

  it("의견이 비어 있으면 보내지 않고, 이미 결정된 이의(APPEAL_CLOSED)는 안내한다", async () => {
    mock(
      () => json(200, [hostAppeal(5)]),
      () => json(409, { status: 409, code: "APPEAL_CLOSED" }),
    );
    renderView();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "의견 저장" }));
    expect(
      await screen.findByText("의견을 입력해 주세요."),
    ).toBeInTheDocument();
    expect(puts()).toHaveLength(0);

    await user.type(screen.getByLabelText(/주최자 의견/), "의견");
    await user.click(screen.getByRole("button", { name: "의견 저장" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "이미 결정된 이의",
    );
  });

  it("결정된 이의는 입력칸 없이 남긴 의견만 보여 준다", async () => {
    mock(() =>
      json(200, [
        hostAppeal(5, { status: "REJECTED", hostOpinion: "확인 불가" }),
      ]),
    );
    renderView();

    expect(await screen.findByText("기각됨")).toBeInTheDocument();
    expect(screen.getByText("확인 불가")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "의견 저장" }),
    ).not.toBeInTheDocument();
  });
});
