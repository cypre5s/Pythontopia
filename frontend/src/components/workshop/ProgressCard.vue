<script setup lang="ts">
/**
 * ProgressCard —— 学情卡（PROGRESS 意图，零 LLM，数据来自 KC 掌握度快照）。
 *
 * 按 masteryScore（0–1）排序：Top3「💪 掌握最好」+ Bottom3「📌 待加强」（与 Top3 不重复，
 * 最弱在前）。每行 = KC 中文名（kcDisplayName）+ 分段像素进度条（<0.4 红 / 0.4–0.7 黄 /
 * >0.7 绿）+ 百分比；score≈0 视为「未学」标签保留展示；Bottom3 行尾「去练 →」emit practice。
 * snapshot 为空渲染空态文案。
 */
import { computed } from 'vue';
import { kcDisplayName } from '@/data/kcNames';

interface MasterySnapshotEntry {
  kcCode: string;
  masteryScore: number;
}

const props = defineProps<{ snapshot: MasterySnapshotEntry[] }>();

const emit = defineEmits<{ (e: 'practice', kcCode: string): void }>();

/** score ≤ 此阈值视作「未学」（接近 0 的噪声不当成已学）。 */
const UNLEARNED_EPS = 0.05;
const SEGMENT_COUNT = 10;

type Band = 'low' | 'mid' | 'high';

interface ProgressRow {
  kcCode: string;
  name: string;
  percent: number;
  filled: number;
  band: Band;
  unlearned: boolean;
}

function bandOf(score: number): Band {
  if (score < 0.4) return 'low';
  if (score <= 0.7) return 'mid';
  return 'high';
}

function toRow(entry: MasterySnapshotEntry): ProgressRow {
  const raw = Number.isFinite(entry.masteryScore) ? entry.masteryScore : 0;
  const score = Math.max(0, Math.min(1, raw));
  return {
    kcCode: entry.kcCode,
    name: kcDisplayName(entry.kcCode),
    percent: Math.round(score * 100),
    filled: Math.round(score * SEGMENT_COUNT),
    band: bandOf(score),
    unlearned: score <= UNLEARNED_EPS,
  };
}

const sorted = computed<MasterySnapshotEntry[]>(() =>
  [...props.snapshot].sort((a, b) => b.masteryScore - a.masteryScore),
);

/** Top3：掌握最好。 */
const topRows = computed<ProgressRow[]>(() => sorted.value.slice(0, 3).map(toRow));

/** Bottom3：待加强（剔除已进 Top3 的条目，最弱在前）。 */
const bottomRows = computed<ProgressRow[]>(() => {
  const taken = new Set(topRows.value.map((r) => r.kcCode));
  const rest = sorted.value.filter((e) => !taken.has(e.kcCode));
  return rest.slice(-3).reverse().map(toRow);
});

/** 进度条 10 个分段格的索引（0–9）。 */
const segments = Array.from({ length: SEGMENT_COUNT }, (_, i) => i);

function onPractice(kcCode: string): void {
  emit('practice', kcCode);
}
</script>

<template>
  <section
    class="progress"
    data-testid="progress-card"
  >
    <p class="progress__title">
      ◉ 学情快照
    </p>
    <p
      v-if="snapshot.length === 0"
      class="progress__empty"
      data-testid="progress-empty"
    >
      还没有学习记录——先去学一个知识点，回来就能看到你的掌握度啦。
    </p>
    <template v-else>
      <div class="progress__group">
        <p class="progress__group-name">
          💪 掌握最好
        </p>
        <ul class="progress__rows">
          <li
            v-for="row in topRows"
            :key="row.kcCode"
            class="progress__row"
            data-testid="progress-top-row"
          >
            <span
              class="progress__name"
              :title="row.kcCode"
            >{{ row.name }}</span>
            <span
              class="progress__bar"
              :class="`progress__bar--${row.band}`"
              aria-hidden="true"
            >
              <span
                v-for="i in segments"
                :key="i"
                class="progress__seg"
                :class="{ 'progress__seg--filled': i < row.filled }"
              />
            </span>
            <span
              v-if="row.unlearned"
              class="progress__tag"
              data-testid="progress-unlearned"
            >未学</span>
            <span
              v-else
              class="progress__pct"
            >{{ row.percent }}%</span>
          </li>
        </ul>
      </div>
      <div
        v-if="bottomRows.length > 0"
        class="progress__group"
      >
        <p class="progress__group-name">
          📌 待加强
        </p>
        <ul class="progress__rows">
          <li
            v-for="row in bottomRows"
            :key="row.kcCode"
            class="progress__row progress__row--weak"
            data-testid="progress-bottom-row"
          >
            <span
              class="progress__name"
              :title="row.kcCode"
            >{{ row.name }}</span>
            <span
              class="progress__bar"
              :class="`progress__bar--${row.band}`"
              aria-hidden="true"
            >
              <span
                v-for="i in segments"
                :key="i"
                class="progress__seg"
                :class="{ 'progress__seg--filled': i < row.filled }"
              />
            </span>
            <span
              v-if="row.unlearned"
              class="progress__tag"
              data-testid="progress-unlearned"
            >未学</span>
            <span
              v-else
              class="progress__pct"
            >{{ row.percent }}%</span>
            <button
              type="button"
              class="progress__practice"
              data-testid="progress-practice"
              @click="onPractice(row.kcCode)"
            >
              去练 →
            </button>
          </li>
        </ul>
      </div>
    </template>
  </section>
</template>

<style scoped>
.progress {
  align-self: flex-start;
  width: 100%;
  max-width: 90%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
  background: #fff8df;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  box-shadow: 2px 2px 0 rgba(140, 106, 35, 0.35);
}
.progress__title {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--pyt-color-text-hint);
}
.progress__empty {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--pyt-color-text-muted);
}
.progress__group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.progress__group-name {
  margin: 0;
  font-size: 11px;
  font-weight: 700;
  color: var(--pyt-color-text-muted);
}
.progress__rows {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.progress__row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.progress__name {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 12px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.progress__row--weak .progress__name {
  font-weight: 400;
}
.progress__bar {
  flex: none;
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  background: rgba(140, 106, 35, 0.12);
  border: 1px solid rgba(140, 106, 35, 0.4);
  border-radius: var(--pyt-radius-sm);
}
.progress__seg {
  width: 7px;
  height: 9px;
  background: rgba(140, 106, 35, 0.15);
  image-rendering: pixelated;
}
/* 分段色块按掌握档位上色：<0.4 红 / 0.4–0.7 黄 / >0.7 绿。 */
.progress__bar--low .progress__seg--filled {
  background: #d9534f;
}
.progress__bar--mid .progress__seg--filled {
  background: #d4af37;
}
.progress__bar--high .progress__seg--filled {
  background: #4d6e1d;
}
.progress__pct {
  flex: none;
  width: 36px;
  text-align: right;
  font-size: 11px;
  color: var(--pyt-color-text-muted);
  font-variant-numeric: tabular-nums;
}
.progress__tag {
  flex: none;
  width: 36px;
  text-align: center;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 0;
  color: var(--pyt-color-text-muted);
  background: rgba(140, 106, 35, 0.12);
  border: 1px solid rgba(140, 106, 35, 0.4);
  border-radius: var(--pyt-radius-sm);
}
.progress__practice {
  flex: none;
  font-family: inherit;
  font-size: 11px;
  padding: 2px 8px;
  background: #fff8df;
  border: 1px solid #8c6a23;
  border-radius: var(--pyt-radius-full);
  color: #2a1a08;
  cursor: pointer;
}
.progress__practice:hover {
  background: #ffe098;
}
</style>
