import { apiFetch } from "./client";
import type { HostStats, Trust } from "./studies";

/** 공개 프로필 (`GET /users/{id}`, API 3장). 탈퇴한 회원은 세 값이 null 이다 */
export type PublicProfile = {
  id: number;
  nickname: string | null;
  jobCode: string | null;
  districtCode: string | null;
  joinedAt: string;
  attendance: Trust;
  hostStats: HostStats;
};

export function fetchPublicProfile(
  id: number,
  signal?: AbortSignal,
): Promise<PublicProfile> {
  return apiFetch<PublicProfile>(`/users/${id}`, { signal });
}

/** 출석 이벤트 종류 (DATA-MODEL 5장). 현재 판정으로 내려온다 */
export type AttendanceVerdict =
  | "ATTENDED"
  | "LATE"
  | "NO_SHOW_CANDIDATE"
  | "NO_SHOW"
  | "NO_SHOW_CLEARED"
  | "EARLY_CANCEL"
  | "LATE_CANCEL"
  | "VOIDED";

/** 지난 출석 기록 한 건 (`GET /me/attendance`, API 3장) */
export type AttendanceRecord = {
  eventId: number;
  studyId: number;
  studyTitle: string;
  type: AttendanceVerdict;
  occurredAt: string;
  appealable: boolean;
  appealDueAt: string | null;
  appealId: number | null;
};

export type AttendancePage = {
  items: AttendanceRecord[];
  nextCursor: string | null;
};

export const ATTENDANCE_PAGE_SIZE = 20;

export function fetchMyAttendance(
  cursor: string | undefined,
  signal?: AbortSignal,
): Promise<AttendancePage> {
  const params = new URLSearchParams({ size: String(ATTENDANCE_PAGE_SIZE) });
  if (cursor) params.set("cursor", cursor);
  return apiFetch<AttendancePage>(`/me/attendance?${params}`, { signal });
}
