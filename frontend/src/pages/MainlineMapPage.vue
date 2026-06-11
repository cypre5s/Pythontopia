<script setup lang="ts">
// R-15 关卡地图页（路由 /mainline-map）：羊皮卷 Phaser 地图 + 可读 Vue 覆盖层。
//
// 设计（对齐 WorldPage = 画布 + HUD 覆盖层 的项目惯例）：
//   - 背景：经现有 Phaser 单例挂载 ChapterMapScene（星露谷风地图），不 new Phaser.Game。
//   - 覆盖层：章节节点列表 + 选中章节的 stage 三件套面板（无障碍、可读、可点），是测试与
//     键盘可达的主 UI；Phaser 地图为装饰增强。
//   - 数据：`GET /api/mainline/overview` 一次拉取（mainline store），后端权威。
//
// Phaser 复用注意（详见交付报告「场景注册」）：本页尽力复用单例——无实例则以本页根容器
// 新建（仅含 ChapterMapScene）；已有实例（如从 KeepAlive 的 /world 过来）则把其 canvas 暂借到
// 本页并注册启动 ChapterMapScene，离开时归还。最终更稳的做法是由主代理把 ChapterMapScene
// 注册进共享场景列表（WorldCanvas/phaserGame）并按路由切场景。
defineOptions({ name: 'MainlineMapPage' });

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  createPhaserGame,
  getPhaserGame,
  type PhaserGameHandle,
  type PhaserSceneConfig,
} from '@game/adapters/phaserGame';
import {
  ensureChapterMapSceneStarted,
  onChapterMapSelect,
  setChapterMapData,
  setChapterMapSelection,
  stopChapterMapScene,
  type ChapterMapNode,
  type MainlineGameLike,
} from '@game/scenes/ChapterMapScene';
import { chapterNodeState, useMainlineStore } from '@stores/mainline';
import { useToastStore } from '@stores/toast';
import ChapterNodeList from '@/components/mainline/ChapterNodeList.vue';
import ChapterStagePanel from '@/components/mainline/ChapterStagePanel.vue';

const router = useRouter();
const store = useMainlineStore();
const toast = useToastStore();

const root = ref<HTMLElement | null>(null);
let gameHandle: PhaserGameHandle | null = null;
let createdGame = false;
let borrowedCanvas: HTMLCanvasElement | null = null;
let originalCanvasParent: HTMLElement | null = null;

// 章节学习目标（点击节点后才在地图上弹出显示）：通识性概述，非课件原文引用。
const CHAPTER_OBJECTIVES: Record<number, string> = {
  1: '认识计算思维，了解计算机与计算工具的发展。',
  2: '搭好 Python 环境，掌握变量、数据类型与输入输出。',
  3: '看懂进制与编码，玩转数值与字符串处理。',
  4: '用顺序、分支与循环，让程序自动跑起来。',
  5: '用列表、字典、集合组织与处理数据。',
  6: '学会读写文件，做简单的数据存取与分析。',
  7: '用函数、递归、模块与面向对象封装复杂度。',
};

const nodes = computed<ChapterMapNode[]>(() =>
  store.chapters.map((ch) => ({
    chapter: ch.chapter,
    title: ch.title,
    state: chapterNodeState(ch),
    stars: ch.stars,
    objective: CHAPTER_OBJECTIVES[ch.chapter],
  })),
);

const selectedChapter = computed<number | null>(() => store.selectedChapter);
const selectedView = computed(() => store.selectedChapterView);

// 节点数据变化 → 喂给场景（setChapterMapData 在场景未就绪时只缓存，就绪后自动重绘）。
watch(nodes, (next) => setChapterMapData(next), { immediate: true });
// 选中章节变化 → 同步给场景高亮 + 弹出该节点目标说明牌。
watch(selectedChapter, (ch) => setChapterMapSelection(ch), { immediate: true });

function onSelect(chapter: number): void {
  store.selectChapter(chapter);
}

function borrowExistingCanvas(game: MainlineGameLike): void {
  const canvas = game.canvas;
  if (canvas instanceof HTMLCanvasElement && root.value) {
    originalCanvasParent = canvas.parentElement;
    borrowedCanvas = canvas;
    root.value.appendChild(canvas);
  }
}

function mountScene(): void {
  const existing = getPhaserGame() as unknown as MainlineGameLike | null;
  if (existing) {
    createdGame = false;
    try {
      borrowExistingCanvas(existing);
      ensureChapterMapSceneStarted(existing);
    } catch {
      // 复用失败不阻塞覆盖层 UI（地图仅为增强）。
    }
    return;
  }
  if (!root.value) return;
  // 无实例：以本页根容器新建单例（空场景），再注册启动 ChapterMapScene。
  gameHandle = createPhaserGame(root.value, [] as PhaserSceneConfig[]);
  createdGame = true;
  try {
    ensureChapterMapSceneStarted(getPhaserGame() as unknown as MainlineGameLike | null);
  } catch {
    // 同上，地图为增强。
  }
}

