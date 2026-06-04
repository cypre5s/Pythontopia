<script setup lang="ts">
/**
 * PyButton：全站统一按钮（统一标准 §3）。游戏 UI 控件 → 像素字 + 牛皮纸投影。
 *   - variant: primary（一屏仅一个）/ secondary / ghost
 *   - size: sm / md
 *   - block: 占满整行
 * 全部走 --pyt-* 令牌，禁散落 hex。
 */
import { computed } from 'vue';

const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost';
    size?: 'sm' | 'md';
    type?: 'button' | 'submit';
    disabled?: boolean;
    block?: boolean;
    /** 可覆盖的测试钩子；多个 PyButton 同屏时用它区分（缺省 py-button）。 */
    testId?: string;
  }>(),
  {
    variant: 'primary',
    size: 'md',
    type: 'button',
    disabled: false,
    block: false,
    testId: 'py-button',
  },
);

const emit = defineEmits<{ (event: 'click', ev: MouseEvent): void }>();

// exactOptionalPropertyTypes 下不直绑可选 prop 到原生属性，统一收敛。
const isDisabled = computed<boolean>(() => props.disabled === true);
const buttonType = computed<'button' | 'submit'>(() =>
  props.type === 'submit' ? 'submit' : 'button',
);

function onClick(ev: MouseEvent): void {
  if (isDisabled.value) return;
  emit('click', ev);
}
</script>

<template>
  <button
    :type="buttonType"
    class="py-btn"
    :class="[`py-btn--${variant}`, `py-btn--${size}`, { 'py-btn--block': block }]"
    :disabled="isDisabled"
    :data-testid="testId"
    @click="onClick"
  >
    <slot />
  </button>
</template>

<style scoped>
.py-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--pyt-space-1);
  font-family: var(--pyt-font-pixel);
  font-weight: 700;
  border-radius: var(--pyt-radius-md);
  cursor: pointer;
  transition: transform var(--pyt-motion-fast), background-color var(--pyt-motion-base);
  white-space: nowrap;
}
.py-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* sizes */
.py-btn--sm {
  font-size: var(--pyt-font-size-sm);
  padding: var(--pyt-space-1) var(--pyt-space-3);
}
.py-btn--md {
  font-size: var(--pyt-font-size-base);
  padding: var(--pyt-space-2) var(--pyt-space-4);
}
.py-btn--block {
  width: 100%;
}

/* primary：实心金，一屏仅一个 */
.py-btn--primary {
  background: var(--pyt-color-gold);
  color: var(--pyt-color-primary-dark);
  border: 3px solid var(--pyt-color-primary);
  box-shadow: 0 3px 0 var(--pyt-color-primary);
}
.py-btn--primary:hover:not(:disabled) {
  background: var(--pyt-color-gold-bright);
}
.py-btn--primary:active:not(:disabled) {
  transform: translateY(2px);
  box-shadow: 0 1px 0 var(--pyt-color-primary);
}

/* secondary：描边米黄 */
.py-btn--secondary {
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
  border: 2px solid var(--pyt-color-border);
  box-shadow: 0 2px 0 var(--pyt-color-border);
}
.py-btn--secondary:hover:not(:disabled) {
  background: var(--pyt-color-warning-bg);
}
.py-btn--secondary:active:not(:disabled) {
  transform: translateY(2px);
  box-shadow: none;
}

/* ghost：无填充，弱化次要操作 */
.py-btn--ghost {
  background: transparent;
  color: var(--pyt-color-text-hint);
  border: 2px solid transparent;
}
.py-btn--ghost:hover:not(:disabled) {
  background: var(--pyt-color-warning-bg);
}
</style>
