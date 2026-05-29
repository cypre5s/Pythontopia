<script setup lang="ts">
// Tab 快捷轮盘：按住弹出 8 扇区，松开执行选中项。
import { computed, onMounted, onBeforeUnmount, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useGameSettingsStore } from '@stores/gameSettings';
import { useWorldStore } from '@stores/world';
import { playUiClick, playUiHover } from '@/composables/useStardewFx';

interface WheelItem {
  icon: string;
  label: string;
  action: () => void;
}

const router = useRouter();
const settings = useGameSettingsStore();
const world = useWorldStore();

const items: readonly WheelItem[] = [
  { icon: 'L',  label: '课程',  action: () => void router.push('/library') },
  { icon: 'A',  label: '闯关',  action: () => void router.push('/arena/1') },
  { icon: 'T',  label: '传送',  action: () => window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open')) },
  { icon: 'Z',  label: '休息',  action: () => void world.sleep() },
  { icon: 'B',  label: '矿洞',  action: () => void router.push('/bug-mine') },
  { icon: 'W',  label: '工坊',  action: () => void router.push('/workshop') },
  { icon: '#',  label: '训练',  action: () => void router.push('/training') },
] as const;

const SECTOR_COUNT = items.length;
const SECTOR_ANGLE = 360 / SECTOR_COUNT;       // 45°
const START_ANGLE = 270;                        // 正上方起始
const ICON_RADIUS = 120;                        // 图标到圆心距离 (px)
const RING_SIZE = 360;                          // 轮盘容器边长 (px)
const SECTOR_SIZE = 68;                         // 扇区按钮边长 (px)
const QUICK_TAP_MS = 200;                       // 快速敲击阈值

const open = computed(() => settings.quickWheelOpen);
const hoveredIndex = ref<number>(-1);
const clickMode = ref(false);                   // 快速敲击后转为点击模式

let openTimestamp = 0;

// ── 当前选中项信息（圆心展示） ──────────────────────────────
const selectedIcon = computed(() => {
  const idx = hoveredIndex.value;
  return idx >= 0 && idx < items.length ? items[idx]!.icon : '⚡';
});
const selectedLabel = computed(() => {
  const idx = hoveredIndex.value;
  return idx >= 0 && idx < items.length ? items[idx]!.label : '松开 Tab 执行';
});

// ── 扇区定位：以圆心为基准，按角度放置到圆周上 ─────────────
function sectorPosition(index: number): Record<string, string> {
  const angleDeg = START_ANGLE + index * SECTOR_ANGLE;
  const angleRad = (angleDeg * Math.PI) / 180;
  const cx = RING_SIZE / 2;
  const cy = RING_SIZE / 2;
  const x = cx + ICON_RADIUS * Math.cos(angleRad) - SECTOR_SIZE / 2;
  const y = cy + ICON_RADIUS * Math.sin(angleRad) - SECTOR_SIZE / 2;
  return {
    left: `${x}px`,
    top: `${y}px`,
    width: `${SECTOR_SIZE}px`,
    height: `${SECTOR_SIZE}px`,
  };
}

// 连接线 —— 从扇区中心指向圆心的伪元素需要角度 + 长度
function connectorStyle(index: number): Record<string, string> {
  const angleDeg = START_ANGLE + index * SECTOR_ANGLE;
  const angleRad = (angleDeg * Math.PI) / 180;
  const dx = ICON_RADIUS * Math.cos(angleRad);
  const dy = ICON_RADIUS * Math.sin(angleRad);
  const length = Math.sqrt(dx * dx + dy * dy);
  const pointToCenterDeg = angleDeg + 180;
  return {
    '--connector-length': `${length - SECTOR_SIZE / 2 - 18}px`,
    '--connector-angle': `${pointToCenterDeg + 90}deg`,
  };
}

// ── 根据鼠标相对圆心角度确定 hover 扇区 ────────────────────
function calcSectorFromMouse(event: MouseEvent): number {
  const ring = document.querySelector('.wheel-ring') as HTMLElement | null;
  if (!ring) return -1;
  const rect = ring.getBoundingClientRect();
  const cx = rect.left + rect.width / 2;
  const cy = rect.top + rect.height / 2;
  const dx = event.clientX - cx;
  const dy = event.clientY - cy;
  const dist = Math.sqrt(dx * dx + dy * dy);
  if (dist < 20) return -1;                    // 太靠近中心则不选
  let angleDeg = (Math.atan2(dy, dx) * 180) / Math.PI;
  if (angleDeg < 0) angleDeg += 360;
  let relative = angleDeg - START_ANGLE;
  if (relative < 0) relative += 360;
  return Math.floor(relative / SECTOR_ANGLE) % SECTOR_COUNT;
}

function onMouseMove(event: MouseEvent): void {
  const idx = calcSectorFromMouse(event);
  if (idx !== hoveredIndex.value) {
    hoveredIndex.value = idx;
    if (idx >= 0) playUiHover();
  }
}

function onHover(index: number): void {
  if (index !== hoveredIndex.value) {
    hoveredIndex.value = index;
    playUiHover();
  }
}

// ── 执行选中项 ──────────────────────────────────────────────
function executeSelected(): void {
  const idx = hoveredIndex.value;
  if (idx >= 0 && idx < SECTOR_COUNT) {
    playUiClick();
    items[idx]!.action();
  }
  closeWheel();
}

function onSelect(index: number): void {
  hoveredIndex.value = index;
  playUiClick();
  if (index >= 0 && index < items.length) items[index]!.action();
  closeWheel();
}

function closeWheel(): void {
  hoveredIndex.value = -1;
  clickMode.value = false;
  settings.closeQuickWheel();
}

function onMaskClick(): void {
  closeWheel();
}

// ── 键盘监听：Tab 长按/短按 + ESC ──────────────────────────
function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
  if (target.isContentEditable) return true;
  return false;
}

