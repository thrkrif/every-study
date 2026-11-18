"use client";

import { useEffect, useRef, useState } from "react";
import { loadKakaoMaps } from "@/lib/kakaoMap";
import styles from "./PlaceMap.module.css";

type PlaceMapProps = {
  place: { name: string; latitude: number; longitude: number };
};

/**
 * 장소를 카카오맵에 표시한다 (DESIGN 5장 장소 고르기, 6장 상세).
 * 지도를 불러오지 못하면(키 없음, 카카오맵 사용 설정 꺼짐 등) 아무것도 그리지 않는다. 장소 글과 지도 링크는 화면이 따로 보여 준다.
 */
export function PlaceMap({ place }: PlaceMapProps) {
  const container = useRef<HTMLDivElement>(null);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    let cancelled = false;
    loadKakaoMaps()
      .then((maps) => {
        if (cancelled || !container.current) return;
        const center = new maps.LatLng(place.latitude, place.longitude);
        const map = new maps.Map(container.current, { center, level: 3 });
        new maps.Marker({ position: center, map });
      })
      .catch(() => {
        if (!cancelled) setUnavailable(true);
      });
    return () => {
      cancelled = true;
    };
  }, [place.latitude, place.longitude]);

  if (unavailable) return null;
  return (
    <div
      ref={container}
      className={styles.map}
      role="img"
      aria-label={`${place.name} 위치 지도`}
    />
  );
}

/** 카카오맵 웹에서 장소를 여는 주소. 지도 SDK 없이도 열린다 */
export function kakaoMapLink(place: PlaceMapProps["place"]): string {
  return `https://map.kakao.com/link/map/${encodeURIComponent(place.name)},${place.latitude},${place.longitude}`;
}
