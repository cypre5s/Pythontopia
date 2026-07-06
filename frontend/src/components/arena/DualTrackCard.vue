<script setup lang="ts">
import { ref } from 'vue';
import type { BossProblemDto } from '@/api/dto/Boss';

defineProps<{ problem: BossProblemDto }>();
const code = defineModel<string>('code', { required: true });

const localOutput = ref<string>('');
const localError = ref<string>('');
const running = ref(false);

async function runLocal(): Promise<void> {
  running.value = true;
  localOutput.value = '';
  localError.value = '';
  try {
    const { getPyodideRunner } = await import('@/game/adapters/pyodide');
    const runner = getPyodideRunner();
    const result = await runner.run(code.value, '');
    localOutput.value = result.stdout ?? '';
    localError.value = result.stderr ?? '';
  } catch (e: unknown) {
    localError.value = e instanceof Error ? e.message : String(e);
  } finally {
    running.value = false;
  }
}
</script>

<template>
  <div
    class="dual-track-card"
    data-testid="dual-track-card"
  >
    <header class="card-head">
      <span class="card-icon">⇆</span>
      <h3>双轨判题</h3>
      <span class="badge-dual">Pyodide 预览 + 后端判</span>
    </header>
    <pre class="question">{{ problem.statement }}</pre>
    <div class="dual-panels">
      <div class="panel panel--editor">
        <h4>编辑器</h4>
        <textarea
          v-model="code"
          data-testid="dual-track-code"
          rows="10"
          placeholder="# 写 Python 代码"
          spellcheck="false"
        />
        <button
          type="button"
          class="run-btn"
          :disabled="running"
          @click="runLocal"
        >
          {{ running ? '执行中…' : '▶ 本地预览 (Pyodide)' }}
        </button>
      </div>
      <div class="panel panel--output">
        <h4>本地输出</h4>
        <pre
          v-if="localOutput"
          class="output output--stdout"
        >{{ localOutput }}</pre>
        <pre
          v-if="localError"
          class="output output--stderr"
        >{{ localError }}</pre>
        <p
          v-if="!localOutput && !localError"
          class="output-placeholder"
        >
          点击"本地预览"查看 Pyodide 运行结果
        </p>
        <p class="dual-hint">
          提交后由后端正式判题，本地预览仅供参考。
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dual-track-card { display: grid; gap: 12px; }
.card-head { display: flex; align-items: center; gap: 8px; }
.card-icon { font-size: 20px; }
.card-head h3 { margin: 0; font-size: 16px; color: var(--pyt-color-text-primary); }
.badge-dual { margin-left: auto; font-size: 11px; color: var(--pyt-color-info); padding: 2px 8px; background: var(--pyt-color-info-bg); border: 1px solid var(--pyt-color-info); border-radius: 3px; }
.question { margin: 0; padding: 10px; background: var(--pyt-color-bg-soft); border: 2px solid var(--pyt-color-border); border-radius: var(--pyt-radius-sm); white-space: pre-wrap; font-size: 13px; line-height: 1.7; color: var(--pyt-color-text-primary); }
.dual-panels { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.panel { background: var(--pyt-color-bg-soft); border: 2px solid var(--pyt-color-border); border-radius: var(--pyt-radius-sm); padding: 10px; display: flex; flex-direction: column; gap: 8px; }
.panel h4 { margin: 0; font-size: 13px; color: var(--pyt-color-info); }
.panel textarea { font-family: var(--pyt-font-mono, monospace); font-size: 13px; background: var(--pyt-color-terminal-bg); color: var(--pyt-color-terminal-text); border: 1px solid var(--pyt-color-border-strong); border-radius: var(--pyt-radius-sm); padding: 8px; resize: vertical; flex: 1; min-height: 140px; }
.run-btn { font-family: inherit; font-size: 12px; padding: 6px 12px; background: var(--pyt-color-success-bg); color: var(--pyt-color-success); border: 2px solid var(--pyt-color-success); border-radius: 3px; cursor: pointer; box-shadow: 2px 2px 0 rgba(47, 143, 60, 0.35); }
.run-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.output { margin: 0; padding: 8px; background: var(--pyt-color-terminal-bg); border: 1px solid var(--pyt-color-border-strong); border-radius: var(--pyt-radius-sm); font-size: 12px; white-space: pre-wrap; color: var(--pyt-color-terminal-text); }
.output--stdout { color: var(--pyt-color-terminal-stdout); border-color: var(--pyt-color-success); }
.output--stderr { color: var(--pyt-color-terminal-stderr-text); border-color: var(--pyt-color-danger); background: var(--pyt-color-terminal-stderr-bg); }
.output-placeholder { color: var(--pyt-color-text-muted); font-size: 12px; margin: 0; }
.dual-hint { font-size: 11px; color: var(--pyt-color-text-muted); margin: 0; margin-top: auto; }
@media (max-width: 700px) { .dual-panels { grid-template-columns: 1fr; } }
</style>
