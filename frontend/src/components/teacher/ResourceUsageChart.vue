<script setup lang="ts">
import { computed } from 'vue';
import type { ResourceUsageDto } from '@/api/dto/TeacherDashboard';

const props = defineProps<{ usage: ResourceUsageDto | null }>();

const types = computed<string[]>(() => {
  if (!props.usage) return [];
  return Array.from(new Set(props.usage.totals.map((t) => t.resourceType)));
});

const days = computed<string[]>(() => {
  if (!props.usage) return [];
  return Array.from(new Set(props.usage.daily.map((d) => d.day))).sort();
});

const max = computed<number>(() => {
  if (!props.usage) return 0;
  return props.usage.daily.reduce((acc, cur) => Math.max(acc, cur.count), 0) || 1;
});

const palette = ['#2f5b8a', '#2a9d8f', '#e9c46a', '#f4a261', '#e76f51', '#9b5de5'];

function colorOf(type: string): string {
  const idx = types.value.indexOf(type);
  return palette[idx % palette.length] ?? palette[0]!;
}

function pointsFor(type: string): string {
  if (!props.usage || days.value.length === 0) return '';
  const width = 320;
  const height = 120;
  const padding = 24;
  const step = days.value.length > 1 ? (width - padding * 2) / (days.value.length - 1) : 0;
  const map = new Map<string, number>();
  for (const entry of props.usage.daily.filter((d) => d.resourceType === type)) {
    map.set(entry.day, entry.count);
  }
  return days.value
    .map((day, idx) => {
      const value = map.get(day) ?? 0;
      const x = padding + idx * step;
      const y = height - padding - (value / max.value) * (height - padding * 2);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');
}
</script>

<template>
  <section
    class="resource-usage"
    data-testid="resource-usage"
  >
    <p
      v-if="!usage || usage.totals.length === 0"
      class="empty"
    >
      暂无资源使用数据。当班级学生在工坊生成资源后，这里会出现折线。
    </p>
    <template v-else>
      <div class="legend">
        <span
          v-for="type in types"
          :key="type"
          class="legend-item"
        >
          <span
            class="swatch"
            :style="{ background: colorOf(type) }"
          />
          {{ type }}
        </span>
      </div>
      <svg
        class="chart"
        viewBox="0 0 320 120"
        role="img"
        aria-label="资源使用 7 天折线"
      >
        <line
          x1="24"
          y1="96"
          x2="296"
          y2="96"
          stroke="#c9a86b"
          stroke-width="1"
        />
        <line
          x1="24"
          y1="24"
          x2="24"
          y2="96"
          stroke="#c9a86b"
          stroke-width="1"
        />
        <text
          v-for="(day, idx) in days"
          :key="day"
          :x="24 + (idx * (272 / Math.max(1, days.length - 1)))"
          y="112"
          font-size="9"
          fill="#6e4f1d"
          text-anchor="middle"
        >
          {{ (day ?? '').slice(5) }}
        </text>
        <polyline
          v-for="type in types"
          :key="type"
          fill="none"
          :stroke="colorOf(type)"
          stroke-width="2"
          :points="pointsFor(type)"
        />
      </svg>
      <table class="totals">
        <thead>
          <tr>
            <th>资源类型</th>
            <th>近 7 天数量</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in usage.totals"
            :key="row.resourceType"
          >
            <td>{{ row.resourceType }}</td>
            <td>{{ row.count }}</td>
          </tr>
        </tbody>
      </table>
    </template>
  </section>
</template>

<style scoped>
.resource-usage {
  display: grid;
  gap: 10px;
}
.empty {
  padding: 16px;
  background: var(--pyt-color-bg-soft);
  border-radius: var(--pyt-radius-lg);
  text-align: center;
}
.legend {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12px;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.swatch {
  display: inline-block;
  width: 14px;
  height: 4px;
  border-radius: 3px;
}
.chart {
  width: 100%;
  height: 140px;
  background: #fffbe9;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
}
.totals {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.totals th,
.totals td {
  border: 1px solid var(--pyt-color-border);
  padding: 4px 8px;
  text-align: center;
}
</style>
