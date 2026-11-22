// 상세 화면의 참여·대기·나가기 버튼을 정하는 규칙 (PLAN T-024, API 4장, DESIGN 6장 상세).
// 화면(React)과 분리해 두어 상태 조합을 표로 검증한다.
import { ApiError } from "./api/errors";
import type { CodeOption } from "./api/meta";
import type {
  CancelType,
  HostOutcome,
  LeavePreview,
  StudyDetail,
} from "./api/studies";

/** 지금 보여 줄 행동. 화면은 이 값 하나로 버튼과 안내를 그린다 */
export type StudyAction =
  | { kind: "none"; text: string }
  | { kind: "login" }
  | { kind: "onboarding" }
  | { kind: "join" }
  | { kind: "join-blocked"; text: string }
  | { kind: "wait"; note: string }
  | { kind: "cancel-wait"; note: string }
  | { kind: "leave"; text: string; host: boolean };

export type ActionContext = {
  study: StudyDetail;
  now: Date;
  /** 로그인 상태. `loading` 이면 버튼을 정하지 않는다 */
  session: "loading" | "anonymous" | "member" | "error";
  /** 회원이 온보딩을 마쳤는가 */
  onboarded: boolean;
  /** 내 직군의 대분류 코드. 온보딩 전이면 null */
  jobCategory: string | null;
  /** 직군 코드 → 이름 (안내 문구용) */
  jobNames: ReadonlyMap<string, string>;
};

/** 직군 코드(대분류 또는 세부 직무)에서 대분류 코드를 찾는다. 목록에 없으면 null */
export function jobCategoryOf(
  jobs: readonly CodeOption[],
  jobCode: string | null,
): string | null {
  if (!jobCode) return null;
  for (const category of jobs) {
    if (category.code === jobCode) return category.code;
    if (category.children.some((child) => child.code === jobCode)) {
      return category.code;
    }
  }
  return null;
}

/**
 * 버튼 결정. `myState` 와 시각으로 정한다. 모집 중 여부는 서버가 준 `recruiting` 을 따른다
 * (체크인 오픈 전 + 정원 여유). 대기는 체크인 오픈 전의 정원이 찬 스터디에서만 된다 (PRD 4.14).
 */
export function actionOf(context: ActionContext): StudyAction {
  const { study, now } = context;
  const started = new Date(study.startAt).getTime() <= now.getTime();
  const applicationOpen =
    now.getTime() < new Date(study.checkinOpensAt).getTime();

  if (study.status !== "ACTIVE") {
    return {
      kind: "none",
      text:
        study.status === "CANCELLED"
          ? "열리지 않은 스터디예요."
          : "끝난 스터디예요.",
    };
  }
  if (context.session === "loading") return { kind: "none", text: "" };
  if (context.session === "anonymous") return { kind: "login" };

  if (study.myState === "HOST") {
    // 시작 뒤에도 주최자는 나갈 수 있다 (승계 없이, PLAN X-13)
    return {
      kind: "leave",
      host: true,
      text: started
        ? "지금 진행 중이에요. 나가도 모임은 계속돼요."
        : "내가 주최하는 스터디예요.",
    };
  }
  if (study.myState === "MEMBER") {
    return started
      ? { kind: "none", text: "참여 중인 스터디예요." }
      : { kind: "leave", host: false, text: "참여 중인 스터디예요." };
  }
  if (started) {
    return { kind: "none", text: "지금 진행 중인 스터디예요." };
  }
  if (study.myState === "WAITING") {
    return applicationOpen
      ? {
          kind: "cancel-wait",
          note: "대기 중이에요. 자리가 나면 알려 드려요.",
        }
      : { kind: "none", text: "신청이 마감됐어요." };
  }

  // myState NONE
  const full = study.memberCount >= study.capacity;
  if (!applicationOpen) return { kind: "none", text: "신청이 마감됐어요." };
  if (full) {
    return { kind: "wait", note: "정원이 찼어요. 자리가 나면 알려 드려요." };
  }
  if (context.session === "error") {
    return { kind: "none", text: "로그인 상태를 확인하지 못했어요." };
  }
  if (!context.onboarded) return { kind: "onboarding" };

  const targets = study.targetJobCodes;
  if (
    targets.length > 0 &&
    (context.jobCategory === null || !targets.includes(context.jobCategory))
  ) {
    const names = targets.map((code) => context.jobNames.get(code) ?? code);
    return {
      kind: "join-blocked",
      text: `희망 직군(${names.join(", ")})이 아니라 참여할 수 없어요.`,
    };
  }
  return { kind: "join" };
}

