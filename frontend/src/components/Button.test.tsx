import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Button } from "./Button";

describe("Button", () => {
  it("요청 중이면 글자를 바꾸고 다시 누를 수 없다", () => {
    render(
      <Button variant="primary" pending pendingLabel="참여하는 중">
        참여하기
      </Button>,
    );
    const button = screen.getByRole("button", { name: "참여하는 중" });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute("aria-busy", "true");
  });

  it("기본 type 은 submit 이 아니라 button 이다", () => {
    render(<Button>참여 취소</Button>);
    expect(screen.getByRole("button", { name: "참여 취소" })).toHaveAttribute(
      "type",
      "button",
    );
  });
});
