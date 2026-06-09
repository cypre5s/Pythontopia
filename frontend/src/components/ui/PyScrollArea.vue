<script setup lang="ts">
/**
 * PyScrollArea：统一像素滚动容器（牛皮纸亮底版滚动条，区别于 HUD 深底 .pyt-scroll）。
 * 提供 maxHeight 限高 + 键盘可滚动（tabindex），用于学习长文 / 列表面板。
 */
import type { CSSProperties } from 'vue';
import { computed } from 'vue';

const props = withDefaults(
  defineProps<{
    /** 最大高度，超出滚动；number 视为 px，亦可传 CSS 字符串。不传则不限高。 */
    maxHeight?: number | string;
    /** 横向滚动而非纵向 */
    horizontal?: boolean;
  }>(),
  {
    maxHeight: undefined,
    horizontal: false,
  },
);

const style = computed<CSSProperties>(() => {
  const result: CSSProperties = {
    overflowY: props.horizontal ? 'hidden' : 'auto',
    overflowX: props.horizontal ? 'auto' : 'hidden',
  };
  if (props.maxHeight !== undefined) {
    result.maxHeight =
      typeof props.maxHeight === 'number' ? `${props.maxHeight}px` : props.maxHeight;
  }
  return result;
});
</script>

<template>
  <div
    class="py-scroll-area"
    :style="style"
    tabindex="0"
  >
    <slot />
  </div>
</template>

<style scoped>
.py-scroll-area {
  scrollbar-width: thin;
  scrollbar-color: var(--pyt-color-border) var(--pyt-color-bg-sunken);
}
.py-scroll-area:focus-visible {
  outline: var(--pyt-focus-ring);
  outline-offset: -2px;
}
.py-scroll-area::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}
.py-scroll-area::-webkit-scrollbar-track {
  background: var(--pyt-color-bg-sunken);
  border-radius: var(--pyt-radius-sm);
}
.py-scroll-area::-webkit-scrollbar-thumb {
  background: var(--pyt-color-border);
  border: 2px solid var(--pyt-color-bg-sunken);
  border-radius: var(--pyt-radius-sm);
}
.py-scroll-area::-webkit-scrollbar-thumb:hover {
  background: var(--pyt-color-text-hint);
}
</style>
