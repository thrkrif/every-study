import { describe, expect, it } from "vitest";
import {
  dateOptions,
  descriptionProblem,
  districtOfAddress,
  fieldTarget,
  formatClock,
  formatDuration,
  isPastStart,
  maxDuration,
  startMinutes,
  titleProblem,
  toStartAt,
} from "./rules";

const DISTRICTS = [
  { code: "SEOUL", label: "서울" },
  { code: "GYEONGGI", label: "경기" },
  { code: "GANGWON", label: "강원" },
  { code: "DAEJEON_SEJONG", label: "대전/세종" },
  { code: "CHUNGNAM", label: "충남" },
];

describe("dateOptions", () => {
  it("오늘(KST)부터 개설 가능 일수 뒤까지만 만든다", () => {
    // 2026-11-17T16:00Z 는 KST 로 11월 18일 01:00 이다
    const options = dateOptions(new Date("2026-11-17T16:00:00Z"), 14);

    expect(options).toHaveLength(15);
    expect(options[0]).toEqual({
      date: "2026-11-18",
      day: 18,
      weekday: "수",
      isToday: true,
    });
    expect(options.at(-1)!.date).toBe("2026-12-02");
  });

  it("KST 로 아직 전날이면 그날이 오늘이다", () => {
    const options = dateOptions(new Date("2026-11-18T14:59:00Z"), 1);
    expect(options[0].date).toBe("2026-11-18");
  });
});

describe("시작 시각", () => {
  it("KST 날짜와 시각을 UTC 로 바꾼다", () => {
    expect(toStartAt("2026-11-20", 10 * 60)).toBe("2026-11-20T01:00:00Z");
    expect(toStartAt("2026-11-20", 0)).toBe("2026-11-19T15:00:00Z");
  });

  it("단위에 맞는 시각만 만든다", () => {
    const minutes = startMinutes(10);
    expect(minutes).toHaveLength(144);
    expect(minutes.every((minute) => minute % 10 === 0)).toBe(true);
  });

  it("지금 이전이거나 같은 시각은 지난 시각이다", () => {
    const now = new Date("2026-11-20T01:00:00Z"); // KST 10:00
    expect(isPastStart("2026-11-20", 10 * 60, now)).toBe(true);
    expect(isPastStart("2026-11-20", 10 * 60 + 10, now)).toBe(false);
  });

  it("자정에 끝나는 것까지 고를 수 있다", () => {
    expect(maxDuration(22 * 60)).toBe(120);
  });

  it("오전·오후로 표시한다", () => {
    expect(formatClock(0)).toBe("오전 12:00");
    expect(formatClock(13 * 60 + 30)).toBe("오후 1:30");
    expect(formatDuration(150)).toBe("2시간 30분");
    expect(formatDuration(30)).toBe("30분");
  });
});

describe("districtOfAddress", () => {
  it("주소의 첫 낱말로 시·도를 찾는다", () => {
    expect(
      districtOfAddress("경기 수원시 팔달구 매산로1가 18", DISTRICTS),
    ).toBe("GYEONGGI");
    expect(districtOfAddress("서울 강남구 역삼동 1", DISTRICTS)).toBe("SEOUL");
  });

  it("긴 이름·정식 이름·묶인 지역도 찾는다", () => {
    expect(districtOfAddress("서울특별시 중구 세종대로 110", DISTRICTS)).toBe(
      "SEOUL",
    );
    expect(districtOfAddress("강원특별자치도 춘천시 중앙로 1", DISTRICTS)).toBe(
      "GANGWON",
    );
    expect(districtOfAddress("세종특별자치시 한누리대로 2130", DISTRICTS)).toBe(
      "DAEJEON_SEJONG",
    );
    expect(districtOfAddress("대전 서구 둔산동 1", DISTRICTS)).toBe(
      "DAEJEON_SEJONG",
    );
    expect(districtOfAddress("충청남도 천안시 동남구 1", DISTRICTS)).toBe(
      "CHUNGNAM",
    );
  });

  it("어느 지역에도 맞지 않으면 찾지 않는다", () => {
    expect(
      districtOfAddress("일본 도쿄도 신주쿠구 1", DISTRICTS),
    ).toBeUndefined();
    expect(districtOfAddress("", DISTRICTS)).toBeUndefined();
  });
});

describe("입력 검사", () => {
  it("제목은 2~40자", () => {
    expect(titleProblem(" 가 ")).toBe("2자 이상 입력해 주세요.");
    expect(titleProblem("면접 스터디")).toBeUndefined();
    expect(titleProblem("가".repeat(41))).toBe("40자까지 쓸 수 있어요.");
  });

  it("소개는 선택이고 2000자까지", () => {
    expect(descriptionProblem("")).toBeUndefined();
    expect(descriptionProblem("짧아요")).toBeUndefined();
    expect(descriptionProblem("가".repeat(2001))).toBe(
      "2000자까지 쓸 수 있어요.",
    );
  });

  it("서버 필드 오류를 단계와 칸으로 바꾼다", () => {
    expect(fieldTarget("title")).toEqual({ step: "about", key: "title" });
    expect(fieldTarget("place.name")).toEqual({ step: "place", key: "place" });
    expect(fieldTarget("targetJobCodes[0]")).toEqual({
      step: "topic",
      key: "targetJobCodes",
    });
    expect(fieldTarget("startAt")).toEqual({
      step: "schedule",
      key: "startAt",
    });
    expect(fieldTarget("unknown")).toBeUndefined();
  });
});
