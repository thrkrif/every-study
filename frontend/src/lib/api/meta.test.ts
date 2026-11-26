import { describe, expect, it } from "vitest";
import { districtsOf, type CodeOption } from "./meta";

const leaf = (code: string, name: string): CodeOption => ({
  code,
  name,
  children: [],
});

describe("districtsOf", () => {
  it("시·도 한 단계면 시·도 목록을 화면 순서대로 꺼낸다", () => {
    const regions: CodeOption[] = [
      leaf("SEOUL", "서울"),
      leaf("DAEJEON_SEJONG", "대전/세종"),
    ];

    expect(districtsOf(regions)).toEqual([
      { code: "SEOUL", label: "서울" },
      { code: "DAEJEON_SEJONG", label: "대전/세종" },
    ]);
  });
});
