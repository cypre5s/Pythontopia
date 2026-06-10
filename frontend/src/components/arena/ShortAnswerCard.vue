<script setup lang="ts">
import type { BossProblemDto } from '@/api/dto/Boss';

defineProps<{ problem: BossProblemDto }>();
const code = defineModel<string>('code', { required: true });
</script>

<template>
  <div
    class="short-answer-card"
    data-testid="short-answer-card"
  >
    <header class="card-head">
      <span class="card-icon">✎</span>
      <h3>简答题</h3>
      <span class="badge-grounding">Grounding 评分</span>
    </header>
    <pre class="question">{{ problem.statement }}</pre>
    <label class="answer-editor">
      <span>你的回答（文字描述，AI 将对照课件评分）</span>
      <textarea
        v-model="code"
        data-testid="short-answer-text"
        rows="6"
        placeholder="用自己的话回答问题，AI 会根据课件内容评分…"
      />
    </label>
    <p class="hint">
      回答将由 AI 对照课件真实内容（packId=43）进行 grounding 评分。
    </p>
  </div>
</template>

<style scoped>
.short-answer-card { display: grid; gap: 12px; }
.card-head { display: flex; align-items: center; gap: 8px; }
.card-icon { font-size: 20px; }
.card-head h3 { margin: 0; font-size: 16px; color: var(--pyt-color-gold-bright); }
.badge-grounding { margin-left: auto; font-size: 11px; color: #b8d99a; padding: 2px 8px; background: rgba(120,220,120,0.2); border: 1px solid #6bbf59; border-radius: 3px; }
.question { margin: 0; padding: 12px; background: #0a1428; border: 2px solid #4a90e2; white-space: pre-wrap; font-size: 14px; line-height: 1.7; color: #e5e7eb; }
.answer-editor { display: flex; flex-direction: column; gap: 4px; font-size: 12px; }
.answer-editor textarea { font-family: inherit; font-size: 14px; background: #0a1428; color: #f0f9ff; border: 2px solid #6bbf59; padding: 10px; resize: vertical; min-height: 100px; line-height: 1.6; }
.hint { font-size: 12px; color: rgba(255,247,230,0.5); margin: 0; }
</style>
