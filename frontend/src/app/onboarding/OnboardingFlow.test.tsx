import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { OnboardingFlow } from "./OnboardingFlow";

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn() }),
}));

const BASE_URL = "http://api.test";

const META = {
  topics: [],
  jobs: [
    {
      code: "IT",
      name: "IT·개발",
      children: [{ code: "IT_BACKEND", name: "백엔드", children: [] }],
    },
    { code: "PUBLIC", name: "공무원·공공", children: [] },
  ],
  regions: [
    { code: "SEOUL", name: "서울", children: [] },
    { code: "GYEONGGI", name: "경기", children: [] },
  ],
  study: {},
  checkin: {},
};

const NEW_MEMBER = {
  id: 7,
  nickname: null,
  jobCode: null,
  districtCode: null,
  role: "USER",
  onboarded: false,
  joinedAt: "2026-11-18T00:00:00Z",
};

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
const problem = (status: number, code: string, extra = {}) =>
  json(status, { status, code, ...extra });

type Handler = (method: string, path: string, body?: string) => Response;

const fetchMock = vi.fn<typeof fetch>();

function serve(handler: Handler) {
  fetchMock.mockImplementation(async (url, init) =>
    handler(
      init?.method ?? "GET",
      String(url).replace(`${BASE_URL}/api/v1`, ""),
      init?.body as string | undefined,
    ),
  );
}

function renderFlow() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <OnboardingFlow />
    </QueryClientProvider>,
  );
}

describe("OnboardingFlow", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE_URL);
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
    replace.mockReset();
  });

  it("액세스 토큰이 만료돼도 갱신에 성공하면 온보딩을 이어서 보여 준다", async () => {
    let refreshed = false;
    serve((method, path) => {
      if (path === "/auth/refresh") {
        refreshed = true;
        return new Response(null, { status: 204 });
      }
      if (path === "/me")
        return refreshed
          ? json(200, NEW_MEMBER)
          : problem(401, "UNAUTHENTICATED");
      return json(200, META);
    });

    renderFlow();

    expect(
      await screen.findByRole("heading", { name: "어떻게 불러 드릴까요?" }),
    ).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it("갱신에도 실패하면 로그인 화면으로 보낸다", async () => {
    serve(() => problem(401, "UNAUTHENTICATED"));

    renderFlow();

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
  });

  it("이미 온보딩을 마친 회원은 첫 화면으로 보낸다", async () => {
    serve((method, path) =>
      path === "/me"
        ? json(200, { ...NEW_MEMBER, nickname: "준비생", onboarded: true })
        : json(200, META),
    );

    renderFlow();

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/"));
  });

  it("닉네임·직군·지역을 차례로 받아 저장하고 첫 화면으로 보낸다", async () => {
    const user = userEvent.setup();
    let saved: unknown;
    serve((method, path, body) => {
      if (path === "/me") return json(200, NEW_MEMBER);
      if (path === "/me/profile") {
        saved = JSON.parse(body!);
        return json(200, {
          ...NEW_MEMBER,
          ...(saved as object),
          onboarded: true,
        });
      }
      return json(200, META);
    });
    renderFlow();

    await user.type(await screen.findByLabelText("닉네임"), "  준비생 ");
    await user.click(screen.getByRole("button", { name: "다음" }));

    await user.click(await screen.findByRole("radio", { name: "IT·개발" }));
    await user.click(screen.getByRole("radio", { name: "백엔드" }));
    await user.click(screen.getByRole("button", { name: "다음" }));

    const start = screen.getByRole("button", { name: "시작하기" });
    expect(start).toBeDisabled();
    expect(screen.getByText("활동 지역을 골라 주세요.")).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "경기" }));
    await user.click(start);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/"));
    expect(saved).toEqual({
      nickname: "준비생",
      jobCode: "IT_BACKEND",
      districtCode: "GYEONGGI",
    });
  });

  it("세부 직무를 고르지 않으면 대분류 코드로 저장한다", async () => {
    const user = userEvent.setup();
    let saved: { jobCode?: string } = {};
    serve((method, path, body) => {
      if (path === "/me") return json(200, NEW_MEMBER);
      if (path === "/me/profile") {
        saved = JSON.parse(body!);
        return json(200, { ...NEW_MEMBER, onboarded: true });
      }
      return json(200, META);
    });
    renderFlow();

    await user.type(await screen.findByLabelText("닉네임"), "준비생");
    await user.click(screen.getByRole("button", { name: "다음" }));
    await user.click(await screen.findByRole("radio", { name: "IT·개발" }));
    expect(screen.getByRole("radio", { name: "고르지 않음" })).toBeChecked();
    await user.click(screen.getByRole("button", { name: "다음" }));
    await user.click(screen.getByRole("radio", { name: "서울" }));
    await user.click(screen.getByRole("button", { name: "시작하기" }));

    await waitFor(() => expect(saved.jobCode).toBe("IT"));
  });

  it("규칙에 맞지 않는 닉네임이면 다음 단계로 가지 않고 이유를 보여 준다", async () => {
    const user = userEvent.setup();
    serve((method, path) =>
      path === "/me" ? json(200, NEW_MEMBER) : json(200, META),
    );
    renderFlow();

    await user.type(await screen.findByLabelText("닉네임"), "준비 생");
    await user.click(screen.getByRole("button", { name: "다음" }));

    expect(
      screen.getByText("한글, 영문, 숫자, 밑줄(_)만 쓸 수 있어요."),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("닉네임")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });

  it("다른 회원이 쓰는 닉네임이면 닉네임 단계로 돌아가 안내한다", async () => {
    const user = userEvent.setup();
    serve((method, path) => {
      if (path === "/me") return json(200, NEW_MEMBER);
      if (path === "/me/profile") return problem(409, "NICKNAME_TAKEN");
      return json(200, META);
    });
    renderFlow();

    await user.type(await screen.findByLabelText("닉네임"), "준비생");
    await user.click(screen.getByRole("button", { name: "다음" }));
    await user.click(await screen.findByRole("radio", { name: "공무원·공공" }));
    await user.click(screen.getByRole("button", { name: "다음" }));
    await user.click(screen.getByRole("radio", { name: "서울" }));
    await user.click(screen.getByRole("button", { name: "시작하기" }));

    expect(
      await screen.findByText(
        "이미 쓰는 닉네임이에요. 다른 닉네임을 입력해 주세요.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("닉네임")).toHaveValue("준비생");
    expect(replace).not.toHaveBeenCalled();
  });
});
