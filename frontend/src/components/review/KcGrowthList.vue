<script setup lang="ts">
import MarkdownView from '@/components/common/MarkdownView.vue';
import type { EvaluationDto } from '@/api/dto/Evaluation';

defineProps<{ evaluation: EvaluationDto }>();
</script>

<template>
  <section
    class="kc-growth"
    data-testid="kc-growth"
  >
    <header class="header">
      <h4>narrative 摘要</h4>
      <p class="hint">
        narrative 中的所有数字与 KC 均由后端核对，未在 metrics / mastery 表内的内容会被自动拒绝。
      </p>
    </header>
    <div
      v-if="evaluation.narrativeSummary"
      class="narrative"
    >
      <MarkdownView :source="evaluation.narrativeSummary" />
    </div>
    <p
      v-else
      class="empty"
    >
      本次评估未生成 narrative 文本（评估周期内无数据）。
    </p>
  </section>
</template>

<style scoped>
.kc-growth {
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 12px;
  display: grid;
  gap: 10px;
}
.header h4 {
  margin: 0;
  color: var(--pyt-color-text-primary);
}
.hint {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--pyt-color-text-muted);
}
.narrative :deep(.md-view) {
  background: var(--pyt-color-bg-soft);
  border: 1px dashed var(--pyt-color-border);
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 13px;
}
.empty {
  margin: 0;
  font-size: 12px;
  color: var(--pyt-color-text-muted);
}
</style>
