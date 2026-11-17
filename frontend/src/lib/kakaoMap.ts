/**
 * 카카오맵 JavaScript SDK (ARCHITECTURE 6장: 클라이언트에서만 로드한다).
 * 키는 브라우저에 공개되는 JavaScript 키이고, 카카오 개발자 콘솔에서 사이트 도메인을 제한한다 (PLAN H-2).
 */

export type PlaceResult = {
  id: string;
  name: string;
  /** 지번 주소. 구 판별에 쓴다 */
  address: string;
  /** 도로명 주소. 없으면 빈 문자열 */
  roadAddress: string;
  latitude: number;
  longitude: number;
  /** 개발 서버 전용 예시 장소면 true (devPlaces.ts) */
  sample?: boolean;
};

// SDK 에서 쓰는 부분만 적은 타입
type KakaoPlace = {
  id: string;
  place_name: string;
  address_name: string;
  road_address_name: string;
  x: string;
  y: string;
};

export type KakaoMaps = {
  load(callback: () => void): void;
  LatLng: new (lat: number, lng: number) => unknown;
  Map: new (
    container: HTMLElement,
    options: { center: unknown; level: number },
  ) => { setCenter(latLng: unknown): void; relayout(): void };
  Marker: new (options: { position: unknown; map: unknown }) => {
    setMap(map: unknown): void;
  };
  services: {
    Places: new () => {
      keywordSearch(
        keyword: string,
        callback: (result: KakaoPlace[], status: string) => void,
        options?: { size?: number },
      ): void;
    };
    Status: { OK: string; ZERO_RESULT: string; ERROR: string };
  };
};

declare global {
  interface Window {
    kakao?: { maps: KakaoMaps };
  }
}

export class KakaoMapUnavailableError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "KakaoMapUnavailableError";
  }
}

let loading: Promise<KakaoMaps> | undefined;

/** SDK 를 한 번만 불러온다. 키가 없거나 불러오지 못하면 KakaoMapUnavailableError. */
export function loadKakaoMaps(): Promise<KakaoMaps> {
  if (typeof window === "undefined") {
    return Promise.reject(new KakaoMapUnavailableError("브라우저에서만 쓴다"));
  }
  loading ??= new Promise<KakaoMaps>((resolve, reject) => {
    const appKey = process.env.NEXT_PUBLIC_KAKAO_MAP_APP_KEY;
    if (!appKey) {
      reject(
        new KakaoMapUnavailableError(
          "NEXT_PUBLIC_KAKAO_MAP_APP_KEY 가 설정되지 않았습니다 (저장소 루트의 .env.example 참고)",
        ),
      );
      return;
    }
    const script = document.createElement("script");
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey)}&autoload=false&libraries=services`;
    script.async = true;
    script.onload = () => {
      const maps = window.kakao?.maps;
      if (!maps) {
        reject(new KakaoMapUnavailableError("카카오맵 SDK 를 읽지 못했습니다"));
        return;
      }
      maps.load(() => resolve(maps));
    };
    script.onerror = () =>
      reject(
        new KakaoMapUnavailableError("카카오맵 SDK 를 불러오지 못했습니다"),
      );
    document.head.appendChild(script);
  }).catch((error) => {
    loading = undefined; // 다음에 다시 시도할 수 있게 한다
    throw error;
  });
  return loading;
}

/**
 * 키워드로 장소를 찾는다. 결과가 없으면 빈 배열.
 * 개발 서버(next dev)에서 SDK 를 불러오지 못하면(키 없음, 카카오맵 사용 설정 꺼짐 등) 예시 장소를 준다.
 */
export async function searchPlaces(keyword: string): Promise<PlaceResult[]> {
  let maps: KakaoMaps;
  try {
    maps = await loadKakaoMaps();
  } catch (error) {
    if (process.env.NODE_ENV === "development") {
      const { devSamplePlaces } = await import("./devPlaces");
      return devSamplePlaces(keyword);
    }
    throw error;
  }
  return new Promise((resolve, reject) => {
    new maps.services.Places().keywordSearch(keyword, (result, status) => {
      if (status === maps.services.Status.ZERO_RESULT) {
        resolve([]);
        return;
      }
      if (status !== maps.services.Status.OK) {
        reject(new KakaoMapUnavailableError("장소 검색에 실패했습니다"));
        return;
      }
      resolve(
        result.map((place) => ({
          id: place.id,
          name: place.place_name,
          address: place.address_name,
          roadAddress: place.road_address_name,
          latitude: Number(place.y),
          longitude: Number(place.x),
        })),
      );
    });
  });
}
