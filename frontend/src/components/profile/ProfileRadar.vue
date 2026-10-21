<script setup lang="ts">
/**
 * 8 维学习画像辅助图（纯 SVG）。只绘制达到证据门槛的维度，避免把默认中性值当成测评结果。
 */
import { computed } from 'vue';
import type { ProfileDimensionDto } from '@/api/learner';

const props = defineProps<{ dimensions: ProfileDimensionDto[] }>();

// 学生侧友好别名：把偏学术的维度名换成口语短label（仅显示，data key 不变）；未列出的用后端 label。
const FRIENDLY_LABELS: Readonly<Record<string, string>> = {
  cognitive_style: '风格（不评估）',
  emotion_persistence: '情绪（不推断）',
  error_pattern: '易错点',
};
function displayLabel(dim: ProfileDimensionDto): string {
  return FRIENDLY_LABELS[dim.key] ?? dim.label;
}

const SIZE = 360;
const CENTER = SIZE / 2;
const RADIUS = 120;
const RINGS = [0.25, 0.5, 0.75, 1];

interface Axis {
  label: string;
  score: number | null;
  anchor: 'start' | 'middle' | 'end';
  x: number;
  y: number;
  lx: number;
  ly: number;
}

function pointAt(angle: number, radius: number): { x: number; y: number } {
  return {
    x: CENTER + radius * Math.cos(angle),
    y: CENTER + radius * Math.sin(angle),
  };
}

const axes = computed<Axis[]>(() => {
  const n = props.dimensions.length || 1;
  return props.dimensions.map((dim, i) => {
    // 从正上方开始顺时针均分。
    const angle = -Math.PI / 2 + (i * 2 * Math.PI) / n;
    const score = dim.confidence >= 0.3 ? Math.max(0, Math.min(1, dim.score)) : null;
    const tip = pointAt(angle, RADIUS * (score ?? 0));
    const label = pointAt(angle, RADIUS + 22);
    const anchor = label.x < CENTER - 8 ? 'start' : label.x > CENTER + 8 ? 'end' : 'middle';
    return { label: displayLabel(dim), score, anchor, x: tip.x, y: tip.y, lx: label.x, ly: label.y };
  });
});

const polygonPoints = computed(() => axes.value.every((axis) => axis.score !== null)
  ? axes.value.map((axis) => `${axis.x.toFixed(1)},${axis.y.toFixed(1)}`).join(' ')
  : '');

function ringPoints(scale: number): string {
  const n = props.dimensions.length || 1;
  const pts: string[] = [];
  for (let i = 0; i < n; i += 1) {
    const angle = -Math.PI / 2 + (i * 2 * Math.PI) / n;
    const p = pointAt(angle, RADIUS * scale);
    pts.push(`${p.x.toFixed(1)},${p.y.toFixed(1)}`);
  }
  return pts.join(' ');
}

function axisLine(a: Axis): { x2: number; y2: number } {
  const angle = Math.atan2(a.ly - CENTER, a.lx - CENTER);
  const end = pointAt(angle, RADIUS);
  return { x2: end.x, y2: end.y };
}
</script>

<template>
  <div
    class="radar"
    data-testid="profile-radar"
  >
    <svg
      :viewBox="`0 0 ${SIZE} ${SIZE}`"
      class="radar__svg"
      role="img"
      aria-label="学习画像辅助图；证据不足的维度不显示分值"
    >
      <polygon
        v-for="(scale, i) in RINGS"
        :key="`ring-${i}`"
        :points="ringPoints(scale)"
        class="radar__ring"
      />
      <line
        v-for="(a, i) in axes"
        :key="`axis-${i}`"
        :x1="CENTER"
        :y1="CENTER"
        :x2="axisLine(a).x2"
        :y2="axisLine(a).y2"
        class="radar__axis"
      />
      <polygon
        v-if="polygonPoints"
        :points="polygonPoints"
        class="radar__area"
      />
      <template
        v-for="(a, i) in axes"
        :key="`pt-${i}`"
      >
        <circle
          v-if="a.score !== null"
          :cx="a.x"
          :cy="a.y"
          r="3.5"
          class="radar__dot"
        />
      </template>
      <text
        v-for="(a, i) in axes"
        :key="`lbl-${i}`"
        :x="a.lx"
        :y="a.ly"
        class="radar__label"
        :text-anchor="a.anchor"
        dominant-baseline="middle"
      >{{ a.label }}<title>{{ a.score === null ? `${a.label}：尚未评估` : `${a.label}：${Math.round(a.score * 100)}%` }}</title></text>
    </svg>
  </div>
</template>

<style scoped>
.radar {
  display: flex;
  justify-content: center;
  padding: 8px;
}
.radar__svg {
  width: 100%;
  max-width: 360px;
  height: auto;
  font-family: var(--pyt-font-pixel);
}
.radar__ring {
  fill: none;
  stroke: var(--pyt-color-border);
  stroke-width: 1;
  opacity: 0.5;
}
.radar__axis {
  stroke: var(--pyt-color-border);
  stroke-width: 1;
  opacity: 0.6;
}
.radar__area {
  fill: rgba(79, 138, 62, 0.35);
  stroke: #4f8a3e;
  stroke-width: 2;
}
.radar__dot {
  fill: #2b6021;
  stroke: var(--pyt-color-bg-elevated);
  stroke-width: 1;
}
.radar__label {
  font-size: 11px;
  fill: var(--pyt-color-text-primary);
}
</style>
