<script setup lang="ts">
/**
 * R-13 §-1 资源工坊主页改造（用户原话 A 方案）：
 *   - 顶部 5 地图传送 + 回小镇 + 课程图书馆 quick access，无需经过 World 场景；
 *   - 中部左 WorkshopPanel（去 KC 解锁限制）+ ResourceListPanel；
 *   - 右 ResourceDetailPanel；
 *   - 整页 fit 浏览器视口，左右两列内部独立滚动，外层不出现纵向滚动条；
 *   - 9-slice 牛皮纸 + 像素风格背景，与星露谷小镇视觉协调。
 */
import { computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import WorkshopPanel from '@/components/workshop/WorkshopPanel.vue';
import ResourceListPanel from '@/components/workshop/ResourceListPanel.vue';
import ResourceDetailPanel from '@/components/workshop/ResourceDetailPanel.vue';
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

const selectedId = computed<number | null>(() => {
  const id = route.query.id;
  if (typeof id === 'string' && /^\d+$/.test(id)) {
    return Number(id);
  }
  return null;
});

const currentMapKey = computed<string | null>(() => world.player?.mapKey ?? null);

onMounted(async () => {
  if (learning.field.crops.length === 0) {
    await learning.loadField();
  }
  if (!learning.profile) {
    await learning.loadProfile();
  }
});

watch(
  selectedId,
  async (id) => {
    if (id !== null) {
      await learning.openResource(id);
    }
  },
  { immediate: false },
);

/**
 * 从工坊一步跳到任意一张大地图（含小镇 hub）。
 * R-13 §-1 C-FastTravel 续修：旧实现直接改 world.player.mapKey 在 player==null
 * 时无效。新实现走 world.setPendingTravel，由 WorldCanvas 接力让 BootScene
 * 启动到目标地图，兼容 player 已初始化 / 尚未 bootstrap 两种情况。
 */
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
        aria-label="资源工坊导航"
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
        <span class="travel-label">资源工坊</span>
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
        <div class="workshop-col workshop-col--left">
          <WorkshopPanel />
          <ResourceListPanel />
        </div>
        <div class="workshop-col workshop-col--right">
          <ResourceDetailPanel :resource-id="selectedId" />
        </div>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
/* R-13 §-1 资源工坊主页：fit 浏览器视口（不再上下滚动） */
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
  border: 3px solid #4d3613;
  background: #fff7e6;
  box-shadow: 4px 4px 0 #4d3613;
  font-family: 'PythontopiaPixel', 'PingFang SC', monospace;
  color: #4d3613;
  flex: 0 0 auto;
}
.travel-back {
  font-family: inherit;
  font-size: 12px;
  padding: 4px 10px;
  background: #f5e2c1;
  border: 2px solid #4d3613;
  cursor: pointer;
  box-shadow: 2px 2px 0 #4d3613;
}
.travel-back:active {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 #4d3613;
}
.travel-divider {
  color: #8a6c2c;
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
  background: #fdf3d0;
  border: 2px solid #c9a86b;
  color: #4d3613;
  cursor: pointer;
  border-radius: 4px;
  min-height: 28px;
}
.travel-chip:hover:not([disabled]) {
  background: #f5e2c1;
  border-color: #4d3613;
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
  background: #d0e3f1;
  border-color: #2a6f97;
  color: #1b4a6b;
}
.travel-chip--arena {
  background: #ffd166;
  border-color: #b8442d;
  color: #4d1b14;
}
.travel-chip__label {
  letter-spacing: 0.5px;
}
.workshop-body {
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: minmax(360px, 1.1fr) minmax(320px, 1fr);
  gap: 14px;
  min-height: 0;
  overflow: hidden;
}
.workshop-col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  overflow: hidden;
}
.workshop-col--left {
  /* WorkshopPanel 占主区，ResourceListPanel 在下方独立滚 */
  display: grid;
  grid-template-rows: 1fr auto;
  gap: 10px;
}
.workshop-col--right {
  display: grid;
  grid-template-rows: 1fr;
  overflow: auto;
}
@media (max-width: 1024px) {
  .workshop-body {
    grid-template-columns: 1fr;
    overflow: auto;
  }
  .workshop-col {
    overflow: visible;
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
