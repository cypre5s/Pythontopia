<script setup lang="ts">
/**
 * 移动端虚拟摇杆 + 动作按钮（仅触屏设备显示）。
 *
 * - 左下：动态摇杆，拖动产生 8 向移动；松手归零。
 * - 右下：互动 / 工具 / 跳 / 跑（跑为切换）。
 * - 输出写入 touchControls 共享状态，由 WorldScene 的 createDirectionController
 *   在键盘无输入时读取，故无需改动场景逻辑。
 * - 对话 / 暂停 / 快捷轮盘打开时隐藏并清零，避免隔着模态层误操作角色。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useDialogStore } from '@stores/dialog';
import { useGameSettingsStore } from '@stores/gameSettings';
import {
  resetTouchControls,
  setTouchButton,
  setTouchDirection,
  type TouchButton,
} from '@game/adapters/touchControls';
import type { ExtendedDirection } from '@game/adapters/input';

defineOptions({ name: 'VirtualJoystick' });

const dialog = useDialogStore();
const gameSettings = useGameSettingsStore();

// 摇杆几何（px）
const BASE_RADIUS = 58;
const DEAD_ZONE = 14;
const FULL_CIRCLE = 360;
const DEG_PER = 180 / Math.PI;

const isTouchDevice = ref(false);
const visible = computed(
  () =>
    isTouchDevice.value
    && !dialog.visible
    && !gameSettings.pauseMenuOpen
    && !gameSettings.quickWheelOpen,
);

const baseEl = ref<HTMLElement | null>(null);
const thumbX = ref(0);
const thumbY = ref(0);
const activePointerId = ref<number | null>(null);
const runActive = ref(false);

function angleToDirection(dx: number, dy: number): ExtendedDirection | null {
  const dist = Math.hypot(dx, dy);
  if (dist < DEAD_ZONE) {
    return null;
  }
  // 屏幕坐标 +y 向下；取数学角（右=0°，上=90°），范围归一到 [0,360)。
  const deg = (Math.atan2(-dy, dx) * DEG_PER + FULL_CIRCLE) % FULL_CIRCLE;
  if (deg < 22.5 || deg >= 337.5) return 'RIGHT';
  if (deg < 67.5) return 'UP_RIGHT';
  if (deg < 112.5) return 'UP';
  if (deg < 157.5) return 'UP_LEFT';
  if (deg < 202.5) return 'LEFT';
  if (deg < 247.5) return 'DOWN_LEFT';
  if (deg < 292.5) return 'DOWN';
  return 'DOWN_RIGHT';
}

function updateThumb(clientX: number, clientY: number): void {
  const el = baseEl.value;
  if (!el) {
    return;
  }
  const rect = el.getBoundingClientRect();
  const cx = rect.left + rect.width / 2;
  const cy = rect.top + rect.height / 2;
  let dx = clientX - cx;
  let dy = clientY - cy;
  const dist = Math.hypot(dx, dy);
  if (dist > BASE_RADIUS) {
    dx = (dx / dist) * BASE_RADIUS;
    dy = (dy / dist) * BASE_RADIUS;
  }
  thumbX.value = dx;
  thumbY.value = dy;
  setTouchDirection(angleToDirection(dx, dy));
}

function onPointerDown(e: PointerEvent): void {
  if (activePointerId.value !== null) {
    return;
  }
  activePointerId.value = e.pointerId;
  (e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId);
  updateThumb(e.clientX, e.clientY);
  e.preventDefault();
}

function onPointerMove(e: PointerEvent): void {
  if (e.pointerId !== activePointerId.value) {
    return;
  }
  updateThumb(e.clientX, e.clientY);
  e.preventDefault();
}

function onPointerUp(e: PointerEvent): void {
  if (e.pointerId !== activePointerId.value) {
    return;
  }
  activePointerId.value = null;
  thumbX.value = 0;
  thumbY.value = 0;
  setTouchDirection(null);
  e.preventDefault();
}

function holdButton(button: TouchButton, pressed: boolean): void {
  setTouchButton(button, pressed);
}

function toggleRun(): void {
  runActive.value = !runActive.value;
  setTouchButton('run', runActive.value);
}

function resetAll(): void {
  activePointerId.value = null;
  thumbX.value = 0;
  thumbY.value = 0;
  runActive.value = false;
  resetTouchControls();
}

// 隐藏（开对话/暂停/轮盘）时清零，避免按住态/方向遗留。
watch(visible, (now) => {
  if (!now) {
    resetAll();
  }
});

onMounted(() => {
  isTouchDevice.value =
    typeof navigator !== 'undefined'
    && ((navigator.maxTouchPoints ?? 0) > 0 || 'ontouchstart' in window);
});

onBeforeUnmount(() => {
  resetAll();
});
</script>

<template>
  <div
    v-if="visible"
    class="vjoy"
    data-testid="virtual-joystick"
    aria-hidden="true"
  >
    <div
      ref="baseEl"
      class="vjoy__base"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
    >
      <span class="vjoy__ring" />
      <span
        class="vjoy__thumb"
        :style="{ transform: `translate(${thumbX}px, ${thumbY}px)` }"
      />
    </div>

    <div class="vjoy__buttons">
      <button
        type="button"
        class="vjoy__btn vjoy__btn--run"
        :class="{ 'is-active': runActive }"
        @pointerdown.prevent="toggleRun"
      >
        跑
      </button>
      <button
        type="button"
        class="vjoy__btn vjoy__btn--jump"
        @pointerdown.prevent="holdButton('jump', true)"
        @pointerup.prevent="holdButton('jump', false)"
        @pointercancel.prevent="holdButton('jump', false)"
        @pointerleave.prevent="holdButton('jump', false)"
      >
        跳
      </button>
      <button
        type="button"
        class="vjoy__btn vjoy__btn--tool"
        @pointerdown.prevent="holdButton('tool', true)"
        @pointerup.prevent="holdButton('tool', false)"
        @pointercancel.prevent="holdButton('tool', false)"
        @pointerleave.prevent="holdButton('tool', false)"
      >
        工具
      </button>
      <button
        type="button"
        class="vjoy__btn vjoy__btn--action"
        @pointerdown.prevent="holdButton('action', true)"
        @pointerup.prevent="holdButton('action', false)"
        @pointercancel.prevent="holdButton('action', false)"
        @pointerleave.prevent="holdButton('action', false)"
      >
        互动
      </button>
    </div>
  </div>
</template>

<style scoped>
/* 容器不拦截事件，仅交互子元素接管（pointer-events:auto）。 */
.vjoy {
  position: fixed;
  inset: 0;
  z-index: 80;
  pointer-events: none;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
}

