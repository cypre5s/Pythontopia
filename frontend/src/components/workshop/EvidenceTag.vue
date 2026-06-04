<script setup lang="ts">
import type { PageRef } from '@/api/dto/Workshop';

const props = defineProps<{
  refs: PageRef[];
}>();
</script>

<template>
  <div
    v-if="props.refs.length > 0"
    class="evidence"
    data-testid="evidence-tag"
  >
    <span class="label">课件证据</span>
    <ul>
      <li
        v-for="ref in props.refs"
        :key="`${ref.page_id}-${ref.page_no}`"
      >
        第 {{ ref.page_no }} 页（page_id={{ ref.page_id }}）
      </li>
    </ul>
  </div>
  <div
    v-else
    class="evidence no-evidence"
  >
    基于通识知识 · 无明确课件引用
  </div>
</template>

<style scoped>
.evidence {
  margin-top: 8px;
  padding: 8px 10px;
  background: #fff2c9;
  border: 1px dashed var(--pyt-color-text-hint);
  border-radius: 6px;
  font-size: 12px;
  color: var(--pyt-color-text-primary);
}
.label {
  font-weight: 600;
  margin-right: 6px;
}
.evidence ul {
  margin: 4px 0 0;
  padding-left: 18px;
}
.no-evidence {
  background: #f4e4c1;
  font-style: italic;
}
</style>
