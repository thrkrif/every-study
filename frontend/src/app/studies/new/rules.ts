// 스터디 만들기 입력 규칙. 원본은 API 4장(개설 검증)·PRD 4.2 이고, 수치는 /meta/options 의 study 값을 받는다.
import type { District } from "@/lib/api/meta";

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"] as const;

export const TITLE_MIN = 2;
export const TITLE_MAX = 40;
export const DESCRIPTION_MAX = 2000;
export const PLACE_NAME_MAX = 100;
export const PLACE_ADDRESS_MAX = 200;

export type StudyRules = {
  capacityMin: number;
  capacityMax: number;
  openableDays: number;
  startMinuteUnit: number;
  durationMinuteUnit: number;
  durationMinMinutes: number;
  minAttendanceRates: number[];
};

export type DateOption = {
  /** KST 날짜 YYYY-MM-DD */
  date: string;
  day: number;
  weekday: (typeof WEEKDAYS)[number];
  isToday: boolean;
};

/** 오늘(KST)부터 개설 가능 일수 뒤까지. 이 밖의 날짜는 화면에 만들지 않는다. */
export function dateOptions(now: Date, openableDays: number): DateOption[] {
  const todayKst =
    Math.floor((now.getTime() + KST_OFFSET_MS) / DAY_MS) * DAY_MS;
  return Array.from({ length: openableDays + 1 }, (_, index) => {
    const kst = new Date(todayKst + index * DAY_MS);
    return {
      date: kst.toISOString().slice(0, 10),
      day: kst.getUTCDate(),
      weekday: WEEKDAYS[kst.getUTCDay()],
      isToday: index === 0,
    };
  });
}

/** KST 날짜와 시각을 ISO-8601 UTC 로 바꾼다. */
export function toStartAt(date: string, minuteOfDay: number): string {
  const [year, month, day] = date.split("-").map(Number);
  const utc = Date.UTC(year, month - 1, day, 0, minuteOfDay) - KST_OFFSET_MS;
  return new Date(utc).toISOString().replace(".000Z", "Z");
}

/** 하루 안의 시작 시각 후보(분). 단위에 맞는 값만 만든다. */
export function startMinutes(unit: number): number[] {
  return Array.from({ length: (24 * 60) / unit }, (_, index) => index * unit);
}

export function isPastStart(
  date: string,
  minuteOfDay: number,
  now: Date,
): boolean {
  return new Date(toStartAt(date, minuteOfDay)).getTime() <= now.getTime();
}

/** 시작한 날 안에 끝나야 하므로(자정까지 허용) 고를 수 있는 최대 진행 시간. */
export function maxDuration(minuteOfDay: number): number {
  return 24 * 60 - minuteOfDay;
}

export function formatClock(minuteOfDay: number): string {
  const hour = Math.floor(minuteOfDay / 60);
  const minute = minuteOfDay % 60;
  const period = hour < 12 ? "오전" : "오후";
  const hour12 = hour % 12 === 0 ? 12 : hour % 12;
  return `${period} ${hour12}:${String(minute).padStart(2, "0")}`;
}

export function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${rest}분`;
  return rest === 0 ? `${hours}시간` : `${hours}시간 ${rest}분`;
}

/**
 * 장소 주소로 활동 지역(구)을 찾는다. 구 표시 이름(예: "수원시 팔달구")의 낱말이 모두 주소에 있으면 그 구다.
 * 지원하지 않는 지역의 장소면 undefined.
 */
export function districtOfAddress(
  address: string,
  districts: readonly District[],
): string | undefined {
  const words = new Set(address.split(/\s+/));
  return districts.find((district) =>
    district.label.split(" ").every((word) => words.has(word)),
  )?.code;
}

export function titleProblem(raw: string): string | undefined {
  const length = raw.trim().length;
  if (length === 0) return "제목을 입력해 주세요.";
  if (length < TITLE_MIN) return `${TITLE_MIN}자 이상 입력해 주세요.`;
  if (length > TITLE_MAX) return `${TITLE_MAX}자까지 쓸 수 있어요.`;
  return undefined;
}

/** 소개는 선택이라 비워도 된다 (PRD 4.2). */
export function descriptionProblem(raw: string): string | undefined {
  if (raw.trim().length > DESCRIPTION_MAX)
    return `${DESCRIPTION_MAX}자까지 쓸 수 있어요.`;
  return undefined;
}

export type CreateStep =
  "about" | "topic" | "schedule" | "place" | "people" | "review";
export const CREATE_STEPS: readonly CreateStep[] = [
  "about",
  "topic",
  "schedule",
  "place",
  "people",
  "review",
];

/** 서버 필드 오류(`errors[].field`)를 보여 줄 단계와 칸 */
export function fieldTarget(
  field: string,
): { step: CreateStep; key: string } | undefined {
  const root = field.split(/[.[]/)[0];
  switch (root) {
    case "title":
    case "description":
      return { step: "about", key: root };
    case "topicCode":
    case "targetJobCodes":
      return { step: "topic", key: root };
    case "startAt":
    case "durationMin":
      return { step: "schedule", key: root };
    case "place":
    case "districtCode":
      return { step: "place", key: "place" };
    case "capacity":
    case "minAttendanceRate":
    case "allowNewcomer":
      return { step: "people", key: root };
    default:
      return undefined;
  }
}
