import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

// 토큰의 원본은 docs/DESIGN.md 4장이다. 코드(tokens.css)가 문서와 어긋나면 실패한다.
const read = (relative: string) =>
  readFileSync(fileURLToPath(new URL(relative, import.meta.url)), "utf8");

const design = read("../../../docs/DESIGN.md");
const css = read("./tokens.css");

function cssVar(name: string): string {
  const match = css.match(new RegExp(`--${name}:\\s*([^;]+);`));
  if (!match) throw new Error(`tokens.css 에 --${name} 이 없다`);
  return match[1].trim().toLowerCase();
}

function tableRows(heading: string): string[][] {
  const start = design.indexOf(heading);
  if (start < 0) throw new Error(`DESIGN.md 에 "${heading}" 이 없다`);
  const lines = design.slice(start).split("\n").slice(1);
  const rows: string[][] = [];
  let inTable = false;
  for (const line of lines) {
    if (line.startsWith("|")) {
      inTable = true;
      rows.push(
        line
          .split("|")
          .slice(1, -1)
          .map((cell) => cell.trim()),
      );
    } else if (inTable) {
      break;
    }
  }
  return rows.slice(2); // 머리줄과 구분줄을 뺀다
}

const codes = (cell: string) =>
  [...cell.matchAll(/`([^`]+)`/g)].map((match) => match[1]);

function designColors(): Map<string, string> {
  const colors = new Map<string, string>();
  for (const [nameCell, valueCell] of tableRows("### 색")) {
    const names = codes(nameCell);
    const values = codes(valueCell);
    names.forEach((name, index) =>
      colors.set(name, values[index].toLowerCase()),
    );
  }
  return colors;
}

// WCAG 2.x 상대 휘도와 대비
function luminance(hex: string): number {
  const channels = [1, 3, 5].map((offset) => {
    const value = parseInt(hex.slice(offset, offset + 2), 16) / 255;
    return value <= 0.04045
      ? value / 12.92
      : Math.pow((value + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
}

function contrast(foreground: string, background: string): number {
  const [light, dark] = [luminance(foreground), luminance(background)].sort(
    (a, b) => b - a,
  );
  return (light + 0.05) / (dark + 0.05);
}

describe("디자인 토큰", () => {
  it("DESIGN.md 색 표의 모든 값이 tokens.css 에 같은 값으로 있다", () => {
    const colors = designColors();
    expect(colors.size).toBeGreaterThan(10);
    for (const [name, value] of colors) {
      expect(cssVar(`color-${name}`), name).toBe(value);
    }
  });

  it("DESIGN.md 글자 표의 크기·행간·굵기가 tokens.css 와 같다", () => {
    const rows = tableRows("### 글꼴");
    expect(rows.length).toBe(6);
    for (const [nameCell, sizeCell, weightCell] of rows) {
      const [name] = codes(nameCell);
      const [size, line] = sizeCell.split("/");
      expect(cssVar(`text-${name}-size`), name).toBe(`${size}px`);
      expect(cssVar(`text-${name}-line`), name).toBe(`${line}px`);
      expect(cssVar(`text-${name}-weight`), name).toBe(weightCell);
    }
  });

  it.each([
    ["ink", "surface"],
    ["ink-sub", "surface"],
    ["ink-sub", "paper"],
    ["ink-sub", "neutral-bg"],
    ["surface", "primary"],
    ["primary", "primary-tint"],
    ["ok", "ok-bg"],
    ["late", "late-bg"],
    ["bad", "bad-bg"],
  ])("글자 %s 와 배경 %s 의 대비가 WCAG AA(4.5) 이상이다", (fg, bg) => {
    const colors = designColors();
    expect(contrast(colors.get(fg)!, colors.get(bg)!)).toBeGreaterThanOrEqual(
      4.5,
    );
  });
});
