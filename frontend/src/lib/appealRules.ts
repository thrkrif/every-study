// 이의 신청·주최자 의견 화면의 문구와 입력 규칙 (PLAN T-037, PRD 4.9, API 5장).
import { isApiError } from "./api/errors";
import { APPEAL_REASON_MAX, type AppealStatus } from "./api/appeals";

const TEXT_NAMES = {
  reason: { empty: "이의 사유를", limit: "이의 사유는" },
  opinion: { empty: "의견을", limit: "의견은" },
} as const;

/** 사유·의견 입력 검사. 공백만이거나 500자를 넘으면 문구를, 괜찮으면 undefined */
export function textProblem(
  text: string,
  kind: keyof typeof TEXT_NAMES,
): string | undefined {
  const trimmed = text.trim();
  if (trimmed.length === 0) return `${TEXT_NAMES[kind].empty} 입력해 주세요.`;
  if (trimmed.length > APPEAL_REASON_MAX) {
    return `${TEXT_NAMES[kind].limit} ${APPEAL_REASON_MAX}자까지 쓸 수 있어요.`;
  }
  return undefined;
}

/** 이의 신청·의견 저장 실패 안내. 코드로 정하고 서버 문구는 쓰지 않는다 (API 1장) */
export function appealErrorText(error: unknown): string {
  if (isApiError(error, "APPEAL_CLOSED")) {
    return "이의를 받는 기간이 지났거나 이미 결정된 이의예요.";
  }
  if (isApiError(error, "ALREADY_APPEALED")) {
    return "이미 이의를 신청한 판정이에요.";
  }
  if (isApiError(error, "APPEAL_NOT_ALLOWED")) {
    return "이의를 신청할 수 없는 판정이에요.";
  }
  if (isApiError(error, "FORBIDDEN")) return "권한이 없어요.";
  if (isApiError(error, "NOT_FOUND")) return "대상을 찾을 수 없어요.";
  if (isApiError(error, "VALIDATION_FAILED")) {
    return "입력한 내용을 확인해 주세요.";
  }
  return "처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.";
}

export function appealStatusText(status: AppealStatus): string {
  switch (status) {
    case "OPEN":
      return "검토 중";
    case "ACCEPTED":
      return "인정됨";
    case "REJECTED":
      return "기각됨";
  }
}
