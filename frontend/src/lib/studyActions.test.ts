import { describe, expect, it } from "vitest";
import { ApiError } from "./api/errors";
import type { CodeOption } from "./api/meta";
import type { MyState, StudyDetail } from "./api/studies";
import {
  actionErrorText,
  actionOf,
  jobCategoryOf,
  leaveConfirmText,
  leftNotice,
  shouldRefetchAfter,
  type ActionContext,
} from "./studyActions";

const NOW = new Date("2026-11-18T01:00:00Z");

const BASE = {
  id: 1,
  status: "ACTIVE",
  startAt: "2026-11-20T05:00:00Z",
  checkinOpensAt: "2026-11-20T04:50:00Z",
  capacity: 4,
  memberCount: 2,
  targetJobCodes: [],
  myState: "NONE",
} as unknown as StudyDetail;

function context(
  study: Partial<StudyDetail> = {},
  rest: Partial<ActionContext> = {},
): ActionContext {
  return {
    study: { ...BASE, ...study },
    now: NOW,
    session: "member",
    onboarded: true,
    jobCategory: "IT",
    jobNames: new Map([
      ["IT", "IT·개발"],
      ["DESIGN", "디자인"],
    ]),
    ...rest,
  };
}

const jobs: CodeOption[] = [
  {
    code: "IT",
    name: "IT·개발",
    children: [{ code: "IT_BACKEND", name: "백엔드", children: [] }],
  },
  { code: "DESIGN", name: "디자인", children: [] },
];

describe("actionOf", () => {
  it("로그인 상태에 따라 비회원은 로그인 안내, 확인 중이면 아무것도 정하지 않는다", () => {
    expect(actionOf(context({}, { session: "anonymous" }))).toEqual({
      kind: "login",
    });
    expect(actionOf(context({}, { session: "loading" }))).toEqual({
      kind: "none",
      text: "",
    });
  });

  it("종료·파투된 스터디는 상태 설명만 보여 준다", () => {
    expect(actionOf(context({ status: "ENDED" }))).toEqual({
      kind: "none",
      text: "끝난 스터디예요.",
    });
    expect(actionOf(context({ status: "CANCELLED" }))).toEqual({
      kind: "none",
      text: "열리지 않은 스터디예요.",
    });
  });

  it.each<[MyState, string]>([
    ["NONE", "join"],
    ["MEMBER", "leave"],
    ["HOST", "leave"],
  ])("모집 중에 myState %s 이면 %s 버튼이다", (myState, kind) => {
    expect(actionOf(context({ myState })).kind).toBe(kind);
  });

  it("정원이 찼으면 대기하기, 대기 중이면 대기 취소다", () => {
    expect(actionOf(context({ memberCount: 4 })).kind).toBe("wait");
    expect(actionOf(context({ memberCount: 4, myState: "WAITING" })).kind).toBe(
      "cancel-wait",
    );
  });

  it("체크인이 열린 뒤에는 신청도 대기도 마감이다", () => {
    const late = { now: new Date("2026-11-20T04:50:00Z") };
    expect(actionOf(context({}, late))).toEqual({
      kind: "none",
      text: "신청이 마감됐어요.",
    });
    expect(actionOf(context({ memberCount: 4 }, late)).kind).toBe("none");
    expect(actionOf(context({ myState: "WAITING" }, late)).kind).toBe("none");
  });

  it("시작한 뒤에는 참여자는 나갈 수 없고 주최자만 나갈 수 있다", () => {
    const started = { now: new Date("2026-11-20T05:10:00Z") };
    expect(actionOf(context({ myState: "MEMBER" }, started))).toEqual({
      kind: "none",
      text: "참여 중인 스터디예요.",
    });
    expect(actionOf(context({ myState: "HOST" }, started))).toMatchObject({
      kind: "leave",
      host: true,
    });
    expect(actionOf(context({}, started))).toEqual({
      kind: "none",
      text: "지금 진행 중인 스터디예요.",
    });
  });

  it("온보딩 전이면 프로필 입력을 안내한다", () => {
    expect(actionOf(context({}, { onboarded: false })).kind).toBe("onboarding");
  });

  it("희망 직군이 아니면 신청 전에 이유를 알려 주고, 맞으면 신청할 수 있다", () => {
    const study = { targetJobCodes: ["DESIGN"] };
    expect(actionOf(context(study))).toEqual({
      kind: "join-blocked",
      text: "희망 직군(디자인)이 아니라 참여할 수 없어요.",
    });
    expect(actionOf(context(study, { jobCategory: "DESIGN" })).kind).toBe(
      "join",
    );
    expect(actionOf(context(study, { jobCategory: null })).kind).toBe(
      "join-blocked",
    );
  });
});

