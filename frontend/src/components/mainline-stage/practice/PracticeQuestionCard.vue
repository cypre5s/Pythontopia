<script setup lang="ts">
/**
 * PracticeQuestionCard —— 小节「练」站内客观题（inline 答题卡）。
 *
 * 一个组件内部按 `question.type` 渲染 CHOICE / JUDGE / FILL：
 * - CHOICE：单选选项（按钮）；提交 answers = [选中项文本]。
 * - JUDGE：「正确 / 错误」两个选择按钮；提交 answers = ["正确" | "错误"]。
 * - FILL：blankCount 个输入框；提交 answers 按空位顺序。
 *
 * 提交调 `answerPracticeQuestion`，按返回 `correct` 显示对/错，并**无论对错**都展示
 * `correctAnswers` + `explanation` 供自检。答对后锁题（幂等，禁用再次提交）并向上
 * `emit('correct', questionId)`；答错可改答重试（清掉上一次判分横幅）。
 */
import { computed, ref } from 'vue';
import { answerPracticeQuestion } from '@/api/mainline';
import type { PracticeAnswerResultDto, StagePracticeQuestionDto } from '@/api/mainline';

const props = defineProps<{
  stageId: number;
  question: StagePracticeQuestionDto;
  /** 展示用题号（父按 ordinal 排序后传 1-based 序号）。 */
  index: number;
  /** 该题是否已答对（来自父 correctSet：初始进度或本端答对后回流）。 */
  done: boolean;
}>();

const emit = defineEmits<{ (e: 'correct', questionId: number): void }>();

const TYPE_LABEL: Record<StagePracticeQuestionDto['type'], string> = {
  CHOICE: '选择题',
  JUDGE: '判断题',
  FILL: '填空题',
};

const blankCount = computed<number>(() =>
  props.question.type === 'FILL' ? Math.max(1, props.question.blankCount) : 0,
);

const choiceIndex = ref<number | null>(null);
const judgeValue = ref<boolean | null>(null);
const blanks = ref<string[]>(
  props.question.type === 'FILL'
    ? Array.from({ length: Math.max(1, props.question.blankCount) }, () => '')
    : [],
);
const pending = ref<boolean>(false);
const result = ref<PracticeAnswerResultDto | null>(null);

/** 已答对（锁题）：父进度命中，或本端最近一次判分为对。 */
const answeredCorrect = computed<boolean>(() => props.done || result.value?.correct === true);

const canSubmit = computed<boolean>(() => {
  if (answeredCorrect.value || pending.value) return false;
  switch (props.question.type) {
    case 'CHOICE':
      return choiceIndex.value !== null;
    case 'JUDGE':
      return judgeValue.value !== null;
    case 'FILL':
      return blanks.value.slice(0, blankCount.value).every((b) => b.trim().length > 0);
    default:
      return false;
  }
});

/** 改答时清掉上一次「答错」横幅，给一个干净的重试。 */
function clearStaleWrong(): void {
  if (result.value && !result.value.correct) result.value = null;
}

function pickChoice(idx: number): void {
  if (answeredCorrect.value) return;
  clearStaleWrong();
  choiceIndex.value = idx;
}

function pickJudge(value: boolean): void {
  if (answeredCorrect.value) return;
  clearStaleWrong();
  judgeValue.value = value;
}

function onBlankInput(i: number, event: Event): void {
  if (answeredCorrect.value) return;
  clearStaleWrong();
  const target = event.target as HTMLInputElement | null;
  const next = blanks.value.slice();
  next[i] = target?.value ?? '';
  blanks.value = next;
}

function buildAnswers(): string[] {
  switch (props.question.type) {
    case 'CHOICE':
      return choiceIndex.value !== null ? [props.question.options[choiceIndex.value] ?? ''] : [];
    case 'JUDGE':
      return judgeValue.value !== null ? [judgeValue.value ? '正确' : '错误'] : [];
    case 'FILL':
      return blanks.value.slice(0, blankCount.value).map((b) => b);
    default:
      return [];
  }
}

async function submit(): Promise<void> {
  if (!canSubmit.value) return;
  pending.value = true;
  try {
    const res = await answerPracticeQuestion(props.stageId, props.question.id, buildAnswers());
    result.value = res;
    if (res.correct) emit('correct', props.question.id);
  } catch {
    // 静默：判分接口失败可改答重试（与三件套其它打点同等幂等容错）。
  } finally {
    pending.value = false;
  }
}

/** 选择题判分后高亮：命中正确答案文本则绿、选错则红。 */
function choiceClass(idx: number): Record<string, boolean> {
  const picked = choiceIndex.value === idx;
  const res = result.value;
  if (!res) return { 'pq-opt': true, 'pq-opt--picked': picked };
  const isCorrect = res.correctAnswers.includes(props.question.options[idx] ?? '\u0000');
  return {
    'pq-opt': true,
    'pq-opt--picked': picked,
    'pq-opt--correct': isCorrect,
    'pq-opt--wrong': picked && !res.correct && !isCorrect,
  };
}

