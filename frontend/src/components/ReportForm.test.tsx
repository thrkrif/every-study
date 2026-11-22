import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ReportForm } from "./ReportForm";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const fetchMock = vi.fn<typeof fetch>();

describe("ReportForm", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://api.test");
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  const setup = () => {
    const onClose = vi.fn();
    render(
      <ReportForm
        targetType="STUDY"
        targetId={5}
        targetLabel="이 스터디"
        onClose={onClose}
      />,
    );
    return { onClose, user: userEvent.setup() };
  };

  it("사유를 고르기 전에는 보낼 수 없고, 호출도 없다", async () => {
    setup();
    expect(screen.getByRole("button", { name: "신고하기" })).toBeDisabled();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("기타는 상세 내용을 적어야 보낼 수 있다", async () => {
    const { user } = setup();
    await user.click(screen.getByLabelText("기타"));
    expect(screen.getByRole("button", { name: "신고하기" })).toBeDisabled();

    await user.type(screen.getByLabelText(/상세 내용/), "자세한 내용");
    expect(screen.getByRole("button", { name: "신고하기" })).toBeEnabled();
  });

  it("보내면 사유와 상세를 서버에 주고 접수 안내를 보여 준다", async () => {
    fetchMock.mockResolvedValue(json(201, { id: 9 }));
    const { user, onClose } = setup();

    await user.click(screen.getByLabelText("광고·홍보"));
    await user.type(screen.getByLabelText(/상세 내용/), "홍보 글");
    await user.click(screen.getByRole("button", { name: "신고하기" }));

    expect(await screen.findByText(/신고를 접수했어요/)).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe("http://api.test/api/v1/reports");
    expect(JSON.parse(String(init?.body))).toEqual({
      targetType: "STUDY",
      targetId: 5,
      reason: "SPAM_AD",
      detail: "홍보 글",
    });
    await user.click(screen.getByRole("button", { name: "닫기" }));
    expect(onClose).toHaveBeenCalled();
  });

  it("이미 신고한 대상이면 그렇게 안내하고 폼을 유지한다", async () => {
    fetchMock.mockResolvedValue(
      json(409, { status: 409, code: "ALREADY_REPORTED" }),
    );
    const { user } = setup();

    await user.click(screen.getByLabelText("욕설·비방"));
    await user.click(screen.getByRole("button", { name: "신고하기" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "이미 신고한 대상이에요",
    );
    expect(screen.getByRole("button", { name: "신고하기" })).toBeEnabled();
  });
});
