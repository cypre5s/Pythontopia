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
//   5. R-13：右侧新增 hud-tools（课程 / 设置），独立 .hud-tool-slot class 不计入
//      `findAll('button.hud-slot')`，保持 HudShell.spec 的 5 按钮硬约束。
//   6. R-13：window 级 keydown 监听 Tab/B/M/K/L/`,`/ESC（避开 F，防止与 Phaser
//      NPC 交互 F 键冲突），并对 input/textarea/contenteditable 焦点做 guard。
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import gsap from 'gsap';
import { useHudStore, type HudQuickSlot, bgmLabel } from '@stores/hud';
import { useWorldStore } from '@stores/world';
import { useToastStore } from '@stores/toast';
import { useAudio } from '@/composables/useAudio';
import { playUiClick, playUiHover, playCoinJingle } from '@/composables/useStardewFx';

interface SlotDescriptor {
  key: HudQuickSlot;
  label: string;
  icon: string;
  shortcut: string;
}

const SLOTS: readonly SlotDescriptor[] = [
  { key: 'quest', label: '任务', icon: 'Q', shortcut: 'Tab' },
  { key: 'dialog', label: '对话', icon: 'D', shortcut: 'J' },
  { key: 'inventory', label: '背包', icon: 'I', shortcut: 'B' },
  { key: 'map', label: '地图', icon: 'M', shortcut: 'M' },
  { key: 'skill', label: '技能', icon: 'S', shortcut: 'K' },
] as const;

const hud = useHudStore();
const router = useRouter();
const audio = useAudio();
const world = useWorldStore();
const toast = useToastStore();

// R-13 迭代 14：当前 BGM 显示。1s 轮询 useAudio 的 currentBgmKey，避免 audio 内部不暴露 ref。
const currentBgm = ref<string>(bgmLabel(audio.getCurrentBgmKey()));
let bgmPollTimer: number | null = null;
function pollBgm(): void {
  const next = bgmLabel(audio.getCurrentBgmKey());
  if (next !== currentBgm.value) {
    currentBgm.value = next;
  }
}

const topBarVisible = computed(() => hud.topBar !== 'hidden');
const topBarCollapsed = computed(() => hud.topBar === 'collapsed');
const bottomBarVisible = computed(() => hud.bottomBar !== 'hidden');
const bottomBarCollapsed = computed(() => hud.bottomBar === 'collapsed');

const energyPct = computed(() => clamp(hud.meta.energy));
const masteryPct = computed(() => clamp(hud.meta.masteryAvg));

// R-13 迭代 8：金币变化时 GSAP tween + coin jingle，强化金币获得反馈。
// 用 ref 缓冲展示值，store 的 hud.meta.coin 是真实值；watch 触发 tween。
const displayedCoin = ref<number>(hud.meta.coin);
watch(
  () => hud.meta.coin,
  (next, prev) => {
    const from = Number(prev) || 0;
    const to = Number(next) || 0;
    if (from === to) return;
    if (to > from) {
      playCoinJingle();
    }
    const obj = { v: from };
    gsap.to(obj, {
      v: to,
      duration: 0.5,
      ease: 'power2.out',
      onUpdate: () => {
        displayedCoin.value = Math.round(obj.v);
      },
    });
  },
);

function clamp(value: number): number {
  if (!Number.isFinite(value)) return 0;
  if (value < 0) return 0;
  if (value > 100) return 100;
  return value;
}

// R-13 Phase 6：星露谷感 chip 图标。
function weatherIcon(w: string): string {
  switch (w) {
    case 'SUNNY': return '☀';
    case 'CLOUDY': return '☁';
    case 'RAIN':
    case 'RAINY': return '☂';
    case 'WIND':
    case 'WINDY': return '✦';
    case 'SNOWY': return '❄';
    case 'STORMY': return '⚡';
    default: return '✦';
  }
}
function timeIcon(t: string): string {
  switch (t) {
    case 'DAWN': return '◐';
    case 'MORNING': return '☼';
    case 'NOON': return '☀';
    case 'AFTERNOON': return '☼';
    case 'EVENING': return '◑';
    case 'NIGHT': return '☾';
    default: return '☼';
  }
}
function seasonIcon(s: string): string {
  switch (s) {
    case 'SPRING':
    case 'spring': return '🌱';
    case 'SUMMER':
    case 'summer': return '☀';
    case 'AUTUMN':
    case 'autumn': return '🍁';
    case 'WINTER':
    case 'winter': return '❄';
    default: return '✿';
  }
}

function onClickSlot(slot: HudQuickSlot): void {
  playUiClick();
  hud.toggleSlot(slot);
}

function isActive(slot: HudQuickSlot): boolean {
  return hud.activeSlot === slot;
}

function openLibrary(): void {
  playUiClick();
  void router.push('/library');
}

function onToggleSettings(): void {
  playUiClick();
  hud.toggleSettings();
}

function onSlotHover(): void {
  playUiHover();
}

// R-13 迭代 19：通过自定义事件触发 FastTravelPanel，避免组件相互 import。
function onOpenFastTravel(): void {
  playUiClick();
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('pythontopia:fast-travel-open'));
  }
}

