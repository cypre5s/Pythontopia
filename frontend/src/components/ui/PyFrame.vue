<script setup lang="ts">
/**
 * PyFrame：Pythontopia 全端 9-slice 像素边框容器。
 *
 * 设计依据：docs/stardew/02-frames-9slice-spec.md
 *
 * 渲染策略：
 *   1. 优先使用 CSS `border-image` + 木色背景 + 像素字（真 9-slice 效果）
 *   2. 资产 PNG 加载失败时（404 / 未到位），通过预加载探测把 root 元素打上
 *      `py-frame--fallback` class，由 CSS 退化到沿用 HudShell 风格的纯 border + box-shadow
 *   3. button 三态用 3 个独立 PNG 切换（与文档 §3.3 一致），其他 variant 按 tone 切色
 *
 * 资产路径占位：`/assets/pixel/ui/frame_{variant}_{toneOrState}.png`
 * （美术后续补图；fallback 已能正确显示，组件层面零阻塞）
 */
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue';

export type PyFrameVariant =
  | 'panel'
  | 'dialog'
  | 'button'
  | 'card'
  | 'modal'
  | 'tooltip';

export type PyFrameTone =
  | 'default'
  | 'primary'
  | 'success'
  | 'warning'
  | 'danger';

export type PyFrameState = 'default' | 'hover' | 'pressed';

export type PyFrameTail = 'top' | 'right' | 'bottom' | 'left' | 'none';

interface PyFrameProps {
  variant?: PyFrameVariant;
  tone?: PyFrameTone;
  state?: PyFrameState;
  tail?: PyFrameTail;
}

const props = withDefaults(defineProps<PyFrameProps>(), {
  variant: 'panel',
  tone: 'default',
  state: 'default',
  tail: 'none',
});

const SLICE: Readonly<Record<PyFrameVariant, number>> = {
  panel: 16,
  dialog: 8,
  button: 8,
  card: 8,
  modal: 16,
  tooltip: 4,
} as const;

const PADDING: Readonly<Record<PyFrameVariant, string>> = {
  panel: '12px 16px',
  dialog: '10px 14px',
  button: '6px 14px',
  card: '10px 12px',
  modal: '16px 20px',
  tooltip: '4px 8px',
} as const;

const ROLE: Readonly<Record<PyFrameVariant, string>> = {
  panel: 'region',
  dialog: 'dialog',
  button: 'button',
  card: 'article',
  modal: 'dialog',
  tooltip: 'tooltip',
} as const;

function resolveSlice(variant: PyFrameVariant): number {
  return SLICE[variant];
}

function resolvePadding(variant: PyFrameVariant): string {
  return PADDING[variant];
}

function resolveRole(variant: PyFrameVariant): string {
  return ROLE[variant];
}

const sourcePath = computed<string>(() => {
  if (props.variant === 'button') {
    return `/assets/pixel/ui/frame_button_${props.state}.png`;
  }
  return `/assets/pixel/ui/frame_${props.variant}_${props.tone}.png`;
});

const tagName = computed<'button' | 'div'>(() =>
  props.variant === 'button' ? 'button' : 'div',
);

const imageFailed = ref(false);
let probeImg: HTMLImageElement | null = null;

function detachProbe(): void {
  if (probeImg) {
    probeImg.onload = null;
    probeImg.onerror = null;
    probeImg = null;
  }
}

function probe(path: string): void {
  imageFailed.value = false;
  detachProbe();
  if (typeof Image === 'undefined') {
    return;
  }
  const img = new Image();
  probeImg = img;
  img.onload = (): void => {
    if (probeImg === img) {
      imageFailed.value = false;
    }
  };
  img.onerror = (): void => {
    if (probeImg === img) {
      imageFailed.value = true;
    }
  };
  img.src = path;
}

onMounted(() => probe(sourcePath.value));
watch(sourcePath, (next: string) => probe(next));
onBeforeUnmount(detachProbe);

const rootClasses = computed<string[]>(() => {
  const list: string[] = [
    'py-frame',
    `py-frame--${props.variant}`,
    `py-frame--tone-${props.tone}`,
  ];
  if (props.variant === 'button') {
    list.push(`py-frame--state-${props.state}`);
  }
  if (props.variant === 'dialog' && props.tail !== 'none') {
    list.push(`py-frame--tail-${props.tail}`);
  }
  if (imageFailed.value) {
    list.push('py-frame--fallback');
  }
  return list;
});

const rootStyle = computed<Record<string, string>>(() => {
  const slice = resolveSlice(props.variant);
  return {
    borderImageSource: `url('${sourcePath.value}')`,
    borderImageSlice: `${slice} fill`,
    borderImageWidth: `${slice}px`,
    borderWidth: `${slice}px`,
    padding: resolvePadding(props.variant),
  };
});

