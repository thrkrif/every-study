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
    hint: "일정·체크인·채팅 새 메시지를 휴대폰으로 받아요.",
  },
  {
    channel: "email",
    label: "이메일",
    hint: "푸시를 못 받을 때를 위해 같은 알림을 이메일로도 받아요. 채팅 새 메시지는 보내지 않아요.",
  },
];

/**
 * 알림 수신 설정 (PRD 4.5·5장 화면 12, API 3장, PLAN T-045). 채널(푸시·이메일)마다 켜고 끈다. 누르면 바로 바뀐 것으로
 * 보이고, 저장에 실패하면 이전 값으로 되돌린다. 알림 센터의 알림은 설정과 상관없이 남는다.
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
      <p className={styles.note}>
        알림 센터의 알림은 설정과 상관없이 남아요. 끈 채널로는 보내지 않아요.
      </p>
    </main>
  );
}
