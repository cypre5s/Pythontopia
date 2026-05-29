<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import { playSfx } from '@/composables/useAudio';
import TutorPanel from '@/components/tutor/TutorPanel.vue';

const learning = useLearningStore();
const toast = useToastStore();
const router = useRouter();

const problem = computed(() => learning.training.problem);
const sampleStdin = computed(() => {
  const samples = problem.value?.samples ?? [];
  const first = samples[0];
  if (!first) return '';
  return first.input;
});

async function preview(): Promise<void> {
  if (!problem.value) {
    toast.show('请先选择题目。', 'error');
    return;
  }
  await learning.runPyodidePreview(sampleStdin.value);
  if (learning.training.lastError) {
    toast.show(learning.training.lastError, 'error');
  }
}

async function submit(): Promise<void> {
  if (!problem.value) {
    toast.show('请先选择题目。', 'error');
    return;
  }
  const code = learning.training.code.trim();
  if (!code) {
    toast.show('代码不能为空。', 'error');
    return;
  }
  try {
    const detail = await learning.submitToServer({
      problemId: problem.value.id,
      code: learning.training.code,
      source: 'TRAINING',
    });
    if (!detail) {
      playSfx('sfx_error');
      toast.show(learning.training.lastError ?? '提交失败', 'error');
      return;
    }
    const verdict = detail.judgeResult?.verdict;
    if (verdict === 'AC') {
      playSfx('sfx_jingle_complete');
      toast.show('提交通过，KC 掌握度 +0.10。', 'success');
    } else if (verdict === 'CE') {
      playSfx('sfx_error');
      toast.show('代码静态检查未通过：' + (detail.judgeResult?.failureReason ?? ''), 'error');
    } else {
      playSfx('sfx_error');
      toast.show('未通过：' + verdict, 'error');
    }
  } catch (error) {
    playSfx('sfx_error');
    const message = error instanceof Error ? error.message : '提交失败';
    toast.show(message, 'error');
  }
}

const HOVER_THROTTLE_MS = 300;
let lastHoverAt = 0;
function onSampleHover(): void {
  const now = Date.now();
  if (now - lastHoverAt < HOVER_THROTTLE_MS) return;
  lastHoverAt = now;
  playSfx('sfx_hover');
}

const showBugMineButton = computed(() => {
  const submission = learning.training.submission;
  if (!submission) return false;
  return submission.submission.status === 'FAILED'
    && submission.judgeResult
    && submission.judgeResult.verdict !== 'CE';
});

async function enterBugMine(): Promise<void> {
  const submission = learning.training.submission;
  if (!submission) return;
  await router.push({
    path: '/bug-mine',
    query: { submissionId: String(submission.submission.id) },
  });
}

// ── R-12 主线 Phase 4：做题页内嵌 AI 导学助手 ──
// 复用 TutorPanel + Phase 3 的 kcCodes/context；把「当前题目 KC + 题面 + 最近判定」
// 作为上下文传入，并在 context 末尾追加防泄答提醒（与 Python 端 SAFETY_KEYWORDS +
// 系统提示「不输出完整答案代码」双重护栏，确保做题时只给思路/提示）。
const showTutor = ref(false);
function toggleTutor(): void {
  playSfx('sfx_click');
  showTutor.value = !showTutor.value;
}
const tutorKcCodes = computed<string[]>(() =>
  problem.value?.kcCode ? [problem.value.kcCode] : [],
);
const tutorContext = computed<string>(() => {
  const p = problem.value;
  if (!p) return '';
  const stmt = (p.statementMarkdown ?? '').slice(0, 600);
  const sub = learning.training.submission;
  const verdict = sub?.judgeResult?.verdict;
  const reason = sub?.judgeResult?.failureReason;
  const verdictPart = verdict
    ? `；学生最近一次提交判定：${verdict}${reason ? `（${reason}）` : ''}`
    : '';
  return (
    `学生正在做练习题《${p.title}》（KC ${p.kcCode}，难度 ${p.difficulty}）。题面：${stmt}` +
    `${verdictPart}。请只给思路、关键提示或苏格拉底式追问，` +
    `严禁给出可直接提交的完整答案代码。`
  );
});
</script>

