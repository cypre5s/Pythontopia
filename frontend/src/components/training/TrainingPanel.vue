<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import { playSfx } from '@/composables/useAudio';
import { MAIN_QUESTS } from '@/data/mainQuests';
import { kcDisplayName } from '@/data/kcNames';
import PyButton from '@/components/ui/PyButton.vue';
import TutorPanel from '@/components/tutor/TutorPanel.vue';

const learning = useLearningStore();
const toast = useToastStore();
const router = useRouter();

const problem = computed(() => learning.training.problem);

// P1-c：掌握度透明化——把本题 KC 的隐藏掌握度翻成「当前/100 + 做对一题约 +20」，
// 若该 KC 是某主线 kc_mastery「测」子任务目标，再显式给出目标分与还差几题。
const MASTERY_PER_PASS = 20;
interface KcMasteryHint {
  current: number;
  target: number | null;
  remaining: number | null;
}
const kcMasteryHint = computed<KcMasteryHint | null>(() => {
  const kc = problem.value?.kcCode;
  if (!kc) return null;
  const snap = learning.field.masterySnapshot.find((s) => s.kcCode === kc);
  const current = Math.round((snap?.masteryScore ?? 0) * 100);
  let target: number | null = null;
  for (const q of MAIN_QUESTS) {
    const ev = q.evidenceRequired;
    if (ev.type === 'kc_mastery' && ev.kcCode === kc) {
      target = ev.thresholdScore;
      break;
    }
  }
  const remaining =
    target === null ? null : current >= target ? 0 : Math.ceil((target - current) / MASTERY_PER_PASS);
  return { current, target, remaining };
});
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
      toast.show('提交通过，掌握进度 +0.10', 'success');
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
        {{ kcDisplayName(problem.kcCode) }} · 难度: {{ problem.difficulty }}
      </p>
      <p
        v-if="kcMasteryHint"
        class="mastery-hint"
        data-testid="training-mastery-hint"
        title="掌握进度：做对题目会涨，达到目标就过关"
      >
        本题知识点掌握进度：<strong>{{ kcMasteryHint.current }}/100</strong>（做对一题约 +{{ MASTERY_PER_PASS }}）
        <template v-if="kcMasteryHint.target !== null">
          · 主线「测」目标 {{ kcMasteryHint.target }}
          <span v-if="kcMasteryHint.remaining">（约再做对 {{ kcMasteryHint.remaining }} 题达标）</span>
          <span v-else>· ✓ 已达标</span>
        </template>
      </p>
      <p class="warning">
        本机试跑不记分；正式提交才会评分入库
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
          <PyButton
            test-id="preview-button"
            variant="secondary"
            :disabled="learning.training.phase === 'previewing'"
            @click="preview"
          >
            本机试跑（不记分）
          </PyButton>
          <PyButton
            test-id="submit-button"
            variant="primary"
            :disabled="learning.training.phase === 'submitting'"
            @click="submit"
          >
            正式提交（评分）
          </PyButton>
        </div>
        <section
          v-if="learning.training.preview"
          class="preview"
          data-testid="preview-output"
        >
          <h4>本机试跑结果（不记分）</h4>
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
          <h4>评分结果</h4>
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
          <PyButton
            v-if="showBugMineButton"
            test-id="bug-mine-button"
            variant="secondary"
            @click="enterBugMine"
          >
            进入 Bug 矿洞分析
          </PyButton>
        </section>
      </article>
    </section>

    <section
      v-if="problem"
      class="tutor-dock"
    >
      <PyButton
        class="tutor-toggle"
        test-id="training-tutor-toggle"
        variant="secondary"
        @click="toggleTutor"
      >
        {{ showTutor ? '收起 AI 助教 ▴' : '🤖 问 AI 助教（只给思路）▾' }}
      </PyButton>
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
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  padding: 16px;
  display: grid;
  gap: 14px;
  color: var(--pyt-color-text-primary);
}
header h2 {
  margin: 0;
}
.meta {
  margin: 4px 0 0;
  color: var(--pyt-color-text-hint);
  font-size: 13px;
}
.warning {
  background: var(--pyt-color-warning-bg);
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}
.mastery-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: 6px;
  padding: 6px 10px;
}
.mastery-hint strong {
  color: var(--pyt-color-text-primary);
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
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
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
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
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
  border: 1px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-elevated);
  color: var(--pyt-color-text-primary);
  resize: vertical;
}
.actions {
  display: flex;
  gap: 8px;
}
.preview, .submission {
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  border-radius: 6px;
  padding: 10px;
  display: grid;
  gap: 6px;
}
.stdout {
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-stdout);
  padding: 8px;
  border-radius: 4px;
  margin: 0;
  white-space: pre-wrap;
}
.stderr {
  background: var(--pyt-color-terminal-stderr-bg);
  color: var(--pyt-color-terminal-stderr-text);
  padding: 8px;
  border-radius: 4px;
  margin: 0;
  white-space: pre-wrap;
}
.reason {
  color: var(--pyt-color-danger);
}
.tutor-dock {
  display: grid;
  gap: 8px;
}
.tutor-toggle {
  justify-self: start;
}
.tutor-dock__body {
  height: 460px;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  overflow: hidden;
  background: var(--pyt-color-bg-elevated);
}
</style>
