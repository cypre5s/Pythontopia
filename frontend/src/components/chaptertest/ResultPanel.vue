<script setup lang="ts">
// 交卷后成绩概览（分数 / 是否通过 / 星级 / 防作弊计数）。逐题解析由下方题卡 review 模式渲染。
import { computed } from 'vue';
import type { ChapterTestResultDto } from '@/api/chapterTest';

const props = defineProps<{ result: ChapterTestResultDto }>();

const stars = computed<number | null>(() =>
  typeof props.result.chapterStars === 'number' ? props.result.chapterStars : null,
);
const starText = computed<string>(() =>
  stars.value === null ? '' : '★'.repeat(stars.value) + '☆'.repeat(Math.max(3 - stars.value, 0)),
);
const integrity = computed(() => props.result.integrity);
const flagged = computed<boolean>(
  () => integrity.value.overtime || integrity.value.paste_blocked > 0 || integrity.value.blur_events > 0,
);
</script>

<template>
  <section
    class="ct-result"
    :class="result.passed ? 'ct-result--pass' : 'ct-result--fail'"
    data-testid="exam-result"
  >
    <div class="ct-result__score">
      <span
        class="ct-result__num"
        data-testid="exam-result-score"
      >{{ result.score }}</span>
      <span class="ct-result__max">/ {{ result.maxScore }}</span>
    </div>
    <div class="ct-result__meta">
      <span
        class="ct-result__verdict"
        data-testid="exam-result-pass"
      >
        {{ result.passed ? '✓ 已通过' : '✗ 未通过' }}
      </span>
      <span
        v-if="stars !== null"
        class="ct-result__stars"
        data-testid="exam-result-stars"
      >{{ starText }}</span>
      <span class="ct-result__attempt">第 {{ result.attemptNo }} 次 · 第 {{ result.chapter }} 章</span>
    </div>
    <ul
      class="ct-result__integrity"
      :class="{ 'ct-result__integrity--flagged': flagged }"
      data-testid="exam-result-integrity"
    >
      <li>粘贴拦截：{{ integrity.paste_blocked }} 次</li>
      <li>失焦：{{ integrity.blur_events }} 次</li>
      <li>超时：{{ integrity.overtime ? '是' : '否' }}</li>
    </ul>
  </section>
</template>

<style scoped>
.ct-result {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: var(--pyt-space-4);
  padding: var(--pyt-space-4);
  background: linear-gradient(180deg, var(--pyt-color-bg-elevated) 0%, var(--pyt-color-bg-sunken) 100%);
  border: 3px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-lg);
  box-shadow: var(--pyt-shadow-card);
}
.ct-result--pass { border-color: var(--pyt-color-success); }
.ct-result--fail { border-color: var(--pyt-color-danger); }
.ct-result__score { display: flex; align-items: baseline; gap: var(--pyt-space-1); }
.ct-result__num { font-size: var(--pyt-font-size-2xl); font-weight: 700; color: var(--pyt-color-text-primary); }
.ct-result__max { font-size: var(--pyt-font-size-base); color: var(--pyt-color-text-muted); }
.ct-result__meta { display: flex; flex-direction: column; gap: var(--pyt-space-1); }
.ct-result__verdict { font-size: var(--pyt-font-size-lg); font-weight: 700; }
.ct-result--pass .ct-result__verdict { color: var(--pyt-color-success); }
.ct-result--fail .ct-result__verdict { color: var(--pyt-color-danger); }
.ct-result__stars { font-size: var(--pyt-font-size-lg); color: var(--pyt-color-gold); }
.ct-result__attempt { font-size: var(--pyt-font-size-xs); color: var(--pyt-color-text-muted); }
.ct-result__integrity {
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
  text-align: right;
}
.ct-result__integrity--flagged { color: var(--pyt-color-warning); }
</style>