describe("jobCategoryOf", () => {
  it("세부 직무 코드에서 대분류를 찾고, 목록에 없으면 null 이다", () => {
    expect(jobCategoryOf(jobs, "IT_BACKEND")).toBe("IT");
    expect(jobCategoryOf(jobs, "DESIGN")).toBe("DESIGN");
    expect(jobCategoryOf(jobs, "NOPE")).toBeNull();
    expect(jobCategoryOf(jobs, null)).toBeNull();
  });
});

describe("actionErrorText", () => {
  const error = (code: string, until?: string) =>
    new ApiError({ status: 409, code, until });

  it("오류 코드별 안내를 낸다", () => {
    expect(actionErrorText(error("STUDY_FULL"))).toContain("대기하기");
    expect(actionErrorText(error("ENTRY_CRITERIA_NOT_MET"))).toContain(
      "입장 기준",
    );
    expect(actionErrorText(error("LOCK_TIMEOUT"))).toContain("한 번 더");
    expect(actionErrorText(error("JOB_NOT_ALLOWED"))).toContain("희망 직군");
    expect(
      actionErrorText(new ApiError({ status: 0, code: "NETWORK_ERROR" })),
    ).toContain("연결");
    expect(actionErrorText(new Error("x"))).toContain("다시 시도");
  });

  it("이용 제한은 끝나는 시각(KST)을 알려 준다", () => {
    expect(
      actionErrorText(error("USER_RESTRICTED", "2026-11-25T05:00:00Z")),
    ).toBe("이용 제한 중이라 11월 25일 오후 2:00까지 신청할 수 없어요.");
    expect(actionErrorText(error("USER_RESTRICTED"))).toBe(
      "이용 제한 중이라 신청할 수 없어요.",
    );
  });
});

describe("shouldRefetchAfter", () => {
  it("화면 상태가 서버와 어긋난 오류만 다시 읽는다", () => {
    expect(
      shouldRefetchAfter(new ApiError({ status: 409, code: "STUDY_FULL" })),
    ).toBe(true);
    expect(
      shouldRefetchAfter(new ApiError({ status: 409, code: "LOCK_TIMEOUT" })),
    ).toBe(false);
    expect(shouldRefetchAfter(new Error("x"))).toBe(false);
  });
});

describe("leaveConfirmText", () => {
  it("참여자: 사전 취소는 영향 없음, 직전 취소는 기록 안내", () => {
    expect(
      leaveConfirmText({
        cancelType: "EARLY",
        penaltyApplies: false,
      }).lines.join(" "),
    ).toContain("영향이 없어요");
    expect(
      leaveConfirmText({ cancelType: "LATE", penaltyApplies: true }).lines.join(
        " ",
      ),
    ).toContain("직전 취소");
  });

  it("주최자: 승계·감점·파투·시작 후를 각각 안내한다", () => {
    const transferred = leaveConfirmText({
      hostOutcome: "TRANSFERRED",
      penaltyApplies: true,
    });
    expect(transferred.lines.join(" ")).toContain("무작위");
    expect(transferred.lines.join(" ")).toContain("-1점");
    expect(
      leaveConfirmText({
        hostOutcome: "TRANSFERRED",
        penaltyApplies: false,
      }).lines.join(" "),
    ).not.toContain("-1점");
    expect(
      leaveConfirmText({ hostOutcome: "CANCELLED", penaltyApplies: false })
        .confirm,
    ).toBe("스터디 취소");
    expect(
      leaveConfirmText({
        hostOutcome: "LEFT_AFTER_START",
        penaltyApplies: false,
      }).lines.join(" "),
    ).toContain("새 주최자를 정하지 않아요");
  });
});

describe("leftNotice", () => {
  it("결과별 한 줄을 돌려준다", () => {
    expect(leftNotice({ cancelType: "EARLY" })).toBe("참여를 취소했어요.");
    expect(leftNotice({ outcome: "CANCELLED" })).toBe("스터디를 취소했어요.");
    expect(leftNotice({ outcome: "TRANSFERRED" })).toContain("새 주최자");
  });
});
