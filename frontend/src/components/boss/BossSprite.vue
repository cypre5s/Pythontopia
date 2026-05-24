<script setup lang="ts">
/**
 * R-13 §5 Phase 8 续：7 章 boss 像素 sprite 立绘（替换 BossPage 占位 emoji 👾）。
 *
 * 设计：纯 SVG inline 绘制 16x16 像素网格，按 chapter 切换调色板 + 形状。
 *   - 1 章 NameError 雾团：紫色雾气 + 问号眼
 *   - 2 章 TypeError 章鱼：青触手 + 大眼
 *   - 3 章 ConditionBug 巨蝙：黑翅 + 红瞳
 *   - 4 章 LoopHang 巨蛇：绿蛇 + 黄信
 *   - 5 章 RecursionBoss 镜像：蓝镜面 + 反射
 *   - 6 章 InheritanceBoss 灵兽：金兽 + 王冠
 *   - 7 章 SyntheticBoss 混合体：彩色拼接 + 多眼
 *
 * 状态 phase 控制简单动画：idle 上下浮动 / hurt 红色 shake / angry 抖动 / victory 上扬。
 * 不引入 GSAP（演示 Beta 用纯 CSS keyframes 已够；R-14 接入 GSAP 做更丝滑的 boss 入场转场）。
 */
import { computed } from 'vue';

interface Props {
  chapter: number;
  phase?: 'idle' | 'hurt' | 'angry' | 'victory';
  size?: number;
}

const props = withDefaults(defineProps<Props>(), {
  phase: 'idle',
  size: 96,
});

interface PixelDef {
  r: number;
  c: number;
  fill: string;
}

interface BossDesign {
  /** 16x16 grid，每个 {r,c,fill} 落一个像素方块。 */
  pixels: PixelDef[];
  /** 主色（用于 box-shadow halo / 边框） */
  accent: string;
}

const GRID = 16;

function rect(r: number, c: number, fill: string): PixelDef {
  return { r, c, fill };
}

function box(r1: number, r2: number, c1: number, c2: number, fill: string): PixelDef[] {
  const out: PixelDef[] = [];
  for (let r = r1; r <= r2; r += 1) {
    for (let c = c1; c <= c2; c += 1) {
      out.push(rect(r, c, fill));
    }
  }
  return out;
}

