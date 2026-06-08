<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import { useMainQuestStore } from '@stores/mainQuest';
import { kcDisplayName } from '@/data/kcNames';
import type { PracticeContent, ProblemSpec } from '@/api/dto/Workshop';
import type { PyodideRunResult } from '@/game/adapters/pyodide';

const props = defineProps<{
  content: PracticeContent;
}>();

const router = useRouter();

const expandedHints = ref<Record<number, boolean>>({});

function toggleHints(index: number): void {
  expandedHints.value[index] = !expandedHints.value[index];
}

type RunStatus = 'passed' | 'failed';

// 每道题独立保存草稿/运行态/输出/判定，互不干扰。
const codeDrafts = ref<Record<number, string>>({});
const running = ref<Record<number, boolean>>({});
const results = ref<Record<number, PyodideRunResult | null>>({});
const runError = ref<Record<number, string | null>>({});
const status = ref<Record<number, RunStatus | null>>({});

function onCodeInput(index: number, event: Event): void {
  codeDrafts.value[index] = (event.target as HTMLTextAreaElement).value;
}

// 本地预览的「通过」定义：跑通无错，且（有样例预期时）首条样例输出逐字匹配。
// 这只是本地自测信号，绝非正式判题结论。
function isPassing(
  problem: ProblemSpec,
  result: PyodideRunResult | null,
  lastError: string | null,
): boolean {
  if (lastError || !result || result.isError) return false;
  const expected = (problem.sample_io[0]?.output ?? '').trim();
  if (expected.length === 0) return true;
  return result.stdout.trim() === expected;
}

// 复用学习 store 的 Pyodide 即时预览 runner，严格不触碰服务端正式判题端点。
// 通过 → 推进主线「练」子任务；未通过 → 暴露「进矿洞」入口。
async function runProblem(index: number): Promise<void> {
  const problem = props.content.problems[index];
  if (!problem) return;
  const learning = useLearningStore();
  running.value[index] = true;
  runError.value[index] = null;
  status.value[index] = null;
  try {
    learning.setTrainingCode(codeDrafts.value[index] ?? '');
    const stdin = problem.sample_io[0]?.input ?? '';
    await learning.runPyodidePreview(stdin);
    const lastError = learning.training.lastError;
    const preview = learning.training.preview;
    runError.value[index] = lastError;
    results.value[index] = lastError ? null : preview;
    const passed = isPassing(problem, preview, lastError);
    status.value[index] = passed ? 'passed' : 'failed';
    if (passed) {
      const kc = problem.kc_codes[0];
      useMainQuestStore().onSubmissionPassed(kc ? { kcCode: kc } : {});
    }
  } finally {
    running.value[index] = false;
  }
}

function enterBugMine(): void {
  void router.push({ path: '/bug-mine' });
}
</script>

