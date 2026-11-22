import { apiFetch } from "./client";

/** `POST /studies` 요청 (API 4장 개설 요청). */
export type CreateStudyInput = {
  title: string;
  description: string;
  topicCode: string;
  districtCode: string;
  /** 직군 대분류. 비우면 직군 무관 */
  targetJobCodes: string[];
  place: { name: string; address: string; latitude: number; longitude: number };
  /** ISO-8601 UTC */
  startAt: string;
  durationMin: number;
  capacity: number;
  minAttendanceRate: number | null;
  allowNewcomer: boolean;
};

export function createStudy(input: CreateStudyInput): Promise<{ id: number }> {
  return apiFetch<{ id: number }>("/studies", { method: "POST", body: input });
}

/** 출석 지표 (API 3장). 신규면 `attendanceRate` 가 null 이다 */
export type Trust = {
  attendanceRate: number | null;
  windowCount: number;
  noShow: number;
  late: number;
  lateCancel: number;
  isNewcomer: boolean;
};

/** 주최 지표 (API 3장, PRD 4.13D). 5회 미만이면 건수만 보인다 */
export type HostStats = {
  hostedCount: number;
  leftWithinWindowCount: number;
  showCountOnly: boolean;
};

export type StudyHost = {
  userId: number;
  /** 탈퇴한 회원이면 null */
  nickname: string | null;
  stats: HostStats;
};

export type EntryCriteria = {
  minAttendanceRate: number | null;
  allowNewcomer: boolean;
};

/** 목록의 한 항목 (`GET /studies`) */
export type StudySummary = {
  id: number;
  title: string;
  topicCode: string;
  districtCode: string;
  startAt: string;
  durationMin: number;
  capacity: number;
  memberCount: number;
  recruiting: boolean;
  targetJobCodes: string[];
  entryCriteria: EntryCriteria;
  host: StudyHost;
};

export type StudyPage = { items: StudySummary[]; nextCursor: string | null };

export type StudySort = "latest" | "deadline";

/** 목록 조건 (API 4장 목록 조회). 비운 값은 보내지 않는다 */
export type StudyFilters = {
  regionCode?: string;
  topicCode?: string;
  jobCode?: string;
  myJob?: boolean;
  includeClosed?: boolean;
  sort: StudySort;
};

export const STUDY_PAGE_SIZE = 20;

export function fetchStudies(
  filters: StudyFilters,
  cursor: string | undefined,
  signal?: AbortSignal,
): Promise<StudyPage> {
  const params = new URLSearchParams();
  if (filters.regionCode) params.set("regionCode", filters.regionCode);
  if (filters.topicCode) params.set("topicCode", filters.topicCode);
  if (filters.jobCode) params.set("jobCode", filters.jobCode);
  if (filters.myJob) params.set("myJob", "true");
  if (filters.includeClosed) params.set("includeClosed", "true");
  params.set("sort", filters.sort);
  params.set("size", String(STUDY_PAGE_SIZE));
  if (cursor) params.set("cursor", cursor);
  return apiFetch<StudyPage>(`/studies?${params}`, { signal });
}

export type StudyMember = {
  userId: number;
  nickname: string | null;
  trust: Trust;
};

export type MyState = "NONE" | "MEMBER" | "WAITING" | "HOST";

/** 상세 (`GET /studies/{id}`). 비회원도 열람한다 */
export type StudyDetail = StudySummary & {
  description: string;
  place: { name: string; address: string; latitude: number; longitude: number };
  sessionDate: string;
  status: "ACTIVE" | "ENDED" | "CANCELLED";
  members: StudyMember[];
  myState: MyState;
  scheduleEditable: boolean;
  checkinOpensAt: string;
  checkinClosesAt: string;
  /** 주최자에게만 있다 */
  waitingCount?: number;
};

export function fetchStudy(
  id: number,
  signal?: AbortSignal,
): Promise<StudyDetail> {
  return apiFetch<StudyDetail>(`/studies/${id}`, { signal });
}

/** 참여 신청 (`POST /studies/{id}/members`). 승인 없이 바로 확정된다 */
export function joinStudy(id: number): Promise<void> {
  return apiFetch<void>(`/studies/${id}/members`, { method: "POST" });
}

/** 대기 등록. 정원이 찼을 때만 된다 */
export function registerWaiting(id: number): Promise<void> {
  return apiFetch<void>(`/studies/${id}/waiting`, { method: "POST" });
}

/** 대기 취소. 대기 중이 아니어도 성공한다 */
export function cancelWaiting(id: number): Promise<void> {
  return apiFetch<void>(`/studies/${id}/waiting/me`, { method: "DELETE" });
}

/** 주최자의 나가기 결과 (API 4장 `outcome`) */
export type HostOutcome = "TRANSFERRED" | "CANCELLED" | "LEFT_AFTER_START";

/** 사전 취소(시작 3시간 전까지)·직전 취소 (PRD 4.6) */
export type CancelType = "EARLY" | "LATE";

/** 나가기 결과. 참여자는 `cancelType`, 주최자는 `outcome` 만 있다 */
export type LeaveResult = { cancelType?: CancelType; outcome?: HostOutcome };

/** 나가기 사전 조회 결과. 참여자는 `cancelType`, 주최자는 `hostOutcome` 만 있다 */
export type LeavePreview = {
  cancelType?: CancelType;
  hostOutcome?: HostOutcome;
  /** 참여자는 지표(출석률)에 반영되는가, 주최자는 주최 감점이 붙는가 */
  penaltyApplies: boolean;
};

export function leaveStudy(id: number): Promise<LeaveResult> {
  return apiFetch<LeaveResult>(`/studies/${id}/members/me`, {
    method: "DELETE",
  });
}

export function fetchLeavePreview(id: number): Promise<LeavePreview> {
  return apiFetch<LeavePreview>(`/studies/${id}/leave-preview`);
}
