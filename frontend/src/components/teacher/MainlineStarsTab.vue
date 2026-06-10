<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { fetchMainlineStars, type MainlineStarMatrixDto } from '@/api/teacher';

// 自取数 Tab：仅依赖 classroomId，主代理在 ClassroomDetail.vue 注册时透传该 prop 即可。
const props = defineProps<{ classroomId: number }>();

const data = ref<MainlineStarMatrixDto | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);

// 星级语义色：少星 → 多星，由浅暖色渐变到掌握绿（数据可视化语义色，集中此处便于统一调整）。
const STAR_LEVELS = [
  { key: 'zeroStar', label: '0★', color: '#d9c9a3' },
  { key: 'oneStar', label: '1★', color: '#e9b949' },
  { key: 'twoStar', label: '2★', color: '#f1c453' },
  { key: 'threeStar', label: '3★', color: '#2f8f6f' },
] as const;

interface Segment {
  label: string;
  color: string;
  count: number;
  pct: number;
}

interface ChapterBar {
  chapter: number;
  avgStars: number;
  segments: Segment[];
}

const totalStudents = computed<number>(() => data.value?.totalStudents ?? 0);

const bars = computed<ChapterBar[]>(() => {
  if (!data.value) return [];
  const total = Math.max(1, data.value.totalStudents);
  return data.value.chapters.map((row) => ({
    chapter: row.chapter,
    avgStars: row.avgStars,
    segments: STAR_LEVELS.map((level) => {
      const count = row[level.key];
      return {
        label: level.label,
        color: level.color,
        count,
        pct: (count / total) * 100,
      };
    }),
  }));
});

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    data.value = await fetchMainlineStars(props.classroomId);
  } catch (e) {
    error.value = e instanceof Error ? e.message : '星级分布加载失败';
  } finally {
    loading.value = false;
  }
}

onMounted(load);
watch(() => props.classroomId, load);
</script>

<template>
  <section
    class="mainline-stars"
    data-testid="mainline-stars-tab"
  >
    <p
      v-if="loading"
      class="banner"
      data-testid="stars-loading"
    >
      星级分布加载中…
    </p>
    <p
      v-else-if="error"
      class="banner banner--error"
      data-testid="stars-error"
    >
      {{ error }}
    </p>
    <p
      v-else-if="totalStudents === 0"
      class="empty"
      data-testid="stars-empty"
    >
      暂无班级成员或主线星级数据。学生通过章末大测试 / 补星后，这里会出现星级分布。
    </p>
    <template v-else>
      <div class="legend">
        <span
          v-for="level in STAR_LEVELS"
          :key="level.key"
          class="legend-item"
        >
          <span
            class="swatch"
            :style="{ background: level.color }"
          />{{ level.label }}
        </span>
        <span class="legend-total">班级 {{ totalStudents }} 人</span>
      </div>

      <div class="matrix">
        <div
          v-for="bar in bars"
          :key="bar.chapter"
          class="row"
          :data-testid="`star-row-${bar.chapter}`"
        >
          <span class="row__label">第 {{ bar.chapter }} 章</span>
          <span
            class="row__bar"
            role="img"
            :aria-label="`第 ${bar.chapter} 章星级分布`"
          >
            <span
              v-for="seg in bar.segments"
              :key="seg.label"
              class="seg"
              :style="{ width: seg.pct + '%', background: seg.color }"
              :title="`${seg.label}：${seg.count} 人`"
            >
              <span
                v-if="seg.count > 0"
                class="seg__count"
              >{{ seg.count }}</span>
            </span>
          </span>
          <span class="row__avg">均 {{ bar.avgStars.toFixed(2) }}★</span>
        </div>
      </div>

      <h3 class="subhead">
        总星排行 Top {{ data?.topStudents.length ?? 0 }}
      </h3>
      <table
        class="leaderboard"
        data-testid="star-topn"
      >
        <thead>
          <tr>
            <th>#</th>
            <th>学生</th>
            <th>总星</th>
            <th>已得星章节</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(stu, idx) in data?.topStudents ?? []"
            :key="stu.studentId"
          >
            <td>{{ idx + 1 }}</td>
            <td class="name">
              {{ stu.displayName }}
            </td>
            <td class="stars">
              {{ stu.totalStars }}
            </td>
            <td>{{ stu.starredChapters }} / 7</td>
          </tr>
        </tbody>
      </table>
    </template>
  </section>
</template>

<style scoped>
.mainline-stars { display: grid; gap: 14px; }
.banner {
  padding: 10px 14px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-md);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-muted);
  margin: 0;
}
.banner--error {
  background: var(--pyt-color-danger-bg);
  color: var(--pyt-color-danger);
}
.empty {
  padding: 16px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
  color: var(--pyt-color-text-hint);
}
.legend {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  align-items: center;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
  padding: 8px 12px;
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-md);
}
.legend-item { display: inline-flex; align-items: center; gap: 6px; }
.legend-total { margin-left: auto; font-weight: 600; color: var(--pyt-color-text-primary); }
.swatch {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: var(--pyt-radius-sm);
  border: 1px solid var(--pyt-color-border);
}
.matrix { display: grid; gap: 6px; }
.row {
  display: grid;
  grid-template-columns: 64px 1fr 72px;
  gap: 10px;
  align-items: center;
}
.row__label {
  font-size: var(--pyt-font-size-sm);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
}
.row__bar {
  display: flex;
  height: 22px;
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-sm);
  overflow: hidden;
  background: var(--pyt-color-bg-sunken);
}
.seg {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  transition: width var(--pyt-motion-slow);
}
.seg__count {
  font-size: 10px;
  font-weight: 700;
  color: var(--pyt-color-shadow);
  font-family: var(--pyt-font-pixel);
}
.row__avg {
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
  text-align: right;
}
.subhead {
  margin: 6px 0 0;
  font-size: var(--pyt-font-size-sm);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
}
.leaderboard {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--pyt-font-size-sm);
}
.leaderboard th,
.leaderboard td {
  border: 1px solid var(--pyt-color-border);
  padding: 5px 8px;
  text-align: center;
}
.leaderboard th {
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-muted);
  font-weight: 600;
}
.leaderboard .name { text-align: left; font-weight: 600; color: var(--pyt-color-text-primary); }
.leaderboard .stars { font-family: var(--pyt-font-pixel); color: var(--pyt-color-primary-dark); font-weight: 700; }
</style>
