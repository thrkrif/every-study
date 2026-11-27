import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { EditProfileView, splitJob } from "./EditProfileView";

const push = vi.fn();
const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push }),
}));

const BASE_URL = "http://api.test";

const JOBS = [
  {
    code: "IT",
    name: "IT·개발",
    children: [{ code: "IT_BACKEND", name: "백엔드", children: [] }],
  },
  { code: "PUBLIC", name: "공무원·공공", children: [] },
];

const META = {
  topics: [],
  jobs: JOBS,
  regions: [
    { code: "SEOUL", name: "서울", children: [] },
    { code: "GYEONGGI", name: "경기", children: [] },
  ],
  study: {},
  checkin: {},
};

const MEMBER = {
  id: 7,
  nickname: "준비생",
  jobCode: "IT_BACKEND",
  districtCode: "GYEONGGI",
  role: "USER",
  onboarded: true,
  joinedAt: "2026-11-18T00:00:00Z",
};

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const fetchMock = vi.fn<typeof fetch>();

function serve(
  handler: (method: string, path: string, body?: string) => Response,
) {
  fetchMock.mockImplementation(async (url, init) =>
    handler(
      init?.method ?? "GET",
      String(url).replace(`${BASE_URL}/api/v1`, ""),
      init?.body as string | undefined,
    ),
  );
}

function renderView() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <EditProfileView />
    </QueryClientProvider>,
  );
}

describe("splitJob", () => {
  it("세부 직무 코드는 대분류와 세부 직무로 나눈다", () => {
    expect(splitJob(JOBS, "IT_BACKEND")).toEqual({
      category: "IT",
      detail: "IT_BACKEND",
    });
  });

  it("대분류 코드는 세부 직무 없이 돌려준다", () => {
    expect(splitJob(JOBS, "PUBLIC")).toEqual({
      category: "PUBLIC",
      detail: "",
    });
  });
});

describe("EditProfileView", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE_URL);
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
    push.mockReset();
    replace.mockReset();
  });

  it("지금 값을 채워 보여 주고, 바꾼 직군·지역을 세 값과 함께 저장한 뒤 마이페이지로 간다", async () => {
    let saved: unknown;
    serve((method, path, body) => {
      if (path === "/me") return json(200, MEMBER);
      if (path === "/meta/options") return json(200, META);
      if (method === "PUT" && path === "/me/profile") {
        saved = JSON.parse(body!);
        return json(200, {
          ...MEMBER,
          jobCode: "PUBLIC",
          districtCode: "SEOUL",
        });
      }
      return json(404, {});
    });
    const user = userEvent.setup();
    renderView();

    expect(await screen.findByLabelText("닉네임")).toHaveValue("준비생");
    expect(await screen.findByRole("radio", { name: /백엔드/ })).toBeChecked();
    expect(screen.getByRole("radio", { name: /경기/ })).toBeChecked();

    await user.click(screen.getByRole("radio", { name: /공무원·공공/ }));
    await user.click(screen.getByRole("radio", { name: /서울/ }));
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/me"));
    expect(saved).toEqual({
      nickname: "준비생",
      jobCode: "PUBLIC",
      districtCode: "SEOUL",
    });
  });

  it("다른 회원이 쓰는 닉네임이면 닉네임 칸에 안내하고 머무른다", async () => {
    serve((method, path) => {
      if (path === "/me") return json(200, MEMBER);
      if (path === "/meta/options") return json(200, META);
      if (method === "PUT")
        return json(409, { status: 409, code: "NICKNAME_TAKEN" });
      return json(404, {});
    });
    const user = userEvent.setup();
    renderView();

    const field = await screen.findByLabelText("닉네임");
    await screen.findByRole("radio", { name: /백엔드/ });
    await user.clear(field);
    await user.type(field, "다른이름");
    await user.click(screen.getByRole("button", { name: "저장" }));

    expect(
      await screen.findByText(/이미 쓰는 닉네임이에요/),
    ).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });

  it("온보딩을 마치지 않은 회원은 온보딩으로 보낸다", async () => {
    serve((_, path) =>
      path === "/me"
        ? json(200, { ...MEMBER, nickname: null, onboarded: false })
        : json(200, META),
    );
    renderView();
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/onboarding"));
  });
});
