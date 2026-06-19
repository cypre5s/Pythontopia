<script setup lang="ts">
/**
 * 百晓工坊主页：
 *   - 顶部传送栏（返回小镇 / 快速传送 / 图书馆 / 闯关塔 / 历史 / 书房 / 调用）；
 *   - 默认仅展示居中的单对话流；
 *   - 历史在左侧，书房、调用过程在右侧按需打开。
 */
import { computed, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppLayout from '@/app/AppLayout.vue';
import WorkshopChat from '@/components/workshop/WorkshopChat.vue';
import MarvisPanel from '@/components/workshop/MarvisPanel.vue';
import StudioDrawer from '@/components/workshop/StudioDrawer.vue';
import ResourceDetailPanel from '@/components/workshop/ResourceDetailPanel.vue';
import ChatHistoryRail from '@/components/workshop/ChatHistoryRail.vue';
import ToolCallTimeline from '@/components/workshop/ToolCallTimeline.vue';
import {
  rememberWorkshopChatHistoryTurn,
  type WorkshopChatHistoryTurnDto,
} from '@/api/workshopHistory';
import { FAST_TRAVEL_ROUTES, type FastTravelRoute } from '@/data/fastTravel';
import { useLearningStore } from '@stores/learning';
import { useWorldStore } from '@stores/world';
import { useToastStore } from '@stores/toast';
import { useWorkshopUiStore } from '@stores/workshopUi';
import { useWorkshopActivityStore } from '@stores/workshopActivity';
import { playUiClick, playChime } from '@/composables/useStardewFx';

// 名字供 App.vue 的 <KeepAlive include> 命中：离开工坊（返回小镇 / 去图书馆 / 闯关塔等任意跳转）
// 不销毁本页，下次回来对话流、面板布局与书房弹窗都停在离开时那一刻。
defineOptions({ name: 'WorkshopPage' });

const learning = useLearningStore();
const world = useWorldStore();
const toast = useToastStore();
const workshopUi = useWorkshopUiStore();
const activity = useWorkshopActivityStore();
const router = useRouter();

// 被 KeepAlive 缓存时是否处于「激活」（当前可见）状态。
// 仅用于守卫 Teleport 到 body 的书房弹窗：失活（切到别的页）时不渲染，回来再渲染，
// 避免弹窗遗留在其它页面之上。直接挂载（非 KeepAlive，如单测）时恒为 true。
const pageActive = ref(true);

// 书房默认收起，聊天占满主区；学生需要时点顶部按钮展开。
// 「100% 保留布局」：面板布局（历史/书房/调用）除 KeepAlive 会话内保留外，再持久化到 localStorage——
// 即便整页刷新/重开，回到工坊也恢复上次的面板布局；对话流本身由 KeepAlive 会话内保留，
// 刷新后可从左栏「历史」一键回看上次那段会话。
type WorkshopDrawer = 'study' | 'calls' | null;
const WORKSHOP_LAYOUT_KEY = 'pyt:workshop-layout';

function loadWorkshopLayout(): { historyOpen: boolean; activeDrawer: WorkshopDrawer } {
  try {
    const raw = window.localStorage.getItem(WORKSHOP_LAYOUT_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as { historyOpen?: unknown; activeDrawer?: unknown };
      const drawer = parsed.activeDrawer;
      return {
        historyOpen: parsed.historyOpen === true,
        activeDrawer: drawer === 'study' || drawer === 'calls' ? drawer : null,
      };
    }
  } catch {
    /* localStorage 不可用（隐私模式）时回退默认布局 */
  }
  return { historyOpen: false, activeDrawer: null };
}

const restoredLayout = loadWorkshopLayout();
const drawerOpen = ref(restoredLayout.activeDrawer === 'study');
const historyOpen = ref(restoredLayout.historyOpen);
const activeDrawer = ref<WorkshopDrawer>(restoredLayout.activeDrawer);
const historyPendingTurn = ref<WorkshopChatHistoryTurnDto | null>(null);

// 面板布局变化即持久化，供刷新/重开后恢复（会话内保留仍由 KeepAlive 负责）。
watch([historyOpen, activeDrawer], ([open, drawer]) => {
  try {
    window.localStorage.setItem(
      WORKSHOP_LAYOUT_KEY,
      JSON.stringify({ historyOpen: open, activeDrawer: drawer }),
    );
  } catch {
    /* 隐私模式禁用 localStorage 时忽略 */
  }
});

// 左栏「聊天记录」选中某段会话时，把整段会话（多轮问答）回看注入对话流并接着聊
// （WorkshopChat 暴露 loadConversation）。选中后**保持历史栏与右侧栏原样**，
// 方便学生连续翻看多段会话（不自动收起任何面板）。
const chatRef = ref<{ loadConversation: (turns: WorkshopChatHistoryTurnDto[]) => void } | null>(null);
function onSelectConversation(turns: WorkshopChatHistoryTurnDto[]): void {
  chatRef.value?.loadConversation(turns);
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
    return;
  }
  if (event.key === 'Escape' && historyOpen.value) {
    closeHistoryDrawer();
  }
}

