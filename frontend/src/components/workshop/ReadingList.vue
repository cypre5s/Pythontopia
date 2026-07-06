<script setup lang="ts">
import EvidenceTag from './EvidenceTag.vue';
import { kcDisplayName } from '@/data/kcNames';
import type { ReadingContent } from '@/api/dto/Workshop';

const props = defineProps<{
  content: ReadingContent;
}>();
</script>

<template>
  <ul class="reading">
    <li
      v-for="(item, index) in props.content.items"
      :key="index"
    >
      <p>{{ item.summary_md }}</p>
      <small>关联知识点: {{ item.kc_codes.map(kcDisplayName).join('、') || '未标注' }}</small>
      <EvidenceTag :refs="item.page_refs" />
    </li>
  </ul>
</template>

<style scoped>
.reading {
  list-style: none;
  padding: 0;
  display: grid;
  gap: 12px;
}
.reading li {
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 12px;
}
.reading p {
  margin: 0 0 4px;
  line-height: 1.55;
}
.reading small {
  color: var(--pyt-color-text-hint);
  font-size: 12px;
}
</style>
