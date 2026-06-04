<script setup lang="ts">
/**
 * 百晓工坊主页（R-13 §-1 合并改造）：
 *   - 顶部传送栏（返回小镇 / 快速传送 / 图书馆 / 闯关塔）；
 *   - 主体：左「对话流」WorkshopChat（@ 引用 + / 选工具 + 自动判定 → 问答/6 类资源），
 *     右「书房」StudioDrawer（可收起，列表 + 详情，详情绑路由 ?id）。
 *   - 原三栏（KC 勾选网格 + 书房 + 详情）合并为对话优先单列 + 抽屉。
 */
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import WorkshopChat from '@/components/workshop/WorkshopChat.vue';
import StudioDrawer from '@/components/workshop/StudioDrawer.vue';
import { FAST_TRAVEL_ROUTES, type FastTravelRoute } from '@/data/fastTravel';
import { useLearningStore } from '@stores/learning';
import { useWorldStore } from '@stores/world';
import { useToastStore } from '@stores/toast';
import { playUiClick, playChime } from '@/composables/useStardewFx';

const learning = useLearningStore();
const world = useWorldStore();
const toast = useToastStore();
const router = useRouter();
const route = useRoute();

const drawerOpen = ref(true);

const selectedId = computed<number | null>(() => {
  const id = route.query.id;
  if (typeof id === 'string' && /^\d+$/.test(id)) {
    return Number(id);
  }
  return null;
});

const currentMapKey = computed<string | null>(() => world.player?.mapKey ?? null);

onMounted(async () => {
  if (learning.field.masterySnapshot.length === 0) {
    await learning.loadField();
  }
  if (!learning.profile) {
    await learning.loadProfile();
  }
});

function onOpenResource(id: number): void {
  drawerOpen.value = true;
  void router.push({ path: '/workshop', query: { id: String(id) } });
}

function onDrawerBack(): void {
  void router.push({ path: '/workshop' });
}

async function travelTo(route_: FastTravelRoute): Promise<void> {
  playUiClick();
  playChime('dawn');
  world.setPendingTravel(route_.toMap, { x: route_.tileX, y: route_.tileY });
  if (world.player) {
    world.player = {
      ...world.player,
      mapKey: route_.toMap,
      tileX: route_.tileX,
      tileY: route_.tileY,
    };
  }
  toast.show(`传送中…即将到达「${route_.label}」`, 'info');
  await router.push({ path: '/world' });
}

function openLibrary(): void {
  playUiClick();
  void router.push({ path: '/library' });
}

function backToWorld(): void {
  playUiClick();
  void router.push({ path: '/world' });
}

function openArena(chapter: number): void {
  playUiClick();
  void router.push({ path: `/arena/${chapter}` });
}
</script>

