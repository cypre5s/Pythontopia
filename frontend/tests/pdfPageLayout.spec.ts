import { describe, expect, it } from "vitest";
import { fitPdfPage } from "../src/shared/pdfPageLayout";

describe("whole-page PDF layout", () => {
  it("fits landscape and portrait originals within both dimensions", () => {
    const landscape = fitPdfPage(1200, 675, 800, 800, 2)!;
    expect(landscape.width).toBe(800);
    expect(landscape.height).toBe(450);
    expect(landscape.renderScale).toBeCloseTo(4 / 3);
    const portrait = fitPdfPage(600, 900, 1100, 720)!;
    expect(portrait.width).toBe(480);
    expect(portrait.height).toBe(720);
  });
  it("shrinks when toolbar, font preference or card dragging reduces available height", () => {
    const small = fitPdfPage(1200, 675, 800, 140)!;
    expect(small.height).toBe(140);
    expect(small.width).toBeLessThan(800);
    expect(small.renderScale).toBeLessThan(0.3);
  });
  it("defers hidden or invalid surfaces and uses a valid screen resolution", () => {
    expect(fitPdfPage(1200, 675, 0, 500)).toBeUndefined();
    expect(fitPdfPage(1200, 675, 700, 0)).toBeUndefined();
    expect(fitPdfPage(NaN, 675, 700, 500)).toBeUndefined();
    expect(fitPdfPage(600, 900, 480, 720, NaN)?.renderScale).toBe(0.8);
  });
});
