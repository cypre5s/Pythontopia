<script setup lang="ts">
/**
 * PyToast：Pythontopia 全端通知条（success / error / info / warning）。
 *
 * 设计依据：
 *   - docs/stardew/00-visual-system-overview.md §5 动画规范（toast 滑入 250ms cubic-bezier）
 *   - docs/stardew/00-visual-system-overview.md §10 可访问性（toast 默认 ≥4000ms；颜色 + 图标 + 文字三通道）
 *   - docs/stardew/01-palette-tokens-css.md（语义色 + z-toast=400 + motion-slow=200ms）
 *   - docs/stardew/07-ui-microcopy.md §3（toast 默认 2400ms，关键 4000ms；本组件采用 props.duration 默认 3000 居中）
 *
 * 落地约束：
 *   1. role="alert" + aria-live=assertive 让屏读器立即播报；warning/error 走 assertive，
 *      success/info 走 polite 减少打断（按 WAI-ARIA APG 通知模式）。
 *   2. duration ≤ 0 视为"不自动关闭"，由调用方手动 emit close；duration > 0 时 setTimeout
 *      到时 emit('close') 由父级移除。
 *   3. 4 类型 4 色 4 符号符合"颜色不依赖"——color blind 模式仍可通过符号识别。
 *      ✓ / ✗ / ℹ / ⚠ 使用 Unicode 字符作为占位，Beta 阶段美术补 24×24 png 后替换。
 *   4. 像素边框 + 暖褐阴影对齐 PyFrame；pixel 字号 sm（12px）满足"≤24 字"约束。
 *   5. 卸载 / 类型/时长变化时清理 timer，避免泄漏。
 */
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue';

export type PyToastType = 'success' | 'error' | 'info' | 'warning';
export type PyToastPosition = 'top' | 'bottom';

interface PyToastProps {
  message: string;
  type: PyToastType;
  duration?: number;
  position?: PyToastPosition;
}

const props = withDefaults(defineProps<PyToastProps>(), {
  duration: 3000,
  position: 'top',
});

const emit = defineEmits<{
  (event: 'close'): void;
}>();

const ICONS: Readonly<Record<PyToastType, string>> = {
  success: '✓',
  error: '✗',
  info: 'ℹ',
  warning: '⚠',
} as const;

const ARIA_LIVE: Readonly<Record<PyToastType, 'polite' | 'assertive'>> = {
  success: 'polite',
  info: 'polite',
  warning: 'assertive',
  error: 'assertive',
} as const;

const ICON_LABEL: Readonly<Record<PyToastType, string>> = {
  success: '成功',
  error: '失败',
  info: '提示',
  warning: '警告',
} as const;

const icon = computed<string>(() => ICONS[props.type]);
const iconLabel = computed<string>(() => ICON_LABEL[props.type]);
const ariaLive = computed<'polite' | 'assertive'>(() => ARIA_LIVE[props.type]);
const visible = ref<boolean>(true);

const rootClasses = computed<string[]>(() => [
  'py-toast',
  `py-toast--${props.type}`,
  `py-toast--${props.position}`,
]);

let timerId: ReturnType<typeof setTimeout> | null = null;

function clearTimer(): void {
  if (timerId !== null) {
    clearTimeout(timerId);
    timerId = null;
  }
}

function scheduleClose(): void {
  clearTimer();
  if (props.duration <= 0) {
    return;
  }
  timerId = setTimeout(() => {
    visible.value = false;
    emit('close');
  }, props.duration);
}

function handleManualClose(): void {
  clearTimer();
  visible.value = false;
  emit('close');
}

onMounted(scheduleClose);

watch(
  () => [props.duration, props.type, props.message] as const,
  () => {
    scheduleClose();
  },
);

onBeforeUnmount(clearTimer);
</script>