function unmountScene(): void {
  const game = getPhaserGame() as unknown as MainlineGameLike | null;
  if (createdGame) {
    // 本页自建的实例：销毁，避免污染 /world 的场景列表（其 BootScene/WorldScene 需自建）。
    gameHandle?.destroy(true);
    gameHandle = null;
  } else {
    // 复用实例：停掉地图场景并归还借用的 canvas。
    try {
      stopChapterMapScene(game);
    } catch {
      // ignore
    }
    if (borrowedCanvas && originalCanvasParent) {
      try {
        originalCanvasParent.appendChild(borrowedCanvas);
      } catch {
        // ignore
      }
    }
  }
  borrowedCanvas = null;
  originalCanvasParent = null;
}

function goEnterStage(stageId: number): void {
  // A5 stage 学习页（主代理集成已注册 /mainline/stage/:stageId）。
  void router.push(`/mainline/stage/${stageId}`);
}
function goOpenTest(chapter: number): void {
  // A4 章末大测试页（主代理集成已注册 /chapter-test/:chapter）。
  void router.push(`/chapter-test/${chapter}`);
}
function goOpenEndless(chapter: number): void {
  // 无尽模式补星：跳已存在的 /endless 并锁定本章范围。
  void router.push({ path: '/endless', query: { chapter: String(chapter) } });
}
async function onClaimErrorbook(chapter: number): Promise<void> {
  try {
    const awarded = await store.claimErrorbookRecovery(chapter);
    toast.show(
      awarded ? `第 ${chapter} 章错题复盘补星 +1 ★` : '尚未满足补星条件（本章错题复盘需 ≥ 3 个已解决）',
      awarded ? 'success' : 'info',
    );
  } catch (error) {
    toast.show(error instanceof Error ? error.message : '补星申领失败', 'error');
  }
}

function closeDrawer(): void {
  store.selectChapter(null);
}

function back(): void {
  void router.push('/world');
}

function retry(): void {
  void store.load(true);
}

onMounted(async () => {
  onChapterMapSelect((chapter) => store.selectChapter(chapter));
  // 强制刷新：stage 学习页的三件套打点不经 mainline store（组件直连 API），
  // 返回地图时缓存的 overview 已过期；force 拉权威快照（有缓存时不闪 loading，
  // 页面 loading 态仅在 !isLoaded 时展示）。
  await store.load(true);
  mountScene();
  setChapterMapData(nodes.value);
});

onBeforeUnmount(() => {
  onChapterMapSelect(null);
  unmountScene();
});
</script>

