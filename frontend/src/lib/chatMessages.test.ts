import { describe, expect, it } from "vitest";
import type { ChatMessage } from "./api/chat";
import {
  blockedSenders,
  isHostChangeNotice,
  lastMessageId,
  mergeMessages,
} from "./chatMessages";

const message = (id: number, over: Partial<ChatMessage> = {}): ChatMessage => ({
  id,
  kind: "TEXT",
  senderId: 1,
  senderNickname: "가",
  content: `메시지 ${id}`,
  hidden: false,
  deleted: false,
  createdAt: "2026-11-23T03:00:00Z",
  ...over,
});

describe("채팅 메시지 목록 규칙", () => {
  it("id 로 합쳐 중복이 없고 오래된 것이 위로 온다", () => {
    const merged = mergeMessages(
      [message(2), message(3)],
      [message(5), message(3), message(1)],
    );

    expect(merged.map((m) => m.id)).toEqual([1, 2, 3, 5]);
  });

  it("같은 id 는 나중에 받은 것으로 바뀐다 (차단·삭제 표시 반영)", () => {
    const merged = mergeMessages(
      [message(2)],
      [message(2, { hidden: true, content: null })],
    );

    expect(merged).toHaveLength(1);
    expect(merged[0].hidden).toBe(true);
  });

  it("가린 메시지의 보낸 사람이 차단한 사람이다", () => {
    const blocked = blockedSenders([
      message(1, { senderId: 7, hidden: true, content: null }),
      message(2, { senderId: 8 }),
      message(3, { senderId: null, kind: "SYSTEM" }),
    ]);

    expect([...blocked]).toEqual([7]);
  });

  it("마지막 메시지 번호와 새 주최자 안내를 알아본다", () => {
    expect(lastMessageId([])).toBeNull();
    expect(lastMessageId([message(1), message(4)])).toBe(4);
    expect(
      isHostChangeNotice(
        message(1, { kind: "SYSTEM", content: "가님이 새 주최자가 됐어요" }),
      ),
    ).toBe(true);
    expect(
      isHostChangeNotice(
        message(1, { kind: "SYSTEM", content: "가님이 입장했어요" }),
      ),
    ).toBe(false);
    expect(isHostChangeNotice(message(1, { content: "새 주최자" }))).toBe(
      false,
    );
  });
});
