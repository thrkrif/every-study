import { describe, expect, it } from "vitest";
import { DEV_SAMPLE_PLACES, devSamplePlaces } from "./devPlaces";

describe("개발용 예시 장소", () => {
  it("검색어가 들어간 장소만 주고, 맞는 것이 없으면 전부 준다", () => {
    expect(devSamplePlaces("영통").map((place) => place.id)).toEqual([
      "dev-yeongtong",
    ]);
    expect(devSamplePlaces("수원역 스터디카페")).toHaveLength(
      DEV_SAMPLE_PLACES.length,
    );
  });

  it("모두 예시 표시가 있다", () => {
    expect(DEV_SAMPLE_PLACES.every((place) => place.sample)).toBe(true);
  });
});