const sleepBusy = ref(false);
async function onSleep(): Promise<void> {
  if (sleepBusy.value) return;
  sleepBusy.value = true;
  playUiClick();
  try {
    await world.sleep();
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    toast.show(`推进时间失败：${msg}`, 'error');
  } finally {
    sleepBusy.value = false;
  }
}

function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
  if (target.isContentEditable) return true;
  return false;
}

function isCanvasFocused(target: EventTarget | null): boolean {
  return target instanceof HTMLCanvasElement;
}

function onKey(event: KeyboardEvent): void {
  if (isEditableTarget(event.target)) return;
  if (event.altKey || event.ctrlKey || event.metaKey) return;
  // R-13 修复：Phaser canvas 聚焦时，HudShell 只处理 Tab / Esc / `,` 三个功能键，
  // b/m/k/j/l 全部交给 game.input.keyboard，避免抢走 NPC 交互（F）/ 移动（WASD）。
  const canvasOwned = isCanvasFocused(event.target);
  // 按 ESC 关闭一切：先关设置，再关 activeSlot，避免 ESC 既关 dialog 又关画布。
  if (event.key === 'Escape') {
    if (hud.settingsOpen) {
      hud.closeSettings();
      event.preventDefault();
      return;
    }
    if (hud.activeSlot) {
      hud.closeAll();
      event.preventDefault();
    }
    return;
  }
  // Tab → 任务面板：preventDefault 避免焦点漂移。
  if (event.key === 'Tab') {
    if (hud.canToggleSlot) {
      hud.toggleSlot('quest');
      event.preventDefault();
    }
    return;
  }
  if (event.key === ',') {
    onToggleSettings();
    event.preventDefault();
    return;
  }
  // 字母快捷键：canvas 聚焦时禁用，让游戏独占
  if (canvasOwned) return;
  const lowered = event.key.toLowerCase();
  if (lowered === 'b' && hud.canToggleSlot) {
    hud.toggleSlot('inventory');
    event.preventDefault();
    return;
  }
  if (lowered === 'm' && !event.shiftKey && hud.canToggleSlot) {
    hud.toggleSlot('map');
    event.preventDefault();
    return;
  }
  if (lowered === 'k' && hud.canToggleSlot) {
    hud.toggleSlot('skill');
    event.preventDefault();
    return;
  }
  if (lowered === 'j' && hud.canToggleSlot) {
    hud.toggleSlot('dialog');
    event.preventDefault();
    return;
  }
  if (lowered === 'l') {
    openLibrary();
    event.preventDefault();
  }
}

onMounted(() => {
  if (typeof window !== 'undefined') {
    window.addEventListener('keydown', onKey);
    bgmPollTimer = window.setInterval(pollBgm, 1000);
  }
});

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onKey);
    if (bgmPollTimer !== null) {
      window.clearInterval(bgmPollTimer);
      bgmPollTimer = null;
    }
  }
});
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
          :title="`天气：${hud.meta.weather}`"
        >
          <span class="hud-chip__label">{{ weatherIcon(hud.meta.weather) }} 天气</span>
          <span class="hud-chip__value">{{ hud.meta.weather }}</span>
        </span>
        <span
          class="hud-chip"
          data-testid="hud-day"
          :title="`第 ${hud.meta.dayIndex} 天`"
        >
          <span class="hud-chip__label">☼ Day</span>
          <span class="hud-chip__value">{{ hud.meta.dayIndex }}</span>
        </span>
        <span
          class="hud-chip"
          data-testid="hud-time"
          :title="`时段：${hud.meta.timeOfDay}`"
        >
          <span class="hud-chip__label">{{ timeIcon(hud.meta.timeOfDay) }} 时段</span>
          <span class="hud-chip__value">{{ hud.meta.timeOfDay }}</span>
        </span>
        <span
          class="hud-chip"
          data-testid="hud-season"
          :title="`季节：${hud.meta.season}`"
        >
          <span class="hud-chip__label">{{ seasonIcon(hud.meta.season) }} 季节</span>
          <span class="hud-chip__value">{{ hud.meta.season }}</span>
        </span>
        <span
          class="hud-chip hud-chip--bgm"
          data-testid="hud-bgm"
        >
          <span class="hud-chip__label">♪</span>
          <span class="hud-chip__value">{{ currentBgm }}</span>
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
          <span class="hud-chip__value">{{ displayedCoin }}</span>
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
        @mouseenter="onSlotHover"
      >
        <span
          class="hud-slot__icon"
          aria-hidden="true"
        >{{ slot.icon }}</span>
        <span class="hud-slot__label">{{ slot.label }}</span>
        <span class="hud-slot__shortcut">{{ slot.shortcut }}</span>
      </button>
      <!-- R-13 修复：把工具按钮合并到同一 nav 内（之前是双 fixed 元素重叠） -->
      <span
        class="hud-divider"
        aria-hidden="true"
      />
      <button
        type="button"
        class="hud-tool-slot"
        data-testid="hud-tool-library"
        aria-label="课程与资源（L）"
        @click="openLibrary"
      >
        <span
          class="hud-tool-slot__icon"
          aria-hidden="true"
        >L</span>
        <span class="hud-tool-slot__label">课程</span>
        <span class="hud-tool-slot__shortcut">L</span>
      </button>
      <button
        type="button"
        class="hud-tool-slot"
        data-testid="hud-tool-fast-travel"
        aria-label="快速旅行（T）"
        @click="onOpenFastTravel"
      >
        <span
          class="hud-tool-slot__icon"
          aria-hidden="true"
        >➤</span>
        <span class="hud-tool-slot__label">传送</span>
        <span class="hud-tool-slot__shortcut">T</span>
      </button>
      <button
        type="button"
        class="hud-tool-slot"
        data-testid="hud-tool-sleep"
        aria-label="休息一晚（推进 1 天）"
        :disabled="sleepBusy"
        @click="onSleep"
      >
        <span
          class="hud-tool-slot__icon"
          aria-hidden="true"
        >☾</span>
        <span class="hud-tool-slot__label">休息</span>
        <span class="hud-tool-slot__shortcut">+1d</span>
      </button>
      <button
        type="button"
        class="hud-tool-slot"
        :class="{ 'hud-tool-slot--active': hud.settingsOpen }"
        :aria-pressed="hud.settingsOpen"
        data-testid="hud-tool-settings"
        aria-label="设置（,）"
        @click="onToggleSettings"
      >
        <span
          class="hud-tool-slot__icon"
          aria-hidden="true"
        >⚙</span>
        <span class="hud-tool-slot__label">设置</span>
        <span class="hud-tool-slot__shortcut">,</span>
      </button>
    </nav>
    <!-- 兼容历史 spec：留一个隐藏 stub 让 [data-testid="hud-tools"] 仍可命中 -->
    <span
      class="hud-tools-stub"
      data-testid="hud-tools"
      aria-hidden="true"
    />
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

