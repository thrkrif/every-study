import { useQuery } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Providers } from "./providers";

function Greeting() {
  const { data } = useQuery({
    queryKey: ["greeting"],
    queryFn: () => "안녕하세요",
  });
  return <p>{data ?? "불러오는 중"}</p>;
}

describe("Providers", () => {
  it("자식이 TanStack Query 로 데이터를 조회할 수 있다", async () => {
    render(
      <Providers>
        <Greeting />
      </Providers>,
    );

    expect(await screen.findByText("안녕하세요")).toBeInTheDocument();
  });
});
