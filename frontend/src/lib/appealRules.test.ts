import { describe, expect, it } from "vitest";
import { ApiError } from "./api/errors";
import { appealErrorText, appealStatusText, textProblem } from "./appealRules";

describe("이의 입력 규칙", () => {
  it("공백뿐이거나 500자를 넘으면 문구를 주고, 500자까지는 통과한다", () => {
    expect(textProblem("   ", "reason")).toBe("이의 사유를 입력해 주세요.");
    expect(textProblem("가".repeat(500), "reason")).toBeUndefined();
    expect(textProblem("가".repeat(501), "opinion")).toBe(
      "의견은 500자까지 쓸 수 있어요.",
    );
  });

  it("오류 코드마다 안내를 정하고 모르는 오류는 일반 안내를 쓴다", () => {
    const err = (code: string) => new ApiError({ status: 409, code });
    expect(appealErrorText(err("APPEAL_CLOSED"))).toContain("기간이 지났");
    expect(appealErrorText(err("ALREADY_APPEALED"))).toContain("이미");
    expect(appealErrorText(err("APPEAL_NOT_ALLOWED"))).toContain(
      "신청할 수 없는",
    );
    expect(appealErrorText(new Error("x"))).toContain("다시 시도");
  });

  it("처리 상태 이름", () => {
    expect(appealStatusText("OPEN")).toBe("검토 중");
    expect(appealStatusText("ACCEPTED")).toBe("인정됨");
    expect(appealStatusText("REJECTED")).toBe("기각됨");
  });
});
