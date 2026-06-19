<script setup lang="ts">
// HUD：紧凑状态条 + 底部 Dock(5快捷+菜单) + 日夜配色。
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useHudStore, type HudQuickSlot, bgmLabel } from '@stores/hud';
import { useWorldStore } from '@stores/world';
import { useToastStore } from '@stores/toast';
import { useMainlineStore, clampStars } from '@stores/mainline';
import { useSessionStore } from '@stores/session';
import { useGameSettingsStore } from '@stores/gameSettings';
import { useGuideStore } from '@stores/guide';
import { useAudio } from '@/composables/useAudio';
import { playUiClick, playUiHover } from '@/composables/useStardewFx';

interface SlotDescriptor {
  key: HudQuickSlot;
  label: string;
  icon: string;
  iconImg?: string;
  shortcut: string;
}

const ICON_BASE = '/assets/pixel/icons/dock';

// R-15：删「任务」槽（与冒险地图重复）；「地图」改为直达冒险地图（/mainline-map），
// 不再切 activeSlot（小地图 HUD 常驻右上角，无需 dock 再开一份）。
const SLOTS: readonly SlotDescriptor[] = [
  { key: 'dialog', label: '对话', icon: '✉', iconImg: `${ICON_BASE}/speech-bubble.svg`, shortcut: 'J' },
  { key: 'map', label: '地图', icon: '◰', iconImg: `${ICON_BASE}/treasure-map.svg`, shortcut: 'M' },
  { key: 'skill', label: '技能', icon: '★', iconImg: `${ICON_BASE}/star-formation.png`, shortcut: 'K' },
] as const;

const hud = useHudStore();
const route = useRoute();
const router = useRouter();
const audio = useAudio();
const world = useWorldStore();
const toast = useToastStore();
const mainline = useMainlineStore();
const session = useSessionStore();
const gameSettings = useGameSettingsStore();
const guide = useGuideStore();

const currentBgm = ref<string>(bgmLabel(audio.getCurrentBgmKey()));
let bgmPollTimer: number | null = null;
function pollBgm(): void {
  const next = bgmLabel(audio.getCurrentBgmKey());
  if (next !== currentBgm.value) {
    currentBgm.value = next;
  }
}

const topBarVisible = computed(() => hud.topBar !== 'hidden');
const bottomBarVisible = computed(() => hud.bottomBar !== 'hidden');
const bottomBarCollapsed = computed(() => hud.bottomBar === 'collapsed');

const energyPct = computed(() => clamp(hud.meta.energy));
const masteryPct = computed(() => clamp(hud.meta.masteryAvg));

// 日夜 / 季节 CSS 类，驱动 pixel-ui.css 中的颜色变量切换
const timeClass = computed(() => `pyt-time--${hud.meta.timeOfDay.toLowerCase()}`);
const seasonClass = computed(() => `pyt-season--${hud.meta.season.toLowerCase()}`);

// 主线星级信息（中间列星星条展示，后端权威 mainline overview）。
const MAX_TOTAL_STARS = 21; // 7 章 × 3★
const currentChapter = computed<number>(() => mainline.firstActionableChapter);
const chapterTitle = computed<string>(() => {
  const view = mainline.chapterByNumber(currentChapter.value);
  return view?.title ?? 'Python 学习之旅';
});
const chapterStars = computed<number>(() => clampStars(mainline.chapterByNumber(currentChapter.value)?.stars ?? 0));
const chapterStarText = computed<string>(
  () => '★'.repeat(chapterStars.value) + '☆'.repeat(3 - chapterStars.value),
);
const totalStars = computed<number>(() => mainline.totalStars);
const totalStarText = computed<string>(() => `${totalStars.value} / ${MAX_TOTAL_STARS}`);
const overallPct = computed<number>(() =>
  Math.round((totalStars.value / MAX_TOTAL_STARS) * 100),
);

function clamp(value: number): number {
  if (!Number.isFinite(value)) return 0;
  if (value < 0) return 0;
  if (value > 100) return 100;
  return value;
}

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
    case 'spring': return '*';
    case 'SUMMER':
    case 'summer': return '+';
    case 'AUTUMN':
    case 'autumn': return '~';
    case 'WINTER':
    case 'winter': return '.';
    default: return '*';
  }
}

function onClickSlot(slot: HudQuickSlot): void {
  playUiClick();
  // R-15：「地图」直达冒险地图（不切 activeSlot 面板）；其余槽走原 toggle 逻辑。
  if (slot === 'map') {
    void router.push('/mainline-map');
    return;
  }
  hud.toggleSlot(slot);
}

