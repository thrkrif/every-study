import { describe, expect, it } from "vitest";
import {
  cityOf,
  entryCriteriaTexts,
  formatLongWhen,
  formatWhen,
  hostFact,
  phaseOf,
  regionChoices,
  trustText,
} from "./studyFormat";

// KST 2026-11-18(수) 10:00
const NOW = new Date("2026-11-18T01:00:00Z");

const REGIONS = [
  {
    code: "GYEONGGI",
    name: "경기",
    children: [
      {
        code: "SUWON",
        name: "수원시",
        children: [
          { code: "SUWON_JANGAN", name: "장안구", children: [] },
          { code: "SUWON_PALDAL", name: "팔달구", children: [] },
        ],
      },
    ],
  },
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

  it("지역 선택지는 시와 구를 화면 순서대로 만들고, 내 구의 시를 찾는다", () => {
    expect(regionChoices(REGIONS)).toEqual([
      { code: "SUWON", label: "수원시" },
      { code: "SUWON_JANGAN", label: "수원시 장안구" },
      { code: "SUWON_PALDAL", label: "수원시 팔달구" },
    ]);
    expect(cityOf("SUWON_PALDAL", REGIONS)).toBe("SUWON");
    expect(cityOf("UNKNOWN", REGIONS)).toBeUndefined();
  });
});
