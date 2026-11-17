import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AttendanceRow } from "./AttendanceRow";

describe("AttendanceRow", () => {
  it("최근 기록을 화면 낭독기용 글로 요약한다", () => {
    render(
      <AttendanceRow
        rate={70}
        records={["PRESENT", "LATE", "NO_SHOW", "PRESENT"]}
      />,
    );
    expect(
      screen.getByRole("img", {
        name: "최근 4회 중 출석 2회, 지각 1회, 노쇼 1회",
      }),
    ).toBeInTheDocument();
    expect(screen.getByText("출석률 70%")).toBeInTheDocument();
  });

  it("기록이 없으면 빈 칸 대신 신규로 표시한다", () => {
    render(<AttendanceRow records={[]} rate={0} />);
    expect(screen.getByText("신규")).toBeInTheDocument();
    expect(screen.queryByText(/출석률/)).not.toBeInTheDocument();
  });
});
