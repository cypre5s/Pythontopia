<script setup lang="ts">
import { ref, computed } from 'vue';
import type { BossProblemDto } from '@/api/dto/Boss';

const props = defineProps<{ problem: BossProblemDto }>();
const code = defineModel<string>('code', { required: true });

const buggyCode = computed(() => props.problem.statement ?? '');
const lines = computed(() => buggyCode.value.split('\n'));
const selectedLine = ref<number | null>(null);

function selectLine(idx: number): void {
  selectedLine.value = idx;
}
</script>

<template>
  <div
    class="debug-card"
    data-testid="debug-card"
  >
    <header class="card-head">
      <span class="card-icon">⚠</span>
      <h3>改错题</h3>
      <span class="badge-bug">找到并修复 Bug</span>
    </header>
    <div class="buggy-code">
      <button
        v-for="(line, idx) in lines"
        :key="idx"
        type="button"
        class="code-line"
        :class="{ 'code-line--selected': selectedLine === idx }"
        :aria-pressed="selectedLine === idx"
        :aria-label="`第 ${idx + 1} 行：标记为可疑`"
        @click="selectLine(idx)"
      >
        <span class="line-num">{{ idx + 1 }}</span>
        <span class="line-text">{{ line }}</span>
        <span
          v-if="selectedLine === idx"
          class="bug-marker"
        >&lt;- BUG?</span>
      </button>
    </div>
    <label class="fix-editor">
      <span>修复后的完整代码</span>
      <textarea
        v-model="code"
        data-testid="debug-fix"
        rows="8"
        placeholder="# 将修复后的完整代码写在这里"
        spellcheck="false"
      />
    </label>
    <p class="hint">
      点击可疑行标记，在下方写出修复后的完整代码。
    </p>
  </div>
</template>

<style scoped>
.debug-card { display: grid; gap: 12px; }
.card-head { display: flex; align-items: center; gap: 8px; }
.card-icon { font-size: 20px; }
.card-head h3 { margin: 0; font-size: 16px; color: var(--pyt-color-gold-bright); }
.badge-bug { margin-left: auto; font-size: 11px; color: var(--pyt-color-danger); padding: 2px 8px; background: var(--pyt-color-danger-bg); border: 1px solid var(--pyt-color-danger); border-radius: 3px; }
.buggy-code { background: var(--pyt-color-terminal-bg); border: 2px solid var(--pyt-color-danger); padding: 8px; font-family: 'JetBrains Mono', monospace; font-size: 13px; }
.code-line { display: flex; align-items: center; gap: 8px; width: 100%; padding: 2px 4px; cursor: pointer; border: none; border-left: 3px solid transparent; border-radius: 2px; background: transparent; color: var(--pyt-color-terminal-text); font: inherit; text-align: left; transition: background 0.15s; }
.code-line:hover { background: var(--pyt-color-danger-bg); }
.code-line:focus-visible { outline: 2px solid var(--pyt-color-gold); outline-offset: -2px; }
.code-line--selected { background: var(--pyt-color-danger-bg); border-left-color: var(--pyt-color-terminal-stderr-text); }
.line-num { color: var(--pyt-color-gold); min-width: 24px; text-align: right; font-size: 11px; user-select: none; }
.line-text { white-space: pre; color: var(--pyt-color-terminal-text); }
.bug-marker { color: var(--pyt-color-terminal-stderr-text); font-size: 12px; margin-left: auto; }
.fix-editor { display: flex; flex-direction: column; gap: 4px; font-size: 12px; }
.fix-editor textarea { font-family: 'JetBrains Mono', monospace; font-size: 13px; background: var(--pyt-color-terminal-bg); color: var(--pyt-color-terminal-text); border: 2px solid var(--pyt-color-gold); padding: 8px; resize: vertical; min-height: 120px; }
.hint { font-size: 12px; color: var(--pyt-color-text-muted); margin: 0; }
</style>
