<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import { playSfx } from '@/composables/useAudio';
import { warmPyodide } from '@/game/adapters/pyodide';
import { translatePyError } from '@/utils/pyErrorHints';
import { loadCodeDraft, saveCodeDraft } from '@/composables/useCodeDraft';
import { useCodeEditorTheme } from '@/composables/useCodeEditorTheme';
import { useResizable } from '@/composables/useResizable';
import MarkdownView from '@/components/common/MarkdownView.vue';
import PyButton from '@/components/ui/PyButton.vue';
import GuideNudgeCard from '@/components/training/GuideNudgeCard.vue';
import PythonCodeEditor from '@/components/training/PythonCodeEditor.vue';
import TutorWorkflowPanel from '@/components/training/TutorWorkflowPanel.vue';
import type { WorkflowEvent } from '@/api/tutorWorkflow';

const learning = useLearningStore();
const toast = useToastStore();
const router = useRouter();
const route = useRoute();

// 进入训练场即后台预热 Pyodide，争取用户点「运行/调试」时已就绪（首次秒开）。
onMounted(() => warmPyodide());

// S1：变式会话 id 改读 store（pendingVariantSessionId），不再从 URL 取私有 id。
// variant（URL 模式标志，非私有 id）仍作变式模式「门闸」：刷新丢 store 即安全降级为普通做题，
// 残留 store 在普通做题导航（无 variant 标志）下也不会误触发变式提交。
const variantBugMineSessionId = computed<number | null>(() => learning.bugMine.pendingVariantSessionId);
const isVariantMode = computed<boolean>(() => route.query.variant === '1' && variantBugMineSessionId.value != null);

const problem = computed(() => learning.training.problem);

// 运行报错「翻译」为初学者友好提示（匹配不到则不显示，仍保留原始 stderr）。
const previewErrorHint = computed(() =>
  translatePyError(learning.training.preview?.stderr ?? learning.training.lastError),
);

// 编辑器「暗色/亮色」开关上提到头部（AI 助教左侧），与编辑器共享同一份响应式状态。
const { isLight: editorIsLight, toggle: toggleEditorTheme } = useCodeEditorTheme();

// 三栏（题干 | 代码 | AI 助手）左右可拖、（代码 | 调试）上下可拖：尺寸用 px ref 驱动 CSS 变量，
// 不直接内联 grid-template-columns，确保窄屏 media query 仍能把布局收成单列。
const problemColPx = ref(420);
const tutorColPx = ref(320);
const runtimeRowPx = ref(170);
const { onPointerDown: onDragProblem } = useResizable(problemColPx, { min: 280, max: 760, axis: 'x' });
const { onPointerDown: onDragTutor } = useResizable(tutorColPx, { min: 240, max: 600, axis: 'x', invert: true });
const { onPointerDown: onDragRuntime } = useResizable(runtimeRowPx, { min: 110, max: 460, axis: 'y', invert: true });
const layoutStyle = computed(() => ({
  '--left-col': `${problemColPx.value}px`,
  '--tutor-col': `${tutorColPx.value}px`,
}));
const solutionStyle = computed(() => ({
  '--runtime-row': `${runtimeRowPx.value}px`,
}));

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

// 代码草稿自动保存：切到某题时恢复该题草稿；编辑时去抖写入 localStorage（刷新/断网/切页不丢）。
watch(
  () => problem.value?.id ?? null,
  (id) => {
    if (id == null) return;
    const draft = loadCodeDraft(id);
    if (draft != null && draft !== learning.training.code) {
      learning.setTrainingCode(draft);
    }
  },
  { immediate: true },
);
let draftSaveTimer: number | null = null;
watch(
  () => learning.training.code,
  (code) => {
    const id = problem.value?.id;
    if (id == null) return;
    if (draftSaveTimer !== null) window.clearTimeout(draftSaveTimer);
    draftSaveTimer = window.setTimeout(() => saveCodeDraft(id, code), 500);
  },
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

// 做题页内嵌 AI 导学助手：阶段卡片式工作流（TutorWorkflowPanel），按 problemId 驱动后端
// 工作流（题目/判定/画像由后端组装，防泄答在后端 prompt 护栏），关键节点带 submission_id 感知环境。
const showTutor = ref(false);
function toggleTutor(): void {
  playSfx('sfx_click');
  showTutor.value = !showTutor.value;
}
const tutorKcCodes = computed<string[]>(() =>
  problem.value?.kcCode ? [problem.value.kcCode] : [],
);
const tutorSubmissionId = computed<number | null>(
  () => learning.training.submission?.submission.id ?? null,
);
const guideNudgeVerdict = computed<string>(
  () => learning.training.submission?.judgeResult?.verdict ?? '',
);
const tutorInitialRequest = ref<{ event: WorkflowEvent; nonce: number } | null>(null);

function onGuideNudgeContinue(event: WorkflowEvent): void {
  playSfx('sfx_click');
  showTutor.value = true;
  tutorInitialRequest.value = { event, nonce: Date.now() };
}

/** 导学助手「插入骨架」→ 写入编辑器代码。 */
function onTutorInsertCode(code: string): void {
  learning.setTrainingCode(code);
}

/** 输入输出样例「一键复制」。 */
async function copySample(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text ?? '');
    toast.show('已复制到剪贴板', 'success');
  } catch {
    toast.show('复制失败，请手动选择复制', 'error');
  }
}
</script>