const DESIGNS: Record<number, BossDesign> = {
  // 1 章 NameError 雾团
  1: {
    accent: '#9d4edd',
    pixels: [
      ...box(3, 10, 4, 11, '#5a189a'),
      ...box(2, 11, 5, 10, '#7b2cbf'),
      ...box(4, 9, 3, 12, '#9d4edd'),
      // 问号「?」眼睛
      rect(5, 5, '#ffd166'),
      rect(5, 6, '#ffd166'),
      rect(6, 7, '#ffd166'),
      rect(7, 6, '#ffd166'),
      rect(9, 6, '#ffd166'),
      rect(5, 9, '#ffd166'),
      rect(5, 10, '#ffd166'),
      rect(6, 11, '#ffd166'),
      rect(7, 10, '#ffd166'),
      rect(9, 10, '#ffd166'),
      // 雾气向下扩散
      rect(11, 4, '#c77dff'),
      rect(11, 6, '#c77dff'),
      rect(11, 9, '#c77dff'),
      rect(11, 11, '#c77dff'),
      rect(12, 5, '#c77dff'),
      rect(12, 10, '#c77dff'),
    ],
  },
  // 2 章 TypeError 章鱼
  2: {
    accent: '#06b6d4',
    pixels: [
      // 头
      ...box(2, 6, 5, 10, '#0e7490'),
      ...box(3, 5, 4, 11, '#06b6d4'),
      ...box(3, 4, 6, 9, '#22d3ee'),
      // 眼睛
      rect(4, 6, '#ffffff'),
      rect(4, 9, '#ffffff'),
      rect(5, 6, '#000000'),
      rect(5, 9, '#000000'),
      // 触手 4 条
      rect(7, 4, '#0e7490'),
      rect(8, 3, '#0e7490'),
      rect(9, 4, '#0e7490'),
      rect(10, 3, '#0e7490'),
      rect(11, 4, '#0e7490'),
      rect(7, 6, '#0e7490'),
      rect(8, 6, '#06b6d4'),
      rect(9, 6, '#0e7490'),
      rect(10, 6, '#06b6d4'),
      rect(11, 6, '#0e7490'),
      rect(7, 9, '#0e7490'),
      rect(8, 9, '#06b6d4'),
      rect(9, 9, '#0e7490'),
      rect(10, 9, '#06b6d4'),
      rect(11, 9, '#0e7490'),
      rect(7, 11, '#0e7490'),
      rect(8, 12, '#0e7490'),
      rect(9, 11, '#0e7490'),
      rect(10, 12, '#0e7490'),
      rect(11, 11, '#0e7490'),
    ],
  },
  // 3 章 ConditionBug 巨蝙
  3: {
    accent: '#374151',
    pixels: [
      // 身体
      ...box(5, 9, 6, 9, '#111827'),
      ...box(6, 8, 7, 8, '#374151'),
      // 头部
      rect(4, 6, '#111827'),
      rect(4, 9, '#111827'),
      rect(3, 6, '#111827'),
      rect(3, 9, '#111827'),
      // 红眼
      rect(6, 7, '#ef4444'),
      rect(6, 8, '#ef4444'),
      // 左翅
      rect(5, 5, '#374151'),
      rect(5, 4, '#374151'),
      rect(6, 3, '#374151'),
      rect(7, 2, '#374151'),
      rect(8, 3, '#374151'),
      rect(9, 4, '#374151'),
      rect(8, 5, '#111827'),
      // 右翅
      rect(5, 10, '#374151'),
      rect(5, 11, '#374151'),
      rect(6, 12, '#374151'),
      rect(7, 13, '#374151'),
      rect(8, 12, '#374151'),
      rect(9, 11, '#374151'),
      rect(8, 10, '#111827'),
    ],
  },
  // 4 章 LoopHang 巨蛇
  4: {
    accent: '#10b981',
    pixels: [
      // S 形蛇
      ...box(3, 4, 4, 8, '#065f46'),
      ...box(3, 4, 9, 11, '#10b981'),
      ...box(5, 6, 9, 11, '#065f46'),
      ...box(5, 6, 6, 8, '#10b981'),
      ...box(7, 8, 4, 6, '#065f46'),
      ...box(7, 8, 7, 9, '#10b981'),
      ...box(9, 10, 4, 6, '#10b981'),
      ...box(9, 10, 7, 11, '#065f46'),
      // 头 + 眼
      rect(3, 11, '#34d399'),
      rect(3, 12, '#34d399'),
      rect(4, 12, '#34d399'),
      rect(4, 11, '#000000'),
      // 信子
      rect(2, 12, '#ef4444'),
      rect(2, 13, '#ef4444'),
    ],
  },
  // 5 章 RecursionBoss 镜像
  5: {
    accent: '#3b82f6',
    pixels: [
      // 镜面 frame
      ...box(2, 13, 4, 11, '#1e3a8a'),
      ...box(3, 12, 5, 10, '#3b82f6'),
      // 镜面闪光对角
      rect(4, 6, '#dbeafe'),
      rect(5, 7, '#bfdbfe'),
      rect(6, 8, '#93c5fd'),
      rect(7, 9, '#60a5fa'),
      rect(8, 8, '#93c5fd'),
      rect(9, 7, '#bfdbfe'),
      rect(10, 6, '#dbeafe'),
      // 双瞳
      rect(7, 6, '#ffffff'),
      rect(7, 10, '#ffffff'),
      rect(8, 6, '#000000'),
      rect(8, 10, '#000000'),
    ],
  },
  // 6 章 InheritanceBoss 灵兽
  6: {
    accent: '#f59e0b',
    pixels: [
      // 王冠
      rect(2, 6, '#fbbf24'),
      rect(2, 9, '#fbbf24'),
      rect(2, 7, '#facc15'),
      rect(2, 8, '#facc15'),
      rect(3, 6, '#fbbf24'),
      rect(3, 7, '#fbbf24'),
      rect(3, 8, '#fbbf24'),
      rect(3, 9, '#fbbf24'),
      // 头
      ...box(4, 7, 5, 10, '#d97706'),
      ...box(5, 6, 6, 9, '#f59e0b'),
      // 眼
      rect(5, 6, '#ffffff'),
      rect(5, 9, '#ffffff'),
      rect(6, 6, '#000000'),
      rect(6, 9, '#000000'),
      // 身体
      ...box(8, 11, 5, 10, '#92400e'),
      ...box(9, 10, 6, 9, '#d97706'),
      // 鬃毛
      rect(4, 4, '#f59e0b'),
      rect(5, 3, '#f59e0b'),
      rect(6, 4, '#f59e0b'),
      rect(4, 11, '#f59e0b'),
      rect(5, 12, '#f59e0b'),
      rect(6, 11, '#f59e0b'),
    ],
  },
  // 7 章 SyntheticBoss 混合体
  7: {
    accent: '#ec4899',
    pixels: [
      ...box(3, 4, 4, 7, '#ec4899'),
      ...box(3, 4, 8, 11, '#8b5cf6'),
      ...box(5, 7, 4, 7, '#22d3ee'),
      ...box(5, 7, 8, 11, '#fbbf24'),
      ...box(8, 10, 4, 7, '#10b981'),
      ...box(8, 10, 8, 11, '#ef4444'),
      ...box(11, 12, 4, 11, '#374151'),
      rect(4, 5, '#ffffff'),
      rect(4, 6, '#000000'),
      rect(4, 9, '#ffffff'),
      rect(4, 10, '#000000'),
      rect(6, 5, '#ffffff'),
      rect(6, 10, '#000000'),
      rect(9, 5, '#000000'),
      rect(9, 10, '#ffffff'),
    ],
  },
};

