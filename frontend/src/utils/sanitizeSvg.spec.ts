import mermaid from "mermaid";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { sanitizeMermaidSvgMarkup, sanitizeSvgMarkup } from "./sanitizeSvg";

describe("sanitizeSvgMarkup", () => {
  it("removes external paint-server references and preserves local definitions", () => {
    const source =
      '<svg viewBox="0 0 10 10"><defs><linearGradient id="local"><stop offset="0" stop-color="#fff" /></linearGradient></defs><rect fill="url(#local)" filter="url(https://attacker.invalid/filter.svg#f)" /></svg>';
    const sanitized = sanitizeSvgMarkup(source);

    expect(sanitized).toContain('fill="url(#local)"');
    expect(sanitized).not.toMatch(/attacker\.invalid/i);
  });

  it("removes CSS-escaped external paint-server references", () => {
    const source =
      '<svg viewBox="0 0 10 10"><rect fill="u\\72l(https://attacker.invalid/paint.svg#x)" /></svg>';

    expect(sanitizeSvgMarkup(source)).not.toMatch(/attacker\.invalid/i);
  });

  it("removes SVG animation that can introduce external paint references after sanitizing", () => {
    const source =
      '<svg viewBox="0 0 10 10"><rect fill="#fff"><animate attributeName="fill" values="url(https://attacker.invalid/paint.svg#x);#fff" dur="1s" repeatCount="indefinite" /></rect></svg>';

    expect(sanitizeSvgMarkup(source)).not.toMatch(
      /<animate\b|attacker\.invalid/i,
    );
  });

  it("rejects non-local paint-server references in Mermaid output too", () => {
    const source =
      '<svg id="mermaid-external-paint" viewBox="0 0 10 10"><rect fill="url(https://attacker.invalid/paint.svg#x)" /></svg>';

    expect(sanitizeMermaidSvgMarkup(source)).not.toMatch(
      /url\s*\(|attacker\.invalid/i,
    );
  });
});

describe("sanitizeMermaidSvgMarkup", () => {
  beforeAll(() => {
    Object.defineProperty(SVGElement.prototype, "getComputedTextLength", {
      configurable: true,
      value: () => 100,
    });
    Object.defineProperty(SVGElement.prototype, "getBBox", {
      configurable: true,
      value: () => ({ x: 0, y: 0, width: 100, height: 20 }),
    });
  });

  afterAll(() => {
    delete (
      SVGElement.prototype as unknown as {
        getComputedTextLength?: () => number;
      }
    ).getComputedTextLength;
    delete (SVGElement.prototype as unknown as { getBBox?: () => DOMRect })
      .getBBox;
  });

  it("removes layout overrides while preserving safe Mermaid colors", async () => {
    mermaid.initialize({ securityLevel: "strict", htmlLabels: false });
    const { svg } = await mermaid.render(
      "unsafe-style-probe",
      "flowchart TD\n  A[Node]:::overlay\n  classDef overlay position:fixed,top:0,left:0,width:100vw,height:100vh,fill:red",
    );

    const sanitized = sanitizeMermaidSvgMarkup(svg);
    expect(sanitized).not.toMatch(/position:\s*fixed/i);
    expect(sanitized).toMatch(/fill:\s*red/i);
  });

  it("removes CSS supplied through Mermaid init directives", async () => {
    mermaid.initialize({ securityLevel: "strict", htmlLabels: false });
    const { svg } = await mermaid.render(
      "unsafe-theme-probe",
      '%%{init: {"themeCSS": "#unsafe-theme-probe { position: fixed; inset: 0; background-image: url(//example.invalid/pixel); }"}}%%\nflowchart TD\n  A[Node]',
    );

    const sanitized = sanitizeMermaidSvgMarkup(svg);
    expect(sanitized).not.toMatch(/position:\s*fixed/i);
    expect(sanitized).not.toMatch(/url\s*\(/i);
  });

  it("drops Mermaid CSS selectors that can reach outside the SVG", async () => {
    mermaid.initialize({ securityLevel: "strict", htmlLabels: false });
    const { svg } = await mermaid.render(
      "unscoped-theme-probe",
      '%%{init: {"themeCSS": "body { background-color: red; }"}}%%\nflowchart TD\n  A[Node]',
    );

    expect(sanitizeMermaidSvgMarkup(svg)).not.toMatch(/(?:^|})\s*body\s*\{/i);
  });
});
