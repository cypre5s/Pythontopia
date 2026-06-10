<script setup lang="ts">
// 判断题渲染器（去答案）。正确答案/解析仅在 reviewItem（提交后）存在时显示。
import { computed } from 'vue';
import type {
  ChapterTestJudgeQuestionDto,
  ChapterTestResultItemDto,
} from '@/api/chapterTest';

const props = defineProps<{
  question: ChapterTestJudgeQuestionDto;
  index: number;
  selected: boolean | null;
  reviewItem?: ChapterTestResultItemDto | null;
}>();

const emit = defineEmits<{ (e: 'select', value: boolean): void }>();

const isReview = computed<boolean>(() => props.reviewItem != null);

function onPick(value: boolean): void {
  if (isReview.value) return;
  emit('select', value);
}

function btnClass(value: boolean): Record<string, boolean> {
  const picked = props.selected === value;
  if (!isReview.value) {
    return { 'ct-judge__btn': true, 'ct-judge__btn--picked': picked };
  }
  const correct = props.reviewItem?.correctJudgement === value;
  return {
    'ct-judge__btn': true,
    'ct-judge__btn--picked': picked,
    'ct-judge__btn--correct': correct,
    'ct-judge__btn--wrong': picked && !correct,
  };
}
</script>

<template>
  <section
    class="ct-card"
    data-testid="judge-question"
    :data-qid="question.qid"
  >
    <header class="ct-card__head">
      <span class="ct-card__no">第 {{ index }} 题</span>
      <span class="ct-card__kind">判断题</span>
      <span class="ct-card__score">{{ question.score }} 分</span>
    </header>
    <pre class="ct-stem">{{ question.stem }}</pre>
    <div class="ct-judge">
      <button
        type="button"
        :class="btnClass(true)"
        :disabled="isReview"
        :data-testid="`judge-true-${question.qid}`"
        @click="onPick(true)"
      >
        ✓ 正确
      </button>
      <button
        type="button"
        :class="btnClass(false)"
        :disabled="isReview"
        :data-testid="`judge-false-${question.qid}`"
        @click="onPick(false)"
      >
        ✗ 错误
      </button>
    </div>

    <div
      v-if="reviewItem"
      class="ct-review"
      :class="reviewItem.correct ? 'ct-review--ok' : 'ct-review--bad'"
      :data-testid="`result-item-${question.qid}`"
    >
      <p class="ct-review__verdict">
        {{ reviewItem.correct ? '✓ 正确' : '✗ 错误' }}
        · 得分 {{ reviewItem.awarded }}/{{ reviewItem.score }}
        <span v-if="reviewItem.correctJudgement !== undefined">
          · 正确答案：{{ reviewItem.correctJudgement ? '正确' : '错误' }}
        </span>
      </p>
      <p class="ct-review__explain">
        {{ reviewItem.explain }}
      </p>
    </div>
  </section>
</template>

<style scoped>
.ct-card {
  display: grid;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-4);
  background: linear-gradient(180deg, var(--pyt-color-bg-elevated) 0%, var(--pyt-color-bg-sunken) 100%);
  border: 3px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-lg);
  box-shadow: var(--pyt-shadow-card);
}
.ct-card__head { display: flex; align-items: center; gap: var(--pyt-space-2); font-size: var(--pyt-font-size-sm); }
.ct-card__no { font-weight: 700; color: var(--pyt-color-text-primary); }
.ct-card__kind {
  padding: 2px var(--pyt-space-2);
  background: var(--pyt-color-accent-bg);
  border: 1px solid var(--pyt-color-accent);
  color: var(--pyt-color-accent);
  border-radius: var(--pyt-radius-sm);
}
.ct-card__score { margin-left: auto; color: var(--pyt-color-text-muted); }
.ct-stem {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  padding: var(--pyt-space-3);
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  font-family: var(--pyt-font-read);
  font-size: var(--pyt-font-size-base);
  line-height: 1.6;
}
.ct-judge { display: flex; gap: var(--pyt-space-3); }
.ct-judge__btn {
  flex: 1 1 0;
  padding: var(--pyt-space-3);
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-read);
  font-size: var(--pyt-font-size-lg);
  cursor: pointer;
  transition: background var(--pyt-motion-base), border-color var(--pyt-motion-base);
}
.ct-judge__btn:hover:not(:disabled) { background: var(--pyt-color-bg-soft); }
.ct-judge__btn:disabled { cursor: default; }
.ct-judge__btn--picked { border-color: var(--pyt-color-primary); background: var(--pyt-color-bg-soft); }
.ct-judge__btn--correct { border-color: var(--pyt-color-success); background: var(--pyt-color-success-bg); }
.ct-judge__btn--wrong { border-color: var(--pyt-color-danger); background: var(--pyt-color-danger-bg); }
.ct-review {
  padding: var(--pyt-space-3);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  font-size: var(--pyt-font-size-sm);
  background: var(--pyt-color-bg-soft);
}
.ct-review--ok { border-color: var(--pyt-color-success); }
.ct-review--bad { border-color: var(--pyt-color-danger); }
.ct-review__verdict { margin: 0 0 var(--pyt-space-2); font-weight: 700; color: var(--pyt-color-text-primary); }
.ct-review__explain { margin: 0; color: var(--pyt-color-text-hint); line-height: 1.6; }
</style>
