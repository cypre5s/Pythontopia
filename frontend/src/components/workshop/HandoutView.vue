<script setup lang="ts">
import EvidenceTag from './EvidenceTag.vue';
import type { HandoutContent } from '@/api/dto/Workshop';

const props = defineProps<{
  content: HandoutContent;
}>();
</script>

<template>
  <article class="handout">
    <section
      v-for="(section, index) in props.content.sections"
      :key="index"
      class="section"
    >
      <h3>{{ section.heading }}</h3>
      <p
        v-for="(paragraph, pIndex) in section.paragraphs"
        :key="pIndex"
      >
        {{ paragraph }}
      </p>
      <pre
        v-for="(snippet, sIndex) in section.code_snippets"
        :key="`code-${sIndex}`"
      ><code>{{ snippet }}</code></pre>
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
  border: 2px solid #c9a86b;
  border-radius: 8px;
  padding: 14px;
  box-shadow: 0 4px 12px rgba(74, 53, 21, 0.12);
}
.section h3 {
  margin: 0 0 6px;
  font-size: 18px;
}
.section p {
  margin: 6px 0;
  line-height: 1.55;
}
.section pre {
  background: #2e2417;
  color: #f1e5c8;
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
}
</style>