<template>
  <div
    v-if="visible"
    :class="rootClasses"
    role="alert"
    :aria-live="ariaLive"
    aria-atomic="true"
    :data-type="type"
    :data-position="position"
    data-testid="py-toast"
  >
    <span
      class="py-toast__icon"
      :data-icon="type"
      aria-hidden="true"
    >{{ icon }}</span>
    <span class="sr-only">{{ iconLabel }}：</span>
    <span
      class="py-toast__message"
      data-testid="py-toast-message"
    >{{ message }}</span>
    <button
      type="button"
      class="py-toast__close"
      aria-label="关闭通知"
      data-testid="py-toast-close"
      @click="handleManualClose"
    >
      ×
    </button>
  </div>
</template>

<style scoped>
.py-toast {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  z-index: var(--pyt-r12-z-toast, var(--pyt-z-toast, 400));
  display: inline-flex;
  align-items: center;
  gap: var(--pyt-r12-space-2, 8px);
  min-width: 200px;
  max-width: 360px;
  padding: var(--pyt-r12-space-2, 8px) var(--pyt-r12-space-3, 12px);
  border: 2px solid var(--pyt-r12-color-primary-900, var(--pyt-color-text-primary));
  border-radius: var(--pyt-r12-radius-md, 4px);
  background-color: var(--pyt-r12-color-gray-50, var(--pyt-color-bg-elevated));
  color: var(--pyt-r12-color-primary-900, var(--pyt-color-text-primary));
  font-family: var(--pyt-r12-font-pixel-cn, var(--pyt-font-pixel, 'Ark Pixel 12'), system-ui);
  font-size: var(--pyt-r12-font-size-sm, 12px);
  letter-spacing: 0.5px;
  box-shadow: var(--pyt-r12-shadow-popover, 0 4px 8px rgba(77, 54, 19, 0.24));
  image-rendering: pixelated;
  animation: py-toast-in 250ms cubic-bezier(0.25, 1.4, 0.5, 1) both;
}

.py-toast--top {
  top: var(--pyt-r12-space-4, 16px);
}

.py-toast--bottom {
  bottom: var(--pyt-r12-space-4, 16px);
}

.py-toast--success {
  border-color: var(--pyt-r12-color-success, #6bbf59);
  background-color: #eaf6e3;
  color: #1f5a2a;
}

.py-toast--error {
  border-color: var(--pyt-r12-color-danger, #d9534f);
  background-color: var(--pyt-color-danger-bg);
  color: #6e2418;
}

.py-toast--info {
  border-color: var(--pyt-r12-color-info, var(--pyt-color-info));
  background-color: #dceef5;
  color: #143b50;
}

.py-toast--warning {
  border-color: var(--pyt-r12-color-warning, var(--pyt-color-warning));
  background-color: #f7e3bd;
  color: var(--pyt-color-text-hint);
}

.py-toast__icon {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
}

.py-toast__message {
  flex: 1 1 auto;
  word-break: break-word;
  line-height: 1.4;
}

.py-toast__close {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: var(--pyt-r12-radius-sm, 2px);
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  opacity: 0.7;
  transition: opacity var(--pyt-r12-motion-base, 120ms) ease,
    background-color var(--pyt-r12-motion-base, 120ms) ease;
}

.py-toast__close:hover,
.py-toast__close:focus-visible {
  opacity: 1;
  background-color: var(--pyt-r12-overlay-soft, rgba(77, 54, 19, 0.12));
}

.py-toast__close:focus-visible {
  outline: var(--pyt-r12-focus-ring, 2px solid var(--pyt-color-gold));
  outline-offset: 2px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@keyframes py-toast-in {
  0% {
    opacity: 0;
    transform: translate(-50%, -8px);
  }
  100% {
    opacity: 1;
    transform: translate(-50%, 0);
  }
}

.py-toast--bottom {
  animation-name: py-toast-in-bottom;
}

@keyframes py-toast-in-bottom {
  0% {
    opacity: 0;
    transform: translate(-50%, 8px);
  }
  100% {
    opacity: 1;
    transform: translate(-50%, 0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .py-toast {
    animation-duration: 80ms;
  }
}
</style>
