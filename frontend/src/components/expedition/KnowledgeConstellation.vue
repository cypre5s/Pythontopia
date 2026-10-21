<script setup lang="ts">
/**
 * 知识星座：65 个 KC 按章节排成星团，按掌握度点亮。无尽远征的跨局 meta 进度可视化。
 * 纯展示组件——掌握度由 prop 注入（便于测试与复用），不直接读 store。
 */
import { computed } from 'vue';
import {
  buildConstellation,
  CONSTELLATION_LIT_THRESHOLD,
  type ConstellationNode,
} from '@/data/kcConstellation';
import { kcDisplayName } from '@/data/kcNames';

const props = withDefaults(
  defineProps<{
    /** KC 编码 → 掌握度 0..1。 */
    mastery?: Record<string, number>;
    /** 已有服务端评估证据的 KC；省略时兼容旧调用方按 mastery keys 判断。 */
    // eslint-disable-next-line vue/require-default-prop -- undefined intentionally enables mastery-key inference.
    assessed?: string[];
  }>(),
  { mastery: () => ({}) },
);

const constellation = buildConstellation();
const nodeById = computed<Record<string, ConstellationNode>>(() => {
  const map: Record<string, ConstellationNode> = {};
  for (const n of constellation.nodes) map[n.code] = n;
  return map;
});

function scoreOf(code: string): number | null {
  return props.mastery[code] ?? null;
}
const assessedCodes = computed(() => new Set(props.assessed ?? Object.keys(props.mastery)));
function isAssessed(code: string): boolean {
  return assessedCodes.value.has(code);
}
const assessedCodes = computed(() => new Set(props.assessed ?? Object.keys(props.mastery)));
function isAssessed(code: string): boolean {
  return assessedCodes.value.has(code);
}
function radiusOf(code: string): number {
  const score = scoreOf(code);
  return isAssessed(code) && score != null ? 4 + score * 4 : 3.5;
}
function fillOf(code: string): string {
  if (!isAssessed(code)) return 'rgba(177, 183, 196, 0.38)';
  const s = scoreOf(code);
  if (s == null) return 'rgba(177, 183, 196, 0.38)';
  if (s >= CONSTELLATION_LIT_THRESHOLD) return '#43a45b'; // 已掌握：点亮绿
  if (s >= 0.2) return '#d99f3a'; // 学习中：金
  return 'rgba(74, 54, 29, 0.45)'; // 有评估证据但当前掌握度偏低
}

const litCount = computed(() => constellation.nodes.filter((node) => {
  const score = scoreOf(node.code);
  return isAssessed(node.code) && score != null && score >= CONSTELLATION_LIT_THRESHOLD;
}).length);
const assessedCount = computed(() => constellation.nodes.filter((node) => isAssessed(node.code)).length);
const total = computed(() => constellation.nodes.length);
function titleFor(code: string): string {
  if (!isAssessed(code)) return `${kcDisplayName(code)} · 尚未评估`;
  const score = scoreOf(code);
  return score == null
    ? `${kcDisplayName(code)} · 已评估 · 掌握度暂无可比数据`
    : `${kcDisplayName(code)} · 掌握 ${Math.round(score * 100)}%`;
}
</script>

<template>
  <figure
    class="kc-constellation"
    data-testid="kc-constellation"
  >
    <figcaption class="kc-constellation__cap">
      知识星座 · 已点亮 <strong>{{ litCount }}</strong> · 已评估 {{ assessedCount }} / {{ total }} 个知识点
    </figcaption>
    <svg
      class="kc-constellation__svg"
      :viewBox="`0 0 ${constellation.width} ${constellation.height}`"
      role="img"
      aria-label="知识星座：按章节排列的 65 个知识点，按掌握度点亮"
    >
      <line
        v-for="(e, i) in constellation.edges"
        :key="`e-${i}`"
        class="kc-edge"
        :x1="nodeById[e.a]?.x"
        :y1="nodeById[e.a]?.y"
        :x2="nodeById[e.b]?.x"
        :y2="nodeById[e.b]?.y"
      />
      <g
        v-for="n in constellation.nodes"
        :key="n.code"
      >
        <circle
          class="kc-star"
          :cx="n.x"
          :cy="n.y"
          :r="radiusOf(n.code)"
          :fill="fillOf(n.code)"
          :data-testid="`kc-star-${n.code}`"
        >
          <title>{{ titleFor(n.code) }}</title>
        </circle>
      </g>
    </svg>
    <div class="kc-constellation__legend">
      <span><i class="dot dot--lit" /> 已掌握</span>
      <span><i class="dot dot--learning" /> 学习中</span>
      <span><i class="dot dot--dim" /> 尚未评估</span>
    </div>
  </figure>
</template>

<style scoped>
.kc-constellation {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.kc-constellation__cap {
  font-size: 13px;
  color: var(--pyt-color-text-hint);
  text-align: center;
}
.kc-constellation__cap strong {
  color: var(--pyt-color-success);
}
.kc-constellation__svg {
  width: 100%;
  height: auto;
  max-height: 60vh;
  background:
    radial-gradient(circle at 50% 45%, rgba(42, 64, 110, 0.16), transparent 60%),
    rgba(20, 26, 38, 0.92);
  border: 2px solid var(--pyt-color-border);
  border-radius: 10px;
}
.kc-edge {
  stroke: rgba(120, 150, 200, 0.18);
  stroke-width: 1;
}
.kc-star {
  transition: r 200ms ease;
}
.kc-constellation__legend {
  display: flex;
  justify-content: center;
  gap: 16px;
  font-size: 12px;
  color: var(--pyt-color-text-muted);
}
.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}
.dot--lit {
  background: #43a45b;
}
.dot--learning {
  background: #d99f3a;
}
.dot--dim {
  background: rgba(120, 120, 120, 0.5);
}
</style>