<template>
  <section
    class="training-workbench"
    data-testid="training-panel"
  >
    <section
      v-if="problem"
      class="oj-layout"
      :class="{ 'oj-layout--tutor': showTutor }"
      :style="layoutStyle"
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
                  <span class="sample-title">输入 {{ i + 1 }}</span>
                  <div class="sample-box">
                    <button
                      type="button"
                      class="sample-copy"
                      title="复制输入"
                      aria-label="复制输入"
                      @click="copySample(sample.input)"
                    >
                      ⧉
                    </button>
                    <pre><code>{{ sample.input || '(空)' }}</code></pre>
                  </div>
                </div>
                <div class="sample-item">
                  <span class="sample-title">输出 {{ i + 1 }}</span>
                  <div class="sample-box">
                    <button
                      type="button"
                      class="sample-copy"
                      title="复制输出"
                      aria-label="复制输出"
                      @click="copySample(sample.output)"
                    >
                      ⧉
                    </button>
                    <pre><code>{{ sample.output || '(空)' }}</code></pre>
                  </div>
                </div>
              </div>
            </section>
          </div>
        </div>
      </aside>

      <div
        class="drag-handle drag-handle--col"
        role="separator"
        aria-orientation="vertical"
        aria-label="拖动调整题干与代码区宽度"
        title="拖动调整宽度"
        @pointerdown="onDragProblem"
      />

      <section
        id="problem-right"
        class="solution-pane"
        :style="solutionStyle"
      >
        <article class="editor-card">
          <header class="editor-card__header">
            <div>
              <h3>main.py</h3>
              <span>Python 3.11</span>
            </div>
            <div class="editor-card__actions">
              <button
                type="button"
                class="editor-theme-btn"
                :title="editorIsLight ? '切换到暗色主题' : '切换到亮色主题'"
                data-testid="training-theme-toggle"
                @click="toggleEditorTheme"
              >
                {{ editorIsLight ? '🌙 暗色' : '☀ 亮色' }}
              </button>
              <button
                type="button"
                class="ai-toggle"
                data-testid="training-tutor-toggle"
                @click="toggleTutor"
              >
                {{ showTutor ? '收起助教' : 'AI 助教' }}
              </button>
            </div>
          </header>
          <PythonCodeEditor
            :model-value="learning.training.code"
            :show-theme-toggle="false"
            @update:model-value="learning.setTrainingCode"
            @run="preview"
          />
        </article>

        <div
          class="drag-handle drag-handle--row"
          role="separator"
          aria-orientation="horizontal"
          aria-label="拖动调整代码区与调试区高度"
          title="拖动调整高度"
          @pointerdown="onDragRuntime"
        />

        <article class="runtime-card">
          <header class="runtime-card__header">
            <div>
              <h3>调试面板</h3>
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
              <div
                v-if="previewErrorHint"
                class="error-hint"
                data-testid="py-error-hint"
              >
                <strong>💡 {{ previewErrorHint.title }}</strong>
                <span>{{ previewErrorHint.hint }}</span>
              </div>
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
            <div
              v-if="showBugMineButton"
              class="submission__actions"
            >
              <PyButton
                test-id="bug-mine-button"
                variant="secondary"
                size="sm"
                @click="enterBugMine"
              >
                进入 Bug 矿洞
              </PyButton>
            </div>
          </section>
          <GuideNudgeCard
            v-if="guideNudgeVerdict"
            :verdict="guideNudgeVerdict"
            :disabled="learning.training.phase === 'submitting'"
            @continue="onGuideNudgeContinue"
          />
        </article>
      </section>

      <div
        v-if="showTutor"
        class="drag-handle drag-handle--col"
        role="separator"
        aria-orientation="vertical"
        aria-label="拖动调整 AI 助手宽度"
        title="拖动调整宽度"
        @pointerdown="onDragTutor"
      />

      <!-- AI 导学助手：阶段卡片式工作流面板（自带标题/关闭/动作链）。 -->
      <aside
        v-if="showTutor"
        class="tutor-drawer"
        data-testid="training-tutor-body"
      >
        <div class="tutor-drawer__body">
          <TutorWorkflowPanel
            :problem-id="problem.id"
            :kc-codes="tutorKcCodes"
            :submission-id="tutorSubmissionId"
            :initial-event-request="tutorInitialRequest"
            @insert-code="onTutorInsertCode"
            @close="toggleTutor"
          />
        </div>
      </aside>
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

