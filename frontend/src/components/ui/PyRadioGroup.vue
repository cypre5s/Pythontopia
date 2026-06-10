<script setup lang="ts">
/**
 * PyRadioGroup：单选组（做题单选 / 设置项等）。像素描边卡片 + 选中金色实心点。
 * a11y：role=radiogroup/radio + 单一 roving tabindex + 方向键在可用项间移动并选中。
 */
import { computed, ref } from 'vue';

interface RadioOption {
  value: string;
  label: string;
  disabled?: boolean;
}

const props = withDefaults(
  defineProps<{
    options: RadioOption[];
    modelValue: string | null;
    disabled?: boolean;
    testIdPrefix?: string;
  }>(),
  {
    modelValue: null,
    disabled: false,
    testIdPrefix: 'py-radio',
  },
);

const emit = defineEmits<{ (event: 'update:modelValue', value: string): void }>();

const groupRef = ref<HTMLDivElement | null>(null);

function isDisabled(option: RadioOption): boolean {
  return props.disabled || option.disabled === true;
}

/** 单一 tab 落点：优先已选项，否则首个可用项。 */
const focusableValue = computed<string | null>(() => {
  const checked = props.options.find((o) => o.value === props.modelValue && !isDisabled(o));
  if (checked) return checked.value;
  const firstEnabled = props.options.find((o) => !isDisabled(o));
  return firstEnabled ? firstEnabled.value : null;
});

function select(option: RadioOption): void {
  if (isDisabled(option)) return;
  if (option.value !== props.modelValue) {
    emit('update:modelValue', option.value);
  }
}

function focusAt(index: number): void {
  const radios = groupRef.value?.querySelectorAll<HTMLButtonElement>('[role="radio"]');
  radios?.[index]?.focus();
}

function onKeydown(event: KeyboardEvent, index: number): void {
  const count = props.options.length;
  if (count === 0) return;
  if (!['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp'].includes(event.key)) return;
  event.preventDefault();
  const dir = event.key === 'ArrowRight' || event.key === 'ArrowDown' ? 1 : -1;
  for (let step = 1; step <= count; step += 1) {
    const next = (((index + dir * step) % count) + count) % count;
    const option = props.options[next];
    if (option && !isDisabled(option)) {
      select(option);
      focusAt(next);
      return;
    }
  }
}
</script>

<template>
  <div
    ref="groupRef"
    class="py-radio-group"
    role="radiogroup"
  >
    <button
      v-for="(option, index) in options"
      :key="option.value"
      type="button"
      role="radio"
      class="py-radio"
      :class="{
        'py-radio--checked': option.value === modelValue,
        'py-radio--disabled': isDisabled(option),
      }"
      :aria-checked="option.value === modelValue"
      :aria-disabled="isDisabled(option)"
      :tabindex="option.value === focusableValue ? 0 : -1"
      :disabled="isDisabled(option)"
      :data-testid="`${testIdPrefix}-${option.value}`"
      @click="select(option)"
      @keydown="onKeydown($event, index)"
    >
      <span
        class="py-radio__dot"
        aria-hidden="true"
      />
      <span class="py-radio__label">{{ option.label }}</span>
    </button>
  </div>
</template>

<style scoped>
.py-radio-group {
  display: grid;
  gap: 8px;
}
.py-radio {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  text-align: left;
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  padding: 8px 12px;
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-read);
  font-size: var(--pyt-font-size-base);
  cursor: pointer;
  transition: background-color var(--pyt-motion-base), border-color var(--pyt-motion-base);
}
.py-radio:hover:not(.py-radio--disabled) {
  background: var(--pyt-color-warning-bg);
}
.py-radio--checked {
  border-color: var(--pyt-color-primary);
  background: var(--pyt-color-warning-bg);
}
.py-radio--disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.py-radio__dot {
  width: 14px;
  height: 14px;
  flex: none;
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-full);
  background: var(--pyt-color-bg-elevated);
  transition: background-color var(--pyt-motion-base), box-shadow var(--pyt-motion-base);
}
.py-radio--checked .py-radio__dot {
  background: var(--pyt-color-gold);
  border-color: var(--pyt-color-primary);
  box-shadow: inset 0 0 0 2px var(--pyt-color-bg-elevated);
}
.py-radio__label {
  flex: 1;
}
</style>