function judgeClass(value: boolean): Record<string, boolean> {
  const picked = judgeValue.value === value;
  const res = result.value;
  if (!res) return { 'pq-judge__btn': true, 'pq-judge__btn--picked': picked };
  const label = value ? '正确' : '错误';
  const isCorrect = res.correctAnswers.includes(label);
  return {
    'pq-judge__btn': true,
    'pq-judge__btn--picked': picked,
    'pq-judge__btn--correct': isCorrect,
    'pq-judge__btn--wrong': picked && !res.correct && !isCorrect,
  };
}
</script>

<template>
  <section
    class="pq-card"
    :class="{ 'pq-card--done': answeredCorrect }"
    :data-testid="`practice-q-${question.id}`"
  >
    <header class="pq-card__head">
      <span class="pq-card__no">第 {{ index }} 题</span>
      <span class="pq-card__kind">{{ TYPE_LABEL[question.type] }}</span>
      <span
        v-if="question.kcCode"
        class="pq-card__kc"
      >{{ question.kcCode }}</span>
      <span
        v-if="answeredCorrect"
        class="pq-card__tick"
        aria-hidden="true"
      >✓</span>
    </header>

    <p class="pq-card__stem">
      {{ question.stem }}
    </p>

    <!-- CHOICE：单选 -->
    <ul
      v-if="question.type === 'CHOICE'"
      class="pq-opts"
    >
      <li
        v-for="(opt, idx) in question.options"
        :key="idx"
      >
        <button
          type="button"
          :class="choiceClass(idx)"
          :disabled="answeredCorrect"
          :data-testid="`practice-q-${question.id}-opt-${idx}`"
          @click="pickChoice(idx)"
        >
          <span class="pq-opt__mark">{{ choiceIndex === idx ? '◉' : '○' }}</span>
          <span class="pq-opt__text">{{ opt }}</span>
        </button>
      </li>
    </ul>

    <!-- JUDGE：正确 / 错误 -->
    <div
      v-else-if="question.type === 'JUDGE'"
      class="pq-judge"
    >
      <button
        type="button"
        :class="judgeClass(true)"
        :disabled="answeredCorrect"
        :data-testid="`practice-q-${question.id}-judge-true`"
        @click="pickJudge(true)"
      >
        ✓ 正确
      </button>
      <button
        type="button"
        :class="judgeClass(false)"
        :disabled="answeredCorrect"
        :data-testid="`practice-q-${question.id}-judge-false`"
        @click="pickJudge(false)"
      >
        ✗ 错误
      </button>
    </div>

    <!-- FILL：blankCount 个输入框 -->
    <div
      v-else
      class="pq-fill"
    >
      <label
        v-for="i in blankCount"
        :key="i - 1"
        class="pq-fill__field"
      >
        <span class="pq-fill__label">空 {{ i }}</span>
        <input
          class="pq-fill__input"
          :value="blanks[i - 1] ?? ''"
          :disabled="answeredCorrect"
          :data-testid="`practice-q-${question.id}-blank-${i - 1}`"
          spellcheck="false"
          autocomplete="off"
          @input="onBlankInput(i - 1, $event)"
        >
      </label>
    </div>

    <div class="pq-card__foot">
      <button
        type="button"
        class="pq-submit"
        :disabled="!canSubmit"
        :data-testid="`practice-q-${question.id}-submit`"
        @click="submit"
      >
        {{ pending ? '判分中…' : answeredCorrect ? '已答对' : '提交' }}
      </button>
    </div>

    <!-- 判分横幅：本端判分结果优先；否则若初始已答对给精简完成态 -->
    <div
      v-if="result"
      class="pq-result"
      :class="result.correct ? 'pq-result--ok' : 'pq-result--bad'"
      :data-testid="`practice-q-${question.id}-result`"
    >
      <p class="pq-result__verdict">
        {{ result.correct ? '✓ 答对了' : '✗ 答错了' }}
      </p>
      <p
        v-if="result.correctAnswers.length"
        class="pq-result__answers"
      >
        参考答案：{{ result.correctAnswers.join(' / ') }}
      </p>
      <p
        v-if="result.explanation"
        class="pq-result__explain"
      >
        讲解：{{ result.explanation }}
      </p>
    </div>
    <div
      v-else-if="answeredCorrect"
      class="pq-result pq-result--ok"
      :data-testid="`practice-q-${question.id}-result`"
    >
      <p class="pq-result__verdict">
        ✓ 已答对
      </p>
    </div>
  </section>
