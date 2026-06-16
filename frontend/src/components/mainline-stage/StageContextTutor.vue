<script setup lang="ts">
/**
 * StageContextTutor —— 主线 stage 内「锚定当前上下文的百晓助教问答」（聊天面板版）。
 *
 * 与百晓工坊同一条答疑 SSE（/api/tutor/ask），但把【当前正在看的内容】作为 context 前置：
 *   - 课件侧：context = 当前页 PPT 文本（由父级 contextProvider 懒取，翻页即变）；
 *   - 视频侧：context = 视频标题 + 本节 KC。
 * 后端 TutorAskRequest.context（「视频标题 / PPT 页 / 题面摘要」）透传 graph 增强证据检索，
 * 让答案紧扣这一页 / 这段视频，而非泛泛而答。
 *
 * R-15 美化：从「单次问答」升级为聊天面板——保留本次会话内的多轮问答历史（气泡流），
 * 输入框固定在底部，消息区随父容器伸缩（父级用 flex 给高度即可占满收纳省出的空间）。
 * 复用 useSseStream，不引入新管线；contextProvider 是函数以便提交时取「此刻」的上下文。
 */
import { nextTick, ref } from 'vue';
import MarkdownView from '@/components/common/MarkdownView.vue';
import { useSseStream } from '@/composables/useSseStream';

interface TutorAskResult {
  answer_md?: string;
  grounding_result?: { passed: boolean; reasons: string[] };
  based_on_general_knowledge?: boolean;
}

/** 一轮已完成的问答（user 提问 + 助教回答 + grounding 小标）。 */
interface TutorTurn {
  question: string;
  answer: string;
  grounding: 'PASSED' | 'NO_EVIDENCE' | 'FAILED' | '';
}

const props = withDefaults(
  defineProps<{
    /** 本节知识点（锚定课件 RAG 检索范围）。 */
    kcCodes: string[];
    /** 输入框占位与提示用的简短情境名（如「这一页课件」「这段视频」）。 */
    scopeLabel?: string;
    /**
     * 取「此刻」上下文文本（当前页 PPT 文本 / 视频标题+KC）。
     * 函数形态：提交问答时调用，确保拿到的是当前页（翻页后变化），可异步。
     */
    contextProvider: () => string | Promise<string>;
  }>(),
  { scopeLabel: '当前内容' },
);

const sse = useSseStream<TutorAskResult>('/api/tutor/ask');

const question = ref('');
const turns = ref<TutorTurn[]>([]);
/** 流式回答期间显示的提问气泡（完成后并入 turns）。 */
const pendingQuestion = ref('');
const busy = ref(false);
const errorMsg = ref('');
const listEl = ref<HTMLElement | null>(null);

const GROUNDING_LABEL: Record<string, { text: string; cls: string }> = {
  PASSED: { text: '引用充分', cls: 'sct__g--passed' },
  NO_EVIDENCE: { text: '通识知识', cls: 'sct__g--general' },
  FAILED: { text: '未通过', cls: 'sct__g--failed' },
};

