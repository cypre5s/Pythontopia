<script setup lang="ts">
import EvidenceTag from './EvidenceTag.vue';
import MarkdownView from '@/components/common/MarkdownView.vue';
import type { HandoutContent, HandoutSection } from '@/api/dto/Workshop';

const props = defineProps<{
  content: HandoutContent;
}>();

function sectionToMarkdown(section: HandoutSection): string {
  const parts: string[] = [];
  for (const paragraph of section.paragraphs) {
    parts.push(paragraph);
  }
  for (const snippet of section.code_snippets) {
    parts.push('```python\n' + snippet + '\n```');
  }
  return parts.join('\n\n');
}
</script>

<template>
  <article class="handout">
    <section
      v-for="(section, index) in props.content.sections"
      :key="index"
      class="section"
    >
      <h3>{{ section.heading }}</h3>
      <MarkdownView
        :source="sectionToMarkdown(section)"
        :allow-images="false"
      />
      <EvidenceTag :refs="section.page_refs" />
    </section>
  </article>
</template>

<style scoped>
.handout {
  display: flex;
  flex-direction: column;
  gap: 18px;
  font-family: 'PingFang SC', 'Noto Sans CJK SC', monospace;
  color: #2b1c0b;
}
.section {
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 14px;
  box-shadow: 0 4px 12px rgba(74, 53, 21, 0.12);
}
.section h3 {
  margin: 0 0 6px;
  font-size: 18px;
}
.section :deep(.md-view) {
  margin: 6px 0;
}
</style>
