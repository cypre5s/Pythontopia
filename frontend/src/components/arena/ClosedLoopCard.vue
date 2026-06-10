<script setup lang="ts">
import { ref } from 'vue';
import type { BossProblemDto } from '@/api/dto/Boss';

defineProps<{ problem: BossProblemDto }>();
const code = defineModel<string>('code', { required: true });

const step = ref<'read' | 'locate' | 'fix'>('read');

function advanceStep(): void {
  if (step.value === 'read') step.value = 'locate';
  else if (step.value === 'locate') step.value = 'fix';
}
</script>

<template>
  <div
    class="closed-loop-card"
    data-testid="closed-loop-card"
  >
    <header class="card-head">
      <span class="card-icon">⟲</span>
      <h3>调试闭环</h3>
      <div class="steps">
        <span :class="['step-dot', { active: step === 'read' }]">1.阅读</span>
        <span class="step-arrow">→</span>
        <span :class="['step-dot', { active: step === 'locate' }]">2.定位</span>
        <span class="step-arrow">→</span>
        <span :class="['step-dot', { active: step === 'fix' }]">3.修复</span>
      </div>
    </header>

    <div
      v-if="step === 'read'"
      class="phase-panel"
    >
      <h4>阶段 1：阅读报错信息</h4>
      <pre class="trace">{{ problem.statement }}</pre>
      <button
        type="button"
        class="advance-btn"
        @click="advanceStep"
      >
        我看懂了，进入定位 →
      </button>
    </div>

    <div
      v-else-if="step === 'locate'"
      class="phase-panel"
    >
      <h4>阶段 2：定位错误根源</h4>
      <pre class="trace trace--dim">{{ problem.statement }}</pre>
      <textarea
        v-model="code"
        class="locate-input"
        rows="3"
        placeholder="写出你认为出错的行号和原因，例如：第 5 行 IndexError，因为列表只有 3 个元素…"
      />
      <button
        type="button"
        class="advance-btn"
        @click="advanceStep"
      >
        已定位，开始修复 →
      </button>
    </div>

    <div
      v-else
      class="phase-panel"
    >
      <h4>阶段 3：编写修复代码</h4>
      <textarea
        v-model="code"
        data-testid="closed-loop-fix"
        rows="10"
        placeholder="# 写修复后的完整代码"
        spellcheck="false"
        class="fix-textarea"
      />
    </div>
  </div>
</template>

<style scoped>
.closed-loop-card { display: grid; gap: 12px; }
.card-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.card-icon { font-size: 20px; }
.card-head h3 { margin: 0; font-size: 16px; color: var(--pyt-color-gold-bright); }
.steps { display: flex; align-items: center; gap: 4px; margin-left: auto; font-size: 11px; }
.step-dot { padding: 2px 8px; border-radius: 3px; background: rgba(74,144,226,0.15); color: #93c5fd; border: 1px solid transparent; }
.step-dot.active { background: rgba(241,196,83,0.3); color: var(--pyt-color-gold-bright); border-color: var(--pyt-color-gold); }
.step-arrow { color: #4a90e2; }
.phase-panel { display: grid; gap: 10px; }
.phase-panel h4 { margin: 0; color: var(--pyt-color-gold); font-size: 14px; }
.trace { margin: 0; padding: 10px; background: #0a1428; border: 2px solid #b3402a; white-space: pre-wrap; font-size: 12px; line-height: 1.6; color: #ff7466; font-family: 'JetBrains Mono', monospace; }
.trace--dim { opacity: 0.6; border-color: #4a90e2; }
.locate-input { font-family: inherit; font-size: 13px; background: #0a1428; color: #f0f9ff; border: 2px solid var(--pyt-color-gold); padding: 8px; resize: vertical; }
.advance-btn { align-self: flex-start; font-family: inherit; font-size: 12px; padding: 6px 14px; background: #1e3a8a; color: var(--pyt-color-bg-elevated); border: 2px solid #4a90e2; border-radius: 4px; cursor: pointer; }
.advance-btn:hover { background: #2a4a9e; }
.fix-textarea { font-family: 'JetBrains Mono', monospace; font-size: 13px; background: #0a1428; color: #f0f9ff; border: 2px solid #6bbf59; padding: 8px; resize: vertical; min-height: 160px; }
</style>
