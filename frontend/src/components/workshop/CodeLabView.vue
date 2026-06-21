<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useLearningStore } from '@stores/learning';
import { usePyodideStatus } from '@/composables/usePyodideStatus';
import PixelSkeleton from '@/components/common/PixelSkeleton.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import { warmPyodide } from '@/game/adapters/pyodide';
import { translatePyError, type PyErrorHint } from '@/utils/pyErrorHints';
import type { CodeLabContent } from '@/api/dto/Workshop';
import type { PyodideRunResult } from '@/game/adapters/pyodide';

const props = defineProps<{
  content: CodeLabContent;
}>();

// Pyodide 首次加载（~10MB / 十几秒）时给出明确进度提示，避免「运行中…」久等无反馈。
const pyStatus = usePyodideStatus();

// 进入代码讲解即后台预热运行时，争取用户点「运行」时已就绪。
onMounted(() => warmPyodide());

// 把运行报错翻译成初学者友好提示（模板内按 index 取用）。
function pyHint(index: number): PyErrorHint | null {
  return translatePyError(outputs.value[index]?.stderr ?? runError.value[index] ?? null);
}

// 每个讲解独立保存运行态/输出，避免一个 walkthrough 的结果污染其它行。
const running = ref<Record<number, boolean>>({});
const outputs = ref<Record<number, PyodideRunResult | null>>({});
const runError = ref<Record<number, string | null>>({});

// 复用学习 store 现成的 Pyodide「即时预览」runner（runPyodidePreview）。
// 严格只做本地预览，绝不调用服务端正式判题端点。
async function runWalkthrough(index: number): Promise<void> {
  const walkthrough = props.content.walkthroughs[index];
  if (!walkthrough) return;
  const learning = useLearningStore();
  running.value[index] = true;
  runError.value[index] = null;
  try {
    learning.setTrainingCode(walkthrough.code);
    await learning.runPyodidePreview('');
    if (learning.training.lastError) {
      runError.value[index] = learning.training.lastError;
      outputs.value[index] = null;
    } else {
      outputs.value[index] = learning.training.preview;
    }
  } finally {
    running.value[index] = false;
  }
}
</script>

<template>
  <div class="code-lab">
    <EmptyState
      v-if="!props.content.walkthroughs.length"
      icon="🧪"
      title="暂无代码讲解"
      description="这个工坊还没有可运行的代码讲解，去其它环节继续学习吧。"
    />
    <section
      v-for="(walkthrough, index) in props.content.walkthroughs"
      :key="index"
      class="walkthrough"
    >
      <header>
        <h3>{{ walkthrough.title }}</h3>
      </header>
      <p class="narrative">
        {{ walkthrough.narrative }}
      </p>
      <pre><code>{{ walkthrough.code }}</code></pre>
      <div class="expected">
        <span>预期输出</span>
        <pre><code>{{ walkthrough.expected_output }}</code></pre>
      </div>
      <div
        v-if="walkthrough.common_mistakes.length > 0"
        class="mistakes"
      >
        <h4>常见错误</h4>
        <ul>
          <li
            v-for="(mistake, mIndex) in walkthrough.common_mistakes"
            :key="mIndex"
          >
            {{ mistake }}
          </li>
        </ul>
      </div>
      <div class="run-area">
        <button
          type="button"
          class="primary"
          :data-testid="`codelab-run-${index}`"
          :disabled="running[index] === true"
          @click="runWalkthrough(index)"
        >
          {{ running[index] ? (pyStatus === 'loading' ? '加载运行时…' : '运行中…') : '运行' }}
        </button>
        <div
          v-if="running[index] && pyStatus === 'loading'"
          class="py-loading"
          data-testid="pyodide-loading"
        >
          <span>正在加载 Python 运行时（首次约十几秒）…</span>
          <PixelSkeleton height="8px" />
        </div>
        <details
          v-if="outputs[index] || runError[index]"
          :data-testid="`codelab-output-${index}`"
          class="run-output"
          open
        >
          <summary>运行输出（本地 Pyodide 预览）</summary>
          <pre class="stdout">{{ outputs[index]?.stdout || '(无标准输出)' }}</pre>
          <pre
            v-if="outputs[index]?.stderr"
            class="stderr"
          >{{ outputs[index]?.stderr }}</pre>
          <pre
            v-if="runError[index]"
            class="stderr"
          >{{ runError[index] }}</pre>
          <div
            v-if="pyHint(index)"
            class="error-hint"
            data-testid="codelab-error-hint"
          >
            <strong>💡 {{ pyHint(index)?.title }}</strong>
            <span>{{ pyHint(index)?.hint }}</span>
          </div>
          <small
            v-if="outputs[index]"
            class="duration"
          >耗时 {{ outputs[index]?.durationMs }}ms</small>
          <p class="disclaimer">
            本地 Pyodide 预览 ≠ 正式判题，仅供调试参考。
          </p>
        </details>
      </div>
    </section>
  </div>
</template>

<style scoped>
.code-lab {
  display: grid;
  gap: 16px;
}
.walkthrough {
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 14px;
}
.narrative {
  line-height: 1.55;
}
.walkthrough pre {
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
}
.expected {
  display: grid;
  grid-template-columns: max-content 1fr;
  align-items: start;
  gap: 8px;
  margin-top: 8px;
}
.mistakes {
  margin-top: 10px;
  padding: 10px;
  background: #fff2c9;
  border-radius: 6px;
}
.run-area {
  margin-top: 12px;
  display: grid;
  gap: 10px;
}
.primary {
  justify-self: start;
  padding: 6px 14px;
  border: 2px solid var(--pyt-color-primary);
  background: var(--pyt-color-gold);
  color: var(--pyt-color-primary-dark);
  border-radius: 4px;
  cursor: pointer;
  font-family: var(--pyt-font-pixel);
  font-weight: 700;
}
.primary:hover:not(:disabled) {
  background: var(--pyt-color-gold-bright);
}
.primary:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.py-loading {
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: var(--pyt-color-text-hint);
}
.error-hint {
  display: grid;
  gap: 3px;
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid rgba(180, 35, 24, 0.3);
  background: rgba(241, 196, 83, 0.14);
  font-size: 12.5px;
  line-height: 1.6;
}
.error-hint strong {
  color: var(--pyt-color-danger, #b42318);
}
.run-output {
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: 6px;
  padding: 8px 10px;
}
.run-output summary {
  cursor: pointer;
  font-weight: 600;
  color: var(--pyt-color-text-hint);
}
.run-output .stdout,
.run-output .stderr {
  margin: 8px 0 0;
  padding: 8px 10px;
  border-radius: 6px;
  white-space: pre-wrap;
  font-size: 13px;
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
}
.run-output .stderr {
  color: var(--pyt-color-terminal-stderr-text);
}
.duration {
  display: block;
  margin-top: 6px;
  color: var(--pyt-color-text-hint);
}
.disclaimer {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
</style>
