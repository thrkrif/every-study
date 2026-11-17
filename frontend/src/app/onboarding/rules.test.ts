import { describe, expect, it } from "vitest";
import { nicknameProblem, stepOfField } from "./rules";

describe("nicknameProblem", () => {
  it.each(["준비생", "ab", "dev_kim", "백엔드2026", "가".repeat(20)])(
    "%s 는 쓸 수 있다",
    (value) => {
      expect(nicknameProblem(value)).toBeUndefined();
    },
  );

  it("앞뒤 공백은 잘라서 본다", () => {
    expect(nicknameProblem("  준비생  ")).toBeUndefined();
  });

  it.each([
    ["", "닉네임을 입력해 주세요."],
    ["   ", "닉네임을 입력해 주세요."],
    ["a", "2자 이상 입력해 주세요."],
    ["가".repeat(21), "20자까지 쓸 수 있어요."],
    ["준비 생", "한글, 영문, 숫자, 밑줄(_)만 쓸 수 있어요."],
    ["ㅋㅋ", "한글, 영문, 숫자, 밑줄(_)만 쓸 수 있어요."],
    ["dev-kim", "한글, 영문, 숫자, 밑줄(_)만 쓸 수 있어요."],
  ])("'%s' 는 거절한다", (value, message) => {
    expect(nicknameProblem(value)).toBe(message);
  });
});

describe("stepOfField", () => {
  it("서버 필드 이름을 입력 단계로 바꾼다", () => {
    expect(stepOfField("nickname")).toBe("nickname");
    expect(stepOfField("jobCode")).toBe("job");
    expect(stepOfField("districtCode")).toBe("region");
    expect(stepOfField("unknown")).toBeUndefined();
  });
});
