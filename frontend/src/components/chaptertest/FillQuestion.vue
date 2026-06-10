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
  background: var(--pyt-color-warning-bg);
  border: 1px solid var(--pyt-color-warning);
  color: var(--pyt-color-warning);
  border-radius: var(--pyt-radius-sm);
}
.ct-card__blanks { color: var(--pyt-color-text-muted); }
.ct-card__score { margin-left: auto; color: var(--pyt-color-text-muted); }
/* 填空正文：终端深色例外（代码块）。 */
.ct-fill__body {
  padding: var(--pyt-space-3);
  background: var(--pyt-color-terminal-bg);
  color: var(--pyt-color-terminal-text);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-sm);
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-base);
  line-height: 2;
  white-space: pre-wrap;
  word-break: break-word;
}
.ct-fill__seg { white-space: pre-wrap; }
.ct-fill__input {
  display: inline-block;
  min-width: 96px;
  margin: 0 var(--pyt-space-1);
  padding: 2px var(--pyt-space-2);
  background: var(--pyt-color-shadow);
  border: 2px dashed var(--pyt-color-gold);
  border-radius: var(--pyt-radius-sm);
  color: var(--pyt-color-gold-bright);
  font-family: inherit;
  font-size: var(--pyt-font-size-base);
  text-align: center;
}
.ct-fill__input:focus { border-color: var(--pyt-color-success); outline: none; }
.ct-fill__input:disabled { opacity: 0.8; }
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
.ct-review__accepted { margin: 0 0 var(--pyt-space-2); color: var(--pyt-color-text-primary); }
.ct-review__explain { margin: 0; color: var(--pyt-color-text-hint); line-height: 1.6; }
</style>