/** 신청·대기·나가기 실패를 한 문장으로. 서버 문구가 아니라 코드로 정한다 (API 1장) */
export function actionErrorText(error: unknown): string {
  if (!(error instanceof ApiError)) return "잠시 뒤 다시 시도해 주세요.";
  switch (error.code) {
    case "STUDY_FULL":
      return "방금 정원이 찼어요. 대기하기로 자리가 나면 알려 드려요.";
    case "STUDY_NOT_FULL":
      return "자리가 생겼어요. 참여하기를 눌러 주세요.";
    case "STUDY_NOT_RECRUITING":
      return "신청이 마감됐어요.";
    case "ALREADY_JOINED":
      return "이미 참여 중이에요.";
    case "ALREADY_WAITING":
      return "이미 대기 중이에요.";
    case "ENTRY_CRITERIA_NOT_MET":
      return "입장 기준(출석률·신규 제한)을 충족하지 못해 참여할 수 없어요.";
    case "JOB_NOT_ALLOWED":
      return "희망 직군이 아니라 참여할 수 없어요.";
    case "USER_RESTRICTED":
      return error.until
        ? `이용 제한 중이라 ${formatUntil(error.until)}까지 신청할 수 없어요.`
        : "이용 제한 중이라 신청할 수 없어요.";
    case "ONBOARDING_REQUIRED":
      return "닉네임·직군·지역을 먼저 입력해 주세요.";
    case "SESSION_STARTED":
      return "이미 시작했거나 끝난 스터디라 나갈 수 없어요.";
    case "STUDY_CLOSED":
      return "끝났거나 열리지 않은 스터디예요.";
    case "LOCK_TIMEOUT":
      return "요청이 몰렸어요. 한 번 더 눌러 주세요.";
    case "NETWORK_ERROR":
      return "연결이 불안정해요. 잠시 뒤 다시 시도해 주세요.";
    default:
      return "잠시 뒤 다시 시도해 주세요.";
  }
}

/** 오류 뒤에 상세를 다시 읽어야 하는가 (화면의 상태가 서버와 어긋났을 때) */
export function shouldRefetchAfter(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    [
      "STUDY_FULL",
      "STUDY_NOT_FULL",
      "STUDY_NOT_RECRUITING",
      "ALREADY_JOINED",
      "ALREADY_WAITING",
      "SESSION_STARTED",
      "STUDY_CLOSED",
    ].includes(error.code ?? "")
  );
}

/** 이용 제한 종료 시각을 "11월 25일 오후 2:00" 로 */
function formatUntil(until: string): string {
  const kst = new Date(new Date(until).getTime() + 9 * 60 * 60 * 1000);
  const hour = kst.getUTCHours();
  const minute = String(kst.getUTCMinutes()).padStart(2, "0");
  const period = hour < 12 ? "오전" : "오후";
  const hour12 = hour % 12 === 0 ? 12 : hour % 12;
  return `${kst.getUTCMonth() + 1}월 ${kst.getUTCDate()}일 ${period} ${hour12}:${minute}`;
}

/** 나가기 확인 창의 제목과 설명 (사전 조회 결과로 정한다) */
export function leaveConfirmText(preview: LeavePreview): {
  title: string;
  lines: string[];
  confirm: string;
} {
  if (preview.hostOutcome) {
    return hostLeaveText(preview.hostOutcome, preview.penaltyApplies);
  }
  return participantLeaveText(preview.cancelType, preview.penaltyApplies);
}

