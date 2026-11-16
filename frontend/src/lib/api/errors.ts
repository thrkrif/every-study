/**
 * 서버 오류 코드 (원본: docs/API.md 1장 오류 표). 표가 바뀌면 여기도 함께 고친다.
 * 사용자에게 보여 줄 문구는 서버의 title/detail 이 아니라 화면이 이 code 로 정한다.
 */
export const API_ERROR_CODES = [
  "VALIDATION_FAILED",
  "UNAUTHENTICATED",
  "FORBIDDEN",
  "CSRF_INVALID",
  "USER_RESTRICTED",
  "NOT_FOUND",
  "STUDY_FULL",
  "STUDY_NOT_RECRUITING",
  "ALREADY_JOINED",
  "ALREADY_WAITING",
  "STUDY_NOT_FULL",
  "ENTRY_CRITERIA_NOT_MET",
  "CAPACITY_BELOW_MEMBERS",
  "SCHEDULE_LOCKED",
  "LOCK_TIMEOUT",
  "CHECKIN_OUT_OF_RANGE",
  "CHECKIN_WINDOW_CLOSED",
  "CHECKIN_CODE_INVALID",
  "HOST_NOT_CHECKED_IN",
  "RATE_LIMITED",
  "INTERNAL_ERROR",
] as const;

/** 서버가 준 코드, 또는 서버에 닿지 못했을 때 클라이언트가 붙이는 `NETWORK_ERROR`. */
export type ApiErrorCode = (typeof API_ERROR_CODES)[number] | "NETWORK_ERROR";

export type FieldError = { field: string; message: string };

type ApiErrorInit = {
  status: number;
  /** 표에 없는 새 코드가 와도 버리지 않도록 문자열도 받는다. */
  code?: ApiErrorCode | (string & {});
  title?: string;
  detail?: string;
  traceId?: string;
  fieldErrors?: FieldError[];
  until?: string;
  cause?: unknown;
};

/** API 호출 실패. 화면은 `code`(없으면 `status`)로 분기한다. */
export class ApiError extends Error {
  /** HTTP 상태. 서버에 닿지 못했으면 0. */
  readonly status: number;
  /** HTTP 표준 오류(405, 415 등)와 JSON 이 아닌 응답에는 없다. */
  readonly code?: ApiErrorCode | (string & {});
  readonly title?: string;
  readonly detail?: string;
  /** 서버 로그를 찾는 키. 문의·오류 화면에 보여 줄 수 있다. */
  readonly traceId?: string;
  /** `VALIDATION_FAILED` 의 필드 오류. 없으면 빈 배열. */
  readonly fieldErrors: FieldError[];
  /** `USER_RESTRICTED` 의 제한 종료 시각 (ISO-8601 UTC). */
  readonly until?: string;

  constructor(init: ApiErrorInit) {
    super(init.code ?? `HTTP ${init.status}`, { cause: init.cause });
    this.name = "ApiError";
    this.status = init.status;
    this.code = init.code;
    this.title = init.title;
    this.detail = init.detail;
    this.traceId = init.traceId;
    this.fieldErrors = init.fieldErrors ?? [];
    this.until = init.until;
  }
}

/** `ApiError` 인지, (code 를 주면) 그 코드인지 확인한다. */
export function isApiError(
  error: unknown,
  code?: ApiErrorCode,
): error is ApiError {
  return (
    error instanceof ApiError && (code === undefined || error.code === code)
  );
}

function asString(value: unknown): string | undefined {
  return typeof value === "string" ? value : undefined;
}

function asFieldErrors(value: unknown): FieldError[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((item) => {
    const field = asString(item?.field);
    const message = asString(item?.message);
    return field !== undefined && message !== undefined
      ? [{ field, message }]
      : [];
  });
}

/** 실패 응답(RFC 9457 problem details)을 `ApiError` 로 바꾼다. 본문이 JSON 이 아니어도 실패하지 않는다. */
export async function toApiError(response: Response): Promise<ApiError> {
  let body: Record<string, unknown> = {};
  try {
    const parsed: unknown = await response.json();
    if (parsed !== null && typeof parsed === "object")
      body = parsed as Record<string, unknown>;
  } catch {
    // 역프록시의 HTML 오류 페이지 등. status 만으로 만든다.
  }
  return new ApiError({
    status: response.status,
    code: asString(body.code),
    title: asString(body.title),
    detail: asString(body.detail),
    traceId: asString(body.traceId),
    fieldErrors: asFieldErrors(body.errors),
    until: asString(body.until),
  });
}