onMounted(async () => {
  // keydown 监听只在本页激活时生效：用同一函数引用重复 add 是幂等的（浏览器去重），
  // 故 onMounted 与 onActivated 都 add 不会重复绑定；失活/卸载时 remove。
  window.addEventListener('keydown', onKeydown);
  pageActive.value = true;
  if (learning.field.masterySnapshot.length === 0) {
    await learning.loadField();
  }
  if (!learning.profile) {
    await learning.loadProfile();
  }
});

// KeepAlive 重新激活（再次进入工坊）：恢复键盘监听与弹窗渲染，状态本就保留。
onActivated(() => {
  window.addEventListener('keydown', onKeydown);
  pageActive.value = true;
});

// KeepAlive 失活（离开工坊但不销毁）：撤掉键盘监听、停渲染 body 上的书房弹窗，
// 但保留 historyOpen / activeDrawer / openResourceId 等状态，回来时原样恢复。
onDeactivated(() => {
  window.removeEventListener('keydown', onKeydown);
  pageActive.value = false;
});

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown);
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
  // 闯关塔已退役并入无尽远征：带章节聚焦进入 /expedition。
  void router.push({ path: '/expedition', query: { focus: String(chapter) } });
}

function openDrawer(drawer: 'history' | Exclude<WorkshopDrawer, null>): void {
  playUiClick();
  if (drawer === 'history') {
    historyOpen.value = !historyOpen.value;
    return;
  }
  activeDrawer.value = activeDrawer.value === drawer ? null : drawer;
  drawerOpen.value = activeDrawer.value === 'study';
}

function closeDrawer(): void {
  activeDrawer.value = null;
  drawerOpen.value = false;
}

function closeHistoryDrawer(): void {
  historyOpen.value = false;
}

function appendHistoryTurn(turn: WorkshopChatHistoryTurnDto): void {
  rememberWorkshopChatHistoryTurn(turn);
  historyPendingTurn.value = turn;
}

function onStudyOpenChange(open: boolean): void {
  drawerOpen.value = open;
  if (!open && activeDrawer.value === 'study') {
    activeDrawer.value = null;
  }
}

const drawerTitle = computed<string>(() => {
  if (activeDrawer.value === 'study') return '我的书房';
  if (activeDrawer.value === 'calls') return '调用过程';
  return '';
});

