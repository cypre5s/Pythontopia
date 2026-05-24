<script setup lang="ts">
/**
 * PySkeleton：Pythontopia 通用骨架屏占位组件。
 *
 * 设计依据：
 *   - docs/stardew/00-visual-system-overview.md §5 动画规范（hover 120ms / shimmer 受 reduced-motion 控制）
 *   - docs/stardew/01-palette-tokens-css.md（--pyt-r12-* 命名空间 + 灰阶/动画 token）
 *   - docs/stardew/07-ui-microcopy.md（空状态优先于"假占位"，但加载中状态仍需骨架以避免布局抖动）
 *
 * 落地约束：
 *   1. 仅作为"加载中"占位，不承载语义文字；可被 aria-hidden 让屏读器跳过，但保留
 *      `role="status"` + `aria-live="polite"` 以便屏读器在出现/消失时通知用户。
 *   2. shimmer 动画走 CSS keyframes；prefers-reduced-motion: reduce 时整体关闭，
 *      退化为纯灰阶填充——不剥夺布局占位但避免视觉刺激。
 *   3. 颜色全部 `var(--pyt-r12-color-gray-*, fallback)`，未引入 tokens-r12.css 时
 *      也能正常渲染，灰度并行迁移期不破图。
 *   4. variant=text 时最后一行宽度自动 ×0.75，模拟"段落最后一行短"的真实排版，
 *      让骨架不至于一眼假；可被外部 width 覆盖。
 */
import { computed } from 'vue';
import { useReducedMotion } from '@/composables/useReducedMotion';

export type PySkeletonVariant = 'text' | 'circle' | 'rect';

interface PySkeletonProps {
  width?: string | number;
  height?: string | number;
  variant?: PySkeletonVariant;
  count?: number;
}

const props = withDefaults(defineProps<PySkeletonProps>(), {
  width: '100%',
  height: 20,
  variant: 'text',
  count: 1,
});

const reduced = useReducedMotion();

function toCssSize(value: string | number): string {
  if (typeof value === 'number') {
    return `${value}px`;
  }
  return value;
}

const safeCount = computed<number>(() => {
  const n = Math.floor(Number(props.count));
  if (!Number.isFinite(n) || n < 1) {
    return 1;
  }
  return n;
});

const itemStyles = computed<Array<Record<string, string>>>(() => {
  const baseWidth = toCssSize(props.width);
  const baseHeight = toCssSize(props.height);
  const list: Array<Record<string, string>> = [];
  for (let index = 0; index < safeCount.value; index += 1) {
    if (props.variant === 'circle') {
      // 圆形以 height 作为直径，保证 1:1，不受 width 影响
      const diameter = baseHeight;
      list.push({
        width: diameter,
        height: diameter,
        borderRadius: '50%',
      });
    } else if (props.variant === 'rect') {
      list.push({
        width: baseWidth,
        height: baseHeight,
        borderRadius: 'var(--pyt-r12-radius-sm, 2px)',
      });
    } else {
      // text 变体：多行时最后一行收窄到 75%，模拟段落自然结束
      const isLastInGroup = index === safeCount.value - 1 && safeCount.value > 1;
      list.push({
        width: isLastInGroup ? `calc(${baseWidth} * 0.75)` : baseWidth,
        height: baseHeight,
        borderRadius: 'var(--pyt-r12-radius-sm, 2px)',
      });
    }
  }
  return list;
});

const rootClasses = computed<string[]>(() => {
  const list: string[] = ['py-skeleton', `py-skeleton--${props.variant}`];
  if (reduced.value) {
    list.push('py-skeleton--reduced-motion');
  }
  return list;
});
</script>

<template>
  <div
    :class="rootClasses"
    role="status"
    aria-live="polite"
    aria-busy="true"
    aria-label="内容加载中"
    :data-variant="variant"
    :data-count="safeCount"
    data-testid="py-skeleton"
  >
    <span
      v-for="(style, index) in itemStyles"
      :key="index"
      class="py-skeleton__item"
      :style="style"
      :data-index="index"
      data-testid="py-skeleton-item"
      aria-hidden="true"
    />
  </div>
</template>

<style scoped>
.py-skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--pyt-r12-space-2, 8px);
  width: 100%;
}

.py-skeleton--circle {
  flex-direction: row;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--pyt-r12-space-2, 8px);
}

.py-skeleton__item {
  display: block;
  position: relative;
  overflow: hidden;
  background-color: var(--pyt-r12-color-gray-100, #f3e4be);
  background-image: linear-gradient(
    90deg,
    var(--pyt-r12-color-gray-100, #f3e4be) 0%,
    var(--pyt-r12-color-gray-50, #fff7e6) 50%,
    var(--pyt-r12-color-gray-100, #f3e4be) 100%
  );
  background-size: 200% 100%;
  background-position: 200% 0;
  animation: py-skeleton-shimmer 1400ms ease-in-out infinite;
  will-change: background-position;
}

.py-skeleton--reduced-motion .py-skeleton__item {
  animation: none;
  background-image: none;
  background-color: var(--pyt-r12-color-gray-100, #f3e4be);
}

@keyframes py-skeleton-shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .py-skeleton__item {
    animation: none;
    background-image: none;
    background-color: var(--pyt-r12-color-gray-100, #f3e4be);
  }
}
</style>
