<script setup lang="ts">
// 世界主页面：全屏沉浸式像素 RPG，HUD/面板以 fixed 悬浮叠加。
defineOptions({ name: 'WorldPage' });

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import FirstTimeHelpModal from '@/components/world/FirstTimeHelpModal.vue';
import ProbeCard from '@/components/world/ProbeCard.vue';
import MoodOverlay from '@/components/world/MoodOverlay.vue';
import OperationHints from '@/components/world/OperationHints.vue';
import DialogModal from '@/components/game/DialogModal.vue';
import WorldCanvas from '@/components/game/WorldCanvas.vue';
import SceneTransition from '@/components/game/SceneTransition.vue';
import {
  HudShell,
  SettingsModal,
  FestivalRibbon,
  FastTravelPanel,
  MainQuestPanel,
  QuickslotPanelHost,
  LearningObjectivePanel,
  PauseMenu,
  QuickWheel,
  GuideOverlay,
} from '@/components/hud';
import { openNpcDialog } from '@game/adapters/dialog';
import { useWorldStore } from '@stores/world';
import { useCalendarStore } from '@stores/calendar';
import { useGameSettingsStore } from '@stores/gameSettings';

const world = useWorldStore();
const calendar = useCalendarStore();
const gameSettings = useGameSettingsStore();

const seasonClass = computed<string>(() => `world-page--season-${calendar.today?.season ?? 'spring'}`);

const FIRST_VISIT_FLAG = 'pyt_first_world_visit';
const HELP_FLAG = 'pyt_helped_v1';
const WELCOME_DELAY_MS = 2000;
// 镇长对话弹出后再多压制几秒探针，给新手留出读对话时间，保证引导不叠弹。
const WELCOME_HOLD_GRACE_MS = 6000;

// 首次进入时，镇长 Py 的「发任务」对话要等新手帮助弹窗关闭后再弹，避免叠加。
let pendingWelcome = false;
// 引导期间（帮助弹窗 / 镇长对话）压制世界内探针卡，保证一次只有一条引导线。
const onboardingHold = ref(false);
let welcomeHoldTimer: number | null = null;

function openWelcomeDialog(delayMs: number): void {
  onboardingHold.value = true;
  window.setTimeout(() => openNpcDialog('mayor_py'), delayMs);
  if (welcomeHoldTimer !== null) window.clearTimeout(welcomeHoldTimer);
  welcomeHoldTimer = window.setTimeout(() => {
    onboardingHold.value = false;
  }, delayMs + WELCOME_HOLD_GRACE_MS);
}

function onHelpDismissed(): void {
  if (pendingWelcome) {
    pendingWelcome = false;
    openWelcomeDialog(400);
  }
}

function onFullscreenChange(): void {
  const isFs = !!document.fullscreenElement;
  if (gameSettings.display.fullscreen !== isFs) {
    gameSettings.updateDisplay({ fullscreen: isFs });
  }
}

watch(() => gameSettings.display.fullscreen, (wantFs) => {
  if (wantFs && !document.fullscreenElement) {
    document.documentElement.requestFullscreen().catch(() => {});
  } else if (!wantFs && document.fullscreenElement) {
    document.exitFullscreen().catch(() => {});
  }
});

onMounted(async () => {
  if (!world.session) {
    await world.loadSession();
  }
  try {
    const seen = window.localStorage.getItem(FIRST_VISIT_FLAG);
    if (!seen) {
      // 帮助弹窗还没看过 → 等它 dismiss 后再触发对话；已看过 → 短延时直接触发。
      if (window.localStorage.getItem(HELP_FLAG)) {
        openWelcomeDialog(800);
      } else {
        // 帮助弹窗即将显示：先压制探针，待 dismiss 后再串接镇长对话。
        onboardingHold.value = true;
        pendingWelcome = true;
      }
      window.localStorage.setItem(FIRST_VISIT_FLAG, '1');
    }
  } catch {
    // 隐私模式 localStorage 不可用：直接延时打开，FirstTimeHelpModal 兜底
    openWelcomeDialog(WELCOME_DELAY_MS);
  }
  document.addEventListener('fullscreenchange', onFullscreenChange);
});

