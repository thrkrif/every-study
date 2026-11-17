import { describe, expect, it } from "vitest";
import { districtsOf, type CodeOption } from "./meta";

const leaf = (code: string, name: string): CodeOption => ({
  code,
  name,
  children: [],
});

describe("districtsOf", () => {
  it("맨 아래 단계(구)만 화면 순서대로 꺼내고 시 이름을 붙인다", () => {
    const regions: CodeOption[] = [
      {
        code: "GYEONGGI",
        name: "경기",
        children: [
          {
            code: "SUWON",
            name: "수원시",
            children: [
              leaf("SUWON_JANGAN", "장안구"),
              leaf("SUWON_GWONSEON", "권선구"),
            ],
          },
        ],
      },
    ];

    expect(districtsOf(regions)).toEqual([
      { code: "SUWON_JANGAN", label: "수원시 장안구" },
      { code: "SUWON_GWONSEON", label: "수원시 권선구" },
    ]);
  });
});
