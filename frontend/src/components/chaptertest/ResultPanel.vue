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
/* 学习通成绩条白纸风：白卡 + 左侧色条标记通过/未通过。 */
.ct-result {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: var(--pyt-space-4);
  padding: var(--pyt-space-4);
  background: #fff;
  border: 1px solid #e6e8ec;
  border-left-width: 4px;
  border-radius: 4px;
  font-family: var(--pyt-font-read), sans-serif;
}
.ct-result--pass { border-left-color: #1fa657; }
.ct-result--fail { border-left-color: #e25c4a; }
.ct-result__score { display: flex; align-items: baseline; gap: 4px; }
.ct-result__num { font-size: 28px; font-weight: 700; color: #333; }
.ct-result__max { font-size: 14px; color: #9aa0a8; }
.ct-result__meta { display: flex; flex-direction: column; gap: 2px; }
.ct-result__verdict { font-size: 15px; font-weight: 700; }
.ct-result--pass .ct-result__verdict { color: #1fa657; }
.ct-result--fail .ct-result__verdict { color: #e25c4a; }
.ct-result__stars { font-size: 15px; color: #f0a93d; }
.ct-result__attempt { font-size: 12px; color: #9aa0a8; }
.ct-result__integrity {
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: 12px;
  color: #9aa0a8;
  text-align: right;
}
.ct-result__integrity--flagged { color: #e0922f; }
</style>
