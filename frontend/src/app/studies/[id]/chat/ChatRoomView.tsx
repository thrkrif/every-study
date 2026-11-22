"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Button } from "@/components/Button";
import { ReportForm } from "@/components/ReportForm";
import buttonStyles from "@/components/Button.module.css";
import { TextAreaField } from "@/components/TextAreaField";
import {
  CHAT_MESSAGE_MAX,
  CHAT_NOTICE_MAX,
  fetchChatMessages,
  fetchChatRoom,
  markChatRead,
  saveChatNotice,
  type ChatMessage,
  type ChatNotice,
} from "@/lib/api/chat";
import { isApiError } from "@/lib/api/errors";
import { blockUser, unblockUser } from "@/lib/api/reports";
import { fetchStudy } from "@/lib/api/studies";
import {
  blockedSenders,
  isHostChangeNotice,
  lastMessageId,
  mergeMessages,
} from "@/lib/chatMessages";
import {
  connectChatSocket,
  type ChatSocket,
  type ChatSocketFactory,
} from "@/lib/chatSocket";
import { useSession } from "@/lib/session";
import { formatClockOf, nicknameOf } from "@/lib/studyFormat";
import styles from "./ChatRoomView.module.css";

type Props = {
  /** 주소의 id. 숫자가 아니면 없는 스터디로 본다 */
  id: string;
  /** 실시간 연결을 만든다. 테스트에서 가짜로 바꿔 끼운다 */
  connect?: ChatSocketFactory;
};

/** 보낼 수 없는 사유를 보여 줄 문구 (서버 코드 → 안내) */
function rejectionText(code: string): string {
  switch (code) {
    case "CHAT_CLOSED":
      return "닫힌 채팅방이라 보낼 수 없어요.";
    case "FORBIDDEN":
      return "참여 중인 사람만 보낼 수 있어요.";
    case "VALIDATION_FAILED":
      return `메시지는 1자 이상 ${CHAT_MESSAGE_MAX}자 이하로 보내 주세요.`;
    default:
      return "메시지를 보내지 못했어요. 잠시 뒤 다시 시도해 주세요.";
  }
}

/**
 * 채팅방 (PRD 4.12·5장 화면 8, API 6장, PLAN T-051). 처음에는 REST 로 방 상태와 최근 대화를 받고, 실시간 연결(STOMP)로
 * 새 메시지를 받는다. 연결이 끊겼다 다시 붙을 때마다 최근 대화를 다시 받아 빠진 구간을 채운다(id 로 합쳐 중복이 없다).
 * 닫힌 방은 입력을 막고, 차단한 사람·지운 메시지는 내용 없이 표시한다.
 */
export function ChatRoomView({ id, connect = connectChatSocket }: Props) {
  const studyId = /^\d+$/.test(id) ? Number(id) : undefined;
  const session = useSession();
  const member = session.status === "member";

  const study = useQuery({
    queryKey: ["study", studyId],
    queryFn: ({ signal }) => fetchStudy(studyId!, signal),
    enabled: studyId !== undefined && member,
    retry: false,
  });
  const room = useQuery({
    queryKey: ["chat-room", studyId],
    queryFn: ({ signal }) => fetchChatRoom(studyId!, signal),
    enabled: studyId !== undefined && member,
    retry: false,
    // 방 상태와 공지는 이 화면이 이벤트로 직접 고친다. 다시 받아 덮어쓰지 않는다.
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });

  if (studyId === undefined || isApiError(room.error, "NOT_FOUND")) {
    return <Notice text="채팅방을 찾을 수 없어요." />;
  }
  if (session.status === "loading") return <Notice text="불러오는 중…" />;
  if (session.status === "anonymous") {
    return (
      <Notice
        text="로그인하면 채팅을 볼 수 있어요."
        href="/login"
        cta="로그인"
      />
    );
  }
  if (isApiError(room.error, "FORBIDDEN")) {
    return (
      <Notice
        text="참여 중인 사람만 볼 수 있는 채팅방이에요."
        href={`/studies/${studyId}`}
        cta="스터디 보기"
      />
    );
  }
  if (room.isError || session.status === "error") {
    return (
      <Notice
        text="채팅방을 불러오지 못했어요."
        action={() => void room.refetch()}
      />
    );
  }
  if (room.isPending || session.status !== "member") {
    return <Notice text="불러오는 중…" />;
  }
  return (
    <ChatRoom
      studyId={studyId}
      title={study.data?.title}
      isHost={study.data?.myState === "HOST"}
      myId={session.me.id}
      initialStatus={room.data.status}
      initialNotice={room.data.notice}
      initialUnread={room.data.unreadCount}
      connect={connect}
    />
  );
}

