import { apiFetch } from "./client";
import type { AttendanceVerdict } from "./profile";
import type { AppealStatus } from "./appeals";

/** 운영자 목록의 이의 한 건 (`GET /admin/appeals`, API 7장). 탈퇴한 신청자는 닉네임이 null */
export type AdminAppeal = {
  id: number;
  studyId: number;
  studyTitle: string;
  userId: number;
  applicantNickname: string | null;
  verdictType: AttendanceVerdict;
  reason: string;
  hostOpinion: string | null;
  status: AppealStatus;
  dueAt: string;
  createdAt: string;
  decidedAt: string | null;
};

export type AdminAppealPage = {
  items: AdminAppeal[];
  nextCursor: string | null;
};

export type AppealFilter = "open" | "accepted" | "rejected" | "all";

/** 운영자 처리 사유(`note`)의 길이 상한. 이의 결정은 신청자 알림에도 실린다 */
export const DECISION_NOTE_MAX = 150;
export const CORRECTION_NOTE_MAX = 200;

export function fetchAdminAppeals(
  status: AppealFilter,
  cursor: string | undefined,
  signal?: AbortSignal,
): Promise<AdminAppealPage> {
  const params = new URLSearchParams({ status, size: "20" });
  if (cursor) params.set("cursor", cursor);
  return apiFetch<AdminAppealPage>(`/admin/appeals?${params}`, { signal });
}

export function decideAppeal(
  appealId: number,
  accept: boolean,
  note: string,
): Promise<AdminAppeal> {
  return apiFetch<AdminAppeal>(`/admin/appeals/${appealId}/decision`, {
    method: "POST",
    body: { accept, note },
  });
}

/** 개별 출석 정정 결과: 출석으로 집계 / 판정 무효화 (API 7장 X-7) */
export type CorrectionResult = "ATTENDED" | "CLEARED";

export type Corrected = {
  eventId: number;
  studyId: number;
  userId: number;
  verdict: "ATTENDED" | "NO_SHOW_CLEARED";
};

export function correctAttendance(
  eventId: number,
  result: CorrectionResult,
  note: string,
): Promise<Corrected> {
  return apiFetch<Corrected>(
    `/admin/attendance-events/${eventId}/corrections`,
    {
      method: "POST",
      body: { result, note },
    },
  );
}
