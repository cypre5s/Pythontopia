<script setup lang="ts">
import { computed, onMounted } from 'vue';
import AppLayout from '@/app/AppLayout.vue';
import ChapterNavPanel from '@/components/world/ChapterNavPanel.vue';
import FirstTimeHelpModal from '@/components/world/FirstTimeHelpModal.vue';
import OperationHints from '@/components/world/OperationHints.vue';
import DialogModal from '@/components/game/DialogModal.vue';
import WorldCanvas from '@/components/game/WorldCanvas.vue';
import { openNpcDialog } from '@game/adapters/dialog';
import { useWorldStore } from '@stores/world';

const world = useWorldStore();

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
    <section class="world-page">
      <WorldCanvas />
      <ChapterNavPanel v-if="isOnFarm" />
      <OperationHints />
      <DialogModal />
      <FirstTimeHelpModal />
    </section>
  </AppLayout>
</template>

<style scoped>
.world-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 16px;
  min-height: calc(100vh - 64px);
  background: linear-gradient(180deg, #2f5a36 0%, #1c2b18 100%);
}
</style>
