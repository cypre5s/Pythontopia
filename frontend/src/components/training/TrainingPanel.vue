<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import { playSfx } from '@/composables/useAudio';
import { kcDisplayName } from '@/data/kcNames';
import MarkdownView from '@/components/common/MarkdownView.vue';
import PyButton from '@/components/ui/PyButton.vue';
import PythonCodeEditor from '@/components/training/PythonCodeEditor.vue';
import TutorPanel from '@/components/tutor/TutorPanel.vue';

const learning = useLearningStore();
const toast = useToastStore();
const router = useRouter();
const route = useRoute();

// S1：变式会话 id 改读 store（pendingVariantSessionId），不再从 URL 取私有 id。
// variant（URL 模式标志，非私有 id）仍作变式模式「门闸」：刷新丢 store 即安全降级为普通做题，
// 残留 store 在普通做题导航（无 variant 标志）下也不会误触发变式提交。
const variantBugMineSessionId = computed<number | null>(() => learning.bugMine.pendingVariantSessionId);
const isVariantMode = computed<boolean>(() => route.query.variant === '1' && variantBugMineSessionId.value != null);

const problem = computed(() => learning.training.problem);

// 掌握度透明化：把本题 KC 的隐藏掌握度翻成「当前/100」直观显示。
// R-15 退役旧 mainQuest 后不再读每 KC 的主线阈值；章末测试才是达标判据（mainline 域）。
interface KcMasteryHint {
  current: number;
}
const kcMasteryHint = computed<KcMasteryHint | null>(() => {
  const kc = problem.value?.kcCode;
  if (!kc) return null;
  const snap = learning.field.masterySnapshot.find((s) => s.kcCode === kc);
  const current = Math.round((snap?.masteryScore ?? 0) * 100);
  return { current };
});
const sampleStdin = computed(() => {
  const samples = problem.value?.samples ?? [];
  const first = samples[0];
  if (!first) return '';
  return first.input;
});
const debugInput = ref('');

watch(
  sampleStdin,
  (nextInput) => {
    debugInput.value = nextInput;
  },
  { immediate: true },
);

