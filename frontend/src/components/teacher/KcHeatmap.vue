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

function color(mastery: number): string {
  if (mastery < 0.2) return '#e76f51';
  if (mastery < 0.5) return '#f4a261';
  if (mastery < 0.8) return '#e9c46a';
  return '#2a9d8f';
}

function fmt(value: number): string {
  return value.toFixed(2);
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
    <div
      v-else
      class="legend"
    >
      <span class="legend-item"><span
        class="swatch"
        style="background:#e76f51"
      />掌握 &lt; 0.2</span>
      <span class="legend-item"><span
        class="swatch"
        style="background:#f4a261"
      />0.2-0.5</span>
      <span class="legend-item"><span
        class="swatch"
        style="background:#e9c46a"
      />0.5-0.8</span>
      <span class="legend-item"><span
        class="swatch"
        style="background:#2a9d8f"
      />≥ 0.8</span>
    </div>
    <div
      v-for="[chapterId, cells] in grouped"
      :key="chapterId"
      class="chapter"
    >
      <h4>{{ chapterId === 0 ? '未归章 KC' : '第 ' + chapterId + ' 章' }}</h4>
      <div class="row">
        <div
          v-for="cell in cells"
          :key="cell.kcCode"
          class="cell"
          :style="{ backgroundColor: color(cell.avgMastery) }"
          :title="cell.kcName + '\n' + cell.kcCode + '\n平均掌握 ' + fmt(cell.avgMastery) + '\n已掌握 ' + cell.masteredCount + '/' + cell.studentCount"
        >
          <span class="code">{{ cell.kcCode }}</span>
          <span class="value">{{ fmt(cell.avgMastery) }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.kc-heatmap {
  display: grid;
  gap: 12px;
}
.empty {
  padding: 16px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
}
.legend {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12px;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.swatch {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 3px;
  border: 1px solid var(--pyt-color-border);
}
.chapter h4 {
  margin: 4px 0;
  font-size: 13px;
  color: var(--pyt-color-text-primary);
}
.row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(72px, 1fr));
  gap: 6px;
}
.cell {
  border-radius: 6px;
  padding: 6px;
  color: #1d1306;
  display: grid;
  gap: 2px;
  align-content: space-between;
  height: 56px;
  font-family: 'JetBrains Mono', 'Menlo', monospace;
  font-size: 11px;
}
.cell .code {
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cell .value {
  text-align: right;
  font-size: 13px;
  font-weight: 700;
}
</style>
