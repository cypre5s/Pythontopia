<script setup lang="ts">
import { ref, computed } from 'vue';
import type { BossProblemDto } from '@/api/dto/Boss';

defineProps<{ problem: BossProblemDto }>();
const code = defineModel<string>('code', { required: true });

const wordCount = computed(() => {
  const text = code.value.trim();
  if (!text) return 0;
  return text.split(/\s+/).length;
});

const quality = computed<'short' | 'ok' | 'good'>(() => {
  if (wordCount.value < 20) return 'short';
  if (wordCount.value < 80) return 'ok';
  return 'good';
});

const showTips = ref(false);
</script>

<template>
  <div
    class="teach-back-card"
    data-testid="teach-back-card"
  >
    <header class="card-head">
      <span class="card-icon">[T]</span>
      <h3>教学反转</h3>
      <span class="badge-teach">向 NPC 解释概念</span>
    </header>

    <div class="scenario">
      <div class="npc-bubble">
        <img
          src="/assets/pixel/portraits/heads/portrait_student_anxious_head.png"
          alt="NPC"
          class="npc-avatar"
          @error="($event.target as HTMLImageElement).style.display='none'"
        >
        <div class="bubble-text">
          <p class="npc-says">
            "同学，我不太理解这个概念，你能用简单的话给我解释一下吗？"
          </p>
          <pre class="concept-prompt">{{ problem.statement }}</pre>
        </div>
      </div>
    </div>

    <label class="teach-editor">
      <span>你的解释（AI 将评估清晰度与准确性）</span>
      <textarea
        v-model="code"
        data-testid="teach-back-text"
        rows="8"
        placeholder="用自己的话向这位同学解释这个概念…&#10;&#10;好的解释通常包括：&#10;1. 概念定义&#10;2. 一个具体例子&#10;3. 常见误区提醒"
      />
    </label>

    <div class="teach-footer">
      <span :class="['word-badge', `word-badge--${quality}`]">
        {{ wordCount }} 字
        <template v-if="quality === 'short'">（建议再写详细些）</template>
        <template v-else-if="quality === 'ok'">（长度适中）</template>
        <template v-else>（内容充实）</template>
      </span>
      <button
        type="button"
        class="tips-btn"
        @click="showTips = !showTips"
      >
        {{ showTips ? '收起提示' : '写作提示' }}
      </button>
    </div>

    <ul
      v-if="showTips"
      class="tips-list"
    >
      <li>先用一句话概括核心含义</li>
      <li>举一个具体的代码例子</li>
      <li>提醒常见的初学者误区</li>
      <li>对比相似但不同的概念</li>
    </ul>
  </div>
</template>

<style scoped>
.teach-back-card { display: grid; gap: 12px; }
.card-head { display: flex; align-items: center; gap: 8px; }
.card-icon { font-size: 20px; }
.card-head h3 { margin: 0; font-size: 16px; color: var(--pyt-color-gold-bright); }
.badge-teach { margin-left: auto; font-size: 11px; color: var(--pyt-color-accent); padding: 2px 8px; background: var(--pyt-color-accent-bg); border: 1px solid var(--pyt-color-accent); border-radius: 3px; }
.scenario { padding: 12px; background: var(--pyt-color-bg-soft); border: 2px solid var(--pyt-color-info); border-radius: 6px; }
.npc-bubble { display: flex; gap: 12px; align-items: flex-start; }
.npc-avatar { width: 48px; height: 48px; image-rendering: pixelated; border: 2px solid var(--pyt-color-gold); border-radius: 4px; flex-shrink: 0; }
.bubble-text { flex: 1; }
.npc-says { margin: 0 0 8px; font-style: italic; color: var(--pyt-color-text-primary); font-size: 13px; }
.concept-prompt { margin: 0; padding: 8px; background: var(--pyt-color-terminal-bg); border: 1px solid var(--pyt-color-border-strong); white-space: pre-wrap; font-size: 13px; color: var(--pyt-color-terminal-text); }
.teach-editor { display: flex; flex-direction: column; gap: 4px; font-size: 12px; }
.teach-editor textarea { font-family: inherit; font-size: 14px; background: var(--pyt-color-terminal-bg); color: var(--pyt-color-terminal-text); border: 2px solid var(--pyt-color-accent); padding: 10px; resize: vertical; min-height: 120px; line-height: 1.6; }
.teach-footer { display: flex; align-items: center; justify-content: space-between; }
.word-badge { font-size: 11px; padding: 2px 8px; border-radius: 3px; }
.word-badge--short { color: var(--pyt-color-danger); background: var(--pyt-color-danger-bg); }
.word-badge--ok { color: var(--pyt-color-gold); background: var(--pyt-color-warning-bg); }
.word-badge--good { color: var(--pyt-color-success); background: var(--pyt-color-success-bg); }
.tips-btn { font-family: inherit; font-size: 11px; padding: 4px 10px; background: transparent; color: var(--pyt-color-info); border: 1px solid var(--pyt-color-info); border-radius: 3px; cursor: pointer; }
.tips-list { margin: 0; padding: 0 0 0 18px; font-size: 12px; color: var(--pyt-color-text-muted); }
.tips-list li { margin: 2px 0; }
</style>
