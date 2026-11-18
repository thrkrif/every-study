"use client";

import { useState } from "react";
import {
  KakaoMapUnavailableError,
  searchPlaces,
  type PlaceResult,
} from "@/lib/kakaoMap";
import { Button } from "./Button";
import { PlaceMap } from "./PlaceMap";
import styles from "./PlacePicker.module.css";

type PlacePickerProps = {
  value: PlaceResult | undefined;
  onChange: (place: PlaceResult) => void;
  /** 장소 검색. 테스트에서 바꿔 끼운다 */
  search?: (keyword: string) => Promise<PlaceResult[]>;
  error?: string;
};

/**
 * 카카오맵 장소 검색으로 장소를 고른다 (PRD 4.2 상세 장소). 고르면 지도에 표시한다.
 * 좌표·장소명·주소는 검색 결과에서 그대로 가져온다.
 */
export function PlacePicker({
  value,
  onChange,
  search = searchPlaces,
  error,
}: PlacePickerProps) {
  const [keyword, setKeyword] = useState("");
  const [results, setResults] = useState<PlaceResult[]>();
  const [state, setState] = useState<"idle" | "searching" | "failed">("idle");
  const [failure, setFailure] = useState<string>();

  async function runSearch() {
    const trimmed = keyword.trim();
    if (!trimmed) return;
    setState("searching");
    try {
      setResults(await search(trimmed));
      setState("idle");
    } catch (cause) {
      setFailure(
        cause instanceof KakaoMapUnavailableError &&
          cause.message.includes("NEXT_PUBLIC_KAKAO_MAP_APP_KEY")
          ? "지도 키가 설정되지 않아 장소를 찾을 수 없어요."
          : "장소를 찾지 못했어요. 잠시 뒤 다시 시도해 주세요.",
      );
      setState("failed");
    }
  }

  return (
    <div className={styles.picker}>
      <div className={styles.searchRow}>
        <label className="visually-hidden" htmlFor="place-keyword">
          장소 검색
        </label>
        <input
          id="place-keyword"
          type="search"
          className={styles.searchInput}
          placeholder="카페, 스터디룸 이름이나 주소"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.nativeEvent.isComposing) {
              event.preventDefault();
              void runSearch();
            }
          }}
          enterKeyHint="search"
        />
        <Button
          onClick={() => void runSearch()}
          pending={state === "searching"}
          pendingLabel="찾는 중"
        >
          찾기
        </Button>
      </div>

      {state === "failed" && (
        <p className={styles.error} role="alert">
          {failure}
        </p>
      )}
      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}

      {value && (
        <div className={styles.selected}>
          <p className={styles.selectedName}>{value.name}</p>
          <p className={styles.address}>{value.roadAddress || value.address}</p>
          <PlaceMap place={value} />
        </div>
      )}

      {results && results.length === 0 && (
        <p className={styles.address}>
          찾는 장소가 없어요. 다른 이름이나 주소로 찾아보세요.
        </p>
      )}
      {results?.some((place) => place.sample) && (
        <p className={styles.address}>
          지도를 불러오지 못해 시험용 예시 장소를 보여 줘요.
        </p>
      )}
      {results && results.length > 0 && (
        <ul className={styles.results} aria-label="검색 결과">
          {results.map((place) => (
            <li key={place.id}>
              <button
                type="button"
                className={styles.result}
                aria-pressed={value?.id === place.id}
                onClick={() => {
                  onChange(place);
                  setResults(undefined);
                }}
              >
                <span className={styles.resultName}>{place.name}</span>
                <span className={styles.address}>
                  {place.roadAddress || place.address}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
