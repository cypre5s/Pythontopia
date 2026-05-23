<script setup lang="ts">
// W3 HUD-Shell：固定顶部状态条 + 固定底部快捷栏。
//
// 设计要点：
//   1. 完全 store-driven：props 与 emits 都为空，只读 useHudStore，避免父组件级别的状态分裂；
//   2. 顶部 / 底部使用 position: fixed，预留中间区域给 Phaser canvas，且 `pointer-events:none`
//      传到容器只在交互元素上 `pointer-events:auto`，确保鼠标穿透到画布；
//   3. 5 个 quickslot 按钮固定顺序 Quest / Dialog / Inventory / Map / Skill，与
//      stores/hud.ts 的 `HudQuickSlot` 类型一一对应；
//   4. 视觉沿用 src/styles/tokens.css 已有像素风变量（fef7e3 / 4d3613 / d4af37），
//      不引入新色板，与 LoginForm / OperationHints / ChapterNavPanel 协调。
import { computed } from 'vue';
import { useHudStore, type HudQuickSlot } from '@stores/hud';

interface SlotDescriptor {
  key: HudQuickSlot;
  label: string;
  icon: string;
  shortcut: string;
}

const SLOTS: readonly SlotDescriptor[] = [
  { key: 'quest', label: '任务', icon: 'Q', shortcut: 'Tab' },
  { key: 'dialog', label: '对话', icon: 'D', shortcut: 'F' },
  { key: 'inventory', label: '背包', icon: 'I', shortcut: 'B' },
  { key: 'map', label: '地图', icon: 'M', shortcut: 'M' },
  { key: 'skill', label: '技能', icon: 'S', shortcut: 'K' },
] as const;

const hud = useHudStore();

const topBarVisible = computed(() => hud.topBar !== 'hidden');
const topBarCollapsed = computed(() => hud.topBar === 'collapsed');
const bottomBarVisible = computed(() => hud.bottomBar !== 'hidden');
const bottomBarCollapsed = computed(() => hud.bottomBar === 'collapsed');

const energyPct = computed(() => clamp(hud.meta.energy));
const masteryPct = computed(() => clamp(hud.meta.masteryAvg));

function clamp(value: number): number {
  if (!Number.isFinite(value)) return 0;
  if (value < 0) return 0;
  if (value > 100) return 100;
  return value;
}

function onClickSlot(slot: HudQuickSlot): void {
  hud.toggleSlot(slot);
}

function isActive(slot: HudQuickSlot): boolean {
  return hud.activeSlot === slot;
}
</script>

<template>
  <div
    class="hud-shell"
    data-testid="hud-shell"
  >
    <header
      v-if="topBarVisible"
      class="hud-top"
      :class="{ 'hud-top--collapsed': topBarCollapsed }"
      data-testid="hud-top"
      role="status"
      aria-label="顶部状态条"
    >
      <div class="hud-top__group">
        <span
          class="hud-chip"
          data-testid="hud-weather"
        >
          <span class="hud-chip__label">天气</span>
          <span class="hud-chip__value">{{ hud.meta.weather }}</span>
        </span>
        <span
          class="hud-chip"
          data-testid="hud-day"
        >
          <span class="hud-chip__label">Day</span>
          <span class="hud-chip__value">{{ hud.meta.dayIndex }}</span>
        </span>
        <span
          class="hud-chip"
          data-testid="hud-time"
        >
          <span class="hud-chip__label">时段</span>
          <span class="hud-chip__value">{{ hud.meta.timeOfDay }}</span>
        </span>
        <span
          class="hud-chip"
          data-testid="hud-season"
        >
          <span class="hud-chip__label">季节</span>
          <span class="hud-chip__value">{{ hud.meta.season }}</span>
        </span>
      </div>
      <div class="hud-top__group">
        <div
          class="hud-bar"
          data-testid="hud-energy"
          :aria-valuenow="energyPct"
          aria-valuemin="0"
          aria-valuemax="100"
          role="progressbar"
          aria-label="能量"
        >
          <span class="hud-bar__label">能量</span>
          <span class="hud-bar__track">
            <span
              class="hud-bar__fill hud-bar__fill--energy"
              :style="{ width: `${energyPct}%` }"
            />
          </span>
          <span class="hud-bar__value">{{ energyPct }}</span>
        </div>
        <span
          class="hud-chip hud-chip--coin"
          data-testid="hud-coin"
        >
          <span class="hud-chip__label">金币</span>
          <span class="hud-chip__value">{{ hud.meta.coin }}</span>
        </span>
        <div
          class="hud-bar"
          data-testid="hud-mastery"
          :aria-valuenow="masteryPct"
          aria-valuemin="0"
          aria-valuemax="100"
          role="progressbar"
          aria-label="平均掌握度"
        >
          <span class="hud-bar__label">掌握</span>
          <span class="hud-bar__track">
            <span
              class="hud-bar__fill hud-bar__fill--mastery"
              :style="{ width: `${masteryPct}%` }"
            />
          </span>
          <span class="hud-bar__value">{{ masteryPct }}</span>
        </div>
      </div>
    </header>

    <nav
      v-if="bottomBarVisible"
      class="hud-bottom"
      :class="{ 'hud-bottom--collapsed': bottomBarCollapsed }"
      data-testid="hud-bottom"
      aria-label="快捷面板"
    >
      <button
        v-for="slot in SLOTS"
        :key="slot.key"
        type="button"
        class="hud-slot"
        :class="{ 'hud-slot--active': isActive(slot.key) }"
        :data-testid="`hud-slot-${slot.key}`"
        :aria-pressed="isActive(slot.key)"
        :aria-label="`${slot.label}（${slot.shortcut}）`"
        @click="onClickSlot(slot.key)"
      >
        <span
          class="hud-slot__icon"
          aria-hidden="true"
        >{{ slot.icon }}</span>
        <span class="hud-slot__label">{{ slot.label }}</span>
        <span class="hud-slot__shortcut">{{ slot.shortcut }}</span>
      </button>
    </nav>
  </div>
