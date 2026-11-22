import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ProfileMetrics } from "./ProfileMetrics";

const trust = {
  attendanceRate: 92,
  windowCount: 10,
  noShow: 0,
  late: 1,
  lateCancel: 0,
  isNewcomer: false,
};
const host = { hostedCount: 7, leftWithinWindowCount: 1, showCountOnly: false };

describe("ProfileMetrics", () => {
  it("신규는 숫자 대신 배지를 보인다", () => {
    render(
      <ProfileMetrics
        attendance={{
          ...trust,
          attendanceRate: null,
          windowCount: 1,
          isNewcomer: true,
        }}
        hostStats={{
          hostedCount: 0,
          leftWithinWindowCount: 0,
          showCountOnly: true,
        }}
      />,
    );

    expect(screen.getByText("신규")).toBeInTheDocument();
    expect(screen.queryByText(/출석률/)).not.toBeInTheDocument();
    expect(screen.getByText("첫 주최")).toBeInTheDocument();
  });

  it("일반 회원은 PRD 4.7 표시 형태로, 주최 5회 이상은 비율로 보인다", () => {
    render(<ProfileMetrics attendance={trust} hostStats={host} />);

    expect(
      screen.getByText("출석률 92% · 최근 10회 중 노쇼 0회 · 지각 1회"),
    ).toBeInTheDocument();
    expect(screen.getByText("주최 7회 중 직전 이탈 14%")).toBeInTheDocument();
    expect(screen.queryByText("신규")).not.toBeInTheDocument();
  });

  it("주최가 5회 미만이면 비율 없이 건수만 보인다", () => {
    render(
      <ProfileMetrics
        attendance={trust}
        hostStats={{
          hostedCount: 3,
          leftWithinWindowCount: 1,
          showCountOnly: true,
        }}
      />,
    );

    expect(screen.getByText("주최 3회 중 직전 이탈 1회")).toBeInTheDocument();
    expect(screen.queryByText(/%$/)).not.toBeInTheDocument();
  });
});
