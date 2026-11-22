import { apiFetch } from "./client";

/** 채팅방 상태 (`GET /studies/{id}/chat`, API 6장) */
export type ChatRoom = {
  status: "OPEN" | "CLOSED";
  notice: ChatNotice | null;
  unreadCount: number;
};

export type ChatNotice = { content: string; updatedAt: string };

/**
 * 메시지 한 건 (이전 대화·실시간 전달 공통). 내가 차단한 사람의 메시지는 `hidden`, 운영자가 지운 메시지는 `deleted`
 * 이고 둘 다 `content` 가 null 이다. 시스템 메시지는 `senderId` 가 null 이다.
 */
export type ChatMessage = {
  id: number;
  kind: "TEXT" | "SYSTEM";
  senderId: number | null;
  senderNickname: string | null;
  content: string | null;
  hidden: boolean;
  deleted: boolean;
  createdAt: string;
};

/** 이전 대화 한 페이지. 최근 것부터이고 `nextCursor` 는 다음 요청의 `before` 다 */
export type ChatMessagePage = {
  items: ChatMessage[];
  nextCursor: string | null;
};

export const CHAT_PAGE_SIZE = 30;
export const CHAT_MESSAGE_MAX = 1000;
export const CHAT_NOTICE_MAX = 500;

export function fetchChatRoom(
  studyId: number,
  signal?: AbortSignal,
): Promise<ChatRoom> {
  return apiFetch<ChatRoom>(`/studies/${studyId}/chat`, { signal });
}

export function fetchChatMessages(
  studyId: number,
  before: string | undefined,
  signal?: AbortSignal,
): Promise<ChatMessagePage> {
  const params = new URLSearchParams({ size: String(CHAT_PAGE_SIZE) });
  if (before) params.set("before", before);
  return apiFetch<ChatMessagePage>(
    `/studies/${studyId}/chat/messages?${params}`,
    { signal },
  );
}

export function saveChatNotice(
  studyId: number,
  content: string,
): Promise<ChatNotice> {
  return apiFetch<ChatNotice>(`/studies/${studyId}/chat/notice`, {
    method: "PUT",
    body: { content },
  });
}

export function markChatRead(
  studyId: number,
  lastReadMessageId: number,
): Promise<void> {
  return apiFetch<void>(`/studies/${studyId}/chat/read`, {
    method: "PUT",
    body: { lastReadMessageId },
  });
}
