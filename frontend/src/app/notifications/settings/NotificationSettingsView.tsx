"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/Button";
import buttonStyles from "@/components/Button.module.css";
import {
  fetchNotificationSettings,
  saveNotificationSettings,
  type NotificationSettings,
} from "@/lib/api/notifications";
import { useSession } from "@/lib/session";
import styles from "./NotificationSettingsView.module.css";

const QUERY_KEY = ["notification-settings"] as const;

const CHANNELS: {
  channel: keyof NotificationSettings;
  label: string;
  hint: string;
}[] = [
  {
    channel: "push",
    label: "푸시 알림",
    hint: "모임 일정, 스터디 취소, 빈자리, 새 채팅을 휴대폰으로 알려 줘요.",
  },
  {
    channel: "email",
    label: "이메일",
    hint: "모임 일정, 스터디 취소, 빈자리를 메일로 알려 줘요.",
  },
];

/**
 * 알림 수신 설정 (PRD 4.5·5장 화면 12, API 3장, PLAN T-045). 채널(푸시·이메일)마다 켜고 끈다. 누르면 바로 바뀐 것으로
 * 보이고, 저장에 실패하면 이전 값으로 되돌린다. 채널을 꺼도 알림 센터에는 남는다.
 * 푸시 구독 상태 표시는 PWA·푸시 구독(T-044)이 생기면 더한다.
 */
export function NotificationSettingsView() {
  const session = useSession();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const settings = useQuery({
    queryKey: QUERY_KEY,
    queryFn: ({ signal }) => fetchNotificationSettings(signal),
    enabled: session.status === "member",
    retry: false,
  });
  const save = useMutation({
    mutationFn: (change: Partial<NotificationSettings>) =>
      saveNotificationSettings(change),
    onMutate: async (change) => {
      setError(null);
      await queryClient.cancelQueries({ queryKey: QUERY_KEY });
      const previous =
        queryClient.getQueryData<NotificationSettings>(QUERY_KEY);
      queryClient.setQueryData<NotificationSettings>(QUERY_KEY, (data) =>
        data ? { ...data, ...change } : data,
      );
      return { previous };
    },
    onError: (_error, _change, context) => {
      queryClient.setQueryData(QUERY_KEY, context?.previous);
      setError("설정을 저장하지 못했어요. 이전 설정으로 되돌렸어요.");
    },
    onSuccess: (saved) => queryClient.setQueryData(QUERY_KEY, saved),
  });

  if (session.status === "loading") {
    return (
      <main className={styles.page}>
        <p className={styles.note}>불러오는 중…</p>
      </main>
    );
  }
  if (session.status !== "member") {
    return (
      <main className={styles.page}>
        <div className={styles.empty} role="status">
          <p>로그인하면 알림 설정을 바꿀 수 있어요.</p>
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

  return (
    <main className={styles.page}>
      <Link href="/notifications" className={styles.back}>
        ← 알림
      </Link>
      <h1 className={styles.title}>알림 설정</h1>
      {settings.isPending && <p className={styles.note}>불러오는 중…</p>}
      {settings.isError && (
        <div className={styles.empty} role="alert">
          <p>설정을 불러오지 못했어요.</p>
          <Button onClick={() => void settings.refetch()}>다시 시도</Button>
        </div>
      )}
      {settings.isSuccess && (
        <ul className={styles.list}>
          {CHANNELS.map((item) => (
            <li key={item.channel} className={styles.row}>
              <span className={styles.rowText}>
                <label htmlFor={`setting-${item.channel}`}>{item.label}</label>
                <span className={styles.rowHint}>{item.hint}</span>
              </span>
              <input
                id={`setting-${item.channel}`}
                type="checkbox"
                role="switch"
                checked={settings.data[item.channel]}
                onChange={(event) =>
                  save.mutate({ [item.channel]: event.target.checked })
                }
              />
            </li>
          ))}
        </ul>
      )}
      {error && (
        <p className={styles.error} role="alert">
          ! {error}
        </p>
      )}
    </main>
  );
}
