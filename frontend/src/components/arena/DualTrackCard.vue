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
  <div class="dual-track-card" data-testid="dual-track-card">
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
        <pre v-if="localOutput" class="output output--stdout">{{ localOutput }}</pre>
        <pre v-if="localError" class="output output--stderr">{{ localError }}</pre>
        <p v-if="!localOutput && !localError" class="output-placeholder">
          点击"本地预览"查看 Pyodide 运行结果
        </p>
        <p class="dual-hint">提交后由后端正式判题，本地预览仅供参考。</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dual-track-card { display: grid; gap: 12px; }
.card-head { display: flex; align-items: center; gap: 8px; }
.card-icon { font-size: 20px; }
.card-head h3 { margin: 0; font-size: 16px; color: #ffd166; }
.badge-dual { margin-left: auto; font-size: 11px; color: #93c5fd; padding: 2px 8px; background: rgba(74,144,226,0.2); border: 1px solid #4a90e2; border-radius: 3px; }
.question { margin: 0; padding: 10px; background: #0a1428; border: 2px solid #4a90e2; white-space: pre-wrap; font-size: 13px; line-height: 1.7; color: #e5e7eb; }
.dual-panels { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.panel { background: rgba(10,20,40,0.6); border: 2px solid #4a90e2; padding: 10px; display: flex; flex-direction: column; gap: 8px; }
.panel h4 { margin: 0; font-size: 13px; color: #93c5fd; }
.panel textarea { font-family: 'JetBrains Mono', monospace; font-size: 13px; background: #0a1428; color: #f0f9ff; border: 1px solid #4a90e2; padding: 8px; resize: vertical; flex: 1; min-height: 140px; }
.run-btn { font-family: inherit; font-size: 12px; padding: 6px 12px; background: #6bbf59; color: #1f3a14; border: 2px solid #2d5b1b; border-radius: 3px; cursor: pointer; }
.run-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.output { margin: 0; padding: 8px; background: #0a1428; border: 1px solid #4a90e2; font-size: 12px; white-space: pre-wrap; }
.output--stdout { color: #b8d99a; border-color: #6bbf59; }
.output--stderr { color: #ff7466; border-color: #b3402a; }
.output-placeholder { color: rgba(255,247,230,0.4); font-size: 12px; margin: 0; }
.dual-hint { font-size: 11px; color: rgba(255,247,230,0.4); margin: 0; margin-top: auto; }
@media (max-width: 700px) { .dual-panels { grid-template-columns: 1fr; } }
</style>
