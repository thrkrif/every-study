import type { PlaceResult } from "./kakaoMap";

/**
 * 개발 서버 전용 예시 장소. 카카오맵을 운영 전까지 켜지 않기로 해서(PLAN H-2),
 * 로컬에서 지도 없이도 스터디를 만들어 볼 수 있게 수원 안의 몇 곳을 둔다 (모두 경기 지역).
 * 좌표는 각 구청 근처의 근사값이고 실제 장소가 아니다. 운영 빌드에서는 쓰지 않는다.
 */
export const DEV_SAMPLE_PLACES: readonly PlaceResult[] = [
  {
    id: "dev-paldal",
    name: "예시 장소 (팔달구)",
    address: "경기 수원시 팔달구",
    roadAddress: "",
    latitude: 37.2826,
    longitude: 127.0196,
    sample: true,
  },
  {
    id: "dev-jangan",
    name: "예시 장소 (장안구)",
    address: "경기 수원시 장안구",
    roadAddress: "",
    latitude: 37.3039,
    longitude: 127.0103,
    sample: true,
  },
  {
    id: "dev-gwonseon",
    name: "예시 장소 (권선구)",
    address: "경기 수원시 권선구",
    roadAddress: "",
    latitude: 37.2577,
    longitude: 126.9718,
    sample: true,
  },
  {
    id: "dev-yeongtong",
    name: "예시 장소 (영통구)",
    address: "경기 수원시 영통구",
    roadAddress: "",
    latitude: 37.2596,
    longitude: 127.0465,
    sample: true,
  },
];

/** 검색어가 들어간 예시 장소. 맞는 것이 없으면 전부 보여 준다. */
export function devSamplePlaces(keyword: string): PlaceResult[] {
  const words = keyword.trim().split(/\s+/).filter(Boolean);
  const matched = DEV_SAMPLE_PLACES.filter((place) =>
    words.some(
      (word) => place.name.includes(word) || place.address.includes(word),
    ),
  );
  return [...(matched.length > 0 ? matched : DEV_SAMPLE_PLACES)];
}