<template>
  <ol
    class="practice"
    data-testid="practice-list"
  >
    <li
      v-for="(problem, index) in props.content.problems"
      :key="index"
      class="problem"
    >
      <header>
        <span class="difficulty">{{ problem.difficulty }}</span>
        <h3>{{ problem.title }}</h3>
      </header>
      <p class="statement">
        {{ problem.statement_md }}
      </p>
      <details
        class="hints"
        :open="expandedHints[index] ?? false"
        @toggle="toggleHints(index)"
      >
        <summary>提示（点击展开）</summary>
        <ul>
          <li
            v-for="(hint, hIndex) in problem.hints"
            :key="hIndex"
          >
            {{ hint }}
          </li>
        </ul>
      </details>
      <section
        class="samples"
        data-testid="sample-io"
      >
        <h4>样例输入 / 输出</h4>
        <div
          v-for="(io, ioIndex) in problem.sample_io"
          :key="ioIndex"
          class="io"
        >
          <pre><code>{{ io.input }}</code></pre>
          <pre><code>{{ io.output }}</code></pre>
        </div>
      </section>
      <p class="kc">
        关联知识点: {{ problem.kc_codes.map(kcDisplayName).join('、') || '未标注' }}
      </p>
      <section class="run-block">
        <label class="code-label">
          <span>写下你的解法，在本地 Pyodide 里先跑一跑</span>
          <textarea
            :data-testid="`practice-code-${index}`"
            :value="codeDrafts[index] ?? ''"
            class="code-input"
            spellcheck="false"
            rows="6"
            placeholder="# 在这里编写 Python 代码"
            @input="onCodeInput(index, $event)"
          />
        </label>
        <div class="run-actions">
          <button
            type="button"
            class="run-btn"
            :data-testid="`practice-run-${index}`"
            :disabled="running[index] === true"
            @click="runProblem(index)"
          >
            {{ running[index] ? '运行中…' : '运行' }}
          </button>
          <button
            v-if="status[index] === 'failed'"
            type="button"
            class="mine-chip"
            :data-testid="`practice-mine-${index}`"
            @click="enterBugMine()"
          >
            进矿洞调试
          </button>
        </div>
        <details
          v-if="results[index] || runError[index]"
          :data-testid="`practice-output-${index}`"
          class="run-output"
          open
        >
          <summary>运行结果（本地 Pyodide 预览）</summary>
          <p
            class="verdict"
            :class="status[index] === 'passed' ? 'verdict--pass' : 'verdict--fail'"
          >
            {{ status[index] === 'passed' ? '本地预览通过 ✓' : '本地预览未通过 ✗' }}
          </p>
          <pre class="stdout">{{ results[index]?.stdout || '(无标准输出)' }}</pre>
          <pre
            v-if="results[index]?.stderr"
            class="stderr"
          >{{ results[index]?.stderr }}</pre>
          <pre
            v-if="runError[index]"
            class="stderr"
          >{{ runError[index] }}</pre>
          <p class="disclaimer">
            本地 Pyodide 预览 ≠ 正式判题；正式判题请到训练场提交。
          </p>
        </details>
      </section>
    </li>
  </ol>
</template>

<style scoped>
.practice {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 16px;
}
.problem {
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 14px;
}
.problem header {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.problem h3 {
  margin: 0;
  font-size: 16px;
}
.difficulty {
  background: var(--pyt-color-text-hint);
  color: var(--pyt-color-bg-elevated);
  border-radius: 999px;
  padding: 1px 8px;
  font-size: 12px;
}
.statement {
  margin: 8px 0;
  line-height: 1.55;
}
.hints summary {
  cursor: pointer;
  color: var(--pyt-color-text-hint);
  font-weight: 600;
}
.hints ul {
  margin-top: 6px;
  padding-left: 18px;
}
.samples h4 {
  margin: 12px 0 6px;
}
.io {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 8px;
}
.io pre {
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  margin: 0;
  padding: 8px;
  border-radius: 6px;
  font-size: 13px;
  overflow-x: auto;
}
.kc {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
.run-block {
  margin-top: 12px;
  display: grid;
  gap: 8px;
}
.code-label {
  display: grid;
  gap: 4px;
}
.code-label span {
  font-size: 13px;
  font-weight: 600;
  color: var(--pyt-color-text-hint);
}
.code-input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--pyt-color-border);
  border-radius: 6px;
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  font-family: 'Fira Code', 'JetBrains Mono', 'Source Code Pro', monospace;
  font-size: 13px;
  line-height: 1.55;
  resize: vertical;
}
.code-input:focus {
  outline: 2px solid var(--pyt-color-gold);
  outline-offset: 1px;
}
.run-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.run-btn {
  padding: 6px 14px;
  border: 2px solid var(--pyt-color-primary);
  background: var(--pyt-color-gold);
  color: var(--pyt-color-primary-dark);
  border-radius: 4px;
  cursor: pointer;
  font-family: var(--pyt-font-pixel);
  font-weight: 700;
}
.run-btn:hover:not(:disabled) {
  background: var(--pyt-color-gold-bright);
}
.run-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.mine-chip {
  padding: 5px 12px;
  border: 2px solid var(--pyt-color-danger);
  background: var(--pyt-color-warning-bg);
  color: var(--pyt-color-danger);
  border-radius: 999px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
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
.verdict {
  margin: 8px 0 0;
  font-size: 13px;
  font-weight: 700;
}
.verdict--pass {
  color: var(--pyt-color-success);
}
.verdict--fail {
  color: var(--pyt-color-danger);
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
.disclaimer {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
</style>
