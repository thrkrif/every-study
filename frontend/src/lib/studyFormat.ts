// 스터디 목록·상세의 표시 규칙. 시각은 서버가 UTC 로 주고 화면은 KST 로 쓴다 (API 1장).
import { formatClock } from "@/app/studies/new/rules";
import type { StudyPhase } from "@/components/StatusChip";
import type { CodeOption } from "./api/meta";
import type {
  EntryCriteria,
  HostStats,
  StudyDetail,
  StudySummary,
  Trust,
} from "./api/studies";

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"] as const;

type KstParts = {
  /** KST 자정 기준 일 번호. 날짜 차이를 셀 때 쓴다 */
  dayNumber: number;
  month: number;
  day: number;
  weekday: (typeof WEEKDAYS)[number];
  minuteOfDay: number;
};

function kst(at: Date): KstParts {
  const shifted = new Date(at.getTime() + KST_OFFSET_MS);
  return {
    dayNumber: Math.floor(shifted.getTime() / DAY_MS),
    month: shifted.getUTCMonth() + 1,
    day: shifted.getUTCDate(),
    weekday: WEEKDAYS[shifted.getUTCDay()],
    minuteOfDay: shifted.getUTCHours() * 60 + shifted.getUTCMinutes(),
  };
}

/** 목록 카드의 시각: "오늘 오후 2:00", "내일 오전 10:00", "11월 25일 (수) 오후 2:00" */
export function formatWhen(startAt: string, now: Date): string {
  const start = kst(new Date(startAt));
  const gap = start.dayNumber - kst(now).dayNumber;
  const clock = formatClock(start.minuteOfDay);
  if (gap === 0) return `오늘 ${clock}`;
  if (gap === 1) return `내일 ${clock}`;
  return `${start.month}월 ${start.day}일 (${start.weekday}) ${clock}`;
}

/** 상세 상단 일시: "11월 25일 수요일 오후 2:00" */
export function formatLongWhen(startAt: string): string {
  const start = kst(new Date(startAt));
  return `${start.month}월 ${start.day}일 ${start.weekday}요일 ${formatClock(start.minuteOfDay)}`;
}

/** 시각만: "오전 9:50" */
export function formatClockOf(at: string): string {
  return formatClock(kst(new Date(at)).minuteOfDay);
}

/**
 * 상태 칩 (PRD 4.14). 종료·파투는 종료, 시작했으면 진행 중,
 * 모집 중(서버 파생값)이 아니면 마감이다.
 */
export function phaseOf(
  study: Pick<StudyDetail, "startAt"> &
    Partial<Pick<StudyDetail, "status">> &
    Pick<StudySummary, "recruiting">,
  now: Date,
): StudyPhase {
  if (study.status && study.status !== "ACTIVE") return "ENDED";
  if (new Date(study.startAt).getTime() <= now.getTime()) return "IN_PROGRESS";
  return study.recruiting ? "RECRUITING" : "CLOSED";
}

/** 출석 지표 한 줄. 기록이 없으면 "신규" */
export function trustText(trust: Trust): string {
  return trust.isNewcomer || trust.attendanceRate === null
    ? "신규"
    : `출석률 ${trust.attendanceRate}%`;
}

/** 주최자 사실 칩 (PRD 4.13D): "주최 N회 중 직전 이탈 N회", 5회 이상이면 비율 */
export function hostFact(stats: HostStats): string {
  if (stats.hostedCount === 0) return "첫 주최";
  if (stats.showCountOnly) {
    return `주최 ${stats.hostedCount}회 중 직전 이탈 ${stats.leftWithinWindowCount}회`;
  }
  const rate = Math.round(
    (stats.leftWithinWindowCount / stats.hostedCount) * 100,
  );
  return `주최 ${stats.hostedCount}회 중 직전 이탈 ${rate}%`;
}

/** 입장 기준 배지 (PRD 4.2·4.7). 기준이 없으면 "누구나" 하나 */
export function entryCriteriaTexts(criteria: EntryCriteria): string[] {
  const texts: string[] = [];
  if (criteria.minAttendanceRate !== null) {
    texts.push(`출석률 ${criteria.minAttendanceRate}% 이상`);
  }
  if (!criteria.allowNewcomer) texts.push("신규 제외");
  return texts.length > 0 ? texts : ["누구나"];
}

export function nicknameOf(nickname: string | null): string {
  return nickname ?? "탈퇴한 사용자";
}

/** 코드 → 이름. 트리의 모든 단계를 담는다 */
export function codeNames(options: readonly CodeOption[]): Map<string, string> {
  const names = new Map<string, string>();
  const walk = (option: CodeOption) => {
    names.set(option.code, option.name);
    option.children.forEach(walk);
  };
  options.forEach(walk);
  return names;
}

/** 구 코드가 속한 시 코드 ("내 지역" 바로가기, PRD 4.3). 못 찾으면 undefined */
export function cityOf(
  districtCode: string,
  regions: readonly CodeOption[],
): string | undefined {
  const find = (
    option: CodeOption,
    parent: CodeOption | undefined,
  ): string | undefined => {
    if (option.code === districtCode) return parent?.code;
    for (const child of option.children) {
      const found = find(child, option);
      if (found) return found;
    }
    return undefined;
  };
  for (const top of regions) {
    const found = find(top, undefined);
    if (found) return found;
  }
  return undefined;
}

export type RegionChoice = { code: string; label: string };

/** 지역 필터 선택지: 시·도 아래의 시와 구를 화면 순서대로. 구는 "수원시 장안구"처럼 시 이름을 붙인다 */
export function regionChoices(regions: readonly CodeOption[]): RegionChoice[] {
  const choices: RegionChoice[] = [];
  const walk = (option: CodeOption, names: string[]) => {
    const label = [...names, option.name].join(" ");
    choices.push({ code: option.code, label });
    for (const child of option.children) walk(child, [...names, option.name]);
  };
  for (const top of regions) {
    for (const child of top.children) walk(child, []);
  }
  return choices;
}
