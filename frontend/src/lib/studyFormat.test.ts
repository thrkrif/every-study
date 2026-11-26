import { describe, expect, it } from "vitest";
import {
  entryCriteriaTexts,
  formatLongWhen,
  formatWhen,
  hostFact,
  phaseOf,
  regionChoices,
  trustSummaryText,
  trustText,
  verdictLabel,
  formatDay,
} from "./studyFormat";

// KST 2026-11-18(수) 10:00
const NOW = new Date("2026-11-18T01:00:00Z");

const REGIONS = [
  { code: "SEOUL", name: "서울", children: [] },
  { code: "DAEJEON_SEJONG", name: "대전/세종", children: [] },
];

describe("스터디 표시 규칙", () => {
  it("시각은 KST 로 오늘·내일을 붙이고, 그 뒤는 날짜와 요일을 쓴다", () => {
    expect(formatWhen("2026-11-18T05:00:00Z", NOW)).toBe("오늘 오후 2:00");
    // KST 11-19 00:30 은 UTC 로는 전날이다
    expect(formatWhen("2026-11-18T15:30:00Z", NOW)).toBe("내일 오전 12:30");
    expect(formatWhen("2026-11-25T01:00:00Z", NOW)).toBe(
      "11월 25일 (수) 오전 10:00",
    );
    expect(formatLongWhen("2026-11-25T05:00:00Z")).toBe(
      "11월 25일 수요일 오후 2:00",
    );
  });

  it("상태는 종료·파투 → 진행 중 → 모집 중/마감 순으로 정한다", () => {
    const future = "2026-11-20T05:00:00Z";
    expect(phaseOf({ startAt: future, recruiting: true }, NOW)).toBe(
      "RECRUITING",
    );
    expect(phaseOf({ startAt: future, recruiting: false }, NOW)).toBe("CLOSED");
    expect(
      phaseOf(
        {
          startAt: "2026-11-18T00:30:00Z",
          recruiting: false,
          status: "ACTIVE",
        },
        NOW,
      ),
    ).toBe("IN_PROGRESS");
    expect(
      phaseOf({ startAt: future, recruiting: false, status: "CANCELLED" }, NOW),
    ).toBe("ENDED");
  });

  it("주최 지표는 5회 미만이면 건수, 이상이면 비율로 쓴다", () => {
    expect(
      hostFact({
        hostedCount: 0,
        leftWithinWindowCount: 0,
        showCountOnly: true,
      }),
    ).toBe("첫 주최");
    expect(
      hostFact({
        hostedCount: 3,
        leftWithinWindowCount: 1,
        showCountOnly: true,
      }),
    ).toBe("주최 3회 중 직전 이탈 1회");
    expect(
      hostFact({
        hostedCount: 8,
        leftWithinWindowCount: 1,
        showCountOnly: false,
      }),
    ).toBe("주최 8회 중 직전 이탈 13%");
  });

  it("입장 기준과 출석 지표를 글로 쓴다", () => {
    expect(
      entryCriteriaTexts({ minAttendanceRate: null, allowNewcomer: true }),
    ).toEqual(["누구나"]);
    expect(
      entryCriteriaTexts({ minAttendanceRate: 70, allowNewcomer: false }),
    ).toEqual(["출석률 70% 이상", "신규 제외"]);
    const base = { windowCount: 0, noShow: 0, late: 0, lateCancel: 0 };
    expect(trustText({ ...base, attendanceRate: null, isNewcomer: true })).toBe(
      "신규",
    );
    expect(trustText({ ...base, attendanceRate: 92, isNewcomer: false })).toBe(
      "출석률 92%",
    );
  });

  it("지역 선택지는 시·도를 화면 순서대로 만든다", () => {
    expect(regionChoices(REGIONS)).toEqual([
      { code: "SEOUL", label: "서울" },
      { code: "DAEJEON_SEJONG", label: "대전/세종" },
    ]);
  });
});

describe("출석 지표 문구 (PRD 4.7)", () => {
  const base = {
    attendanceRate: 92,
    windowCount: 10,
    noShow: 0,
    late: 1,
    lateCancel: 0,
    isNewcomer: false,
  };

  it("표시 형태는 '출석률 · 최근 N회 중 노쇼 · 지각'이다", () => {
    expect(trustSummaryText(base)).toBe(
      "출석률 92% · 최근 10회 중 노쇼 0회 · 지각 1회",
    );
  });

  it("신규이거나 출석률이 없으면 숫자 대신 신규만 쓴다", () => {
    expect(
      trustSummaryText({ ...base, attendanceRate: null, isNewcomer: true }),
    ).toBe("신규");
    expect(trustSummaryText({ ...base, attendanceRate: null })).toBe("신규");
  });

  it("판정 이름과 날짜(KST)를 쓴다", () => {
    expect(verdictLabel("NO_SHOW_CANDIDATE")).toBe("노쇼 후보");
    expect(verdictLabel("VOIDED")).toBe("진행되지 않음");
    // UTC 11-24 16:00 은 KST 11-25 01:00
    expect(formatDay("2026-11-24T16:00:00Z")).toBe("11월 25일 (수)");
  });
});
