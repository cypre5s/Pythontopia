<script setup lang="ts">
// 选择题渲染器（去答案）。提交前只渲染题面 + 选项；正确答案/解析仅在 reviewItem（提交后）存在时显示。
import { computed } from 'vue';
import { optionLetter, letterToIndex } from '@/api/chapterTest';
import type {
  ChapterTestChoiceQuestionDto,
  ChapterTestResultItemDto,
} from '@/api/chapterTest';

const props = defineProps<{
  question: ChapterTestChoiceQuestionDto;
  index: number;
  selected: string | null;
  reviewItem?: ChapterTestResultItemDto | null;
}>();

const emit = defineEmits<{ (e: 'select', letter: string): void }>();

const isReview = computed<boolean>(() => props.reviewItem != null);
const correctIndex = computed<number>(() =>
  props.reviewItem?.correctChoice != null ? letterToIndex(props.reviewItem.correctChoice) : -1,
);

function onPick(index: number): void {
  if (isReview.value) return;
  emit('select', optionLetter(index));
}

function optionClass(index: number): Record<string, boolean> {
  const letter = optionLetter(index);
  const picked = props.selected === letter;
  if (!isReview.value) {
    return { 'ct-opt': true, 'ct-opt--picked': picked };
  }
  const isCorrect = index === correctIndex.value;
  return {
    'ct-opt': true,
    'ct-opt--picked': picked,
    'ct-opt--correct': isCorrect,
    'ct-opt--wrong': picked && !isCorrect,
  };
}
</script>

<template>
  <section
    class="ct-card"
    data-testid="choice-question"
    :data-qid="question.qid"
  >
    <header class="ct-card__head">
      <span class="ct-card__no">第 {{ index }} 题</span>
      <span class="ct-card__kind">选择题</span>
      <span class="ct-card__score">{{ question.score }} 分</span>
    </header>
    <pre class="ct-stem">{{ question.stem }}</pre>
    <ul class="ct-opts">
      <li
        v-for="(opt, idx) in question.options"
        :key="idx"
      >
        <button
          type="button"
          :class="optionClass(idx)"
          :disabled="isReview"
          :data-testid="`choice-opt-${question.qid}-${optionLetter(idx)}`"
          @click="onPick(idx)"
        >
          <span class="ct-opt__letter">{{ optionLetter(idx) }}</span>
          <span class="ct-opt__text">{{ opt }}</span>
        </button>
      </li>
    </ul>

    <div
      v-if="reviewItem"
      class="ct-review"
      :class="reviewItem.correct ? 'ct-review--ok' : 'ct-review--bad'"
      :data-testid="`result-item-${question.qid}`"
    >
      <p class="ct-review__verdict">
        {{ reviewItem.correct ? '✓ 正确' : '✗ 错误' }}
        · 得分 {{ reviewItem.awarded }}/{{ reviewItem.score }}
        <span v-if="reviewItem.correctChoice"> · 正确答案：{{ reviewItem.correctChoice }}</span>
      </p>
      <p class="ct-review__explain">
        {{ reviewItem.explain }}
      </p>
      <p
        v-if="reviewItem.evidencePageIds && reviewItem.evidencePageIds.length"
        class="ct-review__evidence"
      >
        课件依据：第 {{ reviewItem.evidencePageIds.join('、') }} 页
      </p>
    </div>
  </section>
</template>

<style scoped>
/* 学习通考试卷面白纸风（与小节练习 PracticeQuestionCard 同语言）：白卡细线、灰字蓝点缀。 */
.ct-card {
  display: grid;
  gap: 10px;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid #e6e8ec;
  border-radius: 4px;
  font-family: var(--pyt-font-read), sans-serif;
}
.ct-card__head {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 13px;
}
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
.ct-opts { list-style: none; margin: 0; padding: 0 0 0 4px; display: grid; gap: 2px; }
.ct-opt {
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
.ct-opt:hover:not(:disabled) { background: #f5f6f8; }
.ct-opt:disabled { cursor: default; }
.ct-opt:active { transform: none; box-shadow: none; }
.ct-opt--picked { background: #f0f6ff; }
.ct-opt--picked .ct-opt__letter { background: #3a7bd5; border-color: #3a7bd5; color: #fff; }
.ct-opt--correct { background: #f2faf4; }
.ct-opt--correct .ct-opt__letter { background: #1fa657; border-color: #1fa657; color: #fff; }
.ct-opt--wrong { background: #fdf3f2; }
.ct-opt--wrong .ct-opt__letter { background: #e25c4a; border-color: #e25c4a; color: #fff; }
.ct-opt__letter {
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  background: #fff;
  border: 1px solid #c4c8cf;
  color: #666;
  border-radius: 50%;
  font-weight: 400;
  font-size: 12px;
  margin-top: 1px;
}
.ct-opt__text { flex: 1 1 auto; min-width: 0; }
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
.ct-review__evidence { margin: 6px 0 0; color: #8a8a8a; }
</style>
