<script setup lang="ts">
/**
 * R-13 §-1 BossSprite v3：7 章 boss 立绘 = AI 原创 PNG（高清）+ CSS 像素 fallback。
 *
 * v3 改造（用户 G·B 决策）：
 *   - 主路径：每章用 1024×1024 AI 原创像素 PNG（CC-BY-SA 3.0+ 入库），
 *     主题严格对齐 mainQuests.ts 的 boss 名（算盘灵 / 解释器学徒 / ...）+
 *     风格统一星露谷暖色调，与 LoginPage hero 一致；
 *   - Fallback 路径：PNG 加载失败时回退到 v2 的 CSS pixel-art rect 绘制
 *     （兼容 spec 测试 + 无网络场景）；
 *   - 视觉：image-rendering: pixelated 保持像素感，object-fit: contain
 *     居中放大到 size×size。
 *
 * 状态 phase 控制简单动画：idle 上下浮动 / hurt 红色 shake / angry 抖动 / victory 上扬。
 * 不引入 GSAP（演示 Beta 用纯 CSS keyframes 已够；R-14 接入 GSAP 做更丝滑的 boss 入场转场）。
 */
import { computed, ref } from 'vue';

interface Props {
  chapter: number;
  phase?: 'idle' | 'hurt' | 'angry' | 'victory';
  size?: number;
}

const props = withDefaults(defineProps<Props>(), {
  phase: 'idle',
  size: 96,
});

// R-13 §-1 §G·B：每章 boss 立绘 PNG 路径（CC-BY-SA 3.0+ 原创）。
const BOSS_PNG: Record<number, string> = {
  1: '/assets/pixel/bosses/boss-1-abacus-spirit.png',
  2: '/assets/pixel/bosses/boss-2-interpreter-apprentice.png',
  3: '/assets/pixel/bosses/boss-3-binary-cyclop.png',
  4: '/assets/pixel/bosses/boss-4-loop-tower.png',
  5: '/assets/pixel/bosses/boss-5-dict-prism.png',
  6: '/assets/pixel/bosses/boss-6-file-keeper.png',
  7: '/assets/pixel/bosses/boss-7-mirror-abstraction.png',
};

const pngSrc = computed<string | null>(() => BOSS_PNG[props.chapter] ?? null);
const pngLoadFailed = ref<boolean>(false);

