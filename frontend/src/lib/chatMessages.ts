// 채팅 메시지 목록을 다루는 순수 규칙 (PLAN T-051). 이전 대화·실시간 전달·재연결 보충이 같은 메시지를 여러 번 줄 수 있어
// id 로 합치고 오름차순으로 둔다.
import type { ChatMessage } from "./api/chat";

/** id 가 같은 메시지는 나중 것으로 덮어쓰고(차단·삭제 표시가 바뀔 수 있다) 오래된 것이 위로 오게 정렬한다 */
export function mergeMessages(
  existing: readonly ChatMessage[],
  incoming: readonly ChatMessage[],
): ChatMessage[] {
  const byId = new Map<number, ChatMessage>();
  for (const message of existing) byId.set(message.id, message);
  for (const message of incoming) byId.set(message.id, message);
  return [...byId.values()].sort((a, b) => a.id - b.id);
}

/** 이전 대화 응답에서 내가 차단한 사람의 id 를 모은다. 실시간 메시지를 가릴 때 쓴다 */
export function blockedSenders(messages: readonly ChatMessage[]): Set<number> {
  const blocked = new Set<number>();
  for (const message of messages) {
    if (message.hidden && message.senderId !== null) {
      blocked.add(message.senderId);
    }
  }
  return blocked;
}

/** 목록에서 가장 큰 메시지 번호. 비었으면 null */
export function lastMessageId(messages: readonly ChatMessage[]): number | null {
  return messages.length === 0 ? null : messages[messages.length - 1].id;
}

/** 시스템 메시지 중 새 주최자 안내 (PRD 5장 화면 9 승계 안내) */
export function isHostChangeNotice(message: ChatMessage): boolean {
  return (
    message.kind === "SYSTEM" && (message.content ?? "").includes("새 주최자")
  );
}
