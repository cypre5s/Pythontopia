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
/* 学习通考试卷面白纸风（与 ChoiceQuestion 同语言）。 */
.ct-card {
  display: grid;
  gap: 10px;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid #e6e8ec;
  border-radius: 4px;
  font-family: var(--pyt-font-read), sans-serif;
}
.ct-card__head { display: flex; align-items: baseline; gap: 6px; font-size: 13px; }
.ct-card__no { font-weight: 700; color: #333; font-size: 14px; }
.ct-card__kind { color: #8a8a8a; }
.ct-card__score { margin-left: auto; color: #9aa0a8; }
.ct-stem {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  padding: 0;
  background: transparent;
  border: 0;
  color: #333;
  font-family: var(--pyt-font-read), sans-serif;
  font-size: 14px;
  line-height: 1.8;
}
.ct-judge { display: grid; gap: 2px; padding-left: 4px; }
.ct-judge__btn {
  width: 100%;
  min-height: 0;
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
.ct-judge__btn:hover:not(:disabled) { background: #f5f6f8; }
.ct-judge__btn:disabled { cursor: default; }
.ct-judge__btn:active { transform: none; box-shadow: none; }
.ct-judge__btn--picked { background: #f0f6ff; color: #3a7bd5; }
.ct-judge__btn--correct { background: #f2faf4; color: #1fa657; }
.ct-judge__btn--wrong { background: #fdf3f2; color: #e25c4a; }
.ct-review {
  padding: 10px 12px;
  border: 1px solid #eef0f3;
  border-radius: 4px;
  font-size: 13px;
  background: #f7f8fa;
}
.ct-review--ok .ct-review__verdict { color: #1fa657; }
.ct-review--bad .ct-review__verdict { color: #e25c4a; }
.ct-review__verdict { margin: 0; font-weight: 700; }
.ct-review__explain { margin: 6px 0 0; color: #666; line-height: 1.7; }
</style>
