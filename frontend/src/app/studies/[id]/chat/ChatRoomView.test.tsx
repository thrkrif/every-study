import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ChatSocketFactory, ChatSocketHandlers } from "@/lib/chatSocket";
import { ChatRoomView } from "./ChatRoomView";

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

const STUDY = (myState: string) => ({
  id: 1,
  title: "모각코",
  myState,
  status: "ACTIVE",
  members: [],
});

const msg = (id: number, over: Record<string, unknown> = {}) => ({
  id,
  kind: "TEXT",
  senderId: 8,
  senderNickname: "상대",
  content: `메시지 ${id}`,
  hidden: false,
  deleted: false,
  createdAt: "2026-11-23T03:00:00Z",
  ...over,
});

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const fetchMock = vi.fn<typeof fetch>();

type Fake = {
  handlers: ChatSocketHandlers;
  sent: string[];
  closed: boolean;
};

describe("ChatRoomView", () => {
  let sockets: Fake[];
  const connect: ChatSocketFactory = (_studyId, handlers) => {
    const fake: Fake = { handlers, sent: [], closed: false };
    sockets.push(fake);
    return {
      send: (content) => fake.sent.push(content),
      close: () => {
        fake.closed = true;
      },
    };
  };

  beforeEach(() => {
    sockets = [];
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE_URL);
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  type Setup = {
    room?: Record<string, unknown>;
    roomStatus?: number;
    /** 호출 순서대로의 최근 대화 응답 (마지막 응답을 반복한다) */
    pages?: { items: unknown[]; nextCursor: string | null }[];
    older?: Record<string, { items: unknown[]; nextCursor: string | null }>;
    myState?: string;
    messagesStatus?: number;
  };

  function mock(setup: Setup = {}) {
    let latestCalls = 0;
    fetchMock.mockImplementation(async (url, init) => {
      const parsed = new URL(String(url));
      const path = parsed.pathname.replace("/api/v1", "");
      if (path === "/me") return json(200, ME);
      if (path === "/studies/1")
        return json(200, STUDY(setup.myState ?? "MEMBER"));
      if (path === "/studies/1/chat") {
        if (setup.roomStatus) {
          return json(setup.roomStatus, {
            status: setup.roomStatus,
            code: setup.roomStatus === 403 ? "FORBIDDEN" : "NOT_FOUND",
          });
        }
        return json(
          200,
          setup.room ?? { status: "OPEN", notice: null, unreadCount: 0 },
        );
      }
      if (path === "/studies/1/chat/messages") {
        if (setup.messagesStatus) {
          return json(setup.messagesStatus, {
            status: setup.messagesStatus,
            code: "CHAT_CLOSED",
          });
        }
        const before = parsed.searchParams.get("before");
        if (before) {
          return json(
            200,
            setup.older?.[before] ?? { items: [], nextCursor: null },
          );
        }
        const pages = setup.pages ?? [{ items: [], nextCursor: null }];
        const page = pages[Math.min(latestCalls, pages.length - 1)];
        latestCalls += 1;
        return json(200, page);
      }
      if (
        path === "/blocks/8" &&
        (init?.method === "PUT" || init?.method === "DELETE")
      )
        return new Response(null, { status: 204 });
      if (path === "/reports" && init?.method === "POST")
        return json(201, { id: 1 });
      if (path === "/studies/1/chat/read" && init?.method === "PUT")
        return new Response(null, { status: 204 });
      if (path === "/studies/1/chat/notice" && init?.method === "PUT")
        return json(200, {
          content: "새 공지",
          updatedAt: "2026-11-23T04:00:00Z",
        });
      return json(404, { status: 404, code: "NOT_FOUND" });
    });
  }

  function renderView() {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    return render(
      <QueryClientProvider client={client}>
        <ChatRoomView id="1" connect={connect} />
      </QueryClientProvider>,
    );
  }

  const connected = async () => {
    await vi.waitFor(() => expect(sockets).toHaveLength(1));
    act(() => sockets[0].handlers.onConnected());
  };

  it("비회원이면 로그인을 안내하고, 참여자가 아니면 안내한다", async () => {
    fetchMock.mockImplementation(async () =>
      json(401, { status: 401, code: "UNAUTHENTICATED" }),
    );
    const first = renderView();
    expect(await screen.findByRole("link", { name: "로그인" })).toHaveAttribute(
      "href",
      "/login",
    );
    first.unmount();

    mock({ roomStatus: 403 });
    renderView();
    expect(
      await screen.findByText("참여 중인 사람만 볼 수 있는 채팅방이에요."),
    ).toBeInTheDocument();
  });

  it("이전 대화를 오래된 것부터 보여 주고, 읽음 위치를 서버에 알린다", async () => {
    mock({
      pages: [
        {
          items: [msg(3), msg(2, { senderId: 7, senderNickname: "나" })],
          nextCursor: null,
        },
      ],
    });
    renderView();

    await screen.findByText("메시지 3");
    const list = screen.getByRole("list");
    const items = within(list).getAllByRole("listitem");
    expect(items[0]).toHaveTextContent("메시지 2");
    expect(items[1]).toHaveTextContent("메시지 3");
    expect(items[0]).toHaveTextContent("나");
    await vi.waitFor(() => {
      const read = fetchMock.mock.calls.find(
        ([url, init]) =>
          String(url).endsWith("/chat/read") && init?.method === "PUT",
      );
      expect(read).toBeDefined();
      expect(JSON.parse(String(read?.[1]?.body))).toEqual({
        lastReadMessageId: 3,
      });
    });
  });

  it("읽지 않은 메시지 수를 알려 주고 누르면 사라진다", async () => {
    mock({
      room: { status: "OPEN", notice: null, unreadCount: 4 },
      pages: [{ items: [msg(1)], nextCursor: null }],
    });
    renderView();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: /읽지 않은 메시지 4개/ }),
    );

    expect(
      screen.queryByRole("button", { name: /읽지 않은 메시지/ }),
    ).not.toBeInTheDocument();
  });

  it("맨 위의 이전 대화 보기로 더 오래된 대화를 이어 받는다", async () => {
    mock({
      pages: [{ items: [msg(5), msg(4)], nextCursor: "4" }],
      older: { "4": { items: [msg(3), msg(2)], nextCursor: null } },
    });
    renderView();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "이전 대화 보기" }),
    );

    const items = within(await screen.findByRole("list")).getAllByRole(
      "listitem",
    );
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining("메시지 2"),
      expect.stringContaining("메시지 3"),
      expect.stringContaining("메시지 4"),
      expect.stringContaining("메시지 5"),
    ]);
    expect(
      screen.queryByRole("button", { name: "이전 대화 보기" }),
    ).not.toBeInTheDocument();
  });

  it("실시간으로 온 메시지를 덧붙이고, 같은 메시지가 다시 와도 한 번만 보인다", async () => {
    mock({ pages: [{ items: [msg(1)], nextCursor: null }] });
    renderView();
    await screen.findByText("메시지 1");
    await connected();

    act(() => {
      sockets[0].handlers.onTopic({
        type: "MESSAGE",
        message: msg(2) as never,
      });
      sockets[0].handlers.onTopic({
        type: "MESSAGE",
        message: msg(2) as never,
      });
    });

    expect(await screen.findAllByText("메시지 2")).toHaveLength(1);
  });

  it("재연결하면 최근 대화를 다시 받아 빠진 구간을 채우고 중복은 없다", async () => {
    mock({
      pages: [
        { items: [msg(2), msg(1)], nextCursor: null },
        // 연결이 끊긴 사이에 3, 4 가 쌓였다
        { items: [msg(4), msg(3), msg(2), msg(1)], nextCursor: null },
      ],
    });
    renderView();
    await screen.findByText("메시지 2");
    await connected();
    act(() => sockets[0].handlers.onDisconnected());

    act(() => sockets[0].handlers.onConnected()); // 재연결

    expect(await screen.findByText("메시지 4")).toBeInTheDocument();
    expect(screen.getByText("메시지 3")).toBeInTheDocument();
    expect(screen.getAllByText("메시지 2")).toHaveLength(1);
    expect(screen.getAllByText("메시지 1")).toHaveLength(1);
  });

  it("닫힌 방은 입력이 막히고 닫혔다는 안내가 보이며 실시간 연결을 열지 않는다", async () => {
    mock({
      room: {
        status: "CLOSED",
        notice: { content: "마지막 공지", updatedAt: "2026-11-23T03:00:00Z" },
        unreadCount: 0,
      },
    });
    renderView();

    expect(await screen.findByText(/채팅방이 닫혔어요/)).toBeInTheDocument();
    expect(screen.getByText("마지막 공지")).toBeInTheDocument();
    expect(screen.getByLabelText("메시지")).toBeDisabled();
    expect(screen.getByRole("button", { name: "보내기" })).toBeDisabled();
    expect(sockets).toHaveLength(0);
  });

  it("방이 열린 채로 있다가 ROOM_CLOSED 가 오면 입력이 막힌다", async () => {
    mock({ pages: [{ items: [msg(1)], nextCursor: null }] });
    renderView();
    await screen.findByText("메시지 1");
    await connected();
    expect(screen.getByLabelText("메시지")).toBeEnabled();

    act(() => sockets[0].handlers.onTopic({ type: "ROOM_CLOSED" }));

    expect(await screen.findByText(/채팅방이 닫혔어요/)).toBeInTheDocument();
    expect(screen.getByLabelText("메시지")).toBeDisabled();
  });

  it("차단한 사람과 지운 메시지는 내용 없이 표시하고, 그 사람의 새 메시지도 가린다", async () => {
    mock({
      pages: [
        {
          items: [
            msg(3, { deleted: true, content: null }),
            msg(2, { hidden: true, content: null, senderId: 9 }),
            msg(1),
          ],
          nextCursor: null,
        },
      ],
    });
    renderView();

    expect(await screen.findByText("삭제된 메시지예요.")).toBeInTheDocument();
    expect(screen.getByText("차단한 사용자의 메시지예요.")).toBeInTheDocument();
    await connected();

    act(() =>
      sockets[0].handlers.onTopic({
        type: "MESSAGE",
        message: msg(4, {
          senderId: 9,
          content: "차단한 사람의 새 메시지",
        }) as never,
      }),
    );

    expect(
      screen.queryByText("차단한 사람의 새 메시지"),
    ).not.toBeInTheDocument();
    expect(screen.getAllByText("차단한 사용자의 메시지예요.")).toHaveLength(2);
  });

  it("시스템 메시지를 가운데 안내로 보이고 새 주최자 안내를 강조한다", async () => {
    mock({
      pages: [
        {
          items: [
            msg(2, {
              kind: "SYSTEM",
              senderId: null,
              senderNickname: null,
              content: "가님이 새 주최자가 됐어요",
            }),
            msg(1, {
              kind: "SYSTEM",
              senderId: null,
              senderNickname: null,
              content: "나님이 입장했어요",
            }),
          ],
          nextCursor: null,
        },
      ],
    });
    renderView();

    const host = await screen.findByText("가님이 새 주최자가 됐어요");
    expect(host).toHaveAttribute("data-emphasis", "true");
    expect(screen.getByText("나님이 입장했어요")).not.toHaveAttribute(
      "data-emphasis",
    );
  });

  it("연결되면 메시지를 보내고 입력칸을 비운다. 서버가 거부하면 사유를 안내한다", async () => {
    mock();
    renderView();
    const user = userEvent.setup();
    await screen.findByLabelText("메시지");
    expect(screen.getByLabelText("메시지")).toBeDisabled(); // 연결 전
    await connected();

    await user.type(screen.getByLabelText("메시지"), "  안녕하세요  ");
    await user.click(screen.getByRole("button", { name: "보내기" }));

    expect(sockets[0].sent).toEqual(["안녕하세요"]);
    expect(screen.getByLabelText("메시지")).toHaveValue("");

    act(() => sockets[0].handlers.onRejected("FORBIDDEN"));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "참여 중인 사람만 보낼 수 있어요.",
    );
  });

  it("나가서 구독이 끝났다는 알림을 받으면 입력이 막히고 안내한다", async () => {
    mock();
    renderView();
    await screen.findByLabelText("메시지");
    await connected();

    act(() =>
      sockets[0].handlers.onPersonal({
        type: "SUBSCRIPTION_ENDED",
        studyId: 1,
        reason: "LEFT",
      }),
    );

    expect(
      await screen.findByText(/더 이상 이 스터디의 참여자가 아니라/),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("메시지")).toBeDisabled();
  });

  it("공지는 주최자에게만 수정 버튼이 있고 저장하면 바로 바뀐다", async () => {
    mock({
      myState: "HOST",
      room: {
        status: "OPEN",
        notice: { content: "처음 공지", updatedAt: "2026-11-23T03:00:00Z" },
        unreadCount: 0,
      },
    });
    const host = renderView();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "공지 수정" }));
    const field = screen.getByRole("textbox", { name: "공지" });
    await user.clear(field);
    await user.type(field, "새 공지");
    await user.click(screen.getByRole("button", { name: "공지 저장" }));

    expect(await screen.findByText("새 공지")).toBeInTheDocument();
    host.unmount();

    mock({
      myState: "MEMBER",
      room: {
        status: "OPEN",
        notice: { content: "처음 공지", updatedAt: "2026-11-23T03:00:00Z" },
        unreadCount: 0,
      },
    });
    renderView();
    expect(await screen.findByText("처음 공지")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "공지 수정" }),
    ).not.toBeInTheDocument();
  });

  it("상대 메시지를 차단하면 서버에 알리고 그 사람의 메시지가 바로 가려진다", async () => {
    mock({ pages: [{ items: [msg(1), msg(2)], nextCursor: null }] });
    renderView();
    await connected();
    const user = userEvent.setup();
    await screen.findByText("메시지 1");

    await user.click(screen.getAllByRole("button", { name: "차단" })[0]);

    await vi.waitFor(() => {
      const call = fetchMock.mock.calls.find(
        ([url, init]) =>
          String(url).endsWith("/blocks/8") && init?.method === "PUT",
      );
      expect(call).toBeDefined();
    });
    expect(
      await screen.findAllByText("차단한 사용자의 메시지예요."),
    ).toHaveLength(2);
    expect(screen.queryByText("메시지 1")).not.toBeInTheDocument();
    // 이후 실시간으로 오는 그 사람의 메시지도 가린다
    act(() => {
      sockets[0].handlers.onTopic({
        type: "MESSAGE",
        message: msg(3) as never,
      });
    });
    expect(
      await screen.findAllByText("차단한 사용자의 메시지예요."),
    ).toHaveLength(3);
  });

  it("차단한 사람의 메시지에서 차단 해제를 누르면 서버에 알리고 대화를 다시 받는다", async () => {
    mock({
      pages: [
        {
          items: [msg(1, { hidden: true, content: null })],
          nextCursor: null,
        },
        { items: [msg(1)], nextCursor: null },
      ],
    });
    renderView();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "차단 해제" }));

    expect(await screen.findByText("메시지 1")).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(
      ([url, init]) =>
        String(url).endsWith("/blocks/8") && init?.method === "DELETE",
    );
    expect(call).toBeDefined();
  });

  it("메시지를 신고하려면 사유를 골라야 하고, 보내면 접수 안내가 나온다. 내 메시지에는 신고가 없다", async () => {
    mock({
      pages: [
        {
          items: [msg(2, { senderId: 7, senderNickname: "나" }), msg(1)],
          nextCursor: null,
        },
      ],
    });
    renderView();
    const user = userEvent.setup();
    await screen.findByText("메시지 1");

    // 내 메시지(2)에는 신고·차단이 없고 상대 메시지(1)에만 있다
    expect(screen.getAllByRole("button", { name: "신고" })).toHaveLength(1);

    await user.click(screen.getByRole("button", { name: "신고" }));
    const submit = screen.getByRole("button", { name: "신고하기" });
    expect(submit).toBeDisabled();

    await user.click(screen.getByLabelText("욕설·비방"));
    expect(submit).toBeEnabled();
    await user.click(submit);

    expect(await screen.findByText(/신고를 접수했어요/)).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(
      ([url, init]) =>
        String(url).endsWith("/reports") && init?.method === "POST",
    );
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({
      targetType: "MESSAGE",
      targetId: 1,
      reason: "ABUSE",
    });
  });
});