<template>
  <AppLayout>
    <section
      class="workshop-page"
      data-testid="workshop-page"
    >
      <header
        class="travel-bar"
        data-testid="workshop-travel-bar"
        aria-label="百晓工坊导航"
      >
        <button
          type="button"
          class="travel-back"
          data-testid="workshop-back-world"
          @click="backToWorld"
        >
          ← 返回小镇
        </button>
        <span class="travel-divider">/</span>
        <span class="travel-label">百晓工坊</span>
        <span class="travel-spacer" />
        <ul class="travel-list">
          <li
            v-for="r in FAST_TRAVEL_ROUTES"
            :key="r.key"
          >
            <button
              type="button"
              class="travel-chip"
              :class="{ 'travel-chip--current': currentMapKey === r.toMap }"
              :data-testid="`workshop-travel-${r.key}`"
              :title="r.description"
              :disabled="currentMapKey === r.toMap"
              @click="travelTo(r)"
            >
              <span class="travel-chip__label">{{ r.label }}</span>
            </button>
          </li>
          <li>
            <button
              type="button"
              class="travel-chip travel-chip--library"
              data-testid="workshop-open-library"
              title="打开课程图书馆（PDF / 65 KC）"
              @click="openLibrary"
            >
              <span class="travel-chip__label">图书馆 L</span>
            </button>
          </li>
          <li>
            <button
              type="button"
              class="travel-chip travel-chip--arena"
              data-testid="workshop-open-arena"
              title="章节闯关塔（7 章 × 10 关 = 70 关）"
              @click="openArena(1)"
            >
              <span class="travel-chip__label">闯关塔 A</span>
            </button>
          </li>
        </ul>
      </header>

      <div
        class="workshop-body"
        data-testid="workshop-body"
      >
        <div class="workshop-chat-pane">
          <WorkshopChat @open-resource="onOpenResource" />
        </div>
        <div
          class="drawer-slot"
          :class="{ 'drawer-slot--collapsed': !drawerOpen }"
        >
          <StudioDrawer
            v-model:open="drawerOpen"
            :selected-id="selectedId"
            @back="onDrawerBack"
          />
        </div>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.workshop-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px 14px;
  height: calc(100dvh - 56px);
  background:
    linear-gradient(180deg, rgba(255, 247, 230, 0.5), rgba(243, 228, 190, 0.7)),
    repeating-linear-gradient(135deg, rgba(110, 79, 29, 0.05) 0 6px, transparent 6px 14px),
    #fef6dd;
  overflow: hidden;
}
.travel-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border: 3px solid var(--pyt-color-text-primary);
  background: var(--pyt-color-bg-elevated);
  box-shadow: 4px 4px 0 var(--pyt-color-text-primary);
  font-family: var(--pyt-font-pixel), 'PingFang SC', monospace;
  color: var(--pyt-color-text-primary);
  flex: 0 0 auto;
}
.travel-back {
  font-family: inherit;
  font-size: 12px;
  padding: 4px 10px;
  background: var(--pyt-color-warning-bg);
  border: 2px solid var(--pyt-color-text-primary);
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--pyt-color-text-primary);
}
.travel-back:active {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 var(--pyt-color-text-primary);
}
.travel-divider {
  color: var(--pyt-color-text-muted);
}
.travel-label {
  font-size: 13px;
  font-weight: 700;
}
.travel-spacer {
  flex: 1 1 auto;
}
.travel-list {
  list-style: none;
  display: flex;
  gap: 6px;
  margin: 0;
  padding: 0;
  flex-wrap: wrap;
}
.travel-chip {
  font-family: inherit;
  font-size: 12px;
  padding: 4px 10px;
  background: var(--pyt-color-bg-soft);
  border: 2px solid var(--pyt-color-border);
  color: var(--pyt-color-text-primary);
  cursor: pointer;
  border-radius: 4px;
  min-height: 28px;
}
.travel-chip:hover:not([disabled]) {
  background: var(--pyt-color-warning-bg);
  border-color: var(--pyt-color-text-primary);
}
.travel-chip[disabled] {
  opacity: 0.5;
  cursor: not-allowed;
}
.travel-chip--current {
  background: #d6ead1;
  border-color: #4d6e1d;
}
.travel-chip--library {
  background: var(--pyt-color-info-bg);
  border-color: var(--pyt-color-info);
  color: #1b4a6b;
}
.travel-chip--arena {
  background: var(--pyt-color-gold-bright);
  border-color: var(--pyt-color-danger);
  color: #4d1b14;
}
.travel-chip__label {
  letter-spacing: 0.5px;
}

.workshop-body {
  flex: 1 1 auto;
  display: flex;
  align-items: stretch;
  gap: 10px;
  min-height: 0;
  overflow: hidden;
}
.workshop-chat-pane {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f9e7bc 0%, #f1d896 100%);
  border: 3px solid var(--pyt-color-text-hint);
  border-radius: var(--pyt-radius-lg);
  box-shadow: inset 0 0 0 2px rgba(255, 247, 230, 0.55);
  overflow: hidden;
}
.drawer-slot {
  flex: 0 0 360px;
  min-height: 0;
  transition: flex-basis 160ms ease;
}
.drawer-slot--collapsed {
  flex: 0 0 44px;
}

@media (max-width: 900px) {
  .workshop-body {
    flex-direction: column;
    overflow: auto;
  }
  .drawer-slot,
  .drawer-slot--collapsed {
    flex: 0 0 auto;
    max-height: 45vh;
  }
}
@media (max-width: 640px) {
  .workshop-page {
    height: auto;
    min-height: calc(100dvh - 56px);
    overflow: visible;
  }
  .travel-bar {
    flex-wrap: wrap;
  }
}
</style>