type RoomProps = {
  studyId: number;
  title: string | undefined;
  isHost: boolean;
  myId: number;
  initialStatus: "OPEN" | "CLOSED";
  initialNotice: ChatNotice | null;
  initialUnread: number;
  connect: ChatSocketFactory;
};

function ChatRoom({
  studyId,
  title,
  isHost,
  myId,
  initialStatus,
  initialNotice,
  initialUnread,
  connect,
}: RoomProps) {
  const [closedByEvent, setClosedByEvent] = useState(false);
  const [notice, setNotice] = useState(initialNotice);
  /** 위로 스크롤해 받은 이전 대화. 다음 이전 대화의 시작점은 처음 받은 최근 대화에서 이어진다 */
  const [older, setOlder] = useState<{
    items: ChatMessage[];
    cursor: string | null | undefined;
  }>({ items: [], cursor: undefined });
  /** 실시간으로 받은 메시지 */
  const [live, setLive] = useState<ChatMessage[]>([]);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [connected, setConnected] = useState(false);
  const [left, setLeft] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const [unread, setUnread] = useState(initialUnread);
  const [draft, setDraft] = useState("");
  /** 신고 입력을 펼쳐 둔 메시지 */
  const [reporting, setReporting] = useState<number | null>(null);
  /** 이 화면에서 방금 차단·해제한 사람. 서버 응답이 다시 올 때까지 표시를 바로 바꾼다 */
  const [blockedNow, setBlockedNow] = useState<ReadonlySet<number>>(new Set());
  const [unblockedNow, setUnblockedNow] = useState<ReadonlySet<number>>(
    new Set(),
  );
  const [actionError, setActionError] = useState<string | null>(null);

  const socketRef = useRef<ChatSocket | null>(null);
  const markedRef = useRef<number | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const topRef = useRef<HTMLDivElement | null>(null);

  // 최근 대화. 재연결 때 다시 받아(`refetch`) 연결이 끊긴 사이에 쌓인 메시지를 채운다. 닫힌 방은 열람할 수 없다.
  const latest = useQuery({
    queryKey: ["chat-latest", studyId],
    queryFn: ({ signal }) => fetchChatMessages(studyId, undefined, signal),
    enabled: initialStatus === "OPEN",
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });
  const refetchLatest = latest.refetch;

  const status =
    initialStatus === "CLOSED" ||
    closedByEvent ||
    isApiError(latest.error, "CHAT_CLOSED")
      ? "CLOSED"
      : "OPEN";
  const open = status === "OPEN" && !left;
  const ready = initialStatus === "CLOSED" || !latest.isPending;
  const loadError = latest.isError && !isApiError(latest.error, "CHAT_CLOSED");
  const olderCursor =
    older.cursor === undefined
      ? (latest.data?.nextCursor ?? null)
      : older.cursor;

  const messages = useMemo(() => {
    const merged = mergeMessages(
      mergeMessages(older.items, latest.data?.items ?? []),
      live,
    );
    // 이전 대화에서 알게 된 차단한 사람의 새 메시지도 가린다 (서버는 주제 하나로 보내 사람마다 가려 줄 수 없다)
    const blocked = new Set([...blockedSenders(merged), ...blockedNow]);
    for (const id of unblockedNow) blocked.delete(id);
    return merged.map((message) => {
      const mustHide =
        message.senderId !== null && blocked.has(message.senderId);
      if (mustHide && !message.hidden) {
        return { ...message, content: null, hidden: true };
      }
      // 차단을 풀었는데 서버가 가려 둔 메시지는 다시 받아 오기 전까지 가린 채로 둔다
      return message;
    });
  }, [older.items, latest.data, live, blockedNow, unblockedNow]);

  const lastId = lastMessageId(messages);

  // 가장 최근 메시지까지 읽었다고 서버에 알린다. 실패해도 대화에는 영향이 없고 다음 메시지에서 다시 보낸다.
  useEffect(() => {
    if (lastId === null || lastId === markedRef.current || !open) return;
    markedRef.current = lastId;
    markChatRead(studyId, lastId).catch(() => {
      markedRef.current = null;
    });
  }, [lastId, open, studyId]);

  // 실시간 연결: 열린 방에서만 연다. 재연결할 때마다 최근 대화를 다시 받는다.
  useEffect(() => {
    if (initialStatus === "CLOSED") return;
    let first = true;
    const socket = connect(studyId, {
      onConnected: () => {
        setConnected(true);
        if (first) first = false;
        else void refetchLatest();
      },
      onDisconnected: () => setConnected(false),
      onTopic: (event) => {
        if (event.type === "MESSAGE") {
          setLive((current) => mergeMessages(current, [event.message]));
        } else if (event.type === "NOTICE") {
          setNotice(event.notice);
        } else {
          setClosedByEvent(true);
        }
      },
      onPersonal: (event) => {
        if (event.type === "SUBSCRIPTION_ENDED") setLeft(true);
        else setSendError(rejectionText(event.code));
      },
      onRejected: (code) => setSendError(rejectionText(code)),
    });
    socketRef.current = socket;
    return () => {
      socket.close();
      socketRef.current = null;
    };
  }, [connect, studyId, initialStatus, refetchLatest]);

  // 새 메시지가 오면 맨 아래로 (처음 불러올 때 포함). jsdom 등 scrollIntoView 가 없는 환경은 건너뛴다.
  useEffect(() => {
    bottomRef.current?.scrollIntoView?.({ block: "end" });
  }, [lastId]);

  const loadOlder = useCallback(async () => {
    if (olderCursor === null || loadingOlder) return;
    setLoadingOlder(true);
    try {
      const page = await fetchChatMessages(studyId, olderCursor);
      setOlder((current) => ({
        items: mergeMessages(current.items, page.items),
        cursor: page.nextCursor,
      }));
    } catch {
      // 다음에 다시 누르면 된다
    } finally {
      setLoadingOlder(false);
    }
  }, [olderCursor, loadingOlder, studyId]);

  // 맨 위가 보이면 이전 대화를 이어서 받는다 (무한 스크롤). 지원하지 않는 환경은 "이전 대화 보기" 버튼이 대신한다.
  useEffect(() => {
    const sentinel = topRef.current;
    if (
      !sentinel ||
      olderCursor === null ||
      typeof IntersectionObserver === "undefined"
    ) {
      return;
    }
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) void loadOlder();
    });
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [olderCursor, loadOlder]);

  const block = async (userId: number) => {
    setActionError(null);
    try {
      await blockUser(userId);
      setUnblockedNow((current) => without(current, userId));
      setBlockedNow((current) => new Set(current).add(userId));
    } catch {
      setActionError("차단하지 못했어요. 잠시 뒤 다시 시도해 주세요.");
    }
  };

  const unblock = async (userId: number) => {
    setActionError(null);
    try {
      await unblockUser(userId);
      setBlockedNow((current) => without(current, userId));
      setUnblockedNow((current) => new Set(current).add(userId));
      // 가려졌던 메시지의 내용을 다시 받는다 (이전 대화는 다시 위로 올리면 받는다)
      setOlder({ items: [], cursor: undefined });
      void refetchLatest();
    } catch {
      setActionError("차단을 풀지 못했어요. 잠시 뒤 다시 시도해 주세요.");
    }
  };

  const send = () => {
    const content = draft.trim();
    if (content.length === 0 || content.length > CHAT_MESSAGE_MAX) return;
    setSendError(null);
    socketRef.current?.send(content);
    setDraft("");
  };

  return (
    <main className={styles.page}>
      <header className={styles.head}>
        <Link href={`/studies/${studyId}`} className={styles.back}>
          ← 스터디로 돌아가기
        </Link>
        <h1 className={styles.title}>{title ?? "채팅"}</h1>
      </header>

      {status === "CLOSED" && (
        <p role="status" className={styles.banner} data-tone="closed">
          스터디가 끝나 채팅방이 닫혔어요. 대화는 일정 기간 뒤 삭제돼요.
        </p>
      )}
      {left && status === "OPEN" && (
        <p role="status" className={styles.banner} data-tone="closed">
          더 이상 이 스터디의 참여자가 아니라 채팅을 볼 수 없어요.
        </p>
      )}

      <NoticeSection
        studyId={studyId}
        notice={notice}
        isHost={isHost && open}
        onSaved={setNotice}
      />

      {unread > 0 && ready && (
        <button
          type="button"
          className={styles.unread}
          onClick={() => {
            setUnread(0);
            bottomRef.current?.scrollIntoView?.({ block: "end" });
          }}
        >
          읽지 않은 메시지 {unread}개 · 맨 아래로
        </button>
      )}

      <section className={styles.list} aria-label="대화" aria-live="polite">
        <div ref={topRef} />
        {olderCursor !== null && (
          <div className={styles.more}>
            <Button disabled={loadingOlder} onClick={() => void loadOlder()}>
              {loadingOlder ? "불러오는 중…" : "이전 대화 보기"}
            </Button>
          </div>
        )}
        {!ready && <p className={styles.note}>불러오는 중…</p>}
        {loadError && (
          <div className={styles.empty} role="alert">
            <p>대화를 불러오지 못했어요.</p>
            <Button onClick={() => void refetchLatest()}>다시 시도</Button>
          </div>
        )}
        {ready && !loadError && messages.length === 0 && status === "OPEN" && (
          <p className={styles.note}>
            아직 대화가 없어요. 첫 인사를 남겨 보세요.
          </p>
        )}
        <ul className={styles.messages}>
          {messages.map((message) => (
            <li key={message.id}>
              <MessageRow
                message={message}
                mine={message.senderId === myId}
                onReport={() => setReporting(message.id)}
                onBlock={() => void block(message.senderId!)}
                onUnblock={() => void unblock(message.senderId!)}
              />
              {reporting === message.id && (
                <ReportForm
                  targetType="MESSAGE"
                  targetId={message.id}
                  targetLabel="이 메시지"
                  onClose={() => setReporting(null)}
                />
              )}
            </li>
          ))}
        </ul>
        <div ref={bottomRef} />
      </section>
      {actionError && (
        <p className={styles.note} role="alert">
          ! {actionError}
        </p>
      )}

      {open && !connected && (
        <p className={styles.note} role="status">
          연결하는 중이에요…
        </p>
      )}
      <form
        className={styles.composer}
        onSubmit={(event) => {
          event.preventDefault();
          send();
        }}
      >
        <TextAreaField
          label="메시지"
          rows={2}
          maxLength={CHAT_MESSAGE_MAX}
          value={draft}
          onChange={(value) => {
            setDraft(value);
            setSendError(null);
          }}
          placeholder={open ? "메시지를 입력하세요" : "닫힌 채팅방이에요"}
          disabled={!open || !connected}
          error={sendError ?? undefined}
        />
        <Button
          type="submit"
          variant="primary"
          disabled={!open || !connected || draft.trim().length === 0}
        >
          보내기
        </Button>
      </form>
    </main>
  );
}