</template>

<style scoped>
.hud-shell {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 100;
}

.hud-top,
.hud-bottom {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: var(--pyt-space-3, 12px);
  background: rgba(28, 24, 16, 0.82);
  border: 2px solid var(--pyt-color-border, #d4af37);
  border-radius: var(--pyt-radius-md, 6px);
  color: var(--pyt-color-text-on-dark, #fff7e6);
  font-family: var(--pyt-font-pixel, 'Ark Pixel 12'), 'PingFang SC', monospace;
  font-size: var(--pyt-font-size-sm, 12px);
  letter-spacing: 0.5px;
  pointer-events: auto;
  box-shadow: var(--pyt-shadow-card, 0 2px 0 rgba(77, 54, 19, 0.12));
  user-select: none;
  z-index: 100;
}

.hud-top {
  top: 12px;
  padding: 8px 18px;
  max-width: calc(100vw - 32px);
  flex-wrap: wrap;
  justify-content: space-between;
}

.hud-top__group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hud-top--collapsed {
  padding: 2px 12px;
  font-size: var(--pyt-font-size-xs, 11px);
}

.hud-top--collapsed .hud-bar,
.hud-top--collapsed .hud-chip__label {
  display: none;
}

.hud-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: rgba(212, 175, 55, 0.18);
  border: 1px solid rgba(212, 175, 55, 0.5);
  border-radius: var(--pyt-radius-sm, 4px);
}

.hud-chip--coin {
  background: rgba(241, 196, 83, 0.22);
  border-color: #f1c453;
}

.hud-chip__label {
  color: #d4af37;
  font-size: 11px;
}

.hud-chip__value {
  color: #fff7e6;
  font-weight: 700;
}

.hud-bar {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 130px;
}

.hud-bar__label {
  color: #d4af37;
  font-size: 11px;
}

.hud-bar__track {
  position: relative;
  display: inline-block;
  width: 70px;
  height: 8px;
  background: rgba(255, 247, 230, 0.18);
  border: 1px solid #4d3613;
  border-radius: 2px;
  overflow: hidden;
}

.hud-bar__fill {
  display: block;
  height: 100%;
  transition: width 220ms ease-out;
}

.hud-bar__fill--energy {
  background: #6bbf59;
}

.hud-bar__fill--mastery {
  background: #f1c453;
}

.hud-bar__value {
  color: #fff7e6;
  font-weight: 700;
  font-size: 11px;
  min-width: 24px;
  text-align: right;
}

.hud-bottom {
  bottom: 16px;
  padding: 6px 10px;
  gap: 6px;
}

.hud-bottom--collapsed {
  padding: 2px 6px;
}

.hud-slot {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-width: 64px;
  min-height: 56px;
  padding: 6px 8px;
  background: rgba(212, 175, 55, 0.12);
  border: 2px solid rgba(212, 175, 55, 0.45);
  border-radius: var(--pyt-radius-sm, 4px);
  color: #fff7e6;
  font-family: inherit;
  cursor: pointer;
  transition: background 120ms, border-color 120ms, transform 80ms;
}

.hud-slot:hover {
  background: rgba(212, 175, 55, 0.28);
  border-color: #d4af37;
}

.hud-slot:active {
  transform: translateY(1px);
}

.hud-slot--active {
  background: rgba(212, 175, 55, 0.36);
  border-color: #f1c453;
  box-shadow: inset 0 0 0 1px #f1c453;
}

.hud-slot__icon {
  font-size: 18px;
  font-weight: 800;
  color: #f1c453;
  line-height: 1;
}

.hud-slot__label {
  font-size: 11px;
  color: #fff7e6;
}

.hud-slot__shortcut {
  font-size: 10px;
  color: rgba(255, 247, 230, 0.6);
}

@media (max-width: 760px) {
  .hud-top {
    gap: 6px;
    padding: 6px 10px;
    font-size: 11px;
  }
  .hud-bar {
    min-width: 100px;
  }
  .hud-bar__track {
    width: 56px;
  }
  .hud-slot {
    min-width: 52px;
    min-height: 48px;
    padding: 4px;
  }
}
</style>
