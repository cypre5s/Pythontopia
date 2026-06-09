<script setup lang="ts">
/**
 * PyToggleGroup：单选分段控件（筛选/视图切换等）。像素描边 + 选中金色实心。
 * 自制实现（不依赖 reka 的 single/multiple 联合类型），单选语义：modelValue 为当前选中 value。
 * a11y：role=group + 每项 button[aria-pressed] + roving tabindex + 方向键切换。
 */
import { computed, ref } from 'vue';

interface ToggleItem {
  value: string;
  label: string;
  disabled?: boolean;
}

const props = withDefaults(
  defineProps<{
    items: ToggleItem[];
    modelValue: string | null;
    disabled?: boolean;
    ariaLabel?: string;
    testIdPrefix?: string;
  }>(),
  {
    modelValue: null,
    disabled: false,
    ariaLabel: undefined,
    testIdPrefix: 'py-toggle',
  },
);

const emit = defineEmits<{ (event: 'update:modelValue', value: string): void }>();

const groupRef = ref<HTMLDivElement | null>(null);

function isDisabled(item: ToggleItem): boolean {
  return props.disabled || item.disabled === true;
}

const focusableValue = computed<string | null>(() => {
  const active = props.items.find((i) => i.value === props.modelValue && !isDisabled(i));
  if (active) return active.value;
  const firstEnabled = props.items.find((i) => !isDisabled(i));
  return firstEnabled ? firstEnabled.value : null;
});

function select(item: ToggleItem): void {
  if (isDisabled(item)) return;
  if (item.value !== props.modelValue) emit('update:modelValue', item.value);
}

function focusAt(index: number): void {
  const buttons = groupRef.value?.querySelectorAll<HTMLButtonElement>('button');
  buttons?.[index]?.focus();
}

function onKeydown(event: KeyboardEvent, index: number): void {
  const count = props.items.length;
  if (count === 0) return;
  if (!['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp'].includes(event.key)) return;
  event.preventDefault();
  const dir = event.key === 'ArrowRight' || event.key === 'ArrowDown' ? 1 : -1;
  for (let step = 1; step <= count; step += 1) {
    const next = (((index + dir * step) % count) + count) % count;
    const item = props.items[next];
    if (item && !isDisabled(item)) {
      select(item);
      focusAt(next);
      return;
    }
  }
}
</script>

<template>
  <div
    ref="groupRef"
    class="py-toggle-group"
    role="group"
    :aria-label="ariaLabel"
  >
    <button
      v-for="(item, index) in items"
      :key="item.value"
      type="button"
      class="py-toggle"
      :class="{ 'py-toggle--on': item.value === modelValue, 'py-toggle--disabled': isDisabled(item) }"
      :aria-pressed="item.value === modelValue"
      :disabled="isDisabled(item)"
      :tabindex="item.value === focusableValue ? 0 : -1"
      :data-testid="`${testIdPrefix}-${item.value}`"
      @click="select(item)"
      @keydown="onKeydown($event, index)"
    >
      {{ item.label }}
    </button>
  </div>
</template>

<style scoped>
.py-toggle-group {
  display: inline-flex;
  border: 1px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  overflow: hidden;
}
.py-toggle {
  border: none;
  border-right: 1px solid var(--pyt-color-border-soft);
  background: var(--pyt-color-bg-sunken);
  color: var(--pyt-color-text-muted);
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-sm);
  padding: 5px 12px;
  cursor: pointer;
  transition: background-color var(--pyt-motion-base), color var(--pyt-motion-base);
}
.py-toggle:last-child {
  border-right: none;
}
.py-toggle:hover:not(.py-toggle--disabled):not(.py-toggle--on) {
  background: var(--pyt-color-warning-bg);
  color: var(--pyt-color-text-primary);
}
.py-toggle--on {
  background: var(--pyt-color-gold);
  color: var(--pyt-color-primary-dark);
  font-weight: 700;
}
.py-toggle--disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
