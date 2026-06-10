<script setup lang="ts">
/**
 * PyPopover：像素皮浮层。定位/焦点/aria 交给 reka-ui Popover（项目既有依赖），
 * 外观全部走 --pyt 令牌，保持木质像素风、不引入 shadcn 语义 token。
 * 用法：#trigger 插槽放触发器（as-child 合并到子元素）；默认插槽放浮层内容。
 */
import {
  PopoverArrow,
  PopoverContent,
  PopoverPortal,
  PopoverRoot,
  PopoverTrigger,
} from 'reka-ui';

withDefaults(
  defineProps<{
    side?: 'top' | 'right' | 'bottom' | 'left';
    align?: 'start' | 'center' | 'end';
    sideOffset?: number;
    /** 受控开关（可选）；不传则由 reka 自管开合。 */
    open?: boolean;
  }>(),
  {
    side: 'bottom',
    align: 'center',
    sideOffset: 8,
    open: undefined,
  },
);

const emit = defineEmits<{ (event: 'update:open', value: boolean): void }>();
</script>

<template>
  <PopoverRoot
    :open="open"
    @update:open="emit('update:open', $event)"
  >
    <PopoverTrigger as-child>
      <slot name="trigger" />
    </PopoverTrigger>
    <PopoverPortal>
      <PopoverContent
        class="py-popover"
        :side="side"
        :align="align"
        :side-offset="sideOffset"
      >
        <slot />
        <PopoverArrow
          class="py-popover__arrow"
          :width="12"
          :height="6"
        />
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>

<style scoped>
.py-popover {
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  box-shadow: var(--pyt-shadow-modal);
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-read);
  font-size: var(--pyt-font-size-base);
  padding: var(--pyt-space-3);
  z-index: var(--pyt-z-popover);
  max-width: min(360px, calc(100vw - 24px));
}
.py-popover__arrow {
  fill: var(--pyt-color-border);
}
</style>
