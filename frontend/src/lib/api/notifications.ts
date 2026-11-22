import { apiFetch } from "./client";

/** 알림 종류 (API 3장, 백엔드 `NotificationType`) */
export type NotificationType =
  | "STUDY_CANCELLED"
  | "HOST_ASSIGNED"
  | "WAITING_OPENED"
  | "CHECKIN_OPEN"
  | "NO_SHOW_NOTICE";

/** 알림 센터의 한 건 (`GET /me/notifications`) */
export type NotificationItem = {
  id: number;
  type: NotificationType | (string & {});
  /** 연결된 스터디. 없으면 null */
  studyId: number | null;
  title: string;
  body: string;
  read: boolean;
  /** 보이기 시작한 시각 (ISO-8601 UTC) */
  notifiedAt: string;
};

export type NotificationPage = {
  items: NotificationItem[];
  nextCursor: string | null;
};

export const NOTIFICATION_PAGE_SIZE = 20;

export function fetchNotifications(
  cursor: string | undefined,
  signal?: AbortSignal,
): Promise<NotificationPage> {
  const params = new URLSearchParams({ size: String(NOTIFICATION_PAGE_SIZE) });
  if (cursor) params.set("cursor", cursor);
  return apiFetch<NotificationPage>(`/me/notifications?${params}`, { signal });
}

/** 한 건을 읽음으로. 이미 읽었어도 성공한다 */
export function markNotificationRead(id: number): Promise<void> {
  return apiFetch<void>(`/me/notifications/${id}/read`, { method: "POST" });
}

export function markAllNotificationsRead(): Promise<void> {
  return apiFetch<void>("/me/notifications/read-all", { method: "POST" });
}