function onKeyDown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && open.value) {
    event.preventDefault();
    closeWheel();
    return;
  }

  if (event.key !== 'Tab') return;
  if (isEditableTarget(event.target)) return;
  event.preventDefault();

  if (open.value) return;                       // 已开就不重复触发

  openTimestamp = Date.now();
  clickMode.value = false;
  hoveredIndex.value = -1;
  settings.openQuickWheel();
}

function onKeyUp(event: KeyboardEvent): void {
  if (event.key !== 'Tab') return;
  if (!open.value) return;
  event.preventDefault();

  const held = Date.now() - openTimestamp;
  if (held < QUICK_TAP_MS) {
    // 快速敲击 → 保持打开，转为点击模式
    clickMode.value = true;
    return;
  }
  executeSelected();
}

onMounted(() => {
  window.addEventListener('keydown', onKeyDown);
  window.addEventListener('keyup', onKeyUp);
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeyDown);
  window.removeEventListener('keyup', onKeyUp);
  if (open.value) closeWheel();
});
</script>

<template>
  <transition name="wheel">
    <div
      v-if="open"
      class="wheel-mask"
      @click.self="onMaskClick"
      @mousemove="onMouseMove"
    >
      <div class="wheel-ring">
        <!-- 圆心信息 -->
        <div class="wheel-center">
          <span class="wheel-center__icon">{{ selectedIcon }}</span>
          <span class="wheel-center__label">{{ selectedLabel }}</span>
          <span v-if="clickMode" class="wheel-center__hint">点击选择</span>
        </div>

        <!-- 8 个扇区按钮 -->
        <button
          v-for="(item, i) in items"
          :key="i"
          type="button"
          class="wheel-sector"
          :class="{ 'wheel-sector--active': hoveredIndex === i }"
          :style="{ ...sectorPosition(i), ...connectorStyle(i) }"
          @mouseenter="onHover(i)"
          @click="onSelect(i)"
        >
          <span class="wheel-sector__connector" />
          <span class="wheel-sector__icon">{{ item.icon }}</span>
          <span class="wheel-sector__label">{{ item.label }}</span>
        </button>
      </div>
    </div>
  </transition>
</template>

<style scoped>
/* ── 全屏遮罩 ────────────────────────────────────────────── */
.wheel-mask {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: default;
}

