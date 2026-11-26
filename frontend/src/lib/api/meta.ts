import { apiFetch } from "./client";

/** 코드 목록의 한 항목. 순서가 화면에 보일 순서다 (API 4장 메타 옵션). */
export type CodeOption = {
  code: string;
  name: string;
  children: CodeOption[];
};

export type MetaOptions = {
  topics: CodeOption[];
  jobs: CodeOption[];
  regions: CodeOption[];
  study: {
    capacityMin: number;
    capacityMax: number;
    openableDays: number;
    startMinuteUnit: number;
    durationMinuteUnit: number;
    durationMinMinutes: number;
    minAttendanceRates: number[];
  };
  checkin: { radiusMeters: number };
};

export function fetchMetaOptions(signal?: AbortSignal): Promise<MetaOptions> {
  return apiFetch<MetaOptions>("/meta/options", { signal });
}

export type District = {
  code: string;
  /** 표시 이름. 예: "서울", "대전/세종" */
  label: string;
};

/** 지역 트리에서 맨 아래 단계만 화면 순서대로 꺼낸다. 지금 지역은 시·도 한 단계라 시·도 목록 그대로다. */
export function districtsOf(regions: CodeOption[]): District[] {
  const result: District[] = [];
  const walk = (option: CodeOption, names: string[]) => {
    if (option.children.length === 0) {
      result.push({ code: option.code, label: names.join(" ") });
      return;
    }
    for (const child of option.children) walk(child, [...names, child.name]);
  };
  for (const top of regions) {
    for (const child of top.children) walk(child, [child.name]);
    if (top.children.length === 0) walk(top, [top.name]);
  }
  return result;
}
