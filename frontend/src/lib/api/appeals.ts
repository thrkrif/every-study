import { apiFetch } from "./client";
import type { AttendanceVerdict } from "./profile";

/** 이의 처리 상태 (DATA-MODEL 5장) */
export type AppealStatus = "OPEN" | "ACCEPTED" | "REJECTED";

/** 내 이의 한 건 (`POST /attendance-events/{id}/appeals`, `GET /me/appeals`) */
export type Appeal = {
  id: number;
  attendanceEventId: number;
  studyId: number;
  studyTitle: string;
  verdictType: AttendanceVerdict;
  reason: string;
  hostOpinion: string | null;
  status: AppealStatus;
  dueAt: string;
  createdAt: string;
  decidedAt: string | null;
};

export type AppealPage = { items: Appeal[]; nextCursor: string | null };

/** 주최자가 보는 이의 한 건 (`GET /studies/{id}/appeals`). 탈퇴한 신청자는 닉네임이 null */
export type HostAppeal = {
  id: number;
  applicantNickname: string | null;
  verdictType: AttendanceVerdict;
  reason: string;
  hostOpinion: string | null;
  status: AppealStatus;
  dueAt: string;
  createdAt: string;
};

export const APPEAL_REASON_MAX = 500;
export const APPEAL_PAGE_SIZE = 20;

export function fileAppeal(eventId: number, reason: string): Promise<Appeal> {
  return apiFetch<Appeal>(`/attendance-events/${eventId}/appeals`, {
    method: "POST",
    body: { reason },
  });
}

export function fetchMyAppeals(
  cursor: string | undefined,
  signal?: AbortSignal,
): Promise<AppealPage> {
  const params = new URLSearchParams({ size: String(APPEAL_PAGE_SIZE) });
  if (cursor) params.set("cursor", cursor);
  return apiFetch<AppealPage>(`/me/appeals?${params}`, { signal });
}

export function fetchStudyAppeals(
  studyId: number,
  signal?: AbortSignal,
): Promise<HostAppeal[]> {
  return apiFetch<HostAppeal[]>(`/studies/${studyId}/appeals`, { signal });
}

export function saveHostOpinion(
  appealId: number,
  opinion: string,
): Promise<Appeal> {
  return apiFetch<Appeal>(`/appeals/${appealId}/host-opinion`, {
    method: "PUT",
    body: { opinion },
  });
}
