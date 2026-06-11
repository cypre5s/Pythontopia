<script setup lang="ts">
/**
 * StageContextTutor —— 主线 stage 内「锚定当前上下文的百晓助教问答」（轻量版）。
 *
 * 与百晓工坊同一条答疑 SSE（/api/tutor/ask），但把【当前正在看的内容】作为 context 前置：
 *   - 课件侧：context = 当前页 PPT 文本（由父级 contextProvider 懒取，翻页即变）；
 *   - 视频侧：context = 视频标题 + 本节 KC。
 * 后端 TutorAskRequest.context（「视频标题 / PPT 页 / 题面摘要」）透传 graph 增强证据检索，
 * 让答案紧扣这一页 / 这段视频，而非泛泛而答。
 *
 * 轻量：只渲染流式答案 + grounding 小标（不展开课件证据缩略图，适配窄侧栏）。
 * 复用 useSseStream，不引入新管线；contextProvider 是函数以便提交时取「此刻」的上下文。
 */
import { ref } from 'vue';
import MarkdownView from '@/components/common/MarkdownView.vue';
import { useSseStream } from '@/composables/useSseStream';

interface TutorAskResult {
  answer_md?: string;
  grounding_result?: { passed: boolean; reasons: string[] };
  based_on_general_knowledge?: boolean;
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
const answer = ref('');
const grounding = ref<'PASSED' | 'NO_EVIDENCE' | 'FAILED' | ''>('');
const busy = ref(false);
const errorMsg = ref('');

const GROUNDING_LABEL: Record<string, { text: string; cls: string }> = {
  PASSED: { text: '引用充分', cls: 'sct__g--passed' },
  NO_EVIDENCE: { text: '通识知识', cls: 'sct__g--general' },
  FAILED: { text: '未通过', cls: 'sct__g--failed' },
};

async function ask(): Promise<void> {
  const q = question.value.trim();
  if (!q || busy.value) return;
  busy.value = true;
  answer.value = '';
  grounding.value = '';
  errorMsg.value = '';
  let context = '';
  try {
    context = (await props.contextProvider()) || '';
  } catch {
    context = '';
  }
  await sse.start({ question: q, kcCodes: props.kcCodes, context });
  if (sse.status.value === 'done' && sse.result.value) {
    const r = sse.result.value;
    answer.value = r.answer_md || sse.tokens.value;
    grounding.value = r.based_on_general_knowledge
      ? 'NO_EVIDENCE'
      : r.grounding_result && r.grounding_result.passed === false
        ? 'FAILED'
        : 'PASSED';
  } else {
    errorMsg.value = sse.error.value?.message ?? '助教暂时没答上来，请重试。';
  }
  busy.value = false;
}
</script>

<template>
  <section
    class="sct"
    data-testid="stage-context-tutor"
  >
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
        {{ busy ? '思考中…' : '提问' }}
      </button>
    </form>

    <div
      v-if="busy"
      class="sct__loading"
      data-testid="stage-context-tutor-loading"
    >
      <span class="sct__token">{{ sse.tokens.value.slice(-120) }}</span><span class="sct__cursor">▌</span>
    </div>

    <div
      v-else-if="answer"
      class="sct__answer"
      data-testid="stage-context-tutor-answer"
    >
      <span
        v-if="grounding"
        class="sct__g"
        :class="GROUNDING_LABEL[grounding]?.cls"
      >{{ GROUNDING_LABEL[grounding]?.text }}</span>
      <MarkdownView :source="answer" />
    </div>

    <p
      v-else-if="errorMsg"
      class="sct__error"
      data-testid="stage-context-tutor-error"
    >
      {{ errorMsg }}
    </p>
    <p
      v-else
      class="sct__hint"
    >
      就这{{ scopeLabel }}有疑问？直接问，助教会结合此处内容与课件作答。
    </p>
  </section>
</template>

<style scoped>
.sct {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-2);
  background: var(--pyt-color-bg-soft);
  border: 2px dashed var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
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
  background: var(--pyt-color-bg-primary);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-text-primary);
}
.sct__submit {
  flex: 0 0 auto;
  font-family: var(--pyt-font-pixel), monospace;
  font-size: var(--pyt-font-size-xs);
  padding: 4px 12px;
  background: var(--pyt-color-info-bg);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
}
.sct__submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.sct__loading {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
  line-height: 1.5;
  word-break: break-word;
  min-height: 1.5em;
}
.sct__cursor {
  color: var(--pyt-color-gold);
  font-weight: 700;
  animation: sct-blink 0.8s steps(2) infinite;
}
.sct__answer {
  font-size: var(--pyt-font-size-sm);
  line-height: 1.6;
  color: var(--pyt-color-text-primary);
}
.sct__g {
  display: inline-block;
  margin-bottom: var(--pyt-space-1);
  padding: 1px 8px;
  border-radius: 4px;
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
  margin: 0;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
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
