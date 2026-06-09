<script setup lang="ts">
import { computed } from 'vue';
import type { KcHeatmapEntryDto } from '@/api/dto/TeacherDashboard';

const props = defineProps<{ entries: KcHeatmapEntryDto[] }>();

interface Cell {
  kcCode: string;
  kcName: string;
  avgMastery: number;
  studentCount: number;
  masteredCount: number;
  strugglingCount: number;
}

const grouped = computed<Map<number, Cell[]>>(() => {
  const map = new Map<number, Cell[]>();
  for (const entry of props.entries) {
    const chapterId = entry.chapterId ?? 0;
    if (!map.has(chapterId)) {
      map.set(chapterId, []);
    }
    map.get(chapterId)!.push({
      kcCode: entry.kcCode,
      kcName: entry.kcName ?? entry.kcCode,
      avgMastery: entry.avgMastery,
      studentCount: entry.studentCount,
      masteredCount: entry.masteredCount,
      strugglingCount: entry.strugglingCount,
    });
  }
  return map;
});

// 热力梯度：数据可视化语义色（红=薄弱→绿=掌握），集中此处便于统一调整。
const HEAT_SCALE = [
  { max: 0.2, color: '#c0392b', label: '掌握 < 0.2' },
  { max: 0.5, color: '#e08a3c', label: '0.2 – 0.5' },
  { max: 0.8, color: '#d4a72c', label: '0.5 – 0.8' },
  { max: Infinity, color: '#2f8f6f', label: '≥ 0.8' },
] as const;

function color(mastery: number): string {
  return (HEAT_SCALE.find((step) => mastery < step.max) ?? HEAT_SCALE[HEAT_SCALE.length - 1]!).color;
}

function fmt(value: number): string {
  return value.toFixed(2);
}

function pct(value: number): string {
  return Math.round(value * 100) + '%';
}
</script>

<template>
  <section
    class="kc-heatmap"
    data-testid="kc-heatmap"
  >
    <p
      v-if="entries.length === 0"
      class="empty"
    >
      暂无 KC 数据。学生进入知识田之后，热力图会被填充。
    </p>
    <template v-else>
      <div class="legend">
        <span
          v-for="step in HEAT_SCALE"
          :key="step.label"
          class="legend-item"
        >
          <span class="swatch" :style="{ background: step.color }" />{{ step.label }}
        </span>
      </div>
      <div
        v-for="[chapterId, cells] in grouped"
        :key="chapterId"
        class="chapter"
      >
        <h4 class="chapter__title">
          {{ chapterId === 0 ? '未归章 KC' : '第 ' + chapterId + ' 章' }}
          <span class="chapter__count">{{ cells.length }} KC</span>
        </h4>
        <div class="row">
          <div
            v-for="cell in cells"
            :key="cell.kcCode"
            class="cell"
            :title="cell.kcName + '\n' + cell.kcCode + '\n平均掌握 ' + fmt(cell.avgMastery) + '\n已掌握 ' + cell.masteredCount + '/' + cell.studentCount"
          >
            <span class="cell__code">{{ cell.kcCode }}</span>
            <span class="cell__value" :style="{ color: color(cell.avgMastery) }">{{ pct(cell.avgMastery) }}</span>
            <span class="cell__bar">
              <span
                class="cell__bar-fill"
                :style="{ width: pct(cell.avgMastery), background: color(cell.avgMastery) }"
              />
            </span>
            <span class="cell__meta">{{ cell.masteredCount }}/{{ cell.studentCount }} 掌握</span>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.kc-heatmap { display: grid; gap: 16px; }
.empty {
  padding: 16px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
  color: var(--pyt-color-text-hint);
}
/* ─── Legend ─── */
.legend {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  font-size: var(--pyt-font-size-xs);
  color: var(--pyt-color-text-muted);
  padding: 8px 12px;
  background: var(--pyt-color-bg-soft);
  border: 1px solid var(--pyt-color-border-soft);
  border-radius: var(--pyt-radius-md);
}
.legend-item { display: inline-flex; align-items: center; gap: 6px; }
.swatch {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: var(--pyt-radius-sm);
  border: 1px solid var(--pyt-color-border);
}
/* ─── Chapter group ─── */
.chapter { display: grid; gap: 8px; }
.chapter__title {
  margin: 0;
  font-size: var(--pyt-font-size-sm);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}
.chapter__count {
  font-size: var(--pyt-font-size-xs);
  font-family: var(--pyt-font-read);
  color: var(--pyt-color-text-muted);
  font-weight: 400;
}
.row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 8px;
}
/* ─── KC cell: 卡片 + 进度条（取代纯色块，可读性更高） ─── */
.cell {
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  padding: 8px 10px;
  display: grid;
  gap: 4px;
  box-shadow: var(--pyt-shadow-card);
  transition: border-color var(--pyt-motion-base), transform var(--pyt-motion-base);
}
.cell:hover {
  border-color: var(--pyt-color-gold);
  transform: translateY(-1px);
}
.cell__code {
  font-family: var(--pyt-font-mono);
  font-size: var(--pyt-font-size-xs);
  font-weight: 600;
  color: var(--pyt-color-text-hint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cell__value {
  font-size: var(--pyt-font-size-lg);
  font-weight: 700;
  font-family: var(--pyt-font-pixel);
  line-height: 1;
}
.cell__bar {
  height: 6px;
  background: var(--pyt-color-bg-sunken);
  border-radius: var(--pyt-radius-full);
  overflow: hidden;
}
.cell__bar-fill {
  display: block;
  height: 100%;
  border-radius: var(--pyt-radius-full);
  transition: width var(--pyt-motion-slow);
}
.cell__meta {
  font-size: 10px;
  color: var(--pyt-color-text-muted);
}
</style>
