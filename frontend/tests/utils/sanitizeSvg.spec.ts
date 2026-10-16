import { describe, expect, it } from 'vitest';
import { sanitizeMermaidSvgMarkup, sanitizeSvgMarkup } from '@/utils/sanitizeSvg';

// 消费方（TutorVisualAids / MindMapView）把清洗结果 v-html 进页面，数据源是 LLM 生成的 SVG /
// mermaid，因此这里的用例都是「不需要脚本也能作恶」的面。

describe('sanitizeSvgMarkup · 严格档（LLM 直出 SVG）', () => {
  it('剥离 style 属性：全屏覆盖点击劫持 + 外链请求都无法落地', () => {
    const evil =
      '<svg viewBox="0 0 10 10"><rect width="5" height="5" ' +
      'style="position:fixed;inset:0;z-index:99999;background:url(https://attacker.example/leak)">' +
      '</rect></svg>';

    const clean = sanitizeSvgMarkup(evil);

    expect(clean).toContain('<rect');
    expect(clean).not.toContain('style');
    expect(clean).not.toContain('position:fixed');
    expect(clean).not.toContain('attacker.example');
  });

  it('剥离 <style> 元素（同样能做覆盖层与外部请求）', () => {
    const evil =
      '<svg viewBox="0 0 10 10"><style>rect{background:url(https://attacker.example/leak)}</style>' +
      '<rect width="5" height="5"></rect></svg>';

    const clean = sanitizeSvgMarkup(evil);

    expect(clean).not.toContain('<style');
    expect(clean).not.toContain('attacker.example');
  });

  it('保留展示属性，不误伤正常图形', () => {
    const valid =
      '<svg viewBox="0 0 100 60"><rect x="4" y="4" width="40" height="20" fill="#f1c453"></rect>' +
      '<text x="8" y="18">lst[0]</text></svg>';

    const clean = sanitizeSvgMarkup(valid);

    expect(clean).toContain('fill="#f1c453"');
    expect(clean).toContain('lst[0]');
  });

  it('仍然剥离脚本 / 事件属性 / foreignObject / 外链（既有契约不回退）', () => {
    const evil =
      '<svg viewBox="0 0 10 10"><script>alert(1)</script>' +
      '<rect onload="alert(2)" width="5" height="5"></rect>' +
      '<foreignObject><iframe src="https://attacker.example"></iframe></foreignObject>' +
      '<a href="https://attacker.example"><text>x</text></a></svg>';

    const clean = sanitizeSvgMarkup(evil);

    expect(clean).not.toContain('<script');
    expect(clean).not.toContain('onload');
    expect(clean).not.toContain('foreignObject');
    expect(clean).not.toContain('iframe');
    expect(clean).not.toContain('href');
  });
});

describe('sanitizeMermaidSvgMarkup · mermaid 档', () => {
  it('保留 mermaid 自己生成的 <style> 与 style 属性（否则整图无着色无尺寸）', () => {
    const rendered =
      '<svg id="mermaid-1" style="max-width: 76px;"><style>#mermaid-1 .node rect{fill:#fff7e6;}</style>' +
      '<g class="node"><rect style="stroke-width: 1;"></rect><text>KC1</text></g></svg>';

    const clean = sanitizeMermaidSvgMarkup(rendered);

    expect(clean).toMatch(/#fff7e6|rgb\(255,\s*247,\s*230\)/i);
    expect(clean).toContain('max-width: 76px');
    expect(clean).toContain('KC1');
  });

  it('仍然剥离脚本 / 事件属性 / foreignObject / 外链', () => {
    const evil =
      '<svg id="mermaid-1"><script>alert(1)</script>' +
      '<g class="node" onclick="alert(2)"><rect></rect></g>' +
      '<foreignObject><iframe src="https://attacker.example"></iframe></foreignObject>' +
      '<a href="https://attacker.example"><text>x</text></a></svg>';

    const clean = sanitizeMermaidSvgMarkup(evil);

    expect(clean).not.toContain('<script');
    expect(clean).not.toContain('onclick');
    expect(clean).not.toContain('foreignObject');
    expect(clean).not.toContain('iframe');
    expect(clean).not.toContain('attacker.example');
  });
});