<template>
  <section
    class="map-page"
    data-testid="mainline-map-page"
  >
    <div
      ref="root"
      class="map-canvas"
      aria-hidden="true"
    />

    <div class="map-overlay">
      <header class="map-head">
        <button
          type="button"
          class="map-back"
          data-testid="mainline-back"
          @click="back"
        >
          ← 返回小镇
        </button>
        <h1 class="map-title">
          冒险地图
        </h1>
        <span
          class="map-total-stars"
          data-testid="mainline-total-stars"
        >
          ★ {{ store.totalStars }}
        </span>
      </header>

      <p
        v-if="store.loading && !store.isLoaded"
        class="map-state"
        data-testid="mainline-loading"
      >
        地图加载中…
      </p>
      <div
        v-else-if="store.error && !store.isLoaded"
        class="map-state map-state--error"
        data-testid="mainline-error"
      >
        <p>地图加载失败：{{ store.error }}</p>
        <button
          type="button"
          class="map-back"
          data-testid="mainline-retry"
          @click="retry"
        >
          重试
        </button>
      </div>

      <div
        v-else
        class="map-body"
      >
        <!-- 键盘可达 / 测试可达的章节选择镜像：视觉隐藏，地图节点为主交互。 -->
        <aside class="map-nodes-a11y">
          <ChapterNodeList
            :chapters="store.chapters"
            :selected="selectedChapter"
            @select="onSelect"
          />
        </aside>

        <transition name="map-drawer">
          <aside
            v-if="selectedView"
            class="map-drawer"
          >
            <button
              type="button"
              class="map-drawer__close"
              data-testid="mainline-drawer-close"
              aria-label="收起详情，查看完整地图"
              @click="closeDrawer"
            >
              ✕
            </button>
            <ChapterStagePanel
              :chapter="selectedView"
              @enter-stage="goEnterStage"
              @open-test="goOpenTest"
              @open-endless="goOpenEndless"
              @claim-errorbook="onClaimErrorbook"
            />
          </aside>
        </transition>

        <p
          v-if="!selectedView"
          class="map-hint"
          data-testid="mainline-empty"
        >
          🪧 点击地图上的章节牌子，查看小节进度与目标
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.map-page {
  position: fixed;
  inset: 0;
  overflow: hidden;
  background: var(--pyt-color-bg-primary);
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
}
.map-canvas {
  position: absolute;
  inset: 0;
  z-index: var(--pyt-z-base);
}
.map-overlay {
  position: absolute;
  inset: 0;
  z-index: var(--pyt-z-hud);
  display: flex;
  flex-direction: column;
  gap: var(--pyt-space-3);
  padding: var(--pyt-space-4);
  pointer-events: none;
  overflow: auto;
}
.map-overlay > * {
  pointer-events: auto;
}
.map-head {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-3);
}
.map-title {
  margin: 0;
  flex: 1;
  font-size: var(--pyt-font-size-xl);
  text-shadow: 2px 2px 0 var(--pyt-color-gold);
}
.map-total-stars {
  font-size: var(--pyt-font-size-lg);
  color: var(--pyt-color-gold-bright);
  text-shadow: 1px 1px 0 var(--pyt-color-shadow);
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-full);
  padding: var(--pyt-space-1) var(--pyt-space-3);
}
.map-back {
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-warning-bg);
  border: 2px solid var(--pyt-color-text-primary);
  border-radius: var(--pyt-radius-md);
  box-shadow: var(--pyt-shadow-card);
  padding: var(--pyt-space-1) var(--pyt-space-3);
  cursor: pointer;
}
.map-back:active {
  transform: translate(1px, 1px);
  box-shadow: none;
}
.map-state {
  padding: var(--pyt-space-4);
  background: var(--pyt-color-bg-elevated);
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-md);
  color: var(--pyt-color-text-hint);
  max-width: 480px;
}
.map-state--error {
  border-color: var(--pyt-color-danger);
  color: var(--pyt-color-danger);
}
/* 地图当主角：body 本身不拦截指针（让点击穿透到 Phaser 地图节点），
   仅抽屉 / 提示 / 隐藏镜像各自接管自己的指针区域。 */
.map-body {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  pointer-events: none;
}
/* 章节列表镜像：视觉隐藏但保留在无障碍树 / DOM（键盘与测试可达）。 */
.map-nodes-a11y {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}
/* 右侧玻璃抽屉：点击节点浮出，承载小节三件套与入口。 */
.map-drawer {
  pointer-events: auto;
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: clamp(320px, 38vw, 480px);
  overflow: auto;
  padding-left: var(--pyt-space-1);
}
.map-drawer :deep(.stage-panel) {
  background: rgba(255, 248, 226, 0.92);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  padding-top: var(--pyt-space-5);
}
.map-drawer__close {
  position: absolute;
  top: var(--pyt-space-2);
  right: var(--pyt-space-2);
  z-index: 2;
  width: 28px;
  height: 28px;
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-sm);
  line-height: 1;
  color: var(--pyt-color-text-primary);
  background: var(--pyt-color-warning-bg);
  border: 2px solid var(--pyt-color-text-primary);
  border-radius: var(--pyt-radius-md);
  box-shadow: var(--pyt-shadow-card);
  cursor: pointer;
}
.map-drawer__close:active {
  transform: translate(1px, 1px);
  box-shadow: none;
}
.map-drawer-enter-active,
.map-drawer-leave-active {
  transition: transform var(--pyt-motion-base), opacity var(--pyt-motion-base);
}
.map-drawer-enter-from,
.map-drawer-leave-to {
  transform: translateX(16px);
  opacity: 0;
}
/* 未选中时的浮动提示。 */
.map-hint {
  pointer-events: auto;
  position: absolute;
  left: 50%;
  bottom: var(--pyt-space-5);
  transform: translateX(-50%);
  margin: 0;
  padding: var(--pyt-space-2) var(--pyt-space-4);
  font-size: var(--pyt-font-size-sm);
  color: var(--pyt-color-text-primary);
  background: rgba(255, 248, 226, 0.92);
  border: 2px solid var(--pyt-color-border-strong);
  border-radius: var(--pyt-radius-full);
  box-shadow: var(--pyt-shadow-card);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}
@media (max-width: 760px) {
  .map-drawer {
    width: min(92vw, 480px);
  }
}
</style>
