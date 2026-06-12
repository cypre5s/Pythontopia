import type { EvidenceCitation } from '@/api/tutor';

/** 转义注入 HTML 属性的文本，防破坏属性边界 / XSS（MarkdownView 的 DOMPurify 再兜底）。 */
function escapeAttr(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

/**
 * 把答案 Markdown 里的页码引用 `[页 N]`（含 `[页 N·前置知识]` 等后缀）替换为可 hover 的上标角标：
 * `<sup class="pyt-cite" title="课件原文片段">[页 N]</sup>`，hover 直接看命中的课件原文 snippet。
 *
 * N 对应 {@link EvidenceCitation.pageNo}；无匹配证据的页码保持原样（不伪造引用）；无 citations
 * 则整体原样返回。产物含 inline HTML，交由 MarkdownView（marked 透传 + DOMPurify 白名单
 * sup/title/class）安全渲染。
 */
export function annotateCitations(md: string, citations: EvidenceCitation[]): string {
  if (!md || citations.length === 0) return md;
  const byPageNo = new Map<number, EvidenceCitation>();
  for (const c of citations) {
    if (!byPageNo.has(c.pageNo)) byPageNo.set(c.pageNo, c);
  }
  return md.replace(/\[页\s*(\d+)([^\]]*)\]/g, (full, num: string, suffix: string) => {
    const cite = byPageNo.get(Number(num));
    if (!cite) return full;
    // data-* 承载富浮层内容（CSS ::after attr() 取值显示，marked 不解析属性值，避免 snippet 被
    // 当 markdown 渲染）；title 作为可访问性 / 移动端 / 无 CSS 的 fallback。
    const snip = escapeAttr(cite.snippet);
    return (
      `<sup class="pyt-cite" data-page="${num}" data-snippet="${snip}" title="${snip}">` +
      `[页 ${num}${suffix}]</sup>`
    );
  });
}
