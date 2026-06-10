<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import {
  fetchChapterTests,
  type ChapterTestRowDto,
  type ChapterTestStatsDto,
} from '@/api/teacher';

const props = defineProps<{ classroomId: number }>();

const data = ref<ChapterTestStatsDto | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);

const rows = computed<ChapterTestRowDto[]>(() => data.value?.chapters ?? []);
const hasAnyAttempt = computed<boolean>(() =>
  rows.value.some((r) => r.attemptCount > 0),
);

// 箱线坐标映射（分数 0..100 → viewBox x，两侧各留 2 像素余量）。
function bx(score: number): number {
  return 2 + score;
}

function passRatePct(rate: number): string {
  return (rate * 100).toFixed(1) + '%';
}

function passColor(rate: number): string {
  if (rate >= 0.85) return '#2f8f6f';
  if (rate >= 0.6) return '#d4a72c';
  return '#c0392b';
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    data.value = await fetchChapterTests(props.classroomId);
  } catch (e) {
    error.value = e instanceof Error ? e.message : '章测统计加载失败';
  } finally {
    loading.value = false;
  }
}

onMounted(load);
watch(() => props.classroomId, load);
</script>

<template>
  <section
    class="chapter-tests"
    data-testid="chapter-tests-tab"
  >
    <p
      v-if="loading"
      class="banner"
      data-testid="tests-loading"
    >
      章测统计加载中…
    </p>
    <p
      v-else-if="error"
      class="banner banner--error"
      data-testid="tests-error"
    >
      {{ error }}
    </p>
    <template v-else>
      <p
        v-if="!hasAnyAttempt"
        class="empty"
        data-testid="tests-empty"
      >
        暂无章末大测试提交记录。学生完成本章全部 stage 并参加章测后，这里会出现通过率与分数箱线。
      </p>
      <div class="list">
        <article
          v-for="row in rows"
          :key="row.chapter"
          class="card"
          :data-testid="`test-row-${row.chapter}`"
        >
          <header class="card__head">
            <span class="card__title">第 {{ row.chapter }} 章</span>
            <span
              class="card__rate"
              :style="{ color: passColor(row.passRate) }"
            >通过率 {{ passRatePct(row.passRate) }}</span>
          </header>
          <div class="card__stats">
            <span>参试 {{ row.participantCount }} 人</span>
            <span>提交 {{ row.attemptCount }} 次</span>
            <span>≥60 分 {{ row.passedCount }} 人</span>
          </div>
          <div
            v-if="row.scoreDistribution.count > 0"
            class="boxplot-wrap"
          >
            <svg
              class="boxplot"
              viewBox="0 0 104 16"
              preserveAspectRatio="none"
              role="img"
              :aria-label="`第 ${row.chapter} 章分数箱线`"
            >
              <!-- 须线 min..max -->
              <line
                :x1="bx(row.scoreDistribution.min ?? 0)"
                y1="8"
                :x2="bx(row.scoreDistribution.max ?? 0)"
                y2="8"
                stroke="#8a6c2c"
                stroke-width="1"
              />
              <line
                :x1="bx(row.scoreDistribution.min ?? 0)"
                y1="4"
                :x2="bx(row.scoreDistribution.min ?? 0)"
                y2="12"
                stroke="#8a6c2c"
                stroke-width="1"
              />
              <line
                :x1="bx(row.scoreDistribution.max ?? 0)"
                y1="4"
                :x2="bx(row.scoreDistribution.max ?? 0)"
                y2="12"
                stroke="#8a6c2c"
                stroke-width="1"
              />
              <!-- 箱体 q1..q3 -->
              <rect
                :x="bx(row.scoreDistribution.q1 ?? 0)"
                y="3"
                :width="Math.max(0.5, bx(row.scoreDistribution.q3 ?? 0) - bx(row.scoreDistribution.q1 ?? 0))"
                height="10"
                fill="#f1c453"
                fill-opacity="0.55"
                stroke="#c2871f"
                stroke-width="1"
              />
              <!-- 中位线 -->
              <line
                :x1="bx(row.scoreDistribution.median ?? 0)"
                y1="2"
                :x2="bx(row.scoreDistribution.median ?? 0)"
                y2="14"
                stroke="#4d3613"
                stroke-width="1.5"
              />
            </svg>
            <div class="five-num">
              <span>最低 {{ row.scoreDistribution.min }}</span>
              <span>Q1 {{ row.scoreDistribution.q1 }}</span>
              <span class="median">中位 {{ row.scoreDistribution.median }}</span>
              <span>Q3 {{ row.scoreDistribution.q3 }}</span>
              <span>最高 {{ row.scoreDistribution.max }}</span>
            </div>
          </div>
          <p
            v-else
            class="no-data"
          >
            本章暂无提交
          </p>
        </article>
      </div>
    </template>
  </section>
</template>

<style scoped>
.chapter-tests { display: grid; gap: 12px; }
.banner {
  padding: 10px 14px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-md);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
  margin: 0;
}
.banner--error { background: var(--pyt-color-danger-bg); color: var(--pyt-color-danger); }
.empty {
  padding: 16px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
  color: var(--pyt-color-text-hint);
}
.list { display: grid; gap: 10px; }
.card {
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  padding: 10px 12px;
  display: grid;
  gap: 6px;
  box-shadow: var(--pyt-shadow-card);
}
.card__head { display: flex; align-items: baseline; justify-content: space-between; }
.card__title {
  font-size: var(--pyt-font-size-base);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
}
.card__rate { font-size: var(--pyt-font-size-sm); font-weight: 700; }
.card__stats {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
}
.boxplot-wrap { display: grid; gap: 4px; }
.boxplot {
  width: 100%;
  height: 28px;
  background: #fffbe9;
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-sm);
}
.five-num {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: var(--pyt-color-text-hint);
}
.five-num .median { color: var(--pyt-color-text-primary); font-weight: 600; }
.no-data { margin: 0; font-size: var(--pyt-font-size-xs); color: var(--pyt-color-text-hint); }
</style>
