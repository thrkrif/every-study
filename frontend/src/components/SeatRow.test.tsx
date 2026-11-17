import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SeatRow } from "./SeatRow";

describe("SeatRow", () => {
  it("정원과 참여 인원을 글과 숫자로 보여 준다", () => {
    render(<SeatRow capacity={6} memberCount={3} />);
    expect(
      screen.getByRole("img", { name: "정원 6명 중 3명 참여" }),
    ).toBeInTheDocument();
    expect(screen.getByText("3/6명")).toBeInTheDocument();
  });

  it("정원만큼 칸을 그리고 첫 칸은 주최자로 표시한다", () => {
    const { container } = render(<SeatRow capacity={4} memberCount={2} />);
    const seats = container.querySelectorAll("[data-state]");
    expect([...seats].map((seat) => seat.getAttribute("data-state"))).toEqual([
      "host",
      "member",
      "empty",
      "empty",
    ]);
  });

  it("정원이 차면 마감으로 표시하고 정원보다 많이 채우지 않는다", () => {
    render(<SeatRow capacity={4} memberCount={5} />);
    const row = screen.getByRole("img", { name: "정원 4명 중 4명 참여" });
    expect(row).toHaveAttribute("data-full");
  });

  it("내 자리에 표시를 단다", () => {
    const { container } = render(
      <SeatRow capacity={4} memberCount={3} mySeatIndex={2} />,
    );
    const mine = container.querySelectorAll("[data-mine]");
    expect(mine).toHaveLength(1);
    expect(mine[0]).toHaveAttribute("data-state", "member");
  });
});
