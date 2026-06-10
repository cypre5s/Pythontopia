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
.badge-grounding { margin-left: auto; font-size: 11px; color: var(--pyt-color-success); padding: 2px 8px; background: var(--pyt-color-success-bg); border: 1px solid var(--pyt-color-success); border-radius: 3px; }
.question { margin: 0; padding: 12px; background: var(--pyt-color-terminal-bg); border: 2px solid var(--pyt-color-border-strong); white-space: pre-wrap; font-size: 14px; line-height: 1.7; color: var(--pyt-color-terminal-text); }
.answer-editor { display: flex; flex-direction: column; gap: 4px; font-size: 12px; }
.answer-editor textarea { font-family: inherit; font-size: 14px; background: var(--pyt-color-terminal-bg); color: var(--pyt-color-terminal-text); border: 2px solid var(--pyt-color-success); padding: 10px; resize: vertical; min-height: 100px; line-height: 1.6; }
.hint { font-size: 12px; color: var(--pyt-color-text-muted); margin: 0; }
</style>
