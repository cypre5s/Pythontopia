import mermaid from 'mermaid';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { sanitizeMermaidSvgMarkup } from './sanitizeSvg';

describe('sanitizeMermaidSvgMarkup', () => {
  beforeAll(() => {
    Object.defineProperty(SVGElement.prototype, 'getComputedTextLength', {
      configurable: true,
      value: () => 100,
    });
    Object.defineProperty(SVGElement.prototype, 'getBBox', {
      configurable: true,
      value: () => ({ x: 0, y: 0, width: 100, height: 20 }),
    });
  });

  afterAll(() => {
    delete (SVGElement.prototype as unknown as { getComputedTextLength?: () => number })
      .getComputedTextLength;
    delete (SVGElement.prototype as unknown as { getBBox?: () => DOMRect }).getBBox;
  });

  it('removes layout overrides while preserving safe Mermaid colors', async () => {
    mermaid.initialize({ securityLevel: 'strict', htmlLabels: false });
    const { svg } = await mermaid.render(
      'unsafe-style-probe',
      'flowchart TD\n  A[Node]:::overlay\n  classDef overlay position:fixed,top:0,left:0,width:100vw,height:100vh,fill:red',
    );

    const sanitized = sanitizeMermaidSvgMarkup(svg);
    expect(sanitized).not.toMatch(/position:\s*fixed/i);
    expect(sanitized).toMatch(/fill:\s*red/i);
  });

  it('removes CSS supplied through Mermaid init directives', async () => {
    mermaid.initialize({ securityLevel: 'strict', htmlLabels: false });
    const { svg } = await mermaid.render(
      'unsafe-theme-probe',
      '%%{init: {"themeCSS": "#unsafe-theme-probe { position: fixed; inset: 0; background-image: url(//example.invalid/pixel); }"}}%%\nflowchart TD\n  A[Node]',
    );

    const sanitized = sanitizeMermaidSvgMarkup(svg);
    expect(sanitized).not.toMatch(/position:\s*fixed/i);
    expect(sanitized).not.toMatch(/url\s*\(/i);
  });

  it('drops Mermaid CSS selectors that can reach outside the SVG', async () => {
    mermaid.initialize({ securityLevel: 'strict', htmlLabels: false });
    const { svg } = await mermaid.render(
      'unscoped-theme-probe',
      '%%{init: {"themeCSS": "body { background-color: red; }"}}%%\nflowchart TD\n  A[Node]',
    );

    expect(sanitizeMermaidSvgMarkup(svg)).not.toMatch(/(?:^|})\s*body\s*\{/i);
  });
});
