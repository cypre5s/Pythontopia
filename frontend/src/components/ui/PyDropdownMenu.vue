<script setup lang="ts">
/**
 * PyDropdownMenu：像素皮下拉菜单（reka-ui DropdownMenu 底座，定位/焦点/aria 交给 reka）。
 * #trigger 插槽放触发器（as-child 合并到子元素）；items 驱动菜单项，点击 emit select(value)。
 */
import {
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuPortal,
  DropdownMenuRoot,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from 'reka-ui';

interface MenuItem {
  value: string;
  label: string;
  disabled?: boolean;
  separatorBefore?: boolean;
}

withDefaults(
  defineProps<{
    items: MenuItem[];
    side?: 'top' | 'right' | 'bottom' | 'left';
    align?: 'start' | 'center' | 'end';
    sideOffset?: number;
  }>(),
  {
    side: 'bottom',
    align: 'start',
    sideOffset: 6,
  },
);

const emit = defineEmits<{ (event: 'select', value: string): void }>();
</script>

<template>
  <DropdownMenuRoot>
    <DropdownMenuTrigger as-child>
      <slot name="trigger" />
    </DropdownMenuTrigger>
    <DropdownMenuPortal>
      <DropdownMenuContent
        class="py-menu"
        :side="side"
        :align="align"
        :side-offset="sideOffset"
      >
        <template
          v-for="item in items"
          :key="item.value"
        >
          <DropdownMenuSeparator
            v-if="item.separatorBefore"
            class="py-menu__sep"
          />
          <DropdownMenuItem
            class="py-menu__item"
            :disabled="item.disabled"
            @select="emit('select', item.value)"
          >
            {{ item.label }}
          </DropdownMenuItem>
        </template>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>

<style scoped>
.py-menu {
  min-width: 160px;
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  box-shadow: var(--pyt-shadow-modal);
  padding: var(--pyt-space-1);
  z-index: var(--pyt-z-popover);
  font-family: var(--pyt-font-read);
}
.py-menu__item {
  display: flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: var(--pyt-radius-sm);
  font-size: var(--pyt-font-size-base);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
  user-select: none;
  outline: none;
}
.py-menu__item[data-highlighted] {
  background: var(--pyt-color-warning-bg);
}
.py-menu__item[data-disabled] {
  opacity: 0.5;
  cursor: not-allowed;
}
.py-menu__sep {
  height: 1px;
  background: var(--pyt-color-border-soft);
  margin: 4px 2px;
}
</style>
