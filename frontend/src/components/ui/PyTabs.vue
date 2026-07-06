<script setup lang="ts">
/**
 * PyTabs：全站统一分段标签（受控，统一标准 §3）。游戏 UI 控件 → 像素字 + 牛皮纸。
 * 仅负责标签栏本身（tablist）；标签内容由父组件按 modelValue 用 v-if 渲染，
 * 保持与既有面板「tab 栏 + v-if 面板」结构一致，迁移成本最低。
 * a11y：role=tablist/tab + roving tabindex + 方向键/Home/End 切换。
 */
import { ref } from 'vue';

interface TabItem {
  key: string;
  label: string;
}

const props = withDefaults(
  defineProps<{
    tabs: TabItem[];
    modelValue: string;
    /** 每个标签的 data-testid，渲染为 `${testIdPrefix}-${key}`（缺省 tab）。 */
    testIdPrefix?: string;
    size?: 'sm' | 'md';
  }>(),
  {
    testIdPrefix: 'tab',
    size: 'md',
  },
);

const emit = defineEmits<{ (event: 'update:modelValue', key: string): void }>();

const tablistRef = ref<HTMLDivElement | null>(null);

function select(key: string): void {
  if (key !== props.modelValue) {
    emit('update:modelValue', key);
  }
}

function focusTabAt(index: number): void {
  const buttons = tablistRef.value?.querySelectorAll<HTMLButtonElement>('[role="tab"]');
  buttons?.[index]?.focus();
}

function onKeydown(event: KeyboardEvent, index: number): void {
  const count = props.tabs.length;
  if (count === 0) return;
  let next = index;
  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
    next = (index + 1) % count;
  } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
    next = (index - 1 + count) % count;
  } else if (event.key === 'Home') {
    next = 0;
  } else if (event.key === 'End') {
    next = count - 1;
  } else {
    return;
  }
  event.preventDefault();
  const tab = props.tabs[next];
  if (tab) {
    select(tab.key);
    focusTabAt(next);
  }
}
</script>

<template>
  <div
    ref="tablistRef"
    class="py-tabs"
    :class="`py-tabs--${size}`"
    role="tablist"
  >
    <button
      v-for="(tab, index) in tabs"
      :key="tab.key"
      type="button"
      role="tab"
      class="py-tabs__tab"
      :class="{ 'py-tabs__tab--active': tab.key === modelValue }"
      :aria-selected="tab.key === modelValue"
      :tabindex="tab.key === modelValue ? 0 : -1"
      :data-testid="`${testIdPrefix}-${tab.key}`"
      @click="select(tab.key)"
      @keydown="onKeydown($event, index)"
    >
      {{ tab.label }}
    </button>
  </div>
</template>

<style scoped>
.py-tabs {
  display: flex;
  gap: 6px;
  border-bottom: 2px solid var(--pyt-color-border);
}
.py-tabs__tab {
  background: var(--pyt-color-bg-elevated);
  border: 1px solid var(--pyt-color-border);
  border-bottom: none;
  border-radius: var(--pyt-radius-md) var(--pyt-radius-md) 0 0;
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-pixel);
  font-weight: 600;
  cursor: pointer;
  transition: background-color var(--pyt-motion-base), color var(--pyt-motion-base);
}
.py-tabs--md .py-tabs__tab {
  padding: 6px 12px;
  font-size: var(--pyt-font-size-base);
}
.py-tabs--sm .py-tabs__tab {
  padding: 4px 10px;
  font-size: var(--pyt-font-size-sm);
}
.py-tabs__tab:hover {
  background: var(--pyt-color-warning-bg);
}
.py-tabs__tab--active {
  background: var(--pyt-color-text-hint);
  color: var(--pyt-color-bg-elevated);
}
</style>
