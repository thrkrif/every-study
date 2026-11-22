import { apiFetch } from "./client";

/** 내 스터디에서 내 역할 (API 3장) */
export type MyRole = "HOST" | "MEMBER";

/** 내 스터디 한 건 (`GET /me/studies`) */
export type MyStudy = {
  id: number;
  title: string;
  topicCode: string;
  districtCode: string;
  startAt: string;
  durationMin: number;
  capacity: number;
  memberCount: number;
  status: "ACTIVE" | "ENDED" | "CANCELLED";
  role: MyRole;
};

export type MyStudyPage = { items: MyStudy[]; nextCursor: string | null };

export type MyStudyState = "upcoming" | "past";

export const MY_STUDY_PAGE_SIZE = 20;

/** `role` 을 비우면 주최·참여 모두 */
export function fetchMyStudies(
  state: MyStudyState,
  role: "host" | "member" | undefined,
  cursor: string | undefined,
  signal?: AbortSignal,
): Promise<MyStudyPage> {
  const params = new URLSearchParams({
    state,
    size: String(MY_STUDY_PAGE_SIZE),
  });
  if (role) params.set("role", role);
  if (cursor) params.set("cursor", cursor);
  return apiFetch<MyStudyPage>(`/me/studies?${params}`, { signal });
}
