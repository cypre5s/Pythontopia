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
.ct-card {
  display: grid;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-4);
  background: linear-gradient(180deg, var(--pyt-color-bg-elevated) 0%, var(--pyt-color-bg-sunken) 100%);
  border: 3px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-lg);
  box-shadow: var(--pyt-shadow-card);
}
.ct-card__head {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
  font-size: var(--pyt-font-size-sm);
}
.ct-card__no { font-weight: 700; color: var(--pyt-color-text-primary); }
.ct-card__kind {
  padding: 2px var(--pyt-space-2);
  background: var(--pyt-color-info-bg);
  border: 1px solid var(--pyt-color-info);
  color: var(--pyt-color-info);
  border-radius: var(--pyt-radius-sm);
}
.ct-card__score { margin-left: auto; color: var(--pyt-color-text-muted); }
/* 题面：终端深色例外（arena 约定）。 */
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
.ct-opts { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--pyt-space-2); }
.ct-opt {
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-3);
  text-align: left;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-read);
  font-size: var(--pyt-font-size-base);
  cursor: pointer;
  transition: background var(--pyt-motion-base), border-color var(--pyt-motion-base);
}
.ct-opt:hover:not(:disabled) { background: var(--pyt-color-bg-soft); }
.ct-opt:disabled { cursor: default; }
.ct-opt--picked { border-color: var(--pyt-color-primary); background: var(--pyt-color-bg-soft); }
.ct-opt--correct { border-color: var(--pyt-color-success); background: var(--pyt-color-success-bg); }
.ct-opt--wrong { border-color: var(--pyt-color-danger); background: var(--pyt-color-danger-bg); }
.ct-opt__letter {
  flex: 0 0 auto;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  background: var(--pyt-color-primary);
  color: var(--pyt-color-text-on-dark);
  border-radius: var(--pyt-radius-sm);
  font-weight: 700;
  font-size: var(--pyt-font-size-sm);
}
.ct-opt__text { flex: 1 1 auto; }
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
.ct-review__evidence { margin: var(--pyt-space-2) 0 0; color: var(--pyt-color-text-muted); }
</style>
