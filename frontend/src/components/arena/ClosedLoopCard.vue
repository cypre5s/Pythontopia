<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import PythonCodeEditor from '@/components/training/PythonCodeEditor.vue';
import type { BossProblemDto } from '@/api/dto/Boss';

const props = defineProps<{ problem: BossProblemDto }>();
const code = defineModel<string>('code', { required: true });

type StepKey = 'read' | 'locate' | 'fix';

interface StatementBlock {
  type: 'text' | 'code';
  title: string;
  content: string;
}

const FENCE_RE = /^\s*```/;
const CODE_LABEL_RE = /^\s*【\s*(?:错误代码|出错代码|错误的代码|错误程序|有误代码|代码|参考代码|示例代码|程序代码|原代码|原始代码)\s*】\s*$/;
const SECTION_LABEL_RE = /^\s*【\s*([^】]+)\s*】\s*$/;

function stripSectionLabel(line: string, fallback: string): string {
  return SECTION_LABEL_RE.exec(line)?.[1]?.trim() || fallback;
}

const statementBlocks = computed<StatementBlock[]>(() => {
  const src = props.problem.statement ?? '';
  const out: StatementBlock[] = [];
  let buf: string[] = [];
  let type: StatementBlock['type'] = 'text';
  let title = '题目说明';
  let inFence = false;
  let inCodeSection = false;

  const flush = (): void => {
    const content = buf.join('\n').replace(/^\n+/, '').replace(/\s+$/, '');
    if (content.trim()) out.push({ type, title, content });
    buf = [];
  };

  for (const line of src.split('\n')) {
    if (FENCE_RE.test(line)) {
      flush();
      inFence = !inFence;
      inCodeSection = false;
      type = inFence ? 'code' : 'text';
      title = inFence ? '代码片段' : '题目说明';
      continue;
    }
    if (inFence) {
      buf.push(line);
      continue;
    }
    if (CODE_LABEL_RE.test(line)) {
      flush();
      type = 'code';
      title = stripSectionLabel(line, '错误代码');
      inCodeSection = true;
      continue;
    }
    const section = SECTION_LABEL_RE.exec(line);
    if (section) {
      flush();
      type = 'text';
      title = section[1]?.trim() || '题目说明';
      inCodeSection = false;
      continue;
    }
    if (inCodeSection && SECTION_LABEL_RE.test(line)) {
      flush();
      type = 'text';
      title = stripSectionLabel(line, '题目说明');
      inCodeSection = false;
      continue;
    }
    buf.push(line);
  }
  flush();
  return out.length > 0 ? out : [{ type: 'text', title: '题目说明', content: src.trim() }];
});

const activeStep = ref<StepKey>('read');
const locateNote = ref('');
const locatePassed = ref(false);
const causeFeedback = ref('');

const steps: Array<{ key: StepKey; label: string; hint: string }> = [
  { key: 'read', label: '1.阅读', hint: '读题面与错误代码' },
  { key: 'locate', label: '2.定位', hint: '写出错因并判断' },
  { key: 'fix', label: '3.修复', hint: '编辑完整代码' },
];

function isStepUnlocked(step: StepKey): boolean {
  if (step === 'read' || step === 'locate') return true;
  return locatePassed.value;
}

function goStep(step: StepKey): void {
  if (!isStepUnlocked(step)) return;
  activeStep.value = step;
}

function causeLooksValid(text: string): boolean {
  const normalized = text.trim();
  if (normalized.length < 12) return false;
  const mentionsLine = /(?:第\s*)?\d+\s*行|line\s*\d+/i.test(normalized);
  const mentionsReason = /(因为|原因|错|错误|bug|不一致|类型|字符串|数字|转换|输入|输出|range|下标|索引|边界)/i.test(normalized);
  return mentionsLine && mentionsReason;
}

function checkCause(): void {
  if (!causeLooksValid(locateNote.value)) {
    locatePassed.value = false;
    causeFeedback.value = '还差一步：请写清楚可疑行号和为什么错，例如“第 1 行因为 input 得到字符串”。';
    return;
  }
  locatePassed.value = true;
  causeFeedback.value = '定位通过：现在进入修复阶段，错因笔记会保留在右侧供你回看。';
  activeStep.value = 'fix';
}

watch(
  () => props.problem.problemId,
  () => {
    activeStep.value = 'read';
    locateNote.value = '';
    locatePassed.value = false;
    causeFeedback.value = '';
  },
);
</script>

<template>
  <div
    class="closed-loop-card"
    data-testid="closed-loop-card"
  >
    <aside
      class="cl-problem"
      data-testid="closed-loop-problem"
    >
      <header class="cl-problem__head">
        <span class="cl-kicker">题干</span>
        <h3>调试闭环</h3>
      </header>

      <section
        v-for="(block, index) in statementBlocks"
        :key="`${block.type}-${index}`"
        class="cl-block"
        :class="`cl-block--${block.type}`"
        :data-testid="block.type === 'code' ? 'closed-loop-block-code' : 'closed-loop-block-text'"
      >
        <span class="cl-block__label">{{ block.title }}</span>
        <pre
          v-if="block.type === 'code'"
          class="cl-code"
        ><code>{{ block.content }}</code></pre>
        <p
          v-else
          class="cl-prose"
          v-text="block.content"
        />
      </section>

      <section class="cl-samples">
        <div
          class="cl-sample"
          data-testid="closed-loop-sample-input"
        >
          <span class="cl-block__label">样例输入</span>
          <pre>{{ problem.sampleInput || '(空)' }}</pre>
        </div>
        <div
          class="cl-sample"
          data-testid="closed-loop-sample-output"
        >
          <span class="cl-block__label">期望输出</span>
          <pre>{{ problem.sampleOutput || '(空)' }}</pre>
        </div>
      </section>
    </aside>

    <section class="cl-work">
      <nav
        class="cl-progress"
        aria-label="调试闭环阶段"
      >
        <button
          v-for="item in steps"
          :key="item.key"
          type="button"
          class="cl-progress__step"
          :class="{ 'is-active': activeStep === item.key, 'is-locked': !isStepUnlocked(item.key) }"
          :disabled="!isStepUnlocked(item.key)"
          :aria-current="activeStep === item.key ? 'step' : undefined"
          :data-testid="`closed-loop-step-${item.key}`"
          @click="goStep(item.key)"
        >
          <strong>{{ item.label }}</strong>
          <span>{{ item.hint }}</span>
        </button>
      </nav>

      <div
        v-if="activeStep === 'read'"
        class="cl-stage"
        data-testid="closed-loop-stage-read"
      >
        <h4>先把失败现场看清楚</h4>
        <p>左侧题干已经把说明、错误代码和样例分块。先确认程序现在在做什么，再进入错因定位。</p>
        <button
          type="button"
          class="cl-action"
          @click="goStep('locate')"
        >
          我看懂了，进入定位
        </button>
      </div>

      <div
        v-else-if="activeStep === 'locate'"
        class="cl-stage"
        data-testid="closed-loop-stage-locate"
      >
        <h4>写出错因并判断</h4>
        <label class="cl-cause">
          <span>错因判断</span>
          <textarea
            v-model="locateNote"
            data-testid="closed-loop-cause"
            rows="6"
            placeholder="例如：第 1 行错误，因为 input().split() 得到的是字符串，直接相加会拼接成 23。"
          />
        </label>
        <p
          v-if="causeFeedback"
          class="cl-feedback"
          :class="{ 'is-pass': locatePassed }"
          data-testid="closed-loop-cause-feedback"
        >
          {{ causeFeedback }}
        </p>
        <button
          type="button"
          class="cl-action"
          data-testid="closed-loop-check-cause"
          @click="checkCause"
        >
          判断定位
        </button>
      </div>

      <div
        v-else
        class="cl-stage cl-stage--fix"
        data-testid="closed-loop-stage-fix"
      >
        <header class="cl-fix-head">
          <div>
            <h4>编写修复代码</h4>
            <p>这里只提交 Python 代码。错因笔记不会混进判题内容。</p>
          </div>
          <button
            type="button"
            class="cl-link"
            @click="goStep('locate')"
          >
            回看二阶段
          </button>
        </header>
        <div
          v-if="locateNote"
          class="cl-note"
        >
          <strong>错因笔记</strong>
          <span>{{ locateNote }}</span>
        </div>
        <div class="cl-editor-shell">
          <PythonCodeEditor
            v-model="code"
            :show-theme-toggle="false"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.closed-loop-card {
  min-height: min(620px, calc(100dvh - 270px));
  display: grid;
  grid-template-columns: minmax(300px, 0.92fr) minmax(420px, 1.08fr);
  gap: 14px;
}

.cl-problem,
.cl-work {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: rgba(255, 249, 234, 0.96);
  border: 2px solid var(--pyt-color-border);
  border-radius: 8px;
  box-shadow: 3px 3px 0 rgba(74, 54, 29, 0.24);
}

.cl-problem {
  gap: 10px;
  padding: 14px;
  overflow-y: auto;
}

.cl-problem__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.cl-problem__head h3,
.cl-stage h4 {
  margin: 0;
  font-family: var(--pyt-font-read);
  color: var(--pyt-color-text-primary);
}

.cl-kicker,
.cl-block__label {
  width: fit-content;
  font-size: 11px;
  color: var(--pyt-color-text-hint);
  background: rgba(241, 216, 150, 0.42);
  border: 1px solid rgba(74, 54, 29, 0.22);
  border-radius: 4px;
  padding: 3px 7px;
}

.cl-block,
.cl-sample {
  display: grid;
  gap: 6px;
}

.cl-prose,
.cl-sample pre {
  margin: 0;
  padding: 10px 12px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.72;
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border);
  border-radius: 6px;
}

.cl-code,
.cl-sample pre {
  font-family: var(--pyt-font-mono, 'Consolas', monospace);
}

.cl-code {
  margin: 0;
  padding: 12px 14px;
  overflow-x: auto;
  white-space: pre;
  font-size: 13px;
  line-height: 1.62;
  color: var(--pyt-color-terminal-text);
  background: var(--pyt-color-terminal-bg);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: 6px;
}

.cl-code code {
  font-family: inherit;
}

.cl-samples {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.cl-work {
  background: rgba(255, 252, 242, 0.96);
}

.cl-progress {
  flex: 0 0 auto;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid rgba(74, 54, 29, 0.22);
  background: rgba(255, 249, 234, 0.82);
}

.cl-progress__step {
  display: grid;
  gap: 2px;
  padding: 8px 10px;
  text-align: left;
  cursor: pointer;
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-soft);
  border: 2px solid rgba(74, 54, 29, 0.24);
  border-radius: 6px;
  font-family: inherit;
}

.cl-progress__step strong {
  font-size: 13px;
}

.cl-progress__step span {
  font-size: 10px;
  color: var(--pyt-color-text-hint);
}

.cl-progress__step.is-active {
  border-color: var(--pyt-color-warning);
  background: var(--pyt-color-warning-bg);
}

.cl-progress__step.is-locked {
  opacity: 0.46;
  cursor: not-allowed;
}

.cl-stage {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  overflow-y: auto;
}

.cl-stage p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--pyt-color-text-hint);
}

.cl-action,
.cl-link {
  width: fit-content;
  font-family: inherit;
  cursor: pointer;
  border-radius: 6px;
}

.cl-action {
  padding: 8px 14px;
  color: var(--pyt-color-info);
  background: var(--pyt-color-info-bg);
  border: 2px solid var(--pyt-color-info);
  box-shadow: 2px 2px 0 rgba(42, 111, 151, 0.28);
}

.cl-link {
  padding: 6px 10px;
  color: var(--pyt-color-text-hint);
  background: transparent;
  border: 1px dashed var(--pyt-color-border);
}

.cl-cause {
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: var(--pyt-color-text-primary);
}

.cl-cause textarea {
  min-height: 128px;
  resize: vertical;
  padding: 10px 12px;
  font-family: var(--pyt-font-read);
  font-size: 13px;
  line-height: 1.65;
  color: var(--pyt-color-terminal-text);
  background: var(--pyt-color-terminal-bg);
  border: 2px solid var(--pyt-color-gold);
  border-radius: 6px;
}

.cl-feedback {
  padding: 9px 11px;
  color: var(--pyt-color-danger);
  background: var(--pyt-color-danger-bg);
  border: 1px solid var(--pyt-color-danger);
  border-radius: 6px;
}

.cl-feedback.is-pass {
  color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
  border-color: var(--pyt-color-success);
}

.cl-stage--fix {
  overflow: hidden;
}

.cl-fix-head {
  flex: 0 0 auto;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.cl-note {
  flex: 0 0 auto;
  display: grid;
  gap: 4px;
  padding: 9px 11px;
  font-size: 12px;
  color: var(--pyt-color-text-hint);
  background: rgba(241, 216, 150, 0.28);
  border: 1px solid rgba(74, 54, 29, 0.18);
  border-radius: 6px;
}

.cl-note strong {
  color: var(--pyt-color-text-primary);
}

.cl-editor-shell {
  flex: 1 1 auto;
  min-height: 260px;
  overflow: hidden;
  border-radius: 6px;
}

.cl-editor-shell :deep(.python-code-editor) {
  height: 100%;
}

@media (max-width: 1024px) {
  .closed-loop-card {
    min-height: 0;
    grid-template-columns: 1fr;
  }

  .cl-progress {
    grid-template-columns: 1fr;
  }
}
</style>