function scrollToBottom(): void {
  void nextTick(() => {
    const el = listEl.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

async function ask(): Promise<void> {
  const q = question.value.trim();
  if (!q || busy.value) return;
  busy.value = true;
  errorMsg.value = '';
  pendingQuestion.value = q;
  question.value = '';
  scrollToBottom();
  let context = '';
  try {
    context = (await props.contextProvider()) || '';
  } catch {
    context = '';
  }
  await sse.start({ question: q, kcCodes: props.kcCodes, context });
  if (sse.status.value === 'done' && sse.result.value) {
    const r = sse.result.value;
    turns.value = [
      ...turns.value,
      {
        question: q,
        answer: r.answer_md || sse.tokens.value,
        grounding: r.based_on_general_knowledge
          ? 'NO_EVIDENCE'
          : r.grounding_result && r.grounding_result.passed === false
            ? 'FAILED'
            : 'PASSED',
      },
    ];
  } else {
    errorMsg.value = sse.error.value?.message ?? '助教暂时没答上来，请重试。';
    // 失败把问题放回输入框，方便直接重试。
    question.value = q;
  }
  pendingQuestion.value = '';
  busy.value = false;
  scrollToBottom();
}
</script>

<template>
  <section
    class="sct"
    data-testid="stage-context-tutor"
  >
    <header class="sct__head">
      <span
        class="sct__head-icon"
        aria-hidden="true"
      >💬</span>
      <span class="sct__head-title">问百晓助教</span>
      <span class="sct__head-scope">基于{{ scopeLabel }}作答</span>
    </header>

    <div
      ref="listEl"
      class="sct__messages"
    >
      <p
        v-if="turns.length === 0 && !busy && !errorMsg"
        class="sct__hint"
      >
        就这{{ scopeLabel }}有疑问？直接问，助教会结合此处内容与课件作答。
      </p>

      <template
        v-for="(t, i) in turns"
        :key="i"
      >
        <div class="sct__msg sct__msg--user">
          <span class="sct__bubble sct__bubble--user">{{ t.question }}</span>
        </div>
        <div class="sct__msg sct__msg--tutor">
          <div
            class="sct__bubble sct__bubble--tutor"
            :data-testid="i === turns.length - 1 ? 'stage-context-tutor-answer' : undefined"
          >
            <span
              v-if="t.grounding"
              class="sct__g"
              :class="GROUNDING_LABEL[t.grounding]?.cls"
            >{{ GROUNDING_LABEL[t.grounding]?.text }}</span>
            <MarkdownView :source="t.answer" />
          </div>
        </div>
      </template>

      <template v-if="busy">
        <div class="sct__msg sct__msg--user">
          <span class="sct__bubble sct__bubble--user">{{ pendingQuestion }}</span>
        </div>
        <div
          class="sct__msg sct__msg--tutor"
          data-testid="stage-context-tutor-loading"
        >
          <span class="sct__bubble sct__bubble--tutor sct__bubble--stream">
            {{ sse.tokens.value.slice(-160) || '正在思考' }}<span class="sct__cursor">▌</span>
          </span>
        </div>
      </template>

      <p
        v-if="errorMsg"
        class="sct__error"
        data-testid="stage-context-tutor-error"
      >
        {{ errorMsg }}
      </p>
    </div>

    <form
      class="sct__form"
      @submit.prevent="ask"
    >
      <input
        v-model="question"
        class="sct__input"
        type="text"
        :placeholder="`问百晓助教（基于${scopeLabel}回答）…`"
        data-testid="stage-context-tutor-input"
      >
      <button
        type="submit"
        class="sct__submit"
        :disabled="busy || !question.trim()"
        data-testid="stage-context-tutor-submit"
      >
        {{ busy ? '思考中…' : '发送' }}
      </button>
    </form>
  </section>
</template>

<style scoped>
.sct {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  min-height: 0;
  padding: var(--pyt-space-2);
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
}
.sct__head {
  display: flex;
  align-items: baseline;
  gap: var(--pyt-space-2);
  padding-bottom: var(--pyt-space-1);
  border-bottom: 1px dashed var(--pyt-color-border);
}
.sct__head-icon {
  font-size: var(--pyt-font-size-sm);
}
.sct__head-title {
  font-family: var(--pyt-font-pixel), sans-serif;
  font-size: var(--pyt-font-size-sm);
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.sct__head-scope {
  margin-left: auto;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
  white-space: nowrap;
}
.sct__messages {
  flex: 1 1 auto;
  min-height: 96px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  padding-right: 2px;
}
.sct__msg {
  display: flex;
}
.sct__msg--user {
  justify-content: flex-end;
}
.sct__msg--tutor {
  justify-content: flex-start;
}
.sct__bubble {
  max-width: 92%;
  padding: var(--pyt-space-1) var(--pyt-space-2);
  border-radius: var(--pyt-radius-md);
  font-size: var(--pyt-font-size-sm);
  line-height: 1.6;
  word-break: break-word;
}
.sct__bubble--user {
  background: var(--pyt-color-warning-bg);
  border: 1px solid var(--pyt-color-gold);
  color: var(--pyt-color-text-primary);
}
.sct__bubble--tutor {
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-read), sans-serif;
}
.sct__bubble--stream {
  color: var(--pyt-color-text-muted);
  font-size: var(--pyt-font-size-xs);
}
.sct__cursor {
  color: var(--pyt-color-gold);
  font-weight: 700;
  animation: sct-blink 0.8s steps(2) infinite;
}
.sct__g {
  display: inline-block;
  margin-bottom: var(--pyt-space-1);
  padding: 0 6px;
  border-radius: var(--pyt-radius-sm);
  font-size: var(--pyt-font-size-xs);
  font-weight: 700;
}
.sct__g--passed {
  background: var(--pyt-color-success-bg);
  color: var(--pyt-color-success);
  border: 1px solid var(--pyt-color-success);
}
.sct__g--general {
  background: var(--pyt-color-warning-bg);
  color: var(--pyt-color-text-muted);
  border: 1px solid var(--pyt-color-gold);
}
.sct__g--failed {
  background: var(--pyt-color-danger-bg, #f8d7d0);
  color: var(--pyt-color-danger);
  border: 1px solid var(--pyt-color-danger);
}
.sct__error {
  margin: 0;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-danger);
}
.sct__hint {
  margin: auto 0;
  text-align: center;
  font-size: var(--pyt-font-size-xs);
  line-height: 1.6;
  color: var(--pyt-color-text-muted);
}
.sct__form {
  display: flex;
  gap: var(--pyt-space-2);
}
.sct__input {
  flex: 1 1 auto;
  min-width: 0;
  padding: 4px 8px;
  font: inherit;
  font-size: var(--pyt-font-size-sm);
  background: var(--pyt-color-bg-primary);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-text-primary);
}
.sct__submit {
  flex: 0 0 auto;
  font-family: var(--pyt-font-pixel), monospace;
  font-size: var(--pyt-font-size-xs);
  min-height: 0;
  padding: 4px 12px;
  background: var(--pyt-color-info-bg);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  box-shadow: 2px 2px 0 var(--pyt-color-border-soft);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
}
.sct__submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
@keyframes sct-blink {
  0% { opacity: 1; }
  50% { opacity: 0; }
}
@media (prefers-reduced-motion: reduce) {
  .sct__cursor {
    animation: none;
  }
}
</style>