</template>

<style scoped>
.pq-card {
  display: grid;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-3);
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
}
.pq-card--done {
  background: var(--pyt-color-success-bg);
  border-color: var(--pyt-color-success);
}
.pq-card__head {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
  font-size: var(--pyt-font-size-xs);
}
.pq-card__no {
  font-family: var(--pyt-font-pixel), sans-serif;
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.pq-card__kind {
  padding: 1px var(--pyt-space-2);
  background: var(--pyt-color-info-bg);
  border: 1px solid var(--pyt-color-info);
  color: var(--pyt-color-info);
  border-radius: var(--pyt-radius-sm);
}
.pq-card__kc {
  font-family: var(--pyt-font-pixel-mono), monospace;
  color: var(--pyt-color-text-muted);
}
.pq-card__tick {
  margin-left: auto;
  color: var(--pyt-color-success);
  font-weight: 700;
}
.pq-card__stem {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--pyt-font-read), sans-serif;
  font-size: var(--pyt-font-size-sm);
  line-height: 1.6;
  color: var(--pyt-color-text-primary);
}
.pq-opts {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--pyt-space-1);
}
.pq-opt {
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: var(--pyt-space-2);
  padding: var(--pyt-space-2);
  text-align: left;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-read), sans-serif;
  font-size: var(--pyt-font-size-sm);
  cursor: pointer;
  transition: background var(--pyt-motion-base), border-color var(--pyt-motion-base);
}
.pq-opt:hover:not(:disabled) {
  background: var(--pyt-color-bg-soft);
}
.pq-opt:disabled {
  cursor: default;
}
.pq-opt--picked {
  border-color: var(--pyt-color-primary);
  background: var(--pyt-color-bg-soft);
}
.pq-opt--correct {
  border-color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
}
.pq-opt--wrong {
  border-color: var(--pyt-color-danger);
  background: var(--pyt-color-danger-bg);
}
.pq-opt__mark {
  flex: 0 0 auto;
  font-family: var(--pyt-font-pixel-mono), monospace;
  color: var(--pyt-color-primary);
}
.pq-opt__text {
  flex: 1 1 auto;
}
.pq-judge {
  display: flex;
  gap: var(--pyt-space-2);
}
.pq-judge__btn {
  flex: 1 1 0;
  padding: var(--pyt-space-2);
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-read), sans-serif;
  font-size: var(--pyt-font-size-sm);
  cursor: pointer;
  transition: background var(--pyt-motion-base), border-color var(--pyt-motion-base);
}
.pq-judge__btn:hover:not(:disabled) {
  background: var(--pyt-color-bg-soft);
}
.pq-judge__btn:disabled {
  cursor: default;
}
.pq-judge__btn--picked {
  border-color: var(--pyt-color-primary);
  background: var(--pyt-color-bg-soft);
}
.pq-judge__btn--correct {
  border-color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
}
.pq-judge__btn--wrong {
  border-color: var(--pyt-color-danger);
  background: var(--pyt-color-danger-bg);
}
.pq-fill {
  display: grid;
  gap: var(--pyt-space-1);
}
.pq-fill__field {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
}
.pq-fill__label {
  flex: 0 0 auto;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.pq-fill__input {
  flex: 1 1 auto;
  min-width: 0;
  padding: 2px var(--pyt-space-2);
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-mono), monospace;
  font-size: var(--pyt-font-size-sm);
}
.pq-fill__input:focus {
  border-color: var(--pyt-color-primary);
  outline: none;
}
.pq-fill__input:disabled {
  opacity: 0.8;
}
.pq-card__foot {
  display: flex;
  justify-content: flex-end;
}
.pq-submit {
  font-family: var(--pyt-font-pixel), monospace;
  font-size: var(--pyt-font-size-xs);
  padding: 2px 12px;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
  box-shadow: 0 2px 0 var(--pyt-color-border);
}
.pq-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}
.pq-submit:not(:disabled):active {
  transform: translateY(2px);
  box-shadow: none;
}
.pq-result {
  padding: var(--pyt-space-2);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  font-size: var(--pyt-font-size-xs);
  background: var(--pyt-color-bg-elevated);
}
.pq-result--ok {
  border-color: var(--pyt-color-success);
  background: var(--pyt-color-success-bg);
}
.pq-result--bad {
  border-color: var(--pyt-color-danger);
  background: var(--pyt-color-danger-bg);
}
.pq-result__verdict {
  margin: 0;
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.pq-result__answers {
  margin: var(--pyt-space-1) 0 0;
  color: var(--pyt-color-text-primary);
}
.pq-result__explain {
  margin: var(--pyt-space-1) 0 0;
  line-height: 1.6;
  color: var(--pyt-color-text-hint);
}
</style>
