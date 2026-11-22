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

/** 운영자 신고 큐의 신고 한 건 (`GET /admin/reports`). 대상 요약은 대상이 없으면 null */
export type AdminReport = {
  id: number;
  reporterId: number;
  reporterNickname: string | null;
  targetType: "STUDY" | "USER" | "MESSAGE";
  targetId: number;
  targetSummary: string | null;
  reason: string;
  detail: string | null;
  status: "OPEN" | "DISMISSED" | "RESOLVED";
  handledBy: number | null;
  handleNote: string | null;
  createdAt: string;
  handledAt: string | null;
};

export type AdminReportPage = {
  items: AdminReport[];
  nextCursor: string | null;
};

/** 신고 처리·강제 종료·메시지 삭제·정지 부여·해제의 사유 길이 상한 (API 7장) */
export const REPORT_NOTE_MAX = 300;
export const ACTION_NOTE_MAX = 200;

export function fetchAdminReports(
  cursor: string | undefined,
  signal?: AbortSignal,
): Promise<AdminReportPage> {
  const params = new URLSearchParams({ status: "open", size: "20" });
  if (cursor) params.set("cursor", cursor);
  return apiFetch<AdminReportPage>(`/admin/reports?${params}`, { signal });
}

export function handleReport(
  reportId: number,
  action: "dismiss" | "resolve",
  note: string,
): Promise<AdminReport> {
  return apiFetch<AdminReport>(`/admin/reports/${reportId}/${action}`, {
    method: "POST",
    body: { note },
  });
}

export function forceEndStudy(studyId: number, note: string): Promise<void> {
  return apiFetch<void>(`/admin/studies/${studyId}/force-end`, {
    method: "POST",
    body: { note },
  });
}

export function deleteChatMessage(
  messageId: number,
  note: string,
): Promise<void> {
  return apiFetch<void>(`/admin/chat-messages/${messageId}`, {
    method: "DELETE",
    body: { note },
  });
}

/** 제재 한 건 (`GET /admin/sanctions`). `reviewRequired` 는 주최자 합계가 운영자 검토 단계인 개설 제한 */
export type AdminSanction = {
  id: number;
  userId: number;
  kind: "JOIN_RESTRICT" | "SUSPEND" | "HOST_RESTRICT";
  source: "AUTO" | "OPERATOR";
  reason: string;
  startsAt: string;
  endsAt: string;
  revokedAt: string | null;
  createdBy: number | null;
  reviewRequired: boolean;
  active: boolean;
};

export type AdminSanctionPage = {
  items: AdminSanction[];
  nextCursor: string | null;
};

export function fetchSanctions(
  filter: { activeOnly: boolean; reviewRequired: boolean },
  cursor: string | undefined,
  signal?: AbortSignal,
): Promise<AdminSanctionPage> {
  const params = new URLSearchParams({
    activeOnly: String(filter.activeOnly),
    reviewRequired: String(filter.reviewRequired),
    size: "20",
  });
  if (cursor) params.set("cursor", cursor);
  return apiFetch<AdminSanctionPage>(`/admin/sanctions?${params}`, { signal });
}

export function suspendUser(
  userId: number,
  days: number,
  note: string,
): Promise<AdminSanction> {
  return apiFetch<AdminSanction>(`/admin/users/${userId}/suspensions`, {
    method: "POST",
    body: { days, note },
  });
}

export function revokeSanction(
  sanctionId: number,
  note: string,
): Promise<void> {
  return apiFetch<void>(`/admin/sanctions/${sanctionId}`, {
    method: "DELETE",
    body: { note },
  });
}