const bodyClass = computed<Record<string, boolean>>(() => ({
  'workshop-body--solo': !historyOpen.value && activeDrawer.value === null,
  'workshop-body--with-history': historyOpen.value,
  'workshop-body--with-side': activeDrawer.value !== null,
}));
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
            :class="{ 'workshop-drawer-tab--active': historyOpen }"
            data-testid="workshop-open-history"
            :aria-expanded="historyOpen"
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
              title="课程图书馆"
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
              title="章节闯关塔"
              @click="openArena(1)"
            >
              <span class="travel-chip__label">闯关塔 A</span>
            </button>
          </li>
        </ul>
      </header>

      <div
        class="workshop-body"
        :class="bodyClass"
        data-testid="workshop-body"
      >
        <aside
          v-if="historyOpen"
          class="workshop-drawer workshop-drawer--history"
          data-testid="workshop-drawer-history"
          data-panel-side="left"
          role="complementary"
          aria-label="历史问答"
        >
          <header class="workshop-drawer__bar">
            <span class="workshop-drawer__title">历史问答</span>
            <button
              type="button"
              class="workshop-drawer__close"
              data-testid="workshop-history-close"
              @click="closeHistoryDrawer"
            >
              关闭
            </button>
          </header>
          <div class="workshop-drawer__body">
            <ChatHistoryRail
              embedded-open
              :pending-turn="historyPendingTurn"
              @select="onSelectConversation"
            />
          </div>
        </aside>

        <div
          class="workshop-chat-pane"
          data-testid="workshop-chat-pane"
        >
          <WorkshopChat
            ref="chatRef"
            @open-resource="onOpenResource"
            @history-saved="appendHistoryTurn"
          />
        </div>
        <aside
          v-if="activeDrawer !== null"
          class="workshop-drawer workshop-drawer--side"
          :data-testid="`workshop-drawer-${activeDrawer}`"
          data-panel-side="right"
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
            <StudioDrawer
              v-if="activeDrawer === 'study'"
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
                暂无调用过程，提问或生成后会显示链路。
              </p>
            </div>
          </div>
        </aside>
      </div>

      <!-- 书房资源详情弹窗：列表点击某行（或聊天「在书房查看」）写入 store openResourceId 后弹出，
           展示讲义 / 思维导图 / 练习等内容；点遮罩、点 ✕ 或按 Esc 关闭。 -->
      <Teleport to="body">
        <div
          v-if="selectedId !== null && pageActive"
          class="resource-modal-overlay"
          data-testid="resource-modal"
          role="dialog"
          aria-modal="true"
          aria-label="书房资源详情"
          @click.self="onDrawerBack"
        >
          <div class="resource-modal">
            <header class="resource-modal__bar">
              <span class="resource-modal__title">📄 资源详情</span>
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
  display: grid;
  align-items: stretch;
  gap: 10px;
  min-height: 0;
  overflow: hidden;
  position: relative;
  grid-template-columns: minmax(0, 1fr);
}
.workshop-body--with-history {
  grid-template-columns: minmax(240px, 300px) minmax(520px, 1fr);
}
.workshop-body--with-side {
  grid-template-columns: minmax(520px, 1fr) minmax(320px, 380px);
}
.workshop-body--with-history.workshop-body--with-side {
  grid-template-columns: minmax(230px, 280px) minmax(500px, 1fr) minmax(320px, 380px);
}
.workshop-chat-pane {
  width: 100%;
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
.workshop-body--solo .workshop-chat-pane {
  justify-self: stretch;
}
.workshop-drawer {
  width: 100%;
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
.workshop-drawer--history {
  box-shadow: 6px 6px 0 rgba(42, 26, 5, 0.16);
}
.workshop-drawer--side {
  box-shadow: -6px 6px 0 rgba(42, 26, 5, 0.18);
}
.workshop-drawer__bar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  /* 与对话区表头 .chat__topbar 统一为同一固定高度（box-sizing 含内边距与下边框），
     让历史 / 对话 / 书房·调用各面板顶部的虚线分隔线落在同一水平线上对齐。 */
  min-height: 44px;
  box-sizing: border-box;
  padding: 6px 10px;
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

@media (max-width: 1120px) {
  .workshop-body {
    grid-template-columns: minmax(0, 1fr);
    overflow: hidden;
  }
  .workshop-body--with-history,
  .workshop-body--with-side,
  .workshop-body--with-history.workshop-body--with-side {
    grid-template-columns: minmax(0, 1fr);
  }
  .workshop-drawer {
    position: absolute;
    inset: 0;
    z-index: 20;
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
