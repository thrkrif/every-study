import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WithdrawView } from "./WithdrawView";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace: vi.fn() }),
}));

const ME = {
  id: 7,
  nickname: "준비생",
  jobCode: "IT",
  districtCode: "X",
  role: "USER",
  onboarded: true,
  joinedAt: "2026-11-17T00:00:00Z",
};

const json = (status: number, body?: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const fetchMock = vi.fn<typeof fetch>();
let loggedIn: boolean;
let deleteStatus: number;

describe("WithdrawView", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://api.test");
    vi.stubGlobal("fetch", fetchMock);
    loggedIn = true;
    deleteStatus = 204;
    push.mockReset();
    fetchMock.mockImplementation(async (url, init) => {
      const path = new URL(String(url)).pathname.replace("/api/v1", "");
      if (path === "/me" && init?.method === "DELETE") {
        if (deleteStatus === 204) loggedIn = false;
        return deleteStatus === 204
          ? new Response(null, { status: 204 })
          : json(deleteStatus, {
              status: deleteStatus,
              code: "INTERNAL_ERROR",
            });
      }
      if (path === "/me" && loggedIn) return json(200, ME);
      return json(401, { status: 401, code: "UNAUTHENTICATED" });
    });
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
    render(
      <QueryClientProvider client={client}>
        <WithdrawView />
      </QueryClientProvider>,
    );
    return client;
  }

  const deleteCalls = () =>
    fetchMock.mock.calls.filter(([, init]) => init?.method === "DELETE");

  it("진행 중 스터디에 미치는 영향을 안내하고, 이해했다고 체크하기 전에는 탈퇴할 수 없다", async () => {
    renderView();

    expect(await screen.findByText(/참여 취소로 처리돼요/)).toBeInTheDocument();
    expect(
      screen.getByText(/남은 참여자 중 한 명에게 넘어가고/),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "회원 탈퇴" })).toBeDisabled();
    expect(deleteCalls()).toHaveLength(0);
  });

  it("확인 창에서 확정하기 전에는 서버를 부르지 않고, 취소하면 아무 일도 없다", async () => {
    renderView();
    const user = userEvent.setup();
    await screen.findByText(/참여 취소로 처리돼요/);

    await user.click(screen.getByLabelText(/이해했고 탈퇴할게요/));
    await user.click(screen.getByRole("button", { name: "회원 탈퇴" }));
    const dialog = screen.getByRole("alertdialog");
    expect(deleteCalls()).toHaveLength(0);

    await user.click(within(dialog).getByRole("button", { name: "취소" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(deleteCalls()).toHaveLength(0);
  });

  it("탈퇴를 확정하면 서버를 부르고 로그인 상태를 비운 뒤 처음 화면으로 보낸다", async () => {
    const client = renderView();
    const user = userEvent.setup();
    await screen.findByText(/참여 취소로 처리돼요/);

    await user.click(screen.getByLabelText(/이해했고 탈퇴할게요/));
    await user.click(screen.getByRole("button", { name: "회원 탈퇴" }));
    await user.click(
      within(screen.getByRole("alertdialog")).getByRole("button", {
        name: "탈퇴 확정",
      }),
    );

    await vi.waitFor(() => expect(push).toHaveBeenCalledWith("/"));
    expect(deleteCalls()).toHaveLength(1);
    // 로그인 상태가 비워져 다시 받으면 비회원이다
    await vi.waitFor(() =>
      expect(
        screen.getByText("로그인한 회원만 탈퇴할 수 있어요."),
      ).toBeInTheDocument(),
    );
    expect(client.getQueryState(["me"])?.status).toBe("error");
  });

  it("탈퇴에 실패하면 안내하고 확인 창을 닫는다", async () => {
    deleteStatus = 500;
    renderView();
    const user = userEvent.setup();
    await screen.findByText(/참여 취소로 처리돼요/);

    await user.click(screen.getByLabelText(/이해했고 탈퇴할게요/));
    await user.click(screen.getByRole("button", { name: "회원 탈퇴" }));
    await user.click(
      within(screen.getByRole("alertdialog")).getByRole("button", {
        name: "탈퇴 확정",
      }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "탈퇴하지 못했어요",
    );
    expect(push).not.toHaveBeenCalled();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("비회원에게는 로그인을 안내한다", async () => {
    loggedIn = false;
    renderView();

    expect(
      await screen.findByText("로그인한 회원만 탈퇴할 수 있어요."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "로그인" })).toHaveAttribute(
      "href",
      "/login",
    );
  });
});
