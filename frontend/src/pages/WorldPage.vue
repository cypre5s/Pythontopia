<script setup lang="ts">
// 世界主页面：全屏沉浸式像素 RPG，HUD/面板以 fixed 悬浮叠加。
import { computed, onBeforeUnmount, onMounted, watch } from 'vue';
import ChapterNavPanel from '@/components/world/ChapterNavPanel.vue';
import FirstTimeHelpModal from '@/components/world/FirstTimeHelpModal.vue';
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
} from '@/components/hud';
import { openNpcDialog } from '@game/adapters/dialog';
import { useWorldStore } from '@stores/world';
import { useCalendarStore } from '@stores/calendar';
import { useGameSettingsStore } from '@stores/gameSettings';

const world = useWorldStore();
const calendar = useCalendarStore();
const gameSettings = useGameSettingsStore();

const seasonClass = computed<string>(() => `world-page--season-${calendar.today?.season ?? 'spring'}`);

const isOnFarm = computed<boolean>(
  () => world.player?.mapKey === 'farm_north',
);

const FIRST_VISIT_FLAG = 'pyt_first_world_visit';
const WELCOME_DELAY_MS = 2000;

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
      window.setTimeout(() => {
        openNpcDialog('mayor_py');
      }, WELCOME_DELAY_MS);
      window.localStorage.setItem(FIRST_VISIT_FLAG, '1');
    }
  } catch {
    // 隐私模式 localStorage 不可用，FirstTimeHelpModal 兜底
  }
  document.addEventListener('fullscreenchange', onFullscreenChange);
});

onBeforeUnmount(() => {
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
    <ChapterNavPanel v-if="isOnFarm" />
    <!-- 历史快捷键提示由 HudShell 各按钮承担，避免文字重复 -->
    <OperationHints v-if="false" />
    <DialogModal />
    <FirstTimeHelpModal />
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
  font-family: var(--pyt-font-pixel, monospace);
  color: #fff7e6;
}

/* 季节渐变（地图未加载完时也能看到星露谷感氛围底色） */
.world-page--season-spring { --world-bg-top: #6bbf59; --world-bg-bot: #2f5a36; }
.world-page--season-summer { --world-bg-top: #2f8f3c; --world-bg-bot: #14401b; }
.world-page--season-autumn { --world-bg-top: #c2871f; --world-bg-bot: #5b3a10; }
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

/* ── Settings-driven CSS ── */

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