function participantLeaveText(
  cancelType: CancelType | undefined,
  penaltyApplies: boolean,
) {
  const lines =
    cancelType === "LATE" || penaltyApplies
      ? [
          "시작 3시간 안이라 직전 취소로 기록돼요.",
          "출석률에 반영되니 꼭 필요할 때만 취소해 주세요.",
        ]
      : ["시작 3시간 전까지의 사전 취소라 출석률에 영향이 없어요."];
  return { title: "참여를 취소할까요?", lines, confirm: "참여 취소" };
}

function hostLeaveText(outcome: HostOutcome, penaltyApplies: boolean) {
  if (outcome === "CANCELLED") {
    return {
      title: "스터디를 취소할까요?",
      lines: [
        "남은 참여자가 없어서 스터디가 취소돼요.",
        "대기 중인 분들에게는 취소 소식이 가요.",
      ],
      confirm: "스터디 취소",
    };
  }
  if (outcome === "LEFT_AFTER_START") {
    return {
      title: "주최에서 나갈까요?",
      lines: [
        "이미 시작해서 새 주최자를 정하지 않아요.",
        "모임은 그대로 진행되고, 체크인 전이라면 미도착 규칙이 적용돼요.",
      ],
      confirm: "나가기",
    };
  }
  const lines = [
    "남은 참여자 중 한 명이 무작위로 새 주최자가 돼요. 일정과 장소는 그대로예요.",
  ];
  if (penaltyApplies) {
    lines.push("시작 3시간 안이라 주최자 지표에 -1점이 기록돼요.");
  }
  return {
    title: "주최를 넘기고 나갈까요?",
    lines,
    confirm: "주최 넘기고 나가기",
  };
}

/** 나가기를 마친 뒤 알릴 한 줄 */
export function leftNotice(result: {
  cancelType?: CancelType;
  outcome?: HostOutcome;
}): string {
  switch (result.outcome) {
    case "TRANSFERRED":
      return "나갔어요. 새 주최자가 정해졌어요.";
    case "CANCELLED":
      return "스터디를 취소했어요.";
    case "LEFT_AFTER_START":
      return "나갔어요. 모임은 그대로 진행돼요.";
    default:
      return "참여를 취소했어요.";
  }
}

/**
 * 스터디 관리(수정·정원 변경) 실패를 한 문장으로 (PLAN T-026). `memberCount` 는 정원 감소 안내에 쓴다.
 * 필드 오류(`VALIDATION_FAILED`)는 서버가 준 칸별 문구를 이어 붙인다.
 */
export function manageErrorText(error: unknown, memberCount: number): string {
  if (!(error instanceof ApiError)) return "잠시 뒤 다시 시도해 주세요.";
  switch (error.code) {
    case "SCHEDULE_LOCKED":
      return "참여자가 있거나 이미 시작해서 일정·장소는 바꿀 수 없어요. 새 스터디를 만들어 주세요.";
    case "CAPACITY_BELOW_MEMBERS":
      return `지금 참여자가 ${memberCount}명이라 그보다 적게 줄일 수 없어요.`;
    case "STUDY_CLOSED":
      return "끝났거나 열리지 않은 스터디는 바꿀 수 없어요.";
    case "FORBIDDEN":
      return "주최자만 바꿀 수 있어요.";
    case "VALIDATION_FAILED":
      return error.fieldErrors.length > 0
        ? error.fieldErrors.map((field) => field.message).join(" ")
        : "입력한 값을 확인해 주세요.";
    case "LOCK_TIMEOUT":
      return "요청이 몰렸어요. 한 번 더 눌러 주세요.";
    case "NETWORK_ERROR":
      return "연결이 불안정해요. 잠시 뒤 다시 시도해 주세요.";
    default:
      return "잠시 뒤 다시 시도해 주세요.";
  }
}
