export type MarkdownBlockType = 'heading' | 'paragraph' | 'code' | 'list';

export interface MarkdownBlock {
  type: MarkdownBlockType;
  text: string;
}

// 预编译正则（模块级常量，避免每次解析 / 每个块重复编译）。
const CRLF_RE = /\r\n/g;
const HEADING_RE = /^#{1,6}\s+/;
const LIST_RE = /^(-|\*|\+|\d+\.)\s+/;
const INLINE_CODE_RE = /`([^`]+)`/g;
const BOLD_RE = /\*\*([^*]+)\*\*/g;
const ITALIC_RE = /\*([^*]+)\*/g;
const UNDERLINE_BOLD_RE = /__([^_]+)__/g;
const LINK_RE = /\[([^\]]+)\]\([^)]*\)/g;
const QUOTE_RE = /^>\s?/;

/**
 * 极简 Markdown → 可读文本块。不引入完整解析器：把题面 / 章节简介切成
 * 标题 / 段落 / 代码块 / 列表四类，页面据此渲染（代码块用等宽深色框）。
 * 行内 **粗体**、`代码`、链接、引用等标记会被剥离为纯文本，保证可读性。
 */
export function parseMarkdown(markdown: string | null | undefined): MarkdownBlock[] {
  if (!markdown) {
    return [];
  }
  const lines = markdown.replace(CRLF_RE, '\n').split('\n');
  const blocks: MarkdownBlock[] = [];
  let inFence = false;
  let codeBuffer: string[] = [];
  let paragraphBuffer: string[] = [];

  const flushParagraph = (): void => {
    if (paragraphBuffer.length > 0) {
      blocks.push({ type: 'paragraph', text: stripInline(paragraphBuffer.join(' ').trim()) });
      paragraphBuffer = [];
    }
  };

  lines.forEach((line) => {
    if (line.trim().startsWith('```')) {
      if (inFence) {
        blocks.push({ type: 'code', text: codeBuffer.join('\n') });
        codeBuffer = [];
        inFence = false;
      } else {
        flushParagraph();
        inFence = true;
      }
      return;
    }
    if (inFence) {
      codeBuffer.push(line);
      return;
    }
    const trimmed = line.trim();
    if (trimmed === '') {
      flushParagraph();
      return;
    }
    if (HEADING_RE.test(trimmed)) {
      flushParagraph();
      blocks.push({ type: 'heading', text: stripInline(trimmed.replace(HEADING_RE, '')) });
      return;
    }
    if (LIST_RE.test(trimmed)) {
      flushParagraph();
      blocks.push({ type: 'list', text: stripInline(trimmed.replace(LIST_RE, '')) });
      return;
    }
    paragraphBuffer.push(trimmed);
  });

  if (inFence && codeBuffer.length > 0) {
    blocks.push({ type: 'code', text: codeBuffer.join('\n') });
  }
  flushParagraph();
  return blocks;
}

function stripInline(text: string): string {
  return text
    .replace(INLINE_CODE_RE, '$1')
    .replace(BOLD_RE, '$1')
    .replace(ITALIC_RE, '$1')
    .replace(UNDERLINE_BOLD_RE, '$1')
    .replace(LINK_RE, '$1')
    .replace(QUOTE_RE, '');
}