const fallback = DESIGNS[1] as BossDesign;
const design = computed<BossDesign>(() => DESIGNS[props.chapter] ?? fallback);
const pixels = computed<PixelDef[]>(() => design.value.pixels);
const phaseClass = computed<string>(() => `boss-sprite--${props.phase}`);
</script>

<template>
  <svg
    class="boss-sprite"
    :class="phaseClass"
    :width="size ?? 96"
    :height="size ?? 96"
    viewBox="0 0 16 16"
    role="img"
    :aria-label="`第 ${chapter} 章 Boss 立绘`"
    shape-rendering="crispEdges"
  >
    <rect
      v-for="(p, idx) in pixels"
      :key="`${idx}-${p.r}-${p.c}`"
      :x="p.c"
      :y="p.r"
      width="1"
      height="1"
      :fill="p.fill"
    />
  </svg>
</template>

<style scoped>
.boss-sprite {
  image-rendering: pixelated;
  filter: drop-shadow(0 0 8px rgba(255, 80, 80, 0.45));
  display: block;
}
.boss-sprite--idle {
  animation: boss-sprite-bob 1.6s ease-in-out infinite;
}
.boss-sprite--hurt {
  animation: boss-sprite-hurt 0.5s ease-out 2;
}
.boss-sprite--angry {
  animation: boss-sprite-angry 0.32s ease-out infinite;
}
.boss-sprite--victory {
  animation: boss-sprite-victory 1.4s ease-out 1;
}

@keyframes boss-sprite-bob {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-6px);
  }
}
@keyframes boss-sprite-hurt {
  0% {
    transform: scale(1.18) rotate(8deg);
    filter: drop-shadow(0 0 12px rgba(255, 80, 80, 0.8)) sepia(0.6) hue-rotate(320deg);
  }
  100% {
    transform: scale(1) rotate(0);
    filter: drop-shadow(0 0 8px rgba(255, 80, 80, 0.45));
  }
}
@keyframes boss-sprite-angry {
  0%,
  100% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-4px) rotate(-2deg);
  }
  75% {
    transform: translateX(4px) rotate(2deg);
  }
}
@keyframes boss-sprite-victory {
  0% {
    transform: scale(1.2);
    opacity: 1;
    filter: drop-shadow(0 0 16px rgba(255, 215, 0, 0.95));
  }
  100% {
    transform: scale(0.6) translateY(-32px);
    opacity: 0;
    filter: drop-shadow(0 0 4px rgba(255, 215, 0, 0.2));
  }
}

@media (prefers-reduced-motion: reduce) {
  .boss-sprite {
    animation: none !important;
  }
}
</style>
