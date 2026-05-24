<script setup lang="ts">
// R-13：挂载 HUD 三件套（HudShell + WorldMinimapHud + SettingsModal）。
// 之前生产页只挂 5 个轻量组件，HUD/小地图/设置全部未呈现给玩家。
import { computed, onMounted } from 'vue';
import AppLayout from '@/app/AppLayout.vue';
import ChapterNavPanel from '@/components/world/ChapterNavPanel.vue';
import FirstTimeHelpModal from '@/components/world/FirstTimeHelpModal.vue';
import OperationHints from '@/components/world/OperationHints.vue';
import DialogModal from '@/components/game/DialogModal.vue';
import WorldCanvas from '@/components/game/WorldCanvas.vue';
// R-13 Phase 6：统一从 hud barrel 取，import 行数从 5 行降到 1 行。
import {
  HudShell,
  WorldMinimapHud,
  SettingsModal,
  FestivalRibbon,
  FastTravelPanel,
  MainQuestPanel,
  QuickslotPanelHost,
} from '@/components/hud';
import { openNpcDialog } from '@game/adapters/dialog';
import { useWorldStore } from '@stores/world';
import { useCalendarStore } from '@stores/calendar';

const world = useWorldStore();
const calendar = useCalendarStore();

// R-13 Phase 3：按季节切换背景渐变，配合 daynight tint 共同营造星露谷氛围。
// 不依赖任何外部资源，纯 CSS variable 控制；reduced-motion 时也只是颜色不动。
const seasonClass = computed<string>(() => `world-page--season-${calendar.today?.season ?? 'spring'}`);

// W4-CHAPTER-NAV：章节导航仅在 farm_north（知识田）地图显示，
// 其他子地图（town_hub / mine_south / campus_east / port_west）保持原 Canvas 视野。
const isOnFarm = computed<boolean>(
  () => world.player?.mapKey === 'farm_north',
);

// W4-GUIDE：首次进入 town_hub 时自动让 mayor_py 打招呼，承接 FirstTimeHelpModal
// 关闭后的"我下一步该做什么"。用 localStorage 记忆，仅触发一次。
const FIRST_VISIT_FLAG = 'pyt_first_world_visit';
const WELCOME_DELAY_MS = 2000;

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
    // 隐私模式下 localStorage 不可用，跳过自动欢迎；FirstTimeHelpModal 仍会兜底。
  }
});
</script>

<template>
  <AppLayout>
    <section
      class="world-page"
      :class="seasonClass"
      :data-season="calendar.today?.season ?? 'spring'"
    >
      <WorldCanvas />
      <ChapterNavPanel v-if="isOnFarm" />
      <!-- R-13 修复：OperationHints 与 HUD 按钮快捷键文字重复，隐藏避免视觉混乱
           历史快捷键提示在 HudShell 各按钮上仍可见。FirstTimeHelpModal 完整介绍。 -->
      <OperationHints v-if="false" />
      <DialogModal />
      <FirstTimeHelpModal />
      <!-- R-13 HUD 三件套 + 节日 ribbon + 快速旅行 + R-13 §5 主线面板 -->
      <HudShell />
      <WorldMinimapHud />
      <SettingsModal />
      <FestivalRibbon />
      <FastTravelPanel />
      <MainQuestPanel />
      <!-- R-13 §-1 critical fix：5 个底部 HUD slot 按钮（Q/J/I/M/K）之前
           只切换 hud.activeSlot 但没人监听弹面板 —— 玩家点了一个起作用的都没有。
           QuickslotPanelHost 统一渲染 5 个面板，让按钮真起作用。 -->
      <QuickslotPanelHost />
    </section>
  </AppLayout>
</template>

<style scoped>
.world-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 12px;
  min-height: calc(100dvh - 48px);
  background: linear-gradient(180deg, var(--world-bg-top) 0%, var(--world-bg-bot) 100%);
  --world-bg-top: #2f5a36;
  --world-bg-bot: #1c2b18;
  transition: background 1.2s ease;
  position: relative;
  overflow: hidden;
}

/* 春：嫩绿 → 樱色 */
.world-page--season-spring {
  --world-bg-top: #6bbf59;
  --world-bg-bot: #2f5a36;
}
/* 夏：青翠 → 深绿 */
.world-page--season-summer {
  --world-bg-top: #2f8f3c;
  --world-bg-bot: #14401b;
}
/* 秋：暖棕 → 落叶 */
.world-page--season-autumn {
  --world-bg-top: #c2871f;
  --world-bg-bot: #5b3a10;
}
/* 冬：素雪 → 寒夜 */
.world-page--season-winter {
  --world-bg-top: #aac6d9;
  --world-bg-bot: #2a3d49;
}

/* R-13：内边四角羽化暗影，让画布更聚焦中心；不遮挡 HUD（HUD 在 z=100，vignette z=0） */
.world-page::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at center, transparent 55%, rgba(0, 0, 0, 0.28) 100%);
  pointer-events: none;
  z-index: 0;
}

/* 注意：只对 WorldCanvas / ChapterNavPanel 这类 in-flow 子元素生效。
   原 `.world-page > *` 覆盖了 fixed 子元素（MinimapHud / FastTravelPanel / HudShell），
   把它们都拉到了 flex 流末尾（页面底部），属于 R-13 §-1 用户痛点。
   现在用 `:not([class*="hud"]):not(.dialog-modal):not(.fast-travel)` 兜底，
   并显式给 fixed 子元素覆写 z-index: auto，避免与 .world-page 同层堆叠。 */
.world-page > .world-frame,
.world-page > nav,
.world-page > article {
  position: relative;
  z-index: 1;
}

@media (max-width: 1024px) {
  .world-page {
    padding: 8px;
    gap: 8px;
  }
}

@media (max-width: 640px) {
  .world-page {
    padding: 4px;
    gap: 6px;
    min-height: calc(100dvh - 40px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .world-page {
    transition: none;
  }
}
</style>
