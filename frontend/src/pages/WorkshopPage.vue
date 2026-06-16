<script setup lang="ts">
/**
 * 百晓工坊主页：
 *   - 顶部传送栏（返回小镇 / 快速传送 / 图书馆 / 闯关塔 / 历史 / 书房 / 调用）；
 *   - 默认仅展示居中的单对话流；
 *   - 历史、书房、调用过程按需从右侧抽屉打开。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import WorkshopChat from '@/components/workshop/WorkshopChat.vue';
import MarvisPanel from '@/components/workshop/MarvisPanel.vue';
import StudioDrawer from '@/components/workshop/StudioDrawer.vue';
import ResourceDetailPanel from '@/components/workshop/ResourceDetailPanel.vue';
import ChatHistoryRail from '@/components/workshop/ChatHistoryRail.vue';
import ToolCallTimeline from '@/components/workshop/ToolCallTimeline.vue';
import type { TutorSessionDto } from '@/api/tutor';
import { FAST_TRAVEL_ROUTES, type FastTravelRoute } from '@/data/fastTravel';
import { useLearningStore } from '@stores/learning';
import { useWorldStore } from '@stores/world';
import { useToastStore } from '@stores/toast';
import { useWorkshopUiStore } from '@stores/workshopUi';
import { useWorkshopActivityStore } from '@stores/workshopActivity';
import { playUiClick, playChime } from '@/composables/useStardewFx';

const learning = useLearningStore();
const world = useWorldStore();
const toast = useToastStore();
const workshopUi = useWorkshopUiStore();
const activity = useWorkshopActivityStore();
const router = useRouter();

// 书房默认收起（窄把手），聊天占满主区；学生需要时点把手展开。
const drawerOpen = ref(false);
type WorkshopDrawer = 'history' | 'study' | 'calls' | null;
const activeDrawer = ref<WorkshopDrawer>(null);

// 左栏「聊天记录」选中某段会话时，把整段会话（多轮问答）回看注入对话流并接着聊
// （WorkshopChat 暴露 loadConversation）。
const chatRef = ref<{ loadConversation: (sessions: TutorSessionDto[]) => void } | null>(null);
function onSelectConversation(sessions: TutorSessionDto[]): void {
  chatRef.value?.loadConversation(sessions);
  activeDrawer.value = null;
}

// R-14 域1④：书房当前打开的资源 id 由 store 持有，不再进 URL（避免暴露可枚举 id）。
const selectedId = computed<number | null>(() => workshopUi.openResourceId);

const currentMapKey = computed<string | null>(() => world.player?.mapKey ?? null);

// Esc 关闭资源详情弹窗（仅在弹窗打开时拦截）。
function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && selectedId.value !== null) {
    onDrawerBack();
    return;
  }
  if (event.key === 'Escape' && activeDrawer.value !== null) {
    closeDrawer();
  }
}

onMounted(async () => {
  window.addEventListener('keydown', onKeydown);
  if (learning.field.masterySnapshot.length === 0) {
    await learning.loadField();
  }
  if (!learning.profile) {
    await learning.loadProfile();
  }
});

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown);
  // 离开工坊清空书房选中，避免下次进入残留旧资源弹窗。
  workshopUi.closeResource();
});

function onOpenResource(id: number): void {
  activeDrawer.value = 'study';
  drawerOpen.value = true;
  // R-14 域1④：资源 id 不进 URL，改存 store（避免学生改网址枚举他人资源；后端本就归属校验）。
  workshopUi.openResource(id);
}

// 关闭资源详情弹窗：清空 store 选中（selectedId 随之变 null，弹窗消失）。
function onDrawerBack(): void {
  workshopUi.closeResource();
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

function openDrawer(drawer: Exclude<WorkshopDrawer, null>): void {
  playUiClick();
  activeDrawer.value = activeDrawer.value === drawer ? null : drawer;
  drawerOpen.value = drawer === 'study';
}

function closeDrawer(): void {
  activeDrawer.value = null;
  drawerOpen.value = false;
}

function onStudyOpenChange(open: boolean): void {
  drawerOpen.value = open;
  if (!open && activeDrawer.value === 'study') {
    activeDrawer.value = null;
  }
}

const drawerTitle = computed<string>(() => {
  if (activeDrawer.value === 'history') return '历史问答';
  if (activeDrawer.value === 'study') return '我的书房';
  if (activeDrawer.value === 'calls') return '调用过程';
  return '';
});
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
        <nav
          class="workshop-drawer-tabs"
          aria-label="百晓工坊面板"
        >
          <button
            type="button"
            class="workshop-drawer-tab"
            :class="{ 'workshop-drawer-tab--active': activeDrawer === 'history' }"
            data-testid="workshop-open-history"
            :aria-expanded="activeDrawer === 'history'"
            @click="openDrawer('history')"
          >
            历史
          </button>
          <button
            type="button"
            class="workshop-drawer-tab"
            :class="{ 'workshop-drawer-tab--active': activeDrawer === 'study' }"
            data-testid="workshop-open-study"
            :aria-expanded="activeDrawer === 'study'"
            @click="openDrawer('study')"
          >
            书房
          </button>
          <button
            type="button"
            class="workshop-drawer-tab"
            :class="{ 'workshop-drawer-tab--active': activeDrawer === 'calls' }"
            data-testid="workshop-open-calls"
            :aria-expanded="activeDrawer === 'calls'"
            @click="openDrawer('calls')"
          >
            调用
          </button>
        </nav>
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
        <div
          class="workshop-chat-pane"
          data-testid="workshop-chat-pane"
        >
          <WorkshopChat
            ref="chatRef"
            @open-resource="onOpenResource"
          />
        </div>
        <aside
          v-if="activeDrawer !== null"
          class="workshop-drawer"
          :data-testid="`workshop-drawer-${activeDrawer}`"
          role="complementary"
          :aria-label="drawerTitle"
        >
          <header class="workshop-drawer__bar">
            <span class="workshop-drawer__title">{{ drawerTitle }}</span>
            <button
              type="button"
              class="workshop-drawer__close"
              data-testid="workshop-drawer-close"
              @click="closeDrawer"
            >
              关闭
            </button>
          </header>
          <div class="workshop-drawer__body">
            <ChatHistoryRail
              v-if="activeDrawer === 'history'"
              embedded-open
              @select="onSelectConversation"
            />
            <StudioDrawer
              v-else-if="activeDrawer === 'study'"
              :open="drawerOpen"
              @update:open="onStudyOpenChange"
            />
            <div
              v-else-if="activeDrawer === 'calls'"
              class="workshop-call-drawer"
            >
              <MarvisPanel />
              <ToolCallTimeline
                :traces="activity.traces"
                :live="activity.status === 'streaming'"
              />
              <p
                v-if="activity.traces.length === 0"
                class="workshop-call-drawer__empty"
                data-testid="workshop-call-empty"
              >
                暂无调用过程。发起一次提问或生成后，这里会保留详细链路。
              </p>
            </div>
          </div>
        </aside>
      </div>

      <!-- 书房资源详情弹窗：列表点击某行（或聊天「在书房查看」）写入 store openResourceId 后弹出，
           展示讲义 / 思维导图 / 练习等内容；点遮罩、点 ✕ 或按 Esc 关闭。 -->
      <Teleport to="body">
        <div
          v-if="selectedId !== null"
          class="resource-modal-overlay"
          data-testid="resource-modal"
          role="dialog"
          aria-modal="true"
          aria-label="书房资源详情"
          @click.self="onDrawerBack"
        >
          <div class="resource-modal">
            <header class="resource-modal__bar">
              <span class="resource-modal__title">📄 讲义 / 资源详情</span>
              <button
                type="button"
                class="resource-modal__close"
                data-testid="resource-modal-close"
                title="关闭（Esc）"
                @click="onDrawerBack"
              >
                ✕
              </button>
            </header>
            <div class="resource-modal__body">
              <ResourceDetailPanel :resource-id="selectedId" />
            </div>
          </div>
        </div>
      </Teleport>
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
.workshop-drawer-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
}
.workshop-drawer-tab {
  font-family: inherit;
  font-size: 12px;
  padding: 4px 9px;
  color: #4f3514;
  background: transparent;
  border: 2px solid transparent;
  border-radius: 999px;
  cursor: pointer;
}
.workshop-drawer-tab:hover,
.workshop-drawer-tab--active {
  background: #fff8df;
  border-color: rgba(140, 106, 35, 0.55);
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
  justify-content: center;
  gap: 10px;
  min-height: 0;
  overflow: hidden;
  position: relative;
}
.workshop-chat-pane {
  flex: 0 1 920px;
  width: min(920px, 100%);
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
.workshop-drawer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  width: min(360px, 92vw);
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: rgba(255, 248, 224, 0.96);
  border: 3px solid var(--pyt-color-text-primary);
  border-radius: var(--pyt-radius-lg);
  box-shadow: -6px 6px 0 rgba(42, 26, 5, 0.22);
  overflow: hidden;
  font-family: var(--pyt-font-pixel);
}
.workshop-drawer__bar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 2px dashed rgba(110, 79, 29, 0.45);
  background: #fff8df;
}
.workshop-drawer__title {
  font-size: 13px;
  font-weight: 700;
}
.workshop-drawer__close {
  font: inherit;
  font-size: 11px;
  padding: 3px 8px;
  border: 2px solid var(--pyt-color-text-primary);
  border-radius: 4px;
  background: var(--pyt-color-warning-bg);
  cursor: pointer;
}
.workshop-drawer__body {
  flex: 1 1 auto;
  min-height: 0;
  padding: 8px;
  overflow: auto;
}
.workshop-drawer__body :deep(.history),
.workshop-drawer__body :deep(.drawer) {
  width: 100%;
  height: 100%;
}
.workshop-drawer__body :deep(.history__handle),
.workshop-drawer__body :deep(.drawer__handle) {
  display: none;
}
.workshop-call-drawer {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 100%;
}
.workshop-call-drawer__empty {
  margin: 0;
  padding: 10px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--pyt-color-text-muted);
  border: 1px dashed rgba(110, 79, 29, 0.45);
  border-radius: var(--pyt-radius-md);
}
/* ── 书房资源详情弹窗（Teleport 到 body，仍受本组件 scoped 作用域约束） ── */
.resource-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--pyt-z-modal);
  background: rgba(42, 26, 5, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.resource-modal {
  width: min(760px, 94vw);
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f9e7bc 0%, #f1d896 100%);
  border: 3px solid var(--pyt-color-text-primary);
  border-radius: var(--pyt-radius-lg);
  box-shadow: 6px 6px 0 var(--pyt-color-text-primary);
  overflow: hidden;
  font-family: var(--pyt-font-pixel);
}
.resource-modal__bar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 2px dashed rgba(110, 79, 29, 0.4);
}
.resource-modal__title {
  font-size: 13px;
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.resource-modal__close {
  flex: 0 0 auto;
  font-family: inherit;
  font-size: 14px;
  line-height: 1;
  width: 28px;
  height: 28px;
  background: var(--pyt-color-warning-bg);
  border: 2px solid var(--pyt-color-text-primary);
  border-radius: 4px;
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--pyt-color-text-primary);
  color: var(--pyt-color-text-primary);
}
.resource-modal__close:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}
.resource-modal__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: 12px;
}

@media (max-width: 900px) {
  .workshop-body {
    overflow: hidden;
  }
  .workshop-drawer {
    left: 0;
    width: auto;
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
