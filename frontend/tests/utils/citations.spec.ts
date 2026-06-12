import { describe, expect, it } from 'vitest';
import { annotateCitations } from '@/utils/citations';
import type { EvidenceCitation } from '@/api/tutor';

const CITES: EvidenceCitation[] = [
  { pageId: 112, pageNo: 12, snippet: 'for 循环可遍历列表', score: 0.8 },
  { pageId: 120, pageNo: 20, snippet: 'while 循环条件为真时重复', score: 0.7 },
];

describe('annotateCitations', () => {
  it('把 [页 N] 替换为带 snippet 的上标角标（data-* 富浮层 + title fallback）', () => {
    const out = annotateCitations('参考 [页 12]，可遍历。', CITES);

    expect(out).toContain('<sup class="pyt-cite"');
    expect(out).toContain('data-page="12"');
    expect(out).toContain('data-snippet="for 循环可遍历列表"');
    expect(out).toContain('title="for 循环可遍历列表"');
    expect(out).toContain('[页 12]</sup>');
  });

  it('保留引用后缀（如 ·前置知识）', () => {
    const out = annotateCitations('见 [页 12·前置知识]。', CITES);

    expect(out).toContain('[页 12·前置知识]</sup>');
  });

  it('无匹配证据的页码保持原样（不伪造引用）', () => {
    expect(annotateCitations('见 [页 99]。', CITES)).toBe('见 [页 99]。');
  });

  it('无 citations 时整体原样返回', () => {
    expect(annotateCitations('见 [页 12]。', [])).toBe('见 [页 12]。');
  });

  it('转义 snippet 里的引号/尖括号，防破坏属性边界', () => {
    const out = annotateCitations('[页 12]', [
      { pageId: 1, pageNo: 12, snippet: 'a "b" <c>', score: 0.5 },
    ]);

    expect(out).toContain('title="a &quot;b&quot; &lt;c&gt;"');
  });

  it('多个引用各自映射到对应页的 snippet', () => {
    const out = annotateCitations('先看 [页 12]，再看 [页 20]。', CITES);

    expect(out).toContain('title="for 循环可遍历列表"');
    expect(out).toContain('title="while 循环条件为真时重复"');
  });
});
