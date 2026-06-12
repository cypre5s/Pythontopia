<script setup lang="ts">
import { computed } from 'vue';
import { marked } from 'marked';
import DOMPurify from 'dompurify';

const props = defineProps<{
  source: string;
  inline?: boolean;
}>();

const PURIFY_CONFIG: { ALLOWED_TAGS: string[]; ALLOWED_ATTR: string[] } = {
  ALLOWED_TAGS: [
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'p', 'br', 'hr', 'ul', 'ol', 'li',
    'strong', 'em', 'del', 'code', 'pre',
    'a', 'img', 'blockquote', 'table',
    'thead', 'tbody', 'tr', 'th', 'td',
    'span', 'div', 'sup', 'sub',
  ],
  ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'class', 'target', 'rel'],
};

/**
 * 流式安全：源码出现未闭合的 ``` 代码围栏时（流式 token 半截到达），先补全闭合再渲染，
 * 避免 marked 对未闭合围栏渲染不稳定——围栏后内容被当普通文本、闭合瞬间又突变成代码块
 * （视觉闪烁），或把后文整段「吞」进 <pre><code>。补全只在围栏数为奇数时加一行，幂等无副作用。
 */
function closeOpenFence(src: string): string {
  const fences = src.match(/^```/gm);
  if (fences && fences.length % 2 === 1) {
    return `${src}\n\`\`\``;
  }
  return src;
}

const rendered = computed(() => {
  if (!props.source) return '';
  // inline 模式无代码块，不做围栏补全。
  const src = props.inline ? props.source : closeOpenFence(props.source);
  const raw = props.inline
    ? marked.parseInline(src) as string
    : marked.parse(src) as string;
  return DOMPurify.sanitize(raw, PURIFY_CONFIG);
});
</script>

<template>
  <!-- eslint-disable vue/no-v-html — rendered 已经 marked + DOMPurify.sanitize 白名单清洗，无 XSS 风险，已审阅 -->
  <div
    v-if="!inline"
    class="md-view"
    v-html="rendered"
  />
  <span
    v-else
    class="md-view md-view--inline"
    v-html="rendered"
  />
  <!-- eslint-enable vue/no-v-html -->
</template>

<style scoped>
/* 统一标准·可读性分层：学习正文用 --pyt-font-read（易读无衬线）而非像素字，
   即便挂在像素字面板里也保证大段讲义/答疑清晰可读。 */
.md-view {
  font-family: var(--pyt-font-read);
  font-size: 15px;
  line-height: 1.75;
  color: var(--pyt-color-text-primary);
  word-break: break-word;
}
.md-view :deep(h1),
.md-view :deep(h2),
.md-view :deep(h3),
.md-view :deep(h4) {
  color: var(--pyt-color-text-primary);
  margin: 16px 0 8px;
  line-height: 1.3;
}
.md-view :deep(h1) { font-size: 20px; }
.md-view :deep(h2) { font-size: 17px; }
.md-view :deep(h3) { font-size: 15px; }
.md-view :deep(p) { margin: 6px 0; }
.md-view :deep(a) {
  color: var(--pyt-color-text-hint);
  text-decoration: underline;
  text-decoration-style: dashed;
}
.md-view :deep(a:hover) { color: #3a2a14; }
.md-view :deep(code) {
  font-family: 'Fira Code', 'JetBrains Mono', monospace;
  background: rgba(110, 79, 29, 0.1);
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 13px;
}
.md-view :deep(pre) {
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  padding: 12px 14px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.5;
  border: 2px solid var(--pyt-color-text-hint);
}
.md-view :deep(pre code) {
  background: transparent;
  padding: 0;
  color: inherit;
}
.md-view :deep(blockquote) {
  margin: 8px 0;
  padding: 8px 14px;
  border-left: 4px solid var(--pyt-color-border);
  background: rgba(249, 231, 188, 0.4);
  color: #5e4923;
}
.md-view :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 13px;
}
.md-view :deep(th),
.md-view :deep(td) {
  border: 1px solid var(--pyt-color-border);
  padding: 6px 10px;
  text-align: left;
}
.md-view :deep(th) {
  background: #f1d896;
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.md-view :deep(ul),
.md-view :deep(ol) {
  margin: 6px 0;
  padding-left: 22px;
}
.md-view :deep(li) { margin: 3px 0; }
.md-view :deep(hr) {
  border: none;
  border-top: 2px dashed var(--pyt-color-border);
  margin: 12px 0;
}
.md-view :deep(img) {
  max-width: 100%;
  border-radius: 6px;
  border: 2px solid var(--pyt-color-border);
}
/* 引用角标（C2）：答案正文 [页 N] → 可 hover 上标，hover 即时弹出像素风「课件原文」浮层卡。 */
.md-view :deep(.pyt-cite) {
  position: relative;
  font-size: 0.72em;
  vertical-align: super;
  color: var(--pyt-color-text-hint);
  cursor: help;
  border-bottom: 1px dashed var(--pyt-color-text-hint);
  padding: 0 1px;
}
.md-view :deep(.pyt-cite:hover) {
  color: #3a2a14;
  background: rgba(249, 231, 188, 0.6);
}
/* 富浮层卡（CSS-only）：hover 即时显示「第 N 页 · 课件原文」，data-* 经 attr() 取值不被 marked
   解析；跳转仍由底部「课件证据」面板承担。 */
.md-view :deep(.pyt-cite:hover::after) {
  content: '📖 第 ' attr(data-page) ' 页　' attr(data-snippet);
  position: absolute;
  left: 0;
  bottom: 1.7em;
  z-index: 20;
  width: max-content;
  max-width: 280px;
  padding: 8px 10px;
  font-family: var(--pyt-font-read);
  font-size: 12px;
  font-weight: 400;
  line-height: 1.55;
  white-space: normal;
  text-align: left;
  color: var(--pyt-color-text-primary);
  background: #fff8df;
  border: 2px solid var(--pyt-color-border);
  border-radius: 6px;
  box-shadow: 2px 2px 0 rgba(140, 106, 35, 0.35);
  pointer-events: none;
}
.md-view--inline { display: inline; }
</style>
