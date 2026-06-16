<script setup lang="ts">
// 填空题渲染器（去答案）。题干按连续下划线切分，空位处插入输入框（代码块走终端深色例外）。
import { computed } from 'vue';
import { splitFillSegments } from './useChapterTestExam';
import type {
  ChapterTestFillQuestionDto,
  ChapterTestResultItemDto,
} from '@/api/chapterTest';

const props = defineProps<{
  question: ChapterTestFillQuestionDto;
  index: number;
  blanks: string[];
  reviewItem?: ChapterTestResultItemDto | null;
}>();

const emit = defineEmits<{ (e: 'change', index: number, value: string): void }>();

const isReview = computed<boolean>(() => props.reviewItem != null);
const segments = computed<string[]>(() => splitFillSegments(props.question.stem).segments);
const blankCount = computed<number>(() => Math.max(segments.value.length - 1, 1));

function blankValue(i: number): string {
  return props.blanks[i] ?? '';
}

function onInput(i: number, event: Event): void {
  const target = event.target as HTMLInputElement | null;
  emit('change', i, target?.value ?? '');
}
</script>

<template>
  <section
    class="ct-card"
    data-testid="fill-question"
    :data-qid="question.qid"
  >
    <header class="ct-card__head">
      <span class="ct-card__no">第 {{ index }} 题</span>
      <span class="ct-card__kind">填空题</span>
      <span class="ct-card__blanks">{{ blankCount }} 空</span>
      <span class="ct-card__score">{{ question.score }} 分</span>
    </header>
    <div class="ct-fill__body">
      <template
        v-for="(seg, i) in segments"
        :key="i"
      >
        <span class="ct-fill__seg">{{ seg }}</span>
        <input
          v-if="i < blankCount"
          class="ct-fill__input"
          :value="blankValue(i)"
          :placeholder="`空 ${i + 1}`"
          :data-testid="`fill-input-${question.qid}-${i}`"
          :disabled="isReview"
          spellcheck="false"
          autocomplete="off"
          @input="onInput(i, $event)"
        >
      </template>
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
      </p>
      <p
        v-if="reviewItem.acceptedAnswers && reviewItem.acceptedAnswers.length"
        class="ct-review__accepted"
      >
        可接受答案：{{ reviewItem.acceptedAnswers.join(' / ') }}
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
.ct-card__blanks { color: #9aa0a8; }
.ct-card__score { margin-left: auto; color: #9aa0a8; }
/* 填空正文：浅灰代码块（题干可能含代码，保持等宽可读但不再深底）。 */
.ct-fill__body {
  padding: 10px 12px;
  background: #f7f8fa;
  color: #333;
  border: 1px solid #eef0f3;
  border-radius: 4px;
  font-family: var(--pyt-font-mono);
  font-size: 13px;
  line-height: 2.1;
  white-space: pre-wrap;
  word-break: break-word;
}
.ct-fill__seg { white-space: pre-wrap; }
.ct-fill__input {
  display: inline-block;
  min-width: 96px;
  margin: 0 4px;
  padding: 2px 8px;
  background: #fff;
  border: 1px solid #d4d8df;
  border-bottom: 2px solid #9aa0a8;
  border-radius: 3px;
  color: #333;
  font-family: inherit;
  font-size: 13px;
  text-align: center;
}
.ct-fill__input:focus { border-color: #3a7bd5; border-bottom-color: #3a7bd5; outline: none; }
.ct-fill__input:disabled { background: #f0f1f3; color: #888; }
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
.ct-review__accepted { margin: 6px 0 0; color: #333; }
.ct-review__explain { margin: 6px 0 0; color: #666; line-height: 1.7; }
</style>