/* ── 左下：移动摇杆 ── */
.vjoy__base {
  position: absolute;
  left: max(20px, env(safe-area-inset-left));
  bottom: calc(24px + env(safe-area-inset-bottom));
  width: 116px;
  height: 116px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 253, 248, 0.34), rgba(92, 74, 50, 0.26));
  border: 2px solid rgba(92, 74, 50, 0.7);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.28);
  pointer-events: auto;
  touch-action: none;
}

.vjoy__ring {
  position: absolute;
  inset: 14px;
  border-radius: 50%;
  border: 1px dashed rgba(92, 74, 50, 0.45);
}

.vjoy__thumb {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #f6d98a, #e0a93f);
  border: 2px solid #5c4a32;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.35);
  will-change: transform;
}

/* ── 右下：动作按钮簇 ── */
.vjoy__buttons {
  position: absolute;
  right: max(20px, env(safe-area-inset-right));
  bottom: calc(24px + env(safe-area-inset-bottom));
  width: 150px;
  height: 150px;
  pointer-events: none;
}

.vjoy__btn {
  position: absolute;
  display: grid;
  place-items: center;
  border-radius: 50%;
  font-family: var(--pyt-font-pixel, sans-serif);
  font-size: 15px;
  font-weight: 700;
  color: #2f2418;
  background: rgba(255, 253, 248, 0.92);
  border: 2px solid #5c4a32;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
  pointer-events: auto;
  touch-action: none;
  user-select: none;
  cursor: pointer;
}

.vjoy__btn:active {
  transform: translateY(1px);
  background: rgba(241, 196, 83, 0.9);
}

/* 互动主键：大、右下角 */
.vjoy__btn--action {
  right: 0;
  bottom: 8px;
  width: 78px;
  height: 78px;
  font-size: 18px;
  background: rgba(241, 196, 83, 0.95);
}

/* 工具：互动键左上 */
.vjoy__btn--tool {
  right: 78px;
  bottom: 26px;
  width: 58px;
  height: 58px;
}

/* 跳：互动键正上 */
.vjoy__btn--jump {
  right: 20px;
  bottom: 92px;
  width: 58px;
  height: 58px;
}

/* 跑（切换）：最上 */
.vjoy__btn--run {
  right: 92px;
  bottom: 96px;
  width: 50px;
  height: 50px;
  font-size: 14px;
}

.vjoy__btn--run.is-active {
  background: var(--pyt-color-success, #1f7a45);
  color: #fffdf8;
}

@media (prefers-reduced-motion: reduce) {
  .vjoy__thumb {
    will-change: auto;
  }
}
</style>