function without(set: ReadonlySet<number>, id: number): Set<number> {
  const next = new Set(set);
  next.delete(id);
  return next;
}

function MessageRow({
  message,
  mine,
  onReport,
  onBlock,
  onUnblock,
}: {
  message: ChatMessage;
  mine: boolean;
  onReport: () => void;
  onBlock: () => void;
  onUnblock: () => void;
}) {
  if (message.kind === "SYSTEM") {
    return (
      <p
        className={styles.system}
        data-emphasis={isHostChangeNotice(message) || undefined}
      >
        {message.content}
      </p>
    );
  }
  const hiddenText = message.hidden
    ? "차단한 사용자의 메시지예요."
    : message.deleted
      ? "삭제된 메시지예요."
      : null;
  return (
    <div className={styles.message} data-mine={mine || undefined}>
      <span className={styles.sender}>
        {mine ? "나" : nicknameOf(message.senderNickname)}
      </span>
      <span
        className={styles.bubble}
        data-muted={hiddenText ? true : undefined}
      >
        {hiddenText ?? message.content}
      </span>
      <span className={`${styles.time} num`}>
        {formatClockOf(message.createdAt)}
      </span>
      {!mine && message.senderId !== null && (
        <span className={styles.tools}>
          {message.hidden ? (
            <Button variant="text" onClick={onUnblock}>
              차단 해제
            </Button>
          ) : (
            !message.deleted && (
              <>
                <Button variant="text" onClick={onReport}>
                  신고
                </Button>
                <Button variant="text" onClick={onBlock}>
                  차단
                </Button>
              </>
            )
          )}
        </span>
      )}
    </div>
  );
}

