<script setup lang="ts">
import type { BossProblemDto } from '@/api/dto/Boss';

defineProps<{ problem: BossProblemDto }>();
const code = defineModel<string>('code', { required: true });
</script>

<template>
  <div
    class="pdf-question-card"
    data-testid="pdf-question-card"
  >
    <header class="card-head">
      <span class="card-icon">[R]</span>
      <h3>课件问答</h3>
      <router-link
        v-if="problem.evidencePageIds && problem.evidencePageIds.length > 0"
        :to="{ name: 'library', query: { pageId: String(problem.evidencePageIds[0]) } }"
        class="pdf-link"
      >
        [PDF] 查看课件 p.{{ problem.evidencePageIds?.[0] }}
      </router-link>
    </header>

    <div
      v-if="problem.evidencePageIds && problem.evidencePageIds.length > 0"
      class="page-ref"
    >
      <span>参考页码：</span>
      <router-link
        v-for="pid in problem.evidencePageIds"
        :key="pid"
        :to="{ name: 'library', query: { pageId: String(pid) } }"
        class="page-chip"
      >
        p.{{ pid }}
      </router-link>
    </div>

    <pre class="question">{{ problem.statement }}</pre>

    <label class="answer-editor">
      <span>你的回答</span>
      <textarea
        v-model="code"
        data-testid="pdf-question-answer"
        rows="6"
        placeholder="阅读课件后回答问题…"
      />
    </label>

    <p class="hint">
      建议先点击上方链接查看对应课件页面，再作答。
    </p>
  </div>
</template>

<style scoped>
.pdf-question-card { display: grid; gap: 12px; }
.card-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.card-icon { font-size: 20px; }
.card-head h3 { margin: 0; font-size: 16px; color: var(--pyt-color-gold-bright); }
.pdf-link { margin-left: auto; font-size: 12px; color: var(--pyt-color-info); text-decoration: none; padding: 2px 10px; background: var(--pyt-color-info-bg); border: 1px solid var(--pyt-color-info); border-radius: 3px; }
.pdf-link:hover { background: #c2dcee; }
.page-ref { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; font-size: 12px; color: var(--pyt-color-text-hint); }
.page-chip { display: inline-block; padding: 2px 8px; background: var(--pyt-color-info-bg); color: var(--pyt-color-info); border: 1px solid var(--pyt-color-info); border-radius: 3px; font-size: 11px; text-decoration: none; }
.page-chip:hover { background: #c2dcee; }
.question { margin: 0; padding: 12px; background: var(--pyt-color-terminal-bg); border: 2px solid var(--pyt-color-border-strong); white-space: pre-wrap; font-size: 14px; line-height: 1.7; color: var(--pyt-color-terminal-text); }
.answer-editor { display: flex; flex-direction: column; gap: 4px; font-size: 12px; }
.answer-editor textarea { font-family: inherit; font-size: 14px; background: var(--pyt-color-terminal-bg); color: var(--pyt-color-terminal-text); border: 2px solid var(--pyt-color-gold); padding: 10px; resize: vertical; min-height: 100px; line-height: 1.6; }
.hint { font-size: 12px; color: var(--pyt-color-text-muted); margin: 0; }
</style>