<template>
  <section
    class="training"
    data-testid="training-panel"
  >
    <header>
      <h2>{{ problem ? problem.title : '请选择题目' }}</h2>
      <p
        v-if="problem"
        class="meta"
      >
        KC: {{ problem.kcCode }} · 难度: {{ problem.difficulty }}
      </p>
      <p class="warning">
        Pyodide 即时预览结果不入库；正式提交才会记录到 practice_submission。
      </p>
    </header>

    <section
      v-if="problem"
      class="layout"
    >
      <aside class="problem">
        <h3>题面</h3>
        <pre class="statement">{{ problem.statementMarkdown }}</pre>
        <p
          v-if="problem.inputDescription"
          class="desc"
        >
          <strong>输入：</strong>{{ problem.inputDescription }}
        </p>
        <p
          v-if="problem.outputDescription"
          class="desc"
        >
          <strong>输出：</strong>{{ problem.outputDescription }}
        </p>
        <h4>样例</h4>
        <div
          v-for="(sample, i) in problem.samples"
          :key="i"
          class="sample"
          @mouseenter="onSampleHover"
        >
          <pre><code>输入：{{ sample.input }}</code></pre>
          <pre><code>输出：{{ sample.output }}</code></pre>
        </div>
      </aside>
      <article class="editor-area">
        <h3>编辑器（Python 3.11）</h3>
        <textarea
          :value="learning.training.code"
          rows="14"
          spellcheck="false"
          data-testid="code-editor"
          @input="(event) => learning.setTrainingCode((event.target as HTMLTextAreaElement).value)"
        />
        <div class="actions">
          <button
            type="button"
            class="ghost"
            :disabled="learning.training.phase === 'previewing'"
            data-testid="preview-button"
            @click="preview"
          >
            即时预览（Pyodide）
          </button>
          <button
            type="button"
            class="primary"
            :disabled="learning.training.phase === 'submitting'"
            data-testid="submit-button"
            @click="submit"
          >
            正式提交（服务端判题）
          </button>
        </div>
        <section
          v-if="learning.training.preview"
          class="preview"
          data-testid="preview-output"
        >
          <h4>即时预览结果（不入库）</h4>
          <pre class="stdout">{{ learning.training.preview.stdout || '(无 stdout)' }}</pre>
          <pre
            v-if="learning.training.preview.stderr"
            class="stderr"
          >{{ learning.training.preview.stderr }}</pre>
          <small>耗时：{{ learning.training.preview.durationMs }}ms</small>
        </section>
        <section
          v-if="learning.training.submission"
          class="submission"
          data-testid="submission-result"
        >
          <h4>正式判题结果</h4>
          <p>
            状态：{{ learning.training.submission.submission.status }} ·
            判定：{{ learning.training.submission.judgeResult?.verdict ?? '未判' }} ·
            通过 {{ learning.training.submission.judgeResult?.passedCases ?? 0 }}/{{
              learning.training.submission.judgeResult?.totalCases ?? 0
            }}
          </p>
          <p
            v-if="learning.training.submission.judgeResult?.failureReason"
            class="reason"
          >
            {{ learning.training.submission.judgeResult.failureReason }}
          </p>
          <button
            v-if="showBugMineButton"
            type="button"
            class="mine"
            data-testid="bug-mine-button"
            @click="enterBugMine"
          >
            进入 Bug 矿洞分析
          </button>
        </section>
      </article>
    </section>

    <section
      v-if="problem"
      class="tutor-dock"
    >
      <button
        type="button"
        class="tutor-toggle"
        data-testid="training-tutor-toggle"
        @click="toggleTutor"
      >
        {{ showTutor ? '收起 AI 导学助手 ▴' : '🤖 问 AI 导学助手（带本题上下文，只给思路）▾' }}
      </button>
      <div
        v-if="showTutor"
        class="tutor-dock__body"
        data-testid="training-tutor-body"
      >
        <TutorPanel
          :kc-codes="tutorKcCodes"
          :context="tutorContext"
        />
      </div>
    </section>
  </section>
</template>

<style scoped>
.training {
  background: #fffbe9;
  border: 2px solid #c9a86b;
  border-radius: 10px;
  padding: 16px;
  display: grid;
  gap: 14px;
  color: #2b1c0b;
}
header h2 {
  margin: 0;
}
.meta {
  margin: 4px 0 0;
  color: #6e4f1d;
  font-size: 13px;
}
.warning {
  background: #ffe7b5;
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 12px;
  color: #6e4f1d;
}
.layout {
  display: grid;
  grid-template-columns: minmax(0, 340px) 1fr;
  gap: 14px;
}
@media (max-width: 1024px) {
  .layout { grid-template-columns: 1fr; }
}
.problem {
  background: #fff7e6;
  border: 1px solid #c9a86b;
  border-radius: 8px;
  padding: 12px;
  display: grid;
  gap: 8px;
}
.statement {
  white-space: pre-wrap;
  margin: 0;
  font-family: inherit;
}
.desc {
  margin: 0;
  font-size: 13px;
}
.sample {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}
.sample pre {
  background: #2e2417;
  color: #f1e5c8;
  margin: 0;
  padding: 6px;
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
}
.editor-area {
  display: grid;
  gap: 12px;
}
.editor-area textarea {
  width: 100%;
  font-family: 'Fira Code', 'Source Code Pro', monospace;
  font-size: 13px;
  padding: 10px;
  border-radius: 6px;
  border: 1px solid #c9a86b;
  background: #fffbe9;
  color: #2b1c0b;
  resize: vertical;
}
.actions {
  display: flex;
  gap: 8px;
}
.actions button {
  padding: 6px 14px;
  border-radius: 6px;
  border: 2px solid #6e4f1d;
  cursor: pointer;
  font-weight: 600;
}
.actions .primary {
  background: #6e4f1d;
  color: #fff7e6;
}
.actions .ghost {
  background: #fff7e6;
  color: #6e4f1d;
}
.actions button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.preview, .submission {
  background: #fff7e6;
  border: 1px solid #c9a86b;
  border-radius: 6px;
  padding: 10px;
  display: grid;
  gap: 6px;
}
.stdout {
  background: #2e2417;
  color: #b1e8a1;
  padding: 8px;
  border-radius: 4px;
  margin: 0;
  white-space: pre-wrap;
}
.stderr {
  background: #381b18;
  color: #ff9d8a;
  padding: 8px;
  border-radius: 4px;
  margin: 0;
  white-space: pre-wrap;
}
.reason {
  color: #b3402a;
}
.mine {
  margin-top: 8px;
  background: #4d3613;
  color: #fff7e6;
  border: 2px solid #2b1c0b;
  padding: 6px 14px;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
}
.tutor-dock {
  display: grid;
  gap: 8px;
}
.tutor-toggle {
  justify-self: start;
  padding: 8px 16px;
  border: 2px solid #6e4f1d;
  background: #ffe7b5;
  color: #6e4f1d;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
}
.tutor-toggle:hover {
  background: #ffdf9e;
}
.tutor-dock__body {
  height: 460px;
  border: 2px solid #c9a86b;
  border-radius: 8px;
  overflow: hidden;
  background: #fff7e6;
}
</style>