function NoticeSection({
  studyId,
  notice,
  isHost,
  onSaved,
}: {
  studyId: number;
  notice: ChatNotice | null;
  isHost: boolean;
  onSaved: (notice: ChatNotice) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async () => {
    const content = text.trim();
    if (content.length === 0 || content.length > CHAT_NOTICE_MAX) {
      setError(`공지는 1자 이상 ${CHAT_NOTICE_MAX}자 이하로 적어 주세요.`);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      onSaved(await saveChatNotice(studyId, content));
      setEditing(false);
    } catch (e) {
      setError(
        isApiError(e, "CHAT_CLOSED")
          ? "닫힌 채팅방의 공지는 바꿀 수 없어요."
          : "공지를 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요.",
      );
    } finally {
      setSaving(false);
    }
  };

  if (!notice && !isHost) return null;
  return (
    <section className={styles.notice} aria-label="공지">
      {editing ? (
        <form
          className={styles.noticeForm}
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          <TextAreaField
            label="공지"
            rows={3}
            maxLength={CHAT_NOTICE_MAX}
            value={text}
            onChange={setText}
            error={error ?? undefined}
          />
          <div className={styles.noticeActions}>
            <Button onClick={() => setEditing(false)} disabled={saving}>
              취소
            </Button>
            <Button
              type="submit"
              variant="primary"
              pending={saving}
              pendingLabel="저장 중…"
            >
              공지 저장
            </Button>
          </div>
        </form>
      ) : (
        <>
          <p className={styles.noticeText}>
            {notice ? (
              <>
                <span className={styles.noticeLabel}>공지</span>{" "}
                {notice.content}
              </>
            ) : (
              "고정한 공지가 없어요."
            )}
          </p>
          {isHost && (
            <Button
              onClick={() => {
                setText(notice?.content ?? "");
                setEditing(true);
              }}
            >
              {notice ? "공지 수정" : "공지 쓰기"}
            </Button>
          )}
        </>
      )}
    </section>
  );
}

function Notice({
  text,
  href,
  cta,
  action,
}: {
  text: string;
  href?: string;
  cta?: string;
  action?: () => void;
}) {
  return (
    <main className={styles.page}>
      <div className={styles.empty} role="status">
        <p>{text}</p>
        {href && cta && (
          <Link
            href={href}
            className={buttonStyles.button}
            data-variant="primary"
          >
            {cta}
          </Link>
        )}
        {action && <Button onClick={action}>다시 시도</Button>}
      </div>
    </main>
  );
}