const rootRole = computed<string>(() => resolveRole(props.variant));
const rootButtonType = computed<'button' | null>(() =>
  props.variant === 'button' ? 'button' : null,
);
</script>

<template>
  <component
    :is="tagName"
    :class="rootClasses"
    :style="rootStyle"
    :role="rootRole"
    :type="rootButtonType"
    :data-variant="variant"
    :data-tone="tone"
    :data-state="variant === 'button' ? state : null"
    :data-tail="variant === 'dialog' ? tail : null"
    data-testid="py-frame"
  >
    <slot />
    <span
      v-if="variant === 'dialog' && tail !== 'none'"
      :class="['py-frame__tail', `py-frame__tail--${tail}`]"
      data-testid="py-frame-tail"
      aria-hidden="true"
    />
  </component>
</template>

<style scoped>
.py-frame {
  position: relative;
  box-sizing: border-box;
  display: inline-block;
  border-style: solid;
  border-color: var(--pyt-color-border-strong, var(--pyt-color-text-primary));
  border-image-repeat: stretch;
  border-radius: 4px;
  background-color: var(--pyt-color-bg-elevated, var(--pyt-color-bg-elevated));
  color: var(--pyt-color-text-primary, var(--pyt-color-text-primary));
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-sm, 12px);
  letter-spacing: 0.5px;
  image-rendering: pixelated;
  text-align: left;
  user-select: none;
}

.py-frame--panel {
  border-image-repeat: round;
  border-color: #5c3a24;
  background-color: #e2bc85;
  color: var(--pyt-color-text-primary);
}

.py-frame--dialog {
  border-color: #5c3a24;
  background-color: #fbe8c5;
  border-radius: 6px;
}

.py-frame--button {
  cursor: pointer;
  background-color: #e2bc85;
  border-color: #5c3a24;
  transition: transform 60ms linear, background-color 120ms linear;
  font-family: inherit;
}
.py-frame--button:focus-visible {
  outline: 2px solid var(--pyt-color-gold);
  outline-offset: 2px;
}

.py-frame--card {
  background-color: #fbe8c5;
  border-color: #5c3a24;
}

.py-frame--modal {
  background-color: #fbe8c5;
  border-color: #5c3a24;
  box-shadow: 0 6px 0 rgba(0, 0, 0, 0.35);
}

.py-frame--tooltip {
  background-color: rgba(28, 24, 16, 0.95);
  border-color: #d4af37;
  color: var(--pyt-color-gold-bright);
  border-radius: 2px;
}

.py-frame--tone-primary {
  box-shadow: inset 0 0 0 1px #d4af37;
}
.py-frame--tone-success {
  border-color: var(--pyt-color-success, var(--pyt-color-success));
  background-color: var(--pyt-color-success-bg, var(--pyt-color-success-bg));
  color: #1f5a2a;
}
.py-frame--tone-warning {
  border-color: var(--pyt-color-warning, var(--pyt-color-warning));
  background-color: var(--pyt-color-warning-bg, var(--pyt-color-warning-bg));
  color: var(--pyt-color-text-hint);
}
.py-frame--tone-danger {
  border-color: var(--pyt-color-danger, var(--pyt-color-danger));
  background-color: var(--pyt-color-danger-bg, var(--pyt-color-danger-bg));
  color: #6e2418;
}

.py-frame--state-hover {
  background-color: #fbe8c5;
}
.py-frame--state-pressed {
  transform: translateY(1px);
  background-color: var(--pyt-color-border);
}

.py-frame--fallback {
  box-shadow: var(--pyt-shadow-card, 0 2px 0 rgba(77, 54, 19, 0.12));
}
.py-frame--modal.py-frame--fallback {
  box-shadow: 0 6px 0 rgba(0, 0, 0, 0.35),
    0 12px 24px rgba(77, 54, 19, 0.18);
}

.py-frame__tail {
  position: absolute;
  width: 0;
  height: 0;
  border-style: solid;
  border-color: transparent;
  pointer-events: none;
}
.py-frame__tail--top {
  top: -8px;
  left: 50%;
  transform: translateX(-50%);
  border-width: 0 8px 8px 8px;
  border-bottom-color: #fbe8c5;
}
.py-frame__tail--right {
  top: 50%;
  right: -8px;
  transform: translateY(-50%);
  border-width: 8px 0 8px 8px;
  border-left-color: #fbe8c5;
}
.py-frame__tail--bottom {
  bottom: -8px;
  left: 50%;
  transform: translateX(-50%);
  border-width: 8px 8px 0 8px;
  border-top-color: #fbe8c5;
}
.py-frame__tail--left {
  top: 50%;
  left: -8px;
  transform: translateY(-50%);
  border-width: 8px 8px 8px 0;
  border-right-color: #fbe8c5;
}

@media (prefers-reduced-motion: reduce) {
  .py-frame--button {
    transition-duration: 30ms;
  }
}
</style>
