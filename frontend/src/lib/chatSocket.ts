// 채팅 실시간 연결 (PLAN T-051, API 6장). STOMP over WebSocket 이고 인증은 핸드셰이크의 쿠키가 한다 (ADR-0002).
// 화면이 이 모듈만 알도록 얇게 감싸고, 테스트는 같은 모양의 가짜 연결을 끼워 넣는다.
import { Client } from "@stomp/stompjs";
import type { ChatMessage, ChatNotice } from "./api/chat";

/** 방 주제 `/topic/studies/{id}/chat` 로 오는 이벤트 */
export type ChatTopicEvent =
  | { type: "MESSAGE"; message: ChatMessage }
  | { type: "NOTICE"; notice: ChatNotice }
  | { type: "ROOM_CLOSED" };

/** 본인 대상 `/user/queue/events` 로 오는 이벤트 */
export type ChatPersonalEvent =
  | { type: "SUBSCRIPTION_ENDED"; studyId: number; reason: string }
  | { type: "SEND_REJECTED"; code: string };

export type ChatSocketHandlers = {
  /** 연결되고 구독이 끝났다. 재연결 때도 불린다 */
  onConnected: () => void;
  onDisconnected: () => void;
  onTopic: (event: ChatTopicEvent) => void;
  onPersonal: (event: ChatPersonalEvent) => void;
  /** 서버가 STOMP ERROR 로 거부했다. 사유 코드가 들어 있다 */
  onRejected: (code: string) => void;
};

export type ChatSocket = {
  send: (content: string) => void;
  close: () => void;
};

export type ChatSocketFactory = (
  studyId: number,
  handlers: ChatSocketHandlers,
) => ChatSocket;

function socketUrl(): string {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!base) {
    throw new Error("NEXT_PUBLIC_API_BASE_URL 이 설정되지 않았습니다");
  }
  return `${base.replace(/\/+$/, "").replace(/^http/, "ws")}/ws`;
}

function parse<T>(body: string): T | null {
  try {
    return JSON.parse(body) as T;
  } catch {
    return null;
  }
}

/** 실제 연결. 끊기면 stompjs 가 다시 연결하고, 그때마다 `onConnected` 가 불린다 */
export const connectChatSocket: ChatSocketFactory = (studyId, handlers) => {
  const client = new Client({
    brokerURL: socketUrl(),
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      client.subscribe(`/topic/studies/${studyId}/chat`, (frame) => {
        const event = parse<ChatTopicEvent>(frame.body);
        if (event) handlers.onTopic(event);
      });
      client.subscribe("/user/queue/events", (frame) => {
        const event = parse<ChatPersonalEvent>(frame.body);
        if (event) handlers.onPersonal(event);
      });
      handlers.onConnected();
    },
    onWebSocketClose: () => handlers.onDisconnected(),
    onStompError: (frame) =>
      handlers.onRejected(frame.headers["message"] ?? ""),
  });
  client.activate();
  return {
    send: (content) => {
      if (!client.connected) return;
      client.publish({
        destination: `/app/studies/${studyId}/chat`,
        body: JSON.stringify({ content }),
      });
    },
    close: () => {
      void client.deactivate();
    },
  };
};