/* 列宽由内联 CSS 变量（--left-col / --tutor-col）驱动拖拽，而非内联 grid-template，
   这样窄屏 media query 仍能把布局收成单列。中间 8px 列是可拖拽手柄。 */
.oj-layout {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-columns: var(--left-col, minmax(340px, 0.9fr)) 8px minmax(0, 1fr);
  gap: 0;
  padding: 18px;
}

/* 助教展开时追加「手柄 + AI 助手」两列（侧栏化，不遮挡编辑器/调试面板） */
.oj-layout--tutor {
  grid-template-columns:
    var(--left-col, minmax(300px, 0.85fr)) 8px minmax(0, 1fr) 8px var(--tutor-col, minmax(280px, 0.55fr));
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
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sample-title {
  font-family: var(--pyt-font-read);
  font-size: 13px;
  font-weight: 700;
}

/* LeetCode 式：复制按钮嵌在数据黑框内部右上角，悬停才显形。 */
.sample-box {
  position: relative;
}
.sample-copy {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 1;
  font-family: var(--pyt-font-pixel);
  font-size: 12px;
  line-height: 1.2;
  padding: 2px 7px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 4px;
  color: #cfe4f5;
  opacity: 0;
  transition: opacity 120ms ease, background 120ms ease;
}
.sample-box:hover .sample-copy,
.sample-copy:focus-visible {
  opacity: 1;
}
.sample-copy:hover {
  background: rgba(255, 255, 255, 0.2);
}

.sample-box pre,
.stdout,
.stderr {
  margin: 0;
  padding: 10px 12px;
  overflow: auto;
  white-space: pre-wrap;
  border-radius: 6px;
  font-family: 'Consolas', 'JetBrains Mono', 'Cascadia Code', Menlo, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.55;
}

.sample-box pre {
  min-height: 54px;
  color: var(--pyt-color-terminal-text);
  background: var(--pyt-color-terminal-bg);
}

/* 可拖拽手柄（题干|代码、代码|调试、代码|AI 助手）。 */
.drag-handle {
  position: relative;
  align-self: stretch;
  background: transparent;
  touch-action: none;
}
.drag-handle--col {
  width: 8px;
  cursor: col-resize;
}
.drag-handle--row {
  height: 8px;
  cursor: row-resize;
}
.drag-handle::before {
  content: '';
  position: absolute;
  border-radius: 999px;
  background: rgba(74, 54, 29, 0.28);
  transition: background 120ms ease;
}
.drag-handle--col::before {
  inset: 14px 2px;
}
.drag-handle--row::before {
  inset: 2px 14px;
}
.drag-handle:hover::before,
.drag-handle:active::before {
  background: var(--pyt-color-warning, #d99f3a);
}

.solution-pane {
  display: grid;
  grid-template-rows: minmax(0, 1fr) 8px var(--runtime-row, minmax(148px, 0.34fr));
  gap: 0;
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

.editor-card__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-toggle,
.editor-theme-btn,
.drawer-close {
  border: 1px solid var(--pyt-color-border);
  border-radius: 6px;
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
  font-family: var(--pyt-font-pixel);
  font-weight: 700;
}

.ai-toggle,
.editor-theme-btn {
  padding: 7px 10px;
  font-size: 12px;
}
.editor-theme-btn {
  font-weight: 600;
  color: var(--pyt-color-text-hint);
}
.editor-theme-btn:hover {
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-warning-bg);
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
  font-family: 'Consolas', 'JetBrains Mono', 'Cascadia Code', Menlo, 'Courier New', monospace;
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

.error-hint {
  flex: 0 0 auto;
  display: grid;
  gap: 3px;
  margin-top: 6px;
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid rgba(180, 35, 24, 0.3);
  background: rgba(241, 196, 83, 0.14);
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--pyt-color-text-primary);
}
.error-hint strong {
  color: var(--pyt-color-danger, #b42318);
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

.submission__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
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
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: rgba(255, 249, 234, 0.96);
  border: 2px solid var(--pyt-color-border);
  border-radius: 8px;
  box-shadow: 0 4px 0 rgba(74, 54, 29, 0.32), 0 10px 28px rgba(60, 41, 18, 0.14);
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
  .oj-layout,
  .oj-layout--tutor {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(280px, 0.9fr) minmax(440px, 1.1fr);
    grid-auto-rows: minmax(320px, auto);
    gap: 14px;
    overflow-y: auto;
  }

  /* 窄屏堆叠：隐藏拖拽手柄，调试区回到自适应两行（不再用拖拽 px 行高）。 */
  .drag-handle {
    display: none;
  }

  .solution-pane {
    grid-template-rows: minmax(280px, 0.9fr) minmax(200px, 0.5fr);
    gap: 14px;
  }

  .tutor-drawer {
    min-height: 360px;
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