function isActive(slot: HudQuickSlot): boolean {
  return hud.activeSlot === slot;
}

// 伴学向导单一入口（常驻 Dock）。独立 store flag，可与其他面板共存。
function onClickGuide(): void {
  playUiClick();
  guide.toggle();
}

/** Dock「指南」：跳转 /guide 图文操作手册。 */
function onOpenManual(): void {
  playUiClick();
  void router.push('/guide');
}

function openLibrary(): void {
  playUiClick();
  void router.push('/library');
}

function openArena(): void {
  playUiClick();
  void router.push('/expedition');
}

function onToggleSettings(): void {
  playUiClick();
  hud.toggleSettings();
}

function onOpenMenu(): void {
  playUiClick();
  gameSettings.openPauseMenu();
}

function onSlotHover(): void {
  playUiHover();
}

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

function onLogout(): void {
  playUiClick();
  session.logout();
  void router.push({ name: 'login' });
}

function onClickQuestSummary(): void {
  playUiClick();
  void router.push('/mainline-map');
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

/** 小镇内 WASD / C 等交给 Phaser，不与 HUD 单键导航抢键。 */
function isWorldGameplayActive(): boolean {
  return route.name === 'world';
}

function onToggleTopBar(): void {
  playUiClick();
  hud.toggleTopBar();
}

function onKey(event: KeyboardEvent): void {
  if (event.altKey || event.ctrlKey || event.metaKey) return;

  // Tab → 打开快捷轮盘（可编辑元素内不拦截）
  if (event.key === 'Tab') {
    if (isEditableTarget(event.target)) return;
    event.preventDefault();
    gameSettings.openQuickWheel();
    return;
  }

  if (isEditableTarget(event.target)) return;

  // H 键切换顶部 HUD 显示/隐藏
  if (event.key === 'h' || event.key === 'H') {
    onToggleTopBar();
    event.preventDefault();
    return;
  }

  const canvasOwned = isCanvasFocused(event.target);

  // ESC 优先级链：暂停菜单 → 轮盘 → 设置 → 面板 → 打开暂停菜单
  if (event.key === 'Escape') {
    event.preventDefault();
    if (gameSettings.pauseMenuOpen) { gameSettings.closePauseMenu(); return; }
    if (gameSettings.quickWheelOpen) { gameSettings.closeQuickWheel(); return; }
    if (hud.settingsOpen) { hud.closeSettings(); return; }
    if (guide.open) { guide.closePanel(); return; }
    if (hud.activeSlot) { hud.closeAll(); return; }
    gameSettings.openPauseMenu();
    return;
  }

  if (event.key === ',') {
    onToggleSettings();
    event.preventDefault();
    return;
  }
  // G → 伴学向导（即便游戏画布聚焦也可呼出，单一入口）
  if ((event.key === 'g' || event.key === 'G') && !event.shiftKey) {
    onClickGuide();
    event.preventDefault();
    return;
  }
  if (canvasOwned) return;
  const lowered = event.key.toLowerCase();
  // M（地图）直达冒险地图；不再占用 Q（任务槽已删）。
  if (lowered === 'm' && !event.shiftKey && hud.canToggleSlot) {
    void router.push('/mainline-map');
    event.preventDefault();
    return;
  }
  const slotByLetter: Record<string, HudQuickSlot | undefined> = {
    j: 'dialog',
    k: 'skill',
  };
  const target = slotByLetter[lowered];
  if (target && !event.shiftKey && hud.canToggleSlot) {
    hud.toggleSlot(target);
    event.preventDefault();
    return;
  }
  if (lowered === 'l') {
    openLibrary();
    event.preventDefault();
    return;
  }
  // 闯关：Shift+A（单键 A 与小镇左移冲突，见 input.ts）
  if (lowered === 'a' && event.shiftKey) {
    openArena();
    event.preventDefault();
    return;
  }
  if (isWorldGameplayActive() && (lowered === 'a' || lowered === 'c')) {
    return;
  }
  if (lowered === 't' && !event.shiftKey) {
    onOpenFastTravel();
    event.preventDefault();
    return;
  }
  if (lowered === 'c' && !event.shiftKey) {
    void onSleep();
    event.preventDefault();
  }
}

onMounted(() => {
  // 拉取后端权威主线总览，填充 HUD 星星条（失败由 store 内部静默兜底）。
  void mainline.load();
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
    :class="[timeClass, seasonClass]"
    data-testid="hud-shell"
  >
    <!-- 顶栏隐藏时的触发条 -->
    <button
      v-if="!topBarVisible"
      type="button"
      class="hud-reveal-strip"
      data-testid="hud-reveal-strip"
      title="展开 HUD (H)"
      aria-label="展开 HUD"
      @click="onToggleTopBar"
    >
      <svg
        class="hud-reveal-strip__arrow"
        width="16"
        height="6"
        viewBox="0 0 16 6"
        aria-hidden="true"
      >
        <path
          d="M4 0h8v1H4zM3 2h10v1H3zM2 4h12v1H2z"
          fill="currentColor"
        />
      </svg>
    </button>

    <!-- 紧凑状态条：左上角天气+时间，右上角玩家 -->
    <div
      v-if="topBarVisible"
      class="hud-status-strip"
      data-testid="hud-top"
      role="status"
      aria-label="状态条"
    >
      <div class="hud-status-left">
        <button
          type="button"
          class="hud-chip hud-chip--btn"
          data-testid="hud-weather"
          :title="`天气：${hud.meta.weather}`"
          @click="onSleep"
        >
          <span class="hud-chip__icon">{{ weatherIcon(hud.meta.weather) }}</span>
          <span class="hud-chip__value">{{ hud.meta.weather }}</span>
        </button>
        <button
          type="button"
          class="hud-chip hud-chip--btn"
          data-testid="hud-day"
          title="点击推进时间"
          @click="onSleep"
        >
          <span class="hud-chip__value">Day {{ hud.meta.dayIndex }}</span>
        </button>
        <button
          type="button"
          class="hud-chip hud-chip--btn"
          data-testid="hud-time"
          :title="`时段：${hud.meta.timeOfDay}`"
          @click="onSleep"
        >
          <span class="hud-chip__icon">{{ timeIcon(hud.meta.timeOfDay) }}</span>
        </button>
        <button
          type="button"
          class="hud-chip hud-chip--btn"
          data-testid="hud-season"
          :title="`季节：${hud.meta.season}`"
          @click="onClickQuestSummary"
        >
          <span class="hud-chip__icon">{{ seasonIcon(hud.meta.season) }}</span>
        </button>
      </div>
      <div class="hud-status-right">
        <button
          type="button"
          class="hud-status-name hud-chip--btn"
          title="点击查看画像"
          @click="playUiClick(); router.push('/profile')"
        >
          {{ session.displayName }}
        </button>
        <button
          type="button"
          class="hud-logout"
          data-testid="hud-logout"
          aria-label="退出登录"
          @click="onLogout"
        >
          退出
        </button>
        <button
          type="button"
          class="hud-hide-btn"
          data-testid="hud-hide-btn"
          title="隐藏 HUD (H)"
          aria-label="隐藏 HUD"
          @click="onToggleTopBar"
        >
          <svg
            width="10"
            height="6"
            viewBox="0 0 10 6"
            aria-hidden="true"
          >
            <path
              d="M1 5h8v-1H1zM2 3h6v-1H2zM3 1h4v-1H3z"
              fill="currentColor"
            />
          </svg>
        </button>
      </div>
    </div>
    <!-- 能量/掌握/BGM/番茄钟 保留为隐藏的 a11y 元素 -->
    <div
      class="hud-meta-hidden"
      aria-hidden="false"
      style="position:absolute;top:-9999px;"
    >
      <div
        class="hud-bar"
        data-testid="hud-energy"
        :aria-valuenow="energyPct"
        aria-valuemin="0"
        aria-valuemax="100"
        role="progressbar"
        aria-label="能量"
      >
        <span class="hud-bar__value">{{ energyPct }}</span>
      </div>
      <div
        class="hud-bar"
        data-testid="hud-mastery"
        :aria-valuenow="masteryPct"
        aria-valuemin="0"
        aria-valuemax="100"
        role="progressbar"
        aria-label="平均掌握度"
      >
        <span class="hud-bar__value">{{ masteryPct }}</span>
      </div>
      <span data-testid="hud-bgm">{{ currentBgm }}</span>
    </div>
    <!-- 中间顶部：主线星星条（紧凑一行，点击进冒险地图） -->
    <button
      v-if="topBarVisible"
      type="button"
      class="hud-quest-bar"
      data-testid="hud-quest-summary"
      :aria-label="`主线星级：第 ${currentChapter} 章 ${chapterTitle}（本章 ${chapterStarText}），总星 ${totalStarText}`"
      @click="onClickQuestSummary"
    >
      <span class="hud-quest-bar__chapter">第{{ currentChapter }}章</span>
      <span class="hud-quest-bar__title">{{ chapterTitle }}</span>
      <span
        class="hud-quest-bar__stars"
        aria-hidden="true"
      >{{ chapterStarText }}</span>
      <span
        class="hud-quest-bar__fill"
        :style="{ width: `${overallPct}%` }"
      />
      <span class="hud-quest-bar__count">★ {{ totalStarText }}</span>
    </button>

    <nav
      v-if="bottomBarVisible"
      class="hud-bottom"
      :class="{ 'hud-bottom--collapsed': bottomBarCollapsed }"
      data-testid="hud-bottom"
      aria-label="快捷面板"
    >
      <!-- AI 助教 = 单一入口，常驻 Dock 最左（calm secondary，不计入 .hud-slot） -->
      <button
        type="button"
        class="hud-guide-slot"
        :class="{ 'hud-guide-slot--active': guide.open }"
        data-testid="hud-guide"
        :aria-pressed="guide.open"
        aria-label="AI 助教（G）"
        title="AI 助教（G）"
        @click="onClickGuide"
        @mouseenter="onSlotHover"
      >
        <span
          class="hud-guide-slot__icon"
          aria-hidden="true"
        >✧</span>
        <span class="hud-guide-slot__label">AI 助教</span>
        <span class="hud-guide-slot__shortcut">G</span>
      </button>
      <span
        class="hud-divider"
        aria-hidden="true"
      />
      <button
        v-for="slot in SLOTS"
        :key="slot.key"
        type="button"
        class="hud-slot"
        :class="{ 'hud-slot--active': isActive(slot.key) }"
        :data-testid="`hud-slot-${slot.key}`"
        :aria-pressed="isActive(slot.key)"
        :aria-label="`${slot.label}（${slot.shortcut}）`"
        :title="`${slot.label}（${slot.shortcut}）`"
        @click="onClickSlot(slot.key)"
        @mouseenter="onSlotHover"
      >
        <span
          class="hud-slot__icon"
          aria-hidden="true"
        >
          <img
            v-if="slot.iconImg"
            :src="slot.iconImg"
            :alt="slot.label"
            class="hud-slot__icon-img"
          >
          <template v-else>{{ slot.icon }}</template>
        </span>
        <span class="hud-slot__label">{{ slot.label }}</span>
        <span class="hud-slot__shortcut">{{ slot.shortcut }}</span>
      </button>
      <!-- 指南 = 跳转 /guide 图文操作指南页（与「技能」相邻，便于新手发现） -->
      <button
        type="button"
        class="hud-slot"
        data-testid="hud-slot-manual"
        aria-label="指南（图文操作手册）"
        title="指南（图文操作手册）"
        @click="onOpenManual"
        @mouseenter="onSlotHover"
      >
        <span
          class="hud-slot__icon"
          aria-hidden="true"
        >📖</span>
        <span class="hud-slot__label">指南</span>
        <span class="hud-slot__shortcut">?</span>
      </button>
      <span
        class="hud-divider"
        aria-hidden="true"
      />
      <button
        type="button"
        class="hud-tool-slot"
        data-testid="hud-tool-menu"
        aria-label="菜单（ESC）"
        title="菜单（ESC）"
        @click="onOpenMenu"
        @mouseenter="onSlotHover"
      >
        <span
          class="hud-tool-slot__icon"
          aria-hidden="true"
        >☰</span>
        <span class="hud-tool-slot__label">菜单</span>
        <span class="hud-tool-slot__shortcut">ESC</span>
      </button>
    </nav>
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

/* ── 通用半透明深色游戏 HUD panel ──────────────────────────────── */
.game-panel,
.hud-top,
.hud-bottom {
  background: var(--pyt-hud-bg);
  border: 1px solid var(--pyt-hud-border);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

/* ── 顶栏隐藏后的触发条 ────────────────────────────────────────── */
.hud-reveal-strip {
  position: fixed;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48px;
  height: 14px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: var(--pyt-hud-bg);
  border: 1px solid var(--pyt-hud-border);
  border-top: none;
  border-radius: 0 0 4px 4px;
  color: var(--pyt-text-secondary);
  cursor: pointer;
  pointer-events: auto;
  z-index: 110;
  opacity: 0.4;
  transition: opacity 180ms, height 180ms, background 180ms;
  padding-bottom: 1px;
  font-family: inherit;
}
.hud-reveal-strip:hover {
  opacity: 1;
  height: 18px;
  background: var(--pyt-btn-hover-bg);
  color: var(--pyt-text-accent);
}
.hud-reveal-strip__arrow {
  display: block;
}

/* ── 可点击芯片 ────────────────────────────────────────────────── */
.hud-chip--btn {
  cursor: pointer;
  transition: background 120ms, border-color 120ms;
}
.hud-chip--btn:hover {
  background: rgba(212, 175, 55, 0.3);
  border-color: var(--pyt-text-accent, var(--pyt-color-gold-bright));
}
.hud-chip--btn:active {
  transform: translateY(1px);
}

/* ── 顶栏隐藏按钮 ──────────────────────────────────────────────── */
.hud-hide-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  background: transparent;
  border: 1px solid var(--pyt-hud-border);
  border-radius: 2px;
  color: var(--pyt-text-secondary);
  cursor: pointer;
  transition: background 120ms, color 120ms;
}
.hud-hide-btn:hover {
  background: var(--pyt-btn-hover-bg);
  color: var(--pyt-text-accent);
}

/* ── 顶栏展开/收起动画 ──────────────────────────────────────────── */
.hud-status-strip {
  animation: hud-slide-down 240ms ease-out both;
}
.hud-quest-bar {
  animation: hud-slide-down 240ms ease-out 60ms both;
}

/* ── 紧凑状态条 ────────────────────────────────────────────────── */
.hud-status-strip {
  position: fixed;
  top: 8px;
  left: 12px;
  right: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  pointer-events: auto;
  user-select: none;
  z-index: 100;
  font-size: 11px;
  color: var(--pyt-text-primary);
  font-family: var(--pyt-font-pixel);
}
.hud-status-left,
.hud-status-right {
  display: flex;
  align-items: center;
  gap: 4px;
  background: var(--pyt-hud-bg);
  border: 1px solid var(--pyt-hud-border);
  border-radius: 2px;
  padding: 3px 8px;
  backdrop-filter: blur(4px);
}
.hud-status-name {
  background: transparent;
  border: 1px solid transparent;
  padding: 2px 6px;
  border-radius: 2px;
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-text-primary);
  font-weight: 600;
  cursor: pointer;
  font-size: 11px;
  max-width: 10ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 中间进度条 */
.hud-quest-bar {
  position: fixed;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--pyt-hud-bg);
  border: 1px solid var(--pyt-hud-border);
  border-radius: 2px;
  padding: 4px 12px;
  color: var(--pyt-text-primary);
  font-family: var(--pyt-font-pixel);
  font-size: 11px;
  cursor: pointer;
  pointer-events: auto;
  z-index: 100;
  min-width: 200px;
  max-width: 380px;
  backdrop-filter: blur(4px);
}
.hud-quest-bar:hover { background: rgba(241, 196, 83, 0.12); }
.hud-quest-bar__chapter {
  color: var(--pyt-text-accent);
  font-weight: 700;
  white-space: nowrap;
}
.hud-quest-bar__title {
  color: var(--pyt-text-primary);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.hud-quest-bar__stars {
  color: var(--pyt-text-accent, var(--pyt-color-gold-bright));
  font-size: 11px;
  letter-spacing: 1px;
  white-space: nowrap;
}
.hud-quest-bar__fill {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 2px;
  background: linear-gradient(90deg, var(--pyt-bar-energy) 0%, var(--pyt-text-accent) 100%);
  border-radius: 0 0 2px 2px;
  transition: width 240ms ease-out;
}
.hud-quest-bar__count {
  color: var(--pyt-text-secondary);
  font-size: 10px;
  white-space: nowrap;
}

/* 旧类保留兼容 */
.hud-top { display: none; }
.hud-top--collapsed .hud-quest__progress,
.hud-top--collapsed .hud-meta-strip {
  display: none;
}

.hud-top__col {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.hud-top__col--left {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.hud-top__col--center {
  justify-content: center;
  flex-direction: column;
  align-items: stretch;
  gap: 4px;
  min-width: 0;
  background: none;
  border: none;
  cursor: pointer;
  color: inherit;
  font-family: inherit;
  text-align: center;
  padding: 4px 12px;
  border-radius: 2px;
  transition: background 140ms ease;
}
.hud-top__col--center:hover {
  background: rgba(241, 196, 83, 0.12);
}
.hud-top__col--center:focus-visible {
  outline: 2px solid var(--pyt-color-gold);
  outline-offset: 2px;
}

.hud-top__col--right {
  justify-content: flex-end;
  gap: 10px;
}

/* ── 状态 chip ─────────────────────────────────────────────────── */
.hud-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: rgba(212, 175, 55, 0.14);
  border: 1px solid rgba(212, 175, 55, 0.45);
  border-radius: 2px;
  font-size: 13px;
  line-height: 1.2;
  white-space: nowrap;
}
.hud-chip__icon {
  font-size: 14px;
  color: var(--pyt-text-accent);
}
.hud-chip__value {
  color: var(--pyt-text-primary);
  font-weight: 600;
}

/* ── 中列任务摘要 ──────────────────────────────────────────────── */
.hud-quest__head {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
}
.hud-quest__chapter {
  color: var(--pyt-text-accent);
  font-weight: 700;
  font-size: 13px;
  letter-spacing: 0.4px;
}
.hud-quest__title {
  color: var(--pyt-text-primary);
  font-weight: 600;
  font-size: 15px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 40vw;
}
.hud-quest__count {
  color: var(--pyt-text-secondary);
  font-size: 12px;
  background: rgba(212, 175, 55, 0.16);
  border: 1px solid rgba(212, 175, 55, 0.4);
  padding: 1px 8px;
  border-radius: 999px;
}
.hud-quest__progress {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}
.hud-quest__bar {
  flex: 1 1 auto;
  position: relative;
  height: 6px;
  background: rgba(0, 0, 0, 0.32);
  border: 1px solid rgba(212, 175, 55, 0.45);
  border-radius: 3px;
  overflow: hidden;
  min-width: 120px;
}
.hud-quest__bar-fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #6bbf59 0%, var(--pyt-color-gold) 100%);
  transition: width 240ms ease-out;
  box-shadow: 0 0 6px rgba(241, 196, 83, 0.5);
}
.hud-quest__objective {
  color: #f0e6c6;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 38vw;
}

/* ── 右列身份 ──────────────────────────────────────────────────── */
.hud-identity {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  line-height: 1.2;
}
.hud-identity__name {
  color: var(--pyt-text-primary);
  font-weight: 700;
  font-size: 14px;
  max-width: 14ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hud-identity__day {
  color: var(--pyt-text-secondary);
  font-size: 11px;
}
.hud-logout {
  font-family: inherit;
  font-size: 12px;
  padding: 4px 12px;
  background: rgba(184, 68, 45, 0.22);
  border: 1px solid var(--pyt-color-danger);
  color: #ffe3da;
  border-radius: 2px;
  cursor: pointer;
  transition: background 140ms, transform 80ms;
}
.hud-logout:hover {
  background: rgba(184, 68, 45, 0.45);
}
.hud-logout:active {
  transform: translateY(1px);
}

/* ── 旧的状态条（能量 / 掌握 / BGM）：折到顶部 HUD 内部，作为补充信息 ── */
.hud-meta-strip {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 11px;
  color: var(--pyt-text-secondary);
  padding-top: 2px;
  border-top: 1px dashed rgba(212, 175, 55, 0.22);
  margin-top: 2px;
}
.hud-bar {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 140px;
}
.hud-bar__label {
  color: var(--pyt-text-gold);
  font-size: 11px;
}
.hud-bar__track {
  position: relative;
  display: inline-block;
  width: 70px;
  height: 6px;
  background: rgba(255, 247, 230, 0.12);
  border: 1px solid var(--pyt-color-text-primary);
  border-radius: 3px;
  overflow: hidden;
}
.hud-bar__fill {
  display: block;
  height: 100%;
  transition: width 220ms ease-out;
}
.hud-bar__fill--energy { background: #6bbf59; }
.hud-bar__fill--mastery { background: var(--pyt-color-gold); }
.hud-bar__value {
  color: var(--pyt-text-primary);
  font-weight: 700;
  font-size: 11px;
  min-width: 22px;
  text-align: right;
}
.hud-bgm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: rgba(120, 170, 220, 0.18);
  border: 1px solid #6aa1d6;
  border-radius: 2px;
  max-width: 200px;
  color: #d8e8f8;
  font-size: 11px;
}
.hud-bgm__icon { color: #aac6d9; font-size: 13px; }
.hud-bgm__value {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ── 番茄钟 ────────────────────────────────────────────────── */
.hud-pomodoro {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  background: rgba(230, 57, 70, 0.15);
  border: 1px solid rgba(230, 57, 70, 0.5);
  border-radius: 2px;
  color: var(--pyt-text-primary);
  font-family: inherit;
  font-size: 11px;
  cursor: pointer;
  transition: background 120ms, transform 80ms;
}
.hud-pomodoro:hover { background: rgba(230, 57, 70, 0.3); }
.hud-pomodoro:active { transform: translateY(1px); }
.hud-pomodoro--work {
  border-color: #e63946;
  animation: pyt-glow-pulse 2s ease-in-out infinite;
}
.hud-pomodoro--break {
  background: rgba(107, 191, 89, 0.15);
  border-color: rgba(107, 191, 89, 0.5);
}
.hud-pomodoro__icon { font-size: 13px; }
.hud-pomodoro__time { font-weight: 700; font-variant-numeric: tabular-nums; }
.hud-pomodoro__phase { font-size: 10px; color: var(--pyt-text-secondary); }

/* ── 底部 Dock ─────────────────────────────────────────────────── */
.hud-bottom {
  position: fixed;
  left: 50%;
  bottom: 22px;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 2px;
  pointer-events: auto;
  z-index: 100;
  user-select: none;
  max-width: calc(100vw - 32px);
  flex-wrap: wrap;
  justify-content: center;
}
.hud-bottom--collapsed {
  padding: 4px 10px;
}

/* 共享 dock 按钮基础样式：HudShell.spec 严格依赖 button.hud-slot 数量 = 5（5 个
   quickslot），不能让工具按钮也带 .hud-slot 类。所以 .hud-slot 与 .hud-tool-slot
   分两套 class，共享 CSS 变量与外观规则。 */
.hud-slot,
.hud-tool-slot {
  width: 58px;
  height: 58px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 4px;
  background: var(--pyt-btn-bg);
  border: 1px solid var(--pyt-btn-border);
  border-radius: 2px;
  color: var(--pyt-text-primary);
  font-family: var(--pyt-font-pixel);
  font-size: 11px;
  cursor: pointer;
  transition: background 140ms ease, border-color 140ms ease, transform 140ms ease, box-shadow 140ms ease;
}
.hud-slot:hover,
.hud-tool-slot:hover {
  background: var(--pyt-btn-hover-bg);
  border-color: var(--pyt-text-accent);
  transform: translateY(-2px) scale(1.05);
  box-shadow: 0 0 10px rgba(241, 196, 83, 0.35), 0 6px 14px rgba(0, 0, 0, 0.35);
}
.hud-slot:active,
.hud-tool-slot:active {
  transform: translateY(0);
}
.hud-slot:focus-visible,
.hud-tool-slot:focus-visible {
  outline: 2px solid var(--pyt-color-gold);
  outline-offset: 2px;
}
.hud-slot:disabled,
.hud-tool-slot:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}
.hud-slot--active,
.hud-tool-slot--active {
  background: var(--pyt-btn-active-bg);
  border-color: var(--pyt-text-accent);
  box-shadow: 0 0 0 1px var(--pyt-text-accent) inset, 0 4px 10px rgba(241, 196, 83, 0.28);
}
/* AI 助教按钮——单一入口，但保持 calm secondary（不脉冲、不抢主按钮）。 */
.hud-guide-slot {
  width: 58px;
  height: 58px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 4px;
  background: var(--pyt-btn-bg);
  border: 1px solid var(--pyt-btn-border);
  border-radius: 2px;
  color: var(--pyt-text-primary);
  font-family: var(--pyt-font-pixel);
  font-size: 11px;
  cursor: pointer;
  transition: background 140ms ease, border-color 140ms ease, transform 140ms ease, box-shadow 140ms ease;
}
.hud-guide-slot:hover {
  background: var(--pyt-btn-hover-bg);
  border-color: var(--pyt-text-accent);
  transform: translateY(-2px) scale(1.05);
  box-shadow: 0 0 10px rgba(241, 196, 83, 0.35), 0 6px 14px rgba(0, 0, 0, 0.35);
}
.hud-guide-slot:active {
  transform: translateY(0);
}
.hud-guide-slot:focus-visible {
  outline: 2px solid var(--pyt-color-gold);
  outline-offset: 2px;
}
.hud-guide-slot--active {
  background: var(--pyt-btn-active-bg);
  border-color: var(--pyt-text-accent);
  box-shadow: 0 0 0 1px var(--pyt-text-accent) inset, 0 4px 10px rgba(241, 196, 83, 0.28);
}
.hud-guide-slot__icon {
  font-size: 22px;
  line-height: 1;
  color: var(--pyt-text-accent);
}
.hud-guide-slot__label {
  font-size: 10px;
  color: var(--pyt-text-primary);
  letter-spacing: 0.5px;
  white-space: nowrap;
}
.hud-guide-slot__shortcut {
  font-size: 9px;
  opacity: 0.7;
}
.hud-slot__icon,
.hud-tool-slot__icon {
  font-size: 22px;
  line-height: 1;
  color: #ffe6a2;
  display: flex;
  align-items: center;
  justify-content: center;
}
.hud-slot__icon-img {
  width: 22px;
  height: 22px;
  object-fit: contain;
  filter: drop-shadow(0 0 2px rgba(255, 230, 162, 0.5));
  image-rendering: pixelated;
}
.hud-slot__label,
.hud-tool-slot__label {
  font-size: 11px;
  color: var(--pyt-text-primary);
  letter-spacing: 0.5px;
}
.hud-slot__shortcut,
.hud-tool-slot__shortcut {
  font-size: 10px;
  color: rgba(255, 247, 230, 0.55);
  line-height: 1;
}
.hud-tool-slot__icon {
  color: var(--pyt-text-accent);
}

.hud-divider {
  display: inline-block;
  width: 1px;
  height: 38px;
  margin: 0 4px;
  background: rgba(212, 175, 55, 0.45);
}

.hud-tools-stub {
  display: none;
}

/* ── 入场动画 ──────────────────────────────────────────────────── */
.hud-top {
  animation: hud-slide-down 320ms ease-out both;
}
.hud-bottom {
  animation: hud-slide-up 320ms ease-out both;
}
@keyframes hud-slide-down {
  from { opacity: 0; transform: translateY(-18px); }
  to   { opacity: 1; transform: translateY(0); }
}
@keyframes hud-slide-up {
  from { opacity: 0; transform: translateX(-50%) translateY(18px); }
  to   { opacity: 1; transform: translateX(-50%) translateY(0); }
}
/* ── 响应式：窄屏（≤1280px）适度压缩 ─────────────────────────── */
@media (max-width: 1280px) {
  .hud-top {
    grid-template-columns: minmax(240px, auto) 1fr minmax(220px, auto);
    gap: 12px;
    padding: 6px 12px;
  }
  .hud-quest__title {
    max-width: 28vw;
    font-size: 14px;
  }
  .hud-quest__objective {
    max-width: 30vw;
  }
}

@media (max-width: 1024px) {
  .hud-top {
    grid-template-columns: 1fr;
    grid-template-rows: auto auto auto;
    top: 12px;
    left: 12px;
    right: 12px;
    padding: 8px 12px;
  }
  .hud-top__col--left {
    justify-content: center;
  }
  .hud-top__col--right {
    justify-content: center;
  }
  .hud-meta-strip {
    justify-content: center;
    flex-wrap: wrap;
  }
  .hud-slot,
  .hud-tool-slot {
    width: 52px;
    height: 52px;
  }
  .hud-slot__icon,
  .hud-tool-slot__icon {
    font-size: 18px;
  }
  .hud-slot__icon-img {
    width: 18px;
    height: 18px;
  }
}

@media (max-width: 760px) {
  .hud-top {
    font-size: 12px;
  }
  .hud-chip {
    font-size: 11px;
    padding: 3px 8px;
  }
  .hud-quest__title {
    font-size: 13px;
    max-width: 60vw;
  }
  .hud-quest__objective {
    max-width: 60vw;
    font-size: 11px;
  }
  .hud-slot,
  .hud-tool-slot {
    width: 48px;
    height: 48px;
    gap: 1px;
  }
  .hud-slot__icon,
  .hud-tool-slot__icon {
    font-size: 16px;
  }
  .hud-slot__icon-img {
    width: 16px;
    height: 16px;
  }
  .hud-slot__label,
  .hud-tool-slot__label {
    display: none;
  }
  .hud-slot__shortcut,
  .hud-tool-slot__shortcut {
    display: none;
  }
  .hud-divider {
    display: none;
  }
  .hud-identity__day {
    display: none;
  }
}
</style>