function onPngError(): void {
  pngLoadFailed.value = true;
}

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
  // 1 章 算盘灵 Abacus Spirit — 木质算盘框 + 5 行算珠 + 顶部框纹
  1: {
    accent: '#8a6c2c',
    pixels: [
      ...box(2, 2, 2, 13, '#4d3613'),
      ...box(13, 13, 2, 13, '#4d3613'),
      ...box(2, 13, 2, 2, '#4d3613'),
      ...box(2, 13, 13, 13, '#4d3613'),
      ...box(3, 12, 3, 12, '#c9a86b'),
      rect(4, 3, '#4d3613'),
      rect(4, 12, '#4d3613'),
      rect(7, 3, '#4d3613'),
      rect(7, 12, '#4d3613'),
      rect(10, 3, '#4d3613'),
      rect(10, 12, '#4d3613'),
      rect(5, 4, '#b8442d'),
      rect(5, 5, '#b8442d'),
      rect(5, 6, '#b8442d'),
      rect(5, 9, '#b8442d'),
      rect(5, 10, '#b8442d'),
      rect(5, 11, '#b8442d'),
      rect(6, 4, '#f59e0b'),
      rect(6, 5, '#f59e0b'),
      rect(6, 10, '#f59e0b'),
      rect(6, 11, '#f59e0b'),
      rect(8, 4, '#1e3a8a'),
      rect(8, 7, '#1e3a8a'),
      rect(8, 8, '#1e3a8a'),
      rect(8, 11, '#1e3a8a'),
      rect(9, 4, '#1e3a8a'),
      rect(9, 5, '#1e3a8a'),
      rect(9, 11, '#1e3a8a'),
      rect(11, 4, '#065f46'),
      rect(11, 5, '#065f46'),
      rect(11, 6, '#065f46'),
      rect(11, 7, '#065f46'),
      rect(11, 10, '#065f46'),
      rect(11, 11, '#065f46'),
      rect(12, 7, '#facc15'),
      rect(12, 8, '#facc15'),
    ],
  },
  // 2 章 解释器学徒 Interpreter — 终端框 + Python 头巾 + 黄白光标 + > prompt
  2: {
    accent: '#3776ab',
    pixels: [
      ...box(2, 13, 2, 13, '#1e293b'),
      ...box(3, 12, 3, 12, '#0f172a'),
      rect(2, 13, '#ef4444'),
      rect(2, 11, '#ffd166'),
      rect(2, 12, '#22d3ee'),
      rect(5, 4, '#facc15'),
      rect(5, 5, '#facc15'),
      rect(6, 4, '#fff7e6'),
      rect(6, 5, '#3776ab'),
      rect(6, 6, '#3776ab'),
      rect(6, 7, '#ffd166'),
      rect(6, 8, '#ffd166'),
      rect(6, 9, '#3776ab'),
      rect(6, 10, '#3776ab'),
      rect(7, 5, '#3776ab'),
      rect(7, 6, '#3776ab'),
      rect(7, 9, '#3776ab'),
      rect(7, 10, '#3776ab'),
      rect(7, 7, '#facc15'),
      rect(7, 8, '#facc15'),
      rect(9, 4, '#fff7e6'),
      rect(9, 5, '#fff7e6'),
      rect(10, 5, '#fff7e6'),
      rect(11, 6, '#fff7e6'),
      rect(11, 7, '#fff7e6'),
      rect(11, 8, '#fff7e6'),
      rect(11, 9, '#fff7e6'),
      rect(11, 10, '#fff7e6'),
      rect(11, 11, '#ffd166'),
    ],
  },
  // 3 章 二进制独眼 Binary Cyclop — 中央巨眼 + 周围 0/1 比特环
  3: {
    accent: '#0f172a',
    pixels: [
      ...box(3, 12, 3, 12, '#0f172a'),
      ...box(4, 11, 4, 11, '#1e293b'),
      ...box(5, 10, 5, 10, '#1e3a8a'),
      ...box(6, 9, 6, 9, '#3b82f6'),
      ...box(7, 8, 7, 8, '#fff7e6'),
      rect(7, 7, '#0f172a'),
      rect(7, 8, '#0f172a'),
      rect(8, 7, '#0f172a'),
      rect(8, 8, '#0f172a'),
      rect(3, 5, '#facc15'),
      rect(3, 6, '#fff7e6'),
      rect(3, 9, '#facc15'),
      rect(3, 10, '#fff7e6'),
      rect(12, 5, '#fff7e6'),
      rect(12, 6, '#facc15'),
      rect(12, 9, '#fff7e6'),
      rect(12, 10, '#facc15'),
      rect(5, 2, '#facc15'),
      rect(8, 2, '#fff7e6'),
      rect(10, 2, '#facc15'),
      rect(5, 13, '#fff7e6'),
      rect(8, 13, '#facc15'),
      rect(10, 13, '#fff7e6'),
    ],
  },
  // 4 章 流程钟楼 Loop Tower — 高塔 + 顶部圆钟 + 时针指针
  4: {
    accent: '#6e4f1d',
    pixels: [
      rect(2, 6, '#facc15'),
      rect(2, 9, '#facc15'),
      rect(3, 6, '#6e4f1d'),
      rect(3, 9, '#6e4f1d'),
      ...box(4, 6, 5, 10, '#fff7e6'),
      ...box(4, 6, 6, 9, '#3776ab'),
      rect(5, 7, '#facc15'),
      rect(5, 8, '#facc15'),
      rect(6, 7, '#ef4444'),
      ...box(7, 8, 5, 10, '#6e4f1d'),
      ...box(7, 8, 6, 9, '#8a6c2c'),
      ...box(9, 12, 5, 10, '#c9a86b'),
      rect(9, 5, '#6e4f1d'),
      rect(9, 10, '#6e4f1d'),
      rect(10, 5, '#6e4f1d'),
      rect(10, 10, '#6e4f1d'),
      rect(11, 5, '#6e4f1d'),
      rect(11, 10, '#6e4f1d'),
      rect(12, 5, '#6e4f1d'),
      rect(12, 10, '#6e4f1d'),
      rect(13, 6, '#4d3613'),
      rect(13, 7, '#4d3613'),
      rect(13, 8, '#4d3613'),
      rect(13, 9, '#4d3613'),
      rect(10, 7, '#4d3613'),
      rect(10, 8, '#4d3613'),
      rect(11, 7, '#fff7e6'),
      rect(11, 8, '#fff7e6'),
    ],
  },
  // 5 章 集合棱镜 Dict Prism — 三色三角棱镜 list/dict/set 拼合
  5: {
    accent: '#ec4899',
    pixels: [
      rect(2, 7, '#ef4444'),
      rect(2, 8, '#ef4444'),
      rect(3, 6, '#ef4444'),
      rect(3, 7, '#ef4444'),
      rect(3, 8, '#ef4444'),
      rect(3, 9, '#ef4444'),
      rect(4, 5, '#ef4444'),
      rect(4, 6, '#f87171'),
      rect(4, 7, '#f87171'),
      rect(4, 8, '#f87171'),
      rect(4, 9, '#f87171'),
      rect(4, 10, '#ef4444'),
      rect(5, 4, '#10b981'),
      rect(5, 5, '#10b981'),
      rect(5, 6, '#10b981'),
      rect(5, 9, '#10b981'),
      rect(5, 10, '#10b981'),
      rect(5, 11, '#10b981'),
      rect(6, 3, '#10b981'),
      rect(6, 4, '#34d399'),
      rect(6, 5, '#34d399'),
      rect(6, 6, '#34d399'),
      rect(6, 9, '#34d399'),
      rect(6, 10, '#34d399'),
      rect(6, 11, '#34d399'),
      rect(6, 12, '#10b981'),
      rect(7, 3, '#3b82f6'),
      rect(7, 4, '#3b82f6'),
      rect(7, 11, '#3b82f6'),
      rect(7, 12, '#3b82f6'),
      rect(8, 4, '#60a5fa'),
      rect(8, 5, '#60a5fa'),
      rect(8, 6, '#60a5fa'),
      rect(8, 7, '#60a5fa'),
      rect(8, 8, '#60a5fa'),
      rect(8, 9, '#60a5fa'),
      rect(8, 10, '#60a5fa'),
      rect(8, 11, '#60a5fa'),
      rect(9, 5, '#3b82f6'),
      rect(9, 6, '#3b82f6'),
      rect(9, 9, '#3b82f6'),
      rect(9, 10, '#3b82f6'),
      rect(10, 6, '#1e3a8a'),
      rect(10, 7, '#1e3a8a'),
      rect(10, 8, '#1e3a8a'),
      rect(10, 9, '#1e3a8a'),
      rect(11, 7, '#facc15'),
      rect(11, 8, '#facc15'),
    ],
  },
  // 6 章 文档守护神 File Keeper — 翻开的卷轴 + 中央书签 + 锁
  6: {
    accent: '#065f46'   ,
    pixels: [
      ...box(3, 11, 2, 13, '#fff7e6'),
      ...box(3, 11, 2, 2, '#8a6c2c'),
      ...box(3, 11, 13, 13, '#8a6c2c'),
      rect(2, 3, '#4d3613'),
      rect(2, 12, '#4d3613'),
      rect(12, 3, '#4d3613'),
      rect(12, 12, '#4d3613'),
      rect(4, 4, '#0f172a'),
      rect(4, 5, '#0f172a'),
      rect(4, 6, '#0f172a'),
      rect(4, 7, '#0f172a'),
      rect(4, 8, '#0f172a'),
      rect(4, 9, '#0f172a'),
      rect(4, 10, '#0f172a'),
      rect(4, 11, '#0f172a'),
      rect(6, 4, '#0f172a'),
      rect(6, 5, '#0f172a'),
      rect(6, 6, '#0f172a'),
      rect(6, 9, '#0f172a'),
      rect(6, 10, '#0f172a'),
      rect(6, 11, '#0f172a'),
      rect(8, 4, '#0f172a'),
      rect(8, 5, '#0f172a'),
      rect(8, 6, '#0f172a'),
      rect(8, 7, '#0f172a'),
      rect(8, 10, '#0f172a'),
      rect(8, 11, '#0f172a'),
      rect(10, 4, '#0f172a'),
      rect(10, 5, '#0f172a'),
      rect(10, 6, '#0f172a'),
      rect(10, 9, '#0f172a'),
      rect(10, 10, '#0f172a'),
      rect(10, 11, '#0f172a'),
      rect(3, 7, '#ef4444'),
      rect(3, 8, '#ef4444'),
      rect(4, 7, '#ef4444'),
      rect(4, 8, '#ef4444'),
      rect(5, 7, '#ef4444'),
      rect(5, 8, '#ef4444'),
      rect(11, 7, '#facc15'),
      rect(11, 8, '#facc15'),
      rect(12, 6, '#065f46'),
      rect(12, 9, '#065f46'),
    ],
  },
  // 7 章 归纳之镜 Mirror of Abstraction — 多层棱柱 + 顶部金光 + 类层级
  7: {
    accent: '#7b2cbf',
    pixels: [
      rect(2, 7, '#facc15'),
      rect(2, 8, '#facc15'),
      rect(3, 6, '#fff7e6'),
      rect(3, 7, '#facc15'),
      rect(3, 8, '#facc15'),
      rect(3, 9, '#fff7e6'),
      rect(4, 5, '#5a189a'),
      rect(4, 6, '#7b2cbf'),
      rect(4, 7, '#9d4edd'),
      rect(4, 8, '#9d4edd'),
      rect(4, 9, '#7b2cbf'),
      rect(4, 10, '#5a189a'),
      rect(5, 4, '#1e3a8a'),
      rect(5, 5, '#3b82f6'),
      rect(5, 6, '#60a5fa'),
      rect(5, 7, '#93c5fd'),
      rect(5, 8, '#93c5fd'),
      rect(5, 9, '#60a5fa'),
      rect(5, 10, '#3b82f6'),
      rect(5, 11, '#1e3a8a'),
      rect(6, 3, '#065f46'),
      rect(6, 4, '#10b981'),
      rect(6, 5, '#34d399'),
      rect(6, 6, '#6ee7b7'),
      rect(6, 7, '#a7f3d0'),
      rect(6, 8, '#a7f3d0'),
      rect(6, 9, '#6ee7b7'),
      rect(6, 10, '#34d399'),
      rect(6, 11, '#10b981'),
      rect(6, 12, '#065f46'),
      rect(7, 4, '#facc15'),
      rect(7, 11, '#facc15'),
      rect(8, 5, '#1e3a8a'),
      rect(8, 6, '#1e3a8a'),
      rect(8, 9, '#1e3a8a'),
      rect(8, 10, '#1e3a8a'),
      rect(9, 6, '#7b2cbf'),
      rect(9, 7, '#7b2cbf'),
      rect(9, 8, '#7b2cbf'),
      rect(9, 9, '#7b2cbf'),
      rect(10, 7, '#facc15'),
      rect(10, 8, '#facc15'),
      rect(11, 6, '#4d3613'),
      rect(11, 7, '#facc15'),
      rect(11, 8, '#facc15'),
      rect(11, 9, '#4d3613'),
      rect(12, 5, '#4d3613'),
      rect(12, 6, '#4d3613'),
      rect(12, 9, '#4d3613'),
      rect(12, 10, '#4d3613'),
    ],
  },
};

const fallback = DESIGNS[1] as BossDesign;
const design = computed<BossDesign>(() => DESIGNS[props.chapter] ?? fallback);
const pixels = computed<PixelDef[]>(() => design.value.pixels);
const phaseClass = computed<string>(() => `boss-sprite--${props.phase}`);
</script>

<template>
  <!-- v3: PNG 优先；加载失败回退 SVG pixel art。 -->
  <img
    v-if="pngSrc && !pngLoadFailed"
    class="boss-sprite boss-sprite--png"
    :class="phaseClass"
    :width="size ?? 96"
    :height="size ?? 96"
    :src="pngSrc"
    :alt="`第 ${chapter} 章 Boss 立绘`"
    @error="onPngError"
  >
  <svg
    v-else
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
.boss-sprite--png {
  object-fit: contain;
  object-position: center;
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
