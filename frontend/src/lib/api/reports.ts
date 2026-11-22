import { apiFetch } from "./client";

/** 신고 대상 종류 (API 7장) */
export type ReportTargetType = "STUDY" | "USER" | "MESSAGE";

/** 신고 사유 코드 (PLAN D-4, DATA-MODEL 9장). `OTHER` 는 상세 내용이 필수다. */
export type ReportReason =
  | "SPAM_AD"
  | "ABUSE"
  | "INAPPROPRIATE_RECRUIT"
  | "HARASSMENT"
  | "FRAUD"
  | "OTHER";

export const REPORT_REASONS: readonly { value: ReportReason; label: string }[] =
  [
    { value: "SPAM_AD", label: "광고·홍보" },
    { value: "ABUSE", label: "욕설·비방" },
    { value: "INAPPROPRIATE_RECRUIT", label: "부적절한 모집" },
    { value: "HARASSMENT", label: "괴롭힘·불쾌감" },
    { value: "FRAUD", label: "사기·위장" },
    { value: "OTHER", label: "기타" },
  ];

export const REPORT_DETAIL_MAX = 500;

export function fileReport(
  targetType: ReportTargetType,
  targetId: number,
  reason: ReportReason,
  detail: string,
): Promise<{ id: number }> {
  return apiFetch<{ id: number }>("/reports", {
    method: "POST",
    body: { targetType, targetId, reason, detail: detail || undefined },
  });
}

/** 사용자 차단 (멱등). 차단한 사람의 채팅 메시지는 내용 없이 보인다 (PRD 4.12) */
export function blockUser(userId: number): Promise<void> {
  return apiFetch<void>(`/blocks/${userId}`, { method: "PUT" });
}

export function unblockUser(userId: number): Promise<void> {
  return apiFetch<void>(`/blocks/${userId}`, { method: "DELETE" });
}
