"use client";

import {
  useInfiniteQuery,
  useMutation,
  useQueryClient,
  type InfiniteData,
} from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useRef } from "react";
import { Button } from "@/components/Button";
import buttonStyles from "@/components/Button.module.css";
import {
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationItem,
  type NotificationPage,
} from "@/lib/api/notifications";
import { useSession } from "@/lib/session";
import { formatWhen } from "@/lib/studyFormat";
import styles from "./NotificationCenter.module.css";

const QUERY_KEY = ["notifications"] as const;
type Cache = InfiniteData<NotificationPage, string | undefined>;

type CenterProps = {
  /** 지금 시각. 테스트에서 바꿔 끼운다 */
  now?: () => Date;
};

/**
 * 알림 센터 (PRD 5장 화면 12, API 3장). 회원만 본다. 알림을 열면 읽음으로 바꾸고 연결된 스터디로 이동한다.
 * 읽음은 화면에 먼저 반영하고(낙관적) 서버에 보낸다. 실패하면 되돌린다.
 */
export function NotificationCenter({ now = () => new Date() }: CenterProps) {
  const session = useSession();
  const queryClient = useQueryClient();

  const notifications = useInfiniteQuery({
    queryKey: QUERY_KEY,
    queryFn: ({ pageParam, signal }) => fetchNotifications(pageParam, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: session.status === "member",
  });

  const sentinel = useRef<HTMLDivElement>(null);
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = notifications;
  useEffect(() => {
    const target = sentinel.current;
    if (!target || !hasNextPage || typeof IntersectionObserver === "undefined")
      return;
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting) && !isFetchingNextPage)
        void fetchNextPage();
    });
    observer.observe(target);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  /** 캐시의 알림을 고친다. 이전 값을 돌려주어 실패 시 되돌릴 수 있다 */
  const patch = (change: (item: NotificationItem) => NotificationItem) => {
    const previous = queryClient.getQueryData<Cache>(QUERY_KEY);
    queryClient.setQueryData<Cache>(QUERY_KEY, (data) =>
      data
        ? {
            ...data,
            pages: data.pages.map((page) => ({
              ...page,
              items: page.items.map(change),
            })),
          }
        : data,
    );
    return previous;
  };
  const rollback = (previous: Cache | undefined) =>
    queryClient.setQueryData(QUERY_KEY, previous);

  const read = useMutation({
    mutationFn: (id: number) => markNotificationRead(id),
    onMutate: (id) =>
      patch((item) => (item.id === id ? { ...item, read: true } : item)),
    onError: (_error, _id, previous) => rollback(previous),
  });
  const readAll = useMutation({
    mutationFn: () => markAllNotificationsRead(),
    onMutate: () => patch((item) => ({ ...item, read: true })),
    onError: (_error, _vars, previous) => rollback(previous),
  });

  if (session.status === "loading") {
    return (
      <main className={styles.page}>
        <p className={styles.note}>불러오는 중…</p>
      </main>
    );
  }
  if (session.status === "anonymous") {
    return (
      <main className={styles.page}>
        <div className={styles.empty}>
          <p>로그인하면 알림을 볼 수 있어요.</p>
          <Link
            href="/login"
            className={buttonStyles.button}
            data-variant="primary"
          >
            로그인
          </Link>
        </div>
      </main>
    );
  }

  const items = notifications.data?.pages.flatMap((page) => page.items) ?? [];
  const unread = items.filter((item) => !item.read).length;
  const current = now();

  return (
    <main className={styles.page}>
      <div className={styles.head}>
        <h1 className={styles.title}>알림</h1>
        <Button
          variant="text"
          disabled={unread === 0 || readAll.isPending}
          onClick={() => readAll.mutate()}
        >
          모두 읽음
        </Button>
      </div>

      {notifications.isPending && <p className={styles.note}>불러오는 중…</p>}

      {notifications.isError && (
        <div className={styles.empty} role="alert">
          <p>알림을 불러오지 못했어요.</p>
          <Button onClick={() => void notifications.refetch()}>
            다시 시도
          </Button>
        </div>
      )}

      {notifications.isSuccess && items.length === 0 && (
        <div className={styles.empty}>
          <p>아직 받은 알림이 없어요.</p>
        </div>
      )}

      {items.length > 0 && (
        <ul className={styles.list}>
          {items.map((item) => (
            <li key={item.id}>
              <NotificationRow
                item={item}
                now={current}
                onOpen={() => {
                  if (!item.read) read.mutate(item.id);
                }}
              />
            </li>
          ))}
        </ul>
      )}

      {hasNextPage && (
        <div ref={sentinel} className={styles.more}>
          <Button
            disabled={isFetchingNextPage}
            onClick={() => void fetchNextPage()}
          >
            {isFetchingNextPage ? "불러오는 중…" : "더 보기"}
          </Button>
        </div>
      )}
    </main>
  );
}

function NotificationRow({
  item,
  now,
  onOpen,
}: {
  item: NotificationItem;
  now: Date;
  onOpen: () => void;
}) {
  const content = (
    <>
      <span className={styles.marker} aria-hidden="true" />
      <span className={styles.text}>
        <span className={styles.rowTitle}>
          {!item.read && <span className="visually-hidden">읽지 않음 </span>}
          {item.title}
        </span>
        <span className={styles.body}>{item.body}</span>
        <span className={`${styles.when} num`}>
          {formatWhen(item.notifiedAt, now)}
        </span>
      </span>
    </>
  );
  // 스터디가 있으면 그 상세로 이동하고, 없으면 읽음 처리만 하는 버튼이다
  return item.studyId !== null ? (
    <Link
      href={`/studies/${item.studyId}`}
      className={styles.row}
      data-read={item.read}
      onClick={onOpen}
    >
      {content}
    </Link>
  ) : (
    <button
      type="button"
      className={styles.row}
      data-read={item.read}
      onClick={onOpen}
    >
      {content}
    </button>
  );
}