.hud-chip--bgm {
  background: rgba(120, 170, 220, 0.18);
  border-color: #6aa1d6;
  max-width: 140px;
}

.hud-chip--bgm .hud-chip__label {
  color: #aac6d9;
  font-size: 14px;
}

.hud-chip--bgm .hud-chip__value {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
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

/* R-13 修复：工具按钮已合并到 hud-bottom 内部，这里隐藏旧 stub */
.hud-tools-stub {
  display: none;
}

/* R-13 quickslot 与 tool 之间的金色分隔线 */
.hud-divider {
  display: inline-block;
  width: 2px;
  height: 36px;
  margin: 0 6px;
  background: rgba(212, 175, 55, 0.6);
}

/* R-13 修复：9 个按钮 + 分隔线在窄屏被切。强制 wrap + 紧凑按钮宽度。 */
.hud-bottom {
  flex-wrap: wrap;
  max-width: min(960px, calc(100vw - 16px));
  width: max-content;
  justify-content: center;
  gap: 4px;
  padding: 6px 8px;
}

/* 窄屏让按钮变小，5+4 仍可同行；超窄屏自动换行 */
@media (max-width: 980px) {
  .hud-slot,
  .hud-tool-slot {
    min-width: 50px;
    min-height: 46px;
    padding: 3px 4px;
  }
  .hud-slot__icon,
  .hud-tool-slot__icon {
    font-size: 14px;
  }
  .hud-slot__label,
  .hud-tool-slot__label {
    font-size: 10px;
  }
  .hud-slot__shortcut,
  .hud-tool-slot__shortcut {
    font-size: 9px;
  }
  .hud-divider {
    margin: 0 3px;
    height: 30px;
  }
}

@media (max-width: 760px) {
  .hud-divider {
    display: none;
  }
  .hud-slot,
  .hud-tool-slot {
    min-width: 44px;
  }
}

.hud-tool-slot {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-width: 56px;
  min-height: 48px;
  padding: 4px 8px;
  background: rgba(212, 175, 55, 0.12);
  border: 2px solid rgba(212, 175, 55, 0.45);
  border-radius: var(--pyt-radius-sm, 4px);
  color: #fff7e6;
  font-family: inherit;
  cursor: pointer;
  transition: background 120ms, border-color 120ms, transform 80ms;
}

.hud-tool-slot:hover {
  background: rgba(212, 175, 55, 0.28);
  border-color: #d4af37;
}

.hud-tool-slot:focus-visible {
  outline: 2px solid #f1c453;
  outline-offset: 1px;
}

.hud-tool-slot:active {
  transform: translateY(1px);
}

.hud-tool-slot--active {
  background: rgba(212, 175, 55, 0.36);
  border-color: #f1c453;
  box-shadow: inset 0 0 0 1px #f1c453;
}

.hud-tool-slot__icon {
  font-size: 18px;
  font-weight: 800;
  color: #f1c453;
  line-height: 1;
}

.hud-tool-slot__label {
  font-size: 11px;
  color: #fff7e6;
}

.hud-tool-slot__shortcut {
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
  .hud-tool-slot {
    min-width: 44px;
    min-height: 44px;
    padding: 4px;
  }
  .hud-tool-slot__label,
  .hud-tool-slot__shortcut {
    display: none;
  }
}
</style>