async function preview(): Promise<void> {
  if (!problem.value) {
    toast.show('请先选择题目。', 'error');
    return;
  }
  await learning.runPyodidePreview(debugInput.value);
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
    if (isVariantMode.value && variantBugMineSessionId.value != null) {
      // submitBugMineVariant 在 PASSED 时会清空 store 的 pendingVariantSessionId，
      // 故先把会话 id 捕获到局部，供成功后回错题矿洞用。
      const variantSessionId = variantBugMineSessionId.value;
      const result = await learning.submitBugMineVariant(code, variantSessionId);
      if (!result) {
        playSfx('sfx_error');
        toast.show(learning.bugMine.lastError ?? '变式提交失败', 'error');
        return;
      }
      if (result.status === 'PASSED') {
        playSfx('sfx_jingle_complete');
        toast.show('L5 变式掌握确认通过，错题矿洞会话已关闭。', 'success');
        // S1：私有 sessionId 不写进 URL，改走 store 入口（BugMinePage consume 后加载会话）。
        learning.setPendingBugMineEntry({ sessionId: variantSessionId });
        await router.push('/bug-mine');
      } else {
        playSfx('sfx_error');
        toast.show('变式仍未通过，请继续调整。', 'error');
      }
      return;
    }

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

const judgeTone = computed<'idle' | 'success' | 'danger' | 'warning'>(() => {
  const verdict = learning.training.submission?.judgeResult?.verdict;
  if (!verdict) return 'idle';
  if (verdict === 'AC') return 'success';
  if (verdict === 'CE') return 'warning';
  return 'danger';
});

async function enterBugMine(): Promise<void> {
  const submission = learning.training.submission;
  if (!submission) return;
  // S1：私有 submissionId 不写进 URL，改走 store 入口（BugMinePage consume 后开矿洞会话）。
  learning.setPendingBugMineEntry({ submissionId: submission.submission.id });
  await router.push('/bug-mine');
}

// 做题页内嵌 AI 导学助手：复用 TutorPanel，把「当前题目 KC + 题面 + 最近判定」
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
    class="training-workbench"
    data-testid="training-panel"
  >
    <section
      v-if="problem"
      class="oj-layout"
    >
      <aside
        id="problem-left"
        class="problem-pane"
      >
        <div class="problem-card">
          <header class="problem-card__header">
            <div class="problem-title-row">
              <h2>{{ problem.title }}</h2>
              <span class="problem-id">#{{ problem.id }}</span>
            </div>
            <div class="problem-meta">
              <span>{{ kcDisplayName(problem.kcCode) }}</span>
              <span>{{ problem.kcCode }}</span>
              <span>难度 {{ problem.difficulty }}</span>
            </div>
            <p
              v-if="kcMasteryHint"
              class="mastery-hint"
              data-testid="training-mastery-hint"
              title="掌握进度：做对题目会涨，达到目标就过关"
            >
              掌握进度 <strong>{{ kcMasteryHint.current }}/100</strong>
            </p>
          </header>

          <div class="problem-content">
            <section class="statement-section">
              <MarkdownView
                class="statement-markdown"
                data-testid="problem-statement"
                :source="problem.statementMarkdown"
              />
            </section>

            <section
              v-if="problem.inputDescription"
              class="statement-section"
            >
              <h3 class="section-title">
                输入
              </h3>
              <MarkdownView
                class="statement-markdown"
                :source="problem.inputDescription"
              />
            </section>

            <section
              v-if="problem.outputDescription"
              class="statement-section"
            >
              <h3 class="section-title">
                输出
              </h3>
              <MarkdownView
                class="statement-markdown"
                :source="problem.outputDescription"
              />
            </section>

            <section class="statement-section">
              <h3 class="section-title">
                输入输出样例
              </h3>
              <div
                v-for="(sample, i) in problem.samples"
                :key="i"
                class="sample-block"
                @mouseenter="onSampleHover"
              >
                <div class="sample-item">
                  <div class="sample-title">
                    输入 {{ i + 1 }}
                  </div>
                  <pre><code>{{ sample.input || '(空)' }}</code></pre>
                </div>
                <div class="sample-item">
                  <div class="sample-title">
                    输出 {{ i + 1 }}
                  </div>
                  <pre><code>{{ sample.output || '(空)' }}</code></pre>
                </div>
              </div>
            </section>
          </div>
        </div>
      </aside>

      <div
        class="drag-handle"
        aria-hidden="true"
      />

      <section
        id="problem-right"
        class="solution-pane"
      >
        <article class="editor-card">
          <header class="editor-card__header">
            <div>
              <h3>main.py</h3>
              <span>Python 3.11</span>
            </div>
            <button
              type="button"
              class="ai-toggle"
              data-testid="training-tutor-toggle"
              @click="toggleTutor"
            >
              {{ showTutor ? '收起助教' : 'AI 助教' }}
            </button>
          </header>
          <PythonCodeEditor
            :model-value="learning.training.code"
            @update:model-value="learning.setTrainingCode"
          />
        </article>

        <article class="runtime-card">
          <header class="runtime-card__header">
            <div>
              <h3>调试面板</h3>
              <span>本机运行不记分，正式提交入库评分</span>
            </div>
            <div class="runtime-actions">
              <PyButton
                test-id="preview-button"
                variant="secondary"
                size="sm"
                :disabled="learning.training.phase === 'previewing'"
                @click="preview"
              >
                运行
              </PyButton>
              <PyButton
                test-id="submit-button"
                variant="primary"
                size="sm"
                :disabled="learning.training.phase === 'submitting'"
                @click="submit"
              >
                提交代码
              </PyButton>
            </div>
          </header>

          <div class="debug-grid">
            <label class="debug-cell">
              <span>Input</span>
              <textarea
                v-model="debugInput"
                spellcheck="false"
                data-testid="debug-input"
              />
            </label>

            <section
              class="debug-cell debug-cell--output"
              data-testid="preview-output"
            >
              <span>Output</span>
              <pre class="stdout">{{ learning.training.preview?.stdout || '(暂无运行输出)' }}</pre>
              <pre
                v-if="learning.training.preview?.stderr"
                class="stderr"
              >{{ learning.training.preview.stderr }}</pre>
              <small v-if="learning.training.preview">
                耗时 {{ learning.training.preview.durationMs }}ms
              </small>
            </section>
          </div>

          <section
            v-if="learning.training.submission"
            class="submission"
            :class="`submission--${judgeTone}`"
            data-testid="submission-result"
          >
            <div class="submission__summary">
              <strong>{{ learning.training.submission.judgeResult?.verdict ?? '未判' }}</strong>
              <span>
                {{ learning.training.submission.submission.status }} ·
                通过 {{ learning.training.submission.judgeResult?.passedCases ?? 0 }}/{{
                  learning.training.submission.judgeResult?.totalCases ?? 0
                }}
              </span>
            </div>
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
              size="sm"
              @click="enterBugMine"
            >
              进入 Bug 矿洞
            </PyButton>
          </section>
        </article>
      </section>
    </section>

    <section
      v-else
      class="empty-training"
    >
      <h2>请选择题目</h2>
      <p v-if="learning.training.lastError">
        {{ learning.training.lastError }}
      </p>
    </section>

    <aside
      v-if="problem && showTutor"
      class="tutor-drawer"
      data-testid="training-tutor-body"
    >
      <header class="tutor-drawer__header">
        <div>
          <h3>AI 导学助手</h3>
          <span>只给思路和提示</span>
        </div>
        <button
          type="button"
          class="drawer-close"
          aria-label="关闭 AI 导学助手"
          @click="toggleTutor"
        >
          ×
        </button>
      </header>
      <div class="tutor-drawer__body">
        <TutorPanel
          :kc-codes="tutorKcCodes"
          :context="tutorContext"
        />
      </div>
    </aside>
  </section>
</template>

<style scoped>
.training-workbench {
  position: relative;
  height: 100%;
  min-height: 0;
  color: var(--pyt-color-text-primary);
  background:
    linear-gradient(180deg, rgba(243, 220, 157, 0.26), rgba(122, 93, 48, 0.1)),
    var(--pyt-color-bg-primary);
}

.oj-layout {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(340px, 0.9fr) 12px minmax(520px, 1.1fr);
  gap: 0;
  padding: 18px;
}

.problem-pane,
.solution-pane {
  min-width: 0;
  min-height: 0;
}

.problem-card,
.editor-card,
.runtime-card {
  background: rgba(255, 249, 234, 0.94);
  border: 2px solid var(--pyt-color-border);
  border-radius: 8px;
  box-shadow: 0 4px 0 rgba(74, 54, 29, 0.32), 0 10px 28px rgba(60, 41, 18, 0.14);
}

.problem-card {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.problem-card__header {
  flex: 0 0 auto;
  padding: 18px 22px 12px;
  border-bottom: 1px solid rgba(74, 54, 29, 0.22);
  background: rgba(255, 252, 242, 0.78);
}

.problem-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.problem-title-row h2 {
  margin: 0;
  font-family: var(--pyt-font-read);
  font-size: 24px;
  line-height: 1.28;
}

.problem-id {
  flex: 0 0 auto;
  font-family: var(--pyt-font-pixel);
  font-size: 11px;
  color: var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: 4px;
  padding: 4px 7px;
}

.problem-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.problem-meta span,
.mastery-hint {
  font-size: 12px;
  color: var(--pyt-color-text-hint);
  background: rgba(241, 216, 150, 0.42);
  border: 1px solid rgba(74, 54, 29, 0.22);
  border-radius: 4px;
  padding: 5px 8px;
}

.mastery-hint {
  margin: 8px 0 0;
}

.mastery-hint strong {
  color: var(--pyt-color-text-primary);
}

.problem-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 22px 26px;
  scrollbar-width: thin;
  scrollbar-color: rgba(94, 69, 33, 0.45) rgba(241, 216, 150, 0.22);
}

.statement-section + .statement-section {
  margin-top: 20px;
}

.section-title {
  margin: 0 0 10px;
  font-family: var(--pyt-font-read);
  font-size: 16px;
  line-height: 1.4;
  color: var(--pyt-color-text-primary);
}

.statement-markdown {
  display: block;
}

.statement-markdown :deep(.md-view) {
  font-size: 15px;
  line-height: 1.85;
}

.sample-block {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 12px;
}

.sample-item {
  min-width: 0;
}

.sample-title {
  margin-bottom: 6px;
  font-family: var(--pyt-font-read);
  font-size: 13px;
  font-weight: 700;
}

.sample-item pre,
.stdout,
.stderr {
  margin: 0;
  padding: 10px 12px;
  overflow: auto;
  white-space: pre-wrap;
  border-radius: 6px;
  font-family: 'Fira Code', 'JetBrains Mono', 'Source Code Pro', monospace;
  font-size: 13px;
  line-height: 1.55;
}

.sample-item pre {
  min-height: 54px;
  color: var(--pyt-color-terminal-text);
  background: var(--pyt-color-terminal-bg);
}

.drag-handle {
  width: 12px;
  height: calc(100% - 18px);
  align-self: center;
  position: relative;
}

.drag-handle::before {
  content: '';
  position: absolute;
  inset: 18px 4px;
  border-radius: 999px;
  background: rgba(74, 54, 29, 0.32);
  box-shadow: 0 0 0 1px rgba(255, 249, 234, 0.6);
}

.solution-pane {
  display: grid;
  grid-template-rows: minmax(430px, 1fr) minmax(148px, 0.34fr);
  gap: 12px;
}

.editor-card,
.runtime-card {
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.editor-card__header,
.runtime-card__header,
.tutor-drawer__header {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid rgba(74, 54, 29, 0.24);
  background: rgba(255, 252, 242, 0.82);
}

.editor-card__header h3,
.runtime-card__header h3,
.tutor-drawer__header h3 {
  margin: 0;
  font-family: var(--pyt-font-read);
  font-size: 16px;
}

.editor-card__header span,
.runtime-card__header span,
.tutor-drawer__header span {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
}

.editor-card :deep(.python-code-editor) {
  flex: 1;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.ai-toggle,
.drawer-close {
  border: 1px solid var(--pyt-color-border);
  border-radius: 6px;
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
  font-family: var(--pyt-font-pixel);
  font-weight: 700;
}

.ai-toggle {
  padding: 7px 10px;
  font-size: 12px;
}

.runtime-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.debug-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr);
  gap: 10px;
  padding: 10px 12px;
}

.debug-cell {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.debug-cell > span {
  flex: 0 0 auto;
  font-family: var(--pyt-font-read);
  font-size: 13px;
  font-weight: 700;
}

.debug-cell textarea {
  flex: 1;
  min-height: 0;
  resize: none;
  padding: 8px 10px;
  border: 1px solid rgba(74, 54, 29, 0.28);
  border-radius: 6px;
  background: rgba(255, 252, 242, 0.82);
  color: var(--pyt-color-text-primary);
  font-family: 'Fira Code', 'JetBrains Mono', 'Source Code Pro', monospace;
  font-size: 13px;
  line-height: 1.55;
}

.debug-cell textarea:focus {
  outline: 2px solid rgba(217, 159, 58, 0.45);
  outline-offset: 1px;
}

.debug-cell--output {
  overflow: hidden;
}

.stdout {
  flex: 1;
  min-height: 0;
  overflow: auto;
  color: var(--pyt-color-terminal-stdout);
  background: var(--pyt-color-terminal-bg);
}

.stderr {
  flex: 0 0 auto;
  max-height: 54px;
  overflow: auto;
  color: var(--pyt-color-terminal-stderr-text);
  background: var(--pyt-color-terminal-stderr-bg);
}

.debug-cell small {
  color: var(--pyt-color-text-hint);
}

.submission {
  margin: 0 12px 10px;
  padding: 8px 10px;
  border: 1px solid rgba(74, 54, 29, 0.24);
  border-radius: 6px;
  background: rgba(255, 252, 242, 0.84);
  display: grid;
  gap: 8px;
}

.submission--success {
  border-color: rgba(65, 138, 76, 0.5);
  background: rgba(229, 245, 222, 0.92);
}

.submission--danger {
  border-color: rgba(173, 69, 55, 0.5);
  background: rgba(255, 232, 226, 0.92);
}

.submission--warning {
  border-color: rgba(194, 133, 34, 0.52);
  background: rgba(255, 241, 210, 0.92);
}

.submission__summary {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 10px;
}

.submission__summary strong {
  font-size: 18px;
  font-family: var(--pyt-font-read);
}

.submission__summary span,
.reason {
  font-size: 13px;
  line-height: 1.6;
}

.reason {
  margin: 0;
  color: var(--pyt-color-danger);
}

.empty-training {
  height: 100%;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: var(--pyt-color-text-primary);
}

.empty-training h2,
.empty-training p {
  margin: 0;
}

.tutor-drawer {
  position: absolute;
  top: 18px;
  right: 18px;
  bottom: 18px;
  width: min(440px, calc(100vw - 36px));
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 30;
  background: rgba(255, 249, 234, 0.96);
  border: 2px solid var(--pyt-color-border);
  border-radius: 8px;
  box-shadow: -8px 0 28px rgba(35, 24, 12, 0.24);
}

.drawer-close {
  width: 32px;
  height: 32px;
  padding: 0;
  font-size: 20px;
  line-height: 1;
}

.tutor-drawer__body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.tutor-drawer__body :deep(.tutor-panel) {
  height: 100%;
  border: 0;
  border-radius: 0;
}

@media (max-width: 1100px) {
  .oj-layout {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(280px, 0.9fr) minmax(440px, 1.1fr);
    gap: 14px;
    overflow-y: auto;
  }

  .drag-handle {
    display: none;
  }
}

@media (max-width: 720px) {
  .oj-layout {
    padding: 10px;
  }

  .problem-title-row h2 {
    font-size: 20px;
  }

  .solution-pane {
    grid-template-rows: minmax(360px, 1fr) minmax(180px, 0.45fr);
  }

  .runtime-card__header {
    align-items: stretch;
    flex-direction: column;
  }

  .runtime-actions,
  .runtime-actions :deep(.py-btn) {
    width: 100%;
  }

  .debug-grid,
  .sample-block {
    grid-template-columns: 1fr;
  }
}
</style>