onBeforeUnmount(() => {
  if (welcomeHoldTimer !== null) {
    window.clearTimeout(welcomeHoldTimer);
    welcomeHoldTimer = null;
  }
  document.removeEventListener('fullscreenchange', onFullscreenChange);
});
</script>

<template>
  <section
    class="world-page"
    :class="[seasonClass, {
      'world-page--no-vignette': !gameSettings.display.showVignette,
      'world-page--focus': gameSettings.education.focusMode,
    }]"
    :data-season="calendar.today?.season ?? 'spring'"
    :data-ui-scale="gameSettings.display.uiScale"
    :data-color-mode="gameSettings.display.colorMode"
    :style="{ '--pyt-code-font-size': gameSettings.education.codeFontSize + 'px' }"
  >
    <WorldCanvas />
    <!-- 快捷键提示由 HudShell 各按钮承担，此处停用避免文字重复 -->
    <OperationHints v-if="false" />
    <DialogModal />
    <FirstTimeHelpModal @dismissed="onHelpDismissed" />
    <ProbeCard :hold="onboardingHold" />
    <MoodOverlay />
    <!-- HUD 套件 -->
    <HudShell />
    <LearningObjectivePanel />
    <SettingsModal />
    <FestivalRibbon />
    <FastTravelPanel />
    <MainQuestPanel />
    <QuickslotPanelHost />
    <PauseMenu />
    <QuickWheel />
    <GuideOverlay />
    <SceneTransition />
  </section>
</template>

<style scoped>
.world-page {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: linear-gradient(180deg, var(--world-bg-top) 0%, var(--world-bg-bot) 100%);
  --world-bg-top: #2f5a36;
  --world-bg-bot: #1c2b18;
  transition: background 1.2s ease;
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-bg-elevated);
}

/* 季节渐变：地图未加载完时也能看到氛围底色 */
.world-page--season-spring { --world-bg-top: #6bbf59; --world-bg-bot: #2f5a36; }
.world-page--season-summer { --world-bg-top: var(--pyt-color-success); --world-bg-bot: #14401b; }
.world-page--season-autumn { --world-bg-top: var(--pyt-color-warning); --world-bg-bot: #5b3a10; }
.world-page--season-winter { --world-bg-top: #aac6d9; --world-bg-bot: #2a3d49; }

/* 四角羽化暗角，让玩家视线收束到画面中心；HUD 在 z>=100，vignette 永远不挡 UI。 */
.world-page::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at center, transparent 58%, rgba(0, 0, 0, 0.32) 100%);
  pointer-events: none;
  z-index: 1;
}

@media (prefers-reduced-motion: reduce) {
  .world-page {
    transition: none;
  }
}

/* ── 设置驱动的样式 ── */

.world-page { --pyt-ui-scale: 1; --pyt-code-font-size: 14px; }
.world-page[data-ui-scale="small"]  { --pyt-ui-scale: 0.85; font-size: calc(14px * 0.85); }
.world-page[data-ui-scale="medium"] { --pyt-ui-scale: 1;    font-size: 14px; }
.world-page[data-ui-scale="large"]  { --pyt-ui-scale: 1.15; font-size: calc(14px * 1.15); }

.world-page[data-color-mode="high-contrast"] {
  filter: contrast(1.3);
  --pyt-text-primary: #ffffff;
  --pyt-panel-border: #ffcc00;
}
.world-page[data-color-mode="colorblind"] {
  filter: saturate(0.5) contrast(1.1);
}

.world-page--no-vignette::after { display: none; }

.world-page--focus::after { display: none; }
.world-page--focus :deep(.hud-top__col--left) { display: none; }
.world-page--focus :deep([data-testid="minimap-hud"]) { opacity: 0.3; }
</style>
