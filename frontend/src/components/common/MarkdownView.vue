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

const rendered = computed(() => {
  if (!props.source) return '';
  const raw = props.inline
    ? marked.parseInline(props.source) as string
    : marked.parse(props.source) as string;
  return DOMPurify.sanitize(raw, PURIFY_CONFIG);
});
</script>

<template>
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
</template>

<style scoped>
.md-view {
  font-family: var(--pyt-font-pixel, monospace);
  font-size: 14px;
  line-height: 1.7;
  color: #3a2a14;
  word-break: break-word;
}
.md-view :deep(h1),
.md-view :deep(h2),
.md-view :deep(h3),
.md-view :deep(h4) {
  color: #4d3613;
  margin: 16px 0 8px;
  line-height: 1.3;
}
.md-view :deep(h1) { font-size: 20px; }
.md-view :deep(h2) { font-size: 17px; }
.md-view :deep(h3) { font-size: 15px; }
.md-view :deep(p) { margin: 6px 0; }
.md-view :deep(a) {
  color: #6e4f1d;
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
  background: #2e2417;
  color: #f1d896;
  padding: 12px 14px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.5;
  border: 2px solid #6e4f1d;
}
.md-view :deep(pre code) {
  background: transparent;
  padding: 0;
  color: inherit;
}
.md-view :deep(blockquote) {
  margin: 8px 0;
  padding: 8px 14px;
  border-left: 4px solid #c9a86b;
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
  border: 1px solid #c9a86b;
  padding: 6px 10px;
  text-align: left;
}
.md-view :deep(th) {
  background: #f1d896;
  font-weight: 700;
  color: #4d3613;
}
.md-view :deep(ul),
.md-view :deep(ol) {
  margin: 6px 0;
  padding-left: 22px;
}
.md-view :deep(li) { margin: 3px 0; }
.md-view :deep(hr) {
  border: none;
  border-top: 2px dashed #c9a86b;
  margin: 12px 0;
}
.md-view :deep(img) {
  max-width: 100%;
  border-radius: 6px;
  border: 2px solid #c9a86b;
}
.md-view--inline { display: inline; }
</style>