/* ── 轮盘容器 ────────────────────────────────────────────── */
.wheel-ring {
  position: relative;
  width: 360px;
  height: 360px;
  user-select: none;
}

/* ── 中心圆 ──────────────────────────────────────────────── */
.wheel-center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 96px;
  height: 96px;
  background: var(--pyt-panel-bg, rgba(28, 24, 16, 0.94));
  border: 3px solid var(--pyt-panel-border, #d4af37);
  border-radius: 2px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  z-index: 2;
  box-shadow:
    0 0 16px rgba(212, 175, 55, 0.25),
    inset 0 0 8px rgba(0, 0, 0, 0.4);
}

.wheel-center__icon {
  font-size: 28px;
  line-height: 1;
}

.wheel-center__label {
  font-size: 11px;
  color: var(--pyt-text-primary, #fff7e6);
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 84px;
  font-family: var(--pyt-font-pixel, monospace);
}

.wheel-center__hint {
  font-size: 9px;
  color: var(--pyt-text-accent, #ffd166);
  letter-spacing: 0.5px;
  opacity: 0.8;
}

/* ── 扇区按钮 ────────────────────────────────────────────── */
.wheel-sector {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 6px;
  background: var(--pyt-panel-bg, rgba(28, 24, 16, 0.94));
  border: 2px solid var(--pyt-panel-border-dark, #6e4f1d);
  border-radius: 2px;
  color: var(--pyt-text-primary, #fff7e6);
  cursor: pointer;
  transition:
    background 120ms ease,
    border-color 120ms ease,
    box-shadow 120ms ease,
    transform 120ms ease;
  z-index: 1;
  font-family: var(--pyt-font-pixel, monospace);
}

.wheel-sector:hover {
  background: rgba(241, 196, 83, 0.18);
  border-color: var(--pyt-panel-border, #d4af37);
}

.wheel-sector:focus-visible {
  outline: 2px solid var(--pyt-text-accent, #ffd166);
  outline-offset: 2px;
}

.wheel-sector--active {
  background: rgba(241, 196, 83, 0.28);
  border-color: #ffd166;
  box-shadow:
    0 0 12px rgba(255, 209, 102, 0.45),
    0 0 4px rgba(255, 209, 102, 0.3);
  transform: scale(1.08);
  z-index: 3;
}

.wheel-sector__icon {
  font-size: 24px;
  line-height: 1;
}

.wheel-sector__label {
  font-size: 10px;
  letter-spacing: 0.4px;
  white-space: nowrap;
  color: var(--pyt-text-primary, #fff7e6);
}

.wheel-sector--active .wheel-sector__label {
  color: var(--pyt-text-accent, #ffd166);
}

/* ── 连接线（扇区中心 → 轮盘中心） ──────────────────────── */
.wheel-sector__connector {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 1px;
  height: var(--connector-length, 60px);
  background: rgba(212, 175, 55, 0.18);
  transform-origin: top center;
  transform: translateX(-50%) rotate(var(--connector-angle, 0deg));
  pointer-events: none;
  transition: background 120ms ease;
}

.wheel-sector--active .wheel-sector__connector {
  background: rgba(255, 209, 102, 0.35);
  width: 2px;
}

/* ── 弹出 / 收起动画 ────────────────────────────────────── */
.wheel-enter-active {
  transition: opacity 150ms ease-out;
}
.wheel-enter-active .wheel-ring {
  animation: wheel-pop-in 200ms cubic-bezier(0.34, 1.56, 0.64, 1) both;
}

.wheel-leave-active {
  transition: opacity 120ms ease-in;
}
.wheel-leave-active .wheel-ring {
  animation: wheel-pop-out 140ms ease-in both;
}

.wheel-enter-from,
.wheel-leave-to {
  opacity: 0;
}

@keyframes wheel-pop-in {
  from {
    transform: scale(0.6);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}

@keyframes wheel-pop-out {
  from {
    transform: scale(1);
    opacity: 1;
  }
  to {
    transform: scale(0.8);
    opacity: 0;
  }
}
</style>
