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
  CHOICE: '单选题',
  JUDGE: '判断题',
  FILL: '填空题',
};

/** 选项字母编号（学习通式 A/B/C/D）。 */
function optionLetter(idx: number): string {
  return String.fromCharCode(65 + idx);
}

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
    <!-- 学习通式题头：「1. (单选题) 题干」 -->
    <p class="pq-card__stem">
      <span class="pq-card__no">{{ index }}.</span>
      <span class="pq-card__kind">({{ TYPE_LABEL[question.type] }})</span>
      {{ question.stem }}
      <span
        v-if="answeredCorrect"
        class="pq-card__tick"
        aria-hidden="true"
      >✓</span>
    </p>

    <!-- CHOICE：单选（A/B/C/D 字母行） -->
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
          <span class="pq-opt__mark">{{ optionLetter(idx) }}</span>
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
        <span class="pq-opt__mark">A</span>
        <span class="pq-opt__text">正确 ✓</span>
      </button>
      <button
        type="button"
        :class="judgeClass(false)"
        :disabled="answeredCorrect"
        :data-testid="`practice-q-${question.id}-judge-false`"
        @click="pickJudge(false)"
      >
        <span class="pq-opt__mark">B</span>
        <span class="pq-opt__text">错误 ✗</span>
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
        <span class="pq-fill__label">第 {{ i }} 空</span>
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

    <!-- 判分区：学习通式浅灰答案块（正确答案 / 答案解析） -->
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
        <span class="pq-result__label">正确答案：</span>{{ result.correctAnswers.join(' / ') }}
      </p>
      <p
        v-if="result.explanation"
        class="pq-result__explain"
      >
        <span class="pq-result__label">答案解析：</span>{{ result.explanation }}
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
/* 学习通作业式朴素风：白底、细分隔、灰字蓝点缀，不用像素字/重边框/大色块。 */
.pq-card {
  display: grid;
  gap: 10px;
  padding: 16px 18px;
  background: #fff;
  border-bottom: 1px solid #ececec;
}
.pq-card:last-child {
  border-bottom: 0;
}
.pq-card--done .pq-card__stem {
  color: #555;
}
.pq-card__stem {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--pyt-font-read), sans-serif;
  font-size: 14px;
  line-height: 1.8;
  color: #333;
}
.pq-card__no {
  font-weight: 700;
  margin-right: 2px;
}
.pq-card__kind {
  color: #8a8a8a;
  font-size: 13px;
  margin-right: 4px;
}
.pq-card__tick {
  margin-left: 6px;
  color: #1fa657;
  font-weight: 700;
}

/* 选项：纯文字行 + 字母圆圈，悬停浅灰 */
.pq-opts {
  list-style: none;
  margin: 0;
  padding: 0 0 0 4px;
  display: grid;
  gap: 2px;
}
.pq-opt,
.pq-judge__btn {
  width: 100%;
  min-height: 0;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 7px 10px;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 4px;
  box-shadow: none;
  color: #333;
  font-family: var(--pyt-font-read), sans-serif;
  font-size: 14px;
  line-height: 1.6;
  cursor: pointer;
  transition: background var(--pyt-motion-base);
}
/* 盖掉全局像素 button 的 :active 位移/投影，保持白纸朴素风 */
.pq-opt:active,
.pq-judge__btn:active,
.pq-submit:active {
  transform: none;
  box-shadow: none;
}
.pq-opt:hover:not(:disabled),
.pq-judge__btn:hover:not(:disabled) {
  background: #f5f6f8;
}
.pq-opt:disabled,
.pq-judge__btn:disabled {
  cursor: default;
}
.pq-opt__mark {
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #c4c8cf;
  border-radius: 50%;
  font-size: 12px;
  color: #666;
  background: #fff;
  margin-top: 1px;
}
.pq-opt__text {
  flex: 1 1 auto;
  min-width: 0;
}
.pq-opt--picked,
.pq-judge__btn--picked {
  background: #f0f6ff;
}
.pq-opt--picked .pq-opt__mark,
.pq-judge__btn--picked .pq-opt__mark {
  background: #3a7bd5;
  border-color: #3a7bd5;
  color: #fff;
}
.pq-opt--correct,
.pq-judge__btn--correct {
  background: #f2faf4;
}
.pq-opt--correct .pq-opt__mark,
.pq-judge__btn--correct .pq-opt__mark {
  background: #1fa657;
  border-color: #1fa657;
  color: #fff;
}
.pq-opt--wrong,
.pq-judge__btn--wrong {
  background: #fdf3f2;
}
.pq-opt--wrong .pq-opt__mark,
.pq-judge__btn--wrong .pq-opt__mark {
  background: #e25c4a;
  border-color: #e25c4a;
  color: #fff;
}

.pq-judge {
  display: grid;
  gap: 2px;
  padding-left: 4px;
}

/* 填空：浅框输入 */
.pq-fill {
  display: grid;
  gap: 8px;
  padding-left: 4px;
}
.pq-fill__field {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pq-fill__label {
  flex: 0 0 auto;
  font-size: 13px;
  color: #8a8a8a;
}
.pq-fill__input {
  flex: 0 1 320px;
  min-width: 0;
  padding: 5px 10px;
  background: #fff;
  border: 1px solid #d4d8df;
  border-radius: 4px;
  color: #333;
  font-family: var(--pyt-font-mono), monospace;
  font-size: 13px;
}
.pq-fill__input:focus {
  border-color: #3a7bd5;
  outline: none;
}
.pq-fill__input:disabled {
  background: #f7f8fa;
  color: #888;
}

.pq-card__foot {
  display: flex;
  justify-content: flex-end;
}
.pq-submit {
  font-family: var(--pyt-font-read), sans-serif;
  font-size: 13px;
  min-height: 0;
  padding: 4px 18px;
  background: #3a7bd5;
  border: 1px solid #3a7bd5;
  border-radius: 4px;
  box-shadow: none;
  color: #fff;
  cursor: pointer;
}
.pq-submit:hover:not(:disabled) {
  background: #2f6cc0;
}
.pq-submit:disabled {
  background: #f0f1f3;
  border-color: #dcdfe5;
  color: #9aa0a8;
  cursor: not-allowed;
}

/* 判分区：学习通式浅灰答案块 */
.pq-result {
  padding: 10px 12px;
  border-radius: 4px;
  font-size: 13px;
  background: #f7f8fa;
  border: 1px solid #eef0f3;
  font-family: var(--pyt-font-read), sans-serif;
  animation: pq-result-in var(--pyt-motion-slow) ease-out;
}
@keyframes pq-result-in {
  from {
    opacity: 0;
    transform: translateY(-3px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .pq-result {
    animation: none;
  }
}
.pq-result--ok .pq-result__verdict {
  color: #1fa657;
}
.pq-result--bad .pq-result__verdict {
  color: #e25c4a;
}
.pq-result__verdict {
  margin: 0;
  font-weight: 700;
}
.pq-result__answers {
  margin: 6px 0 0;
  color: #333;
}
.pq-result__explain {
  margin: 6px 0 0;
  line-height: 1.7;
  color: #666;
}
.pq-result__label {
  color: #8a8a8a;
}
</style>
