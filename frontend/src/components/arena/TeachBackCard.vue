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
  <div class="teach-back-card" data-testid="teach-back-card">
    <header class="card-head">
      <span class="card-icon">🎙</span>
      <h3>教学反转</h3>
      <span class="badge-teach">向 NPC 解释概念</span>
    </header>

    <div class="scenario">
      <div class="npc-bubble">
        <img src="/assets/pixel/portraits/heads/portrait_student_anxious_head.png" alt="NPC" class="npc-avatar" @error="($event.target as HTMLImageElement).style.display='none'">
        <div class="bubble-text">
          <p class="npc-says">"同学，我不太理解这个概念，你能用简单的话给我解释一下吗？"</p>
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
      <button type="button" class="tips-btn" @click="showTips = !showTips">
        {{ showTips ? '收起提示' : '写作提示' }}
      </button>
    </div>

    <ul v-if="showTips" class="tips-list">
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
.card-head h3 { margin: 0; font-size: 16px; color: #ffd166; }
.badge-teach { margin-left: auto; font-size: 11px; color: #c4b5fd; padding: 2px 8px; background: rgba(196,181,253,0.15); border: 1px solid #8b5cf6; border-radius: 3px; }
.scenario { padding: 12px; background: rgba(10,20,40,0.5); border: 2px solid #4a90e2; border-radius: 6px; }
.npc-bubble { display: flex; gap: 12px; align-items: flex-start; }
.npc-avatar { width: 48px; height: 48px; image-rendering: pixelated; border: 2px solid #d4af37; border-radius: 4px; flex-shrink: 0; }
.bubble-text { flex: 1; }
.npc-says { margin: 0 0 8px; font-style: italic; color: #93c5fd; font-size: 13px; }
.concept-prompt { margin: 0; padding: 8px; background: #0a1428; border: 1px solid #4a90e2; white-space: pre-wrap; font-size: 13px; color: #e5e7eb; }
.teach-editor { display: flex; flex-direction: column; gap: 4px; font-size: 12px; }
.teach-editor textarea { font-family: inherit; font-size: 14px; background: #0a1428; color: #f0f9ff; border: 2px solid #8b5cf6; padding: 10px; resize: vertical; min-height: 120px; line-height: 1.6; }
.teach-footer { display: flex; align-items: center; justify-content: space-between; }
.word-badge { font-size: 11px; padding: 2px 8px; border-radius: 3px; }
.word-badge--short { color: #ffb3b3; background: rgba(179,64,42,0.2); }
.word-badge--ok { color: #f1c453; background: rgba(241,196,83,0.15); }
.word-badge--good { color: #b8d99a; background: rgba(120,220,120,0.15); }
.tips-btn { font-family: inherit; font-size: 11px; padding: 4px 10px; background: transparent; color: #93c5fd; border: 1px solid #4a90e2; border-radius: 3px; cursor: pointer; }
.tips-list { margin: 0; padding: 0 0 0 18px; font-size: 12px; color: rgba(255,247,230,0.6); }
.tips-list li { margin: 2px 0; }
</style>
