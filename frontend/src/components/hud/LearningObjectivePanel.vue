<script setup lang="ts">
// R-12 美化新增：左侧「当前学习目标」面板。
//
// 设计目标：
//   - 让玩家一眼看出这是 Python 编程教学 RPG，而非普通像素地图游戏。
//     页面顶部 HUD 给「我在哪一章」「整体进度」，本面板补充「当前知识点 /
//     当前任务 / 学习目标 / 奖励 / 提示」这五项教学语义。
//   - 数据全部来自 mainQuest store + learning store + chapters 配置，
//     不引入新接口；缺数据时也能优雅降级到默认文案。
//   - 可折叠：默认展开，点击标题或按 H 收起；折叠后只剩一根细条，留出主画面视野。
//   - 视觉与新的顶部 HUD 一致：半透明深色 + 金色细边框 + 圆角 14px。
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useMainQuestStore } from '@stores/mainQuest';
import { useLearningStore } from '@stores/learning';
import { useWorldStore } from '@stores/world';
import { useToastStore } from '@stores/toast';
import { findChapterByKcCode } from '@/data/chapters';
import { humanizeKcText } from '@/data/kcNames';
import { FAST_TRAVEL_ROUTES } from '@/data/fastTravel';
import { resolveQuestGuidance, type QuestGuidance } from '@/utils/questGuidance';
import { openNpcDialog } from '@game/adapters/dialog';
import { useDraggable } from '@/composables/useDraggable';
import { playUiClick } from '@/composables/useStardewFx';

const mainQuest = useMainQuestStore();
const learning = useLearningStore();
const world = useWorldStore();
const toast = useToastStore();

const rootRef = ref<HTMLElement | null>(null);
const { style: dragStyle, onPointerDown: onDragStart } = useDraggable(
  rootRef,
  'pyt_lop_pos',
  { top: 96, left: 24 },
);

const collapsed = ref(false);
const hidden = ref(localStorage.getItem('pyt_lop_hidden') === '1');

function hide(): void {
  playUiClick();
  hidden.value = true;
  try { localStorage.setItem('pyt_lop_hidden', '1'); } catch { /* privacy */ }
}
function unhide(): void {
  playUiClick();
  hidden.value = false;
  try { localStorage.removeItem('pyt_lop_hidden'); } catch { /* privacy */ }
}

const currentChapter = computed<number>(() => mainQuest.progress.currentChapter);
const currentChapterView = computed(() => mainQuest.chapterViews[currentChapter.value - 1] ?? null);

const currentSubQuest = computed(() => {
  const view = currentChapterView.value;
  if (!view) return null;
  return (
    view.subQuests.find((s) => s.status === 'in_progress')
    ?? view.subQuests.find((s) => s.status === 'available')
    ?? view.subQuests[view.subQuests.length - 1]
    ?? null
  );
});

const currentKcCode = computed<string | null>(() => {
  // 优先级：learning.field.activeKcCode > 当前子任务首个 kcCode
  if (learning.field.activeKcCode) return learning.field.activeKcCode;
  return currentSubQuest.value?.kcCodes?.[0] ?? null;
});

const currentKcName = computed<string>(() => {
  // R-12：把 KC code 翻译成 chapters.ts 里的章节名，避免直接暴露 PY43-KC-NNN。
  // 缺数据时退化到默认文案。
  const code = currentKcCode.value;
  if (!code) return '变量与表达式';
  const chapter = findChapterByKcCode(code);
  if (chapter) return chapter.name;
  return code;
});

const currentTaskTitle = computed<string>(
  () => humanizeKcText(currentSubQuest.value?.title) || '探索 Pythontopia 小镇',
);
const currentTaskHint = computed<string>(
  () => humanizeKcText(currentSubQuest.value?.hint) || '与 NPC 对话、参与练习，逐步积累 Python 基础。',
);

// 各章节默认教学要点：当子任务没有显式 objective 字段时（types/mainQuest.ts 暂未定义），
// 用本地固定的章节级语义补足，避免硬塞空数组。
// 章节默认要点已对齐 course_chapter.json 真实主题（与 chapters.ts 同步修正）。
const CHAPTER_DEFAULT_GOALS: Readonly<Record<number, readonly string[]>> = Object.freeze({
  1: ['理解计算工具与计算思维', '认识算法与问题分解', '建立用程序解决问题的思路'],
  2: ['搭建 Python 环境并运行程序', '掌握变量 / 数据类型 / 运算符', '使用 input / print 与基础函数'],
  3: ['理解二进制与进制转换', '掌握字符编码与字符串处理', '会用第三方库做信息计算'],
  4: ['掌握 if / elif / else 分支', '使用 for / while 循环与嵌套', '用 break / continue 控制流程'],
  5: ['掌握列表 / 元组操作', '使用字典与集合', '理解序列的通用操作'],
  6: ['读写文件', '基于文件做数据分析', '完成词频统计等数据任务'],
  7: ['定义函数 / 参数 / 返回值与递归', '用模块 / 包 / pip 复用代码', '理解类与面向对象'],
});

const learningGoals = computed<readonly string[]>(() => {
  const ch = currentChapter.value;
  return CHAPTER_DEFAULT_GOALS[ch] ?? CHAPTER_DEFAULT_GOALS[1] ?? [];
});

const rewardText = computed<string>(() => {
  // Phase 6 起移除货币/物品系统：只展示与学习直接相关的掌握度收获，
  // 过滤掉历史遗留的 coin/gem/item 奖励，避免给学生「点了没反应」的虚拟货币暗示。
  const sub = currentSubQuest.value;
  if (sub?.rewards && sub.rewards.length > 0) {
    const masteryRewards = sub.rewards
      .filter((r) => r.kind === 'kc_mastery')
      .map((r) => `掌握度 +${r.amount}${r.ref ? `（${r.ref}）` : ''}`);
    if (masteryRewards.length > 0) return masteryRewards.join('，');
  }
  return '掌握度 +5';
});

const currentMapKey = computed<string>(() => world.player?.mapKey ?? 'town_hub');
const currentMapLabel = computed<string>(() => {
  switch (currentMapKey.value) {
    case 'town_hub': return 'Pythonopia 小镇';
    case 'farm_north': return '知识农场';
    case 'mine_south': return 'Bug 矿洞';
    case 'campus_east': return '函数学院';
    case 'port_west': return '商船港口';
    default: return currentMapKey.value;
  }
});

// R-13：把当前子任务翻译成「下一步该干什么 + 去找哪个 NPC」。
const guidance = computed<QuestGuidance | null>(() =>
  currentSubQuest.value ? resolveQuestGuidance(currentSubQuest.value) : null,
);

/** 传送到目标地图（带落点）；复用 WorldCanvas 的 world-travel 监听重启场景。 */
function travelToMap(mapKey: string, tile: { x: number; y: number } | null): void {
  const route = FAST_TRAVEL_ROUTES.find((r) => r.toMap === mapKey);
  const landing = tile ?? (route ? { x: route.tileX, y: route.tileY } : undefined);
  world.setPendingTravel(mapKey, landing);
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('pythontopia:world-travel'));
  }
}

/**
 * 「前往」：自动指路到当前任务对应的 NPC / 设施。
 *   - dialog：传送到任务地图（若不同）→ 打开该 NPC 对话（对话完成即交证据）；
 *   - workshop / training：直接打开工坊师傅 / 教务 Q 对话，NPC 对话内 route 进设施；
 *   - practice：传送到知识农场 → 打开田老对话；
 *   - building：传送到证据精确点位并标记到点完成。
 * 无当前子任务（待打 Boss）时打开主线面板做引导。
 */
function goNext(): void {
  playUiClick();
  const g = guidance.value;
  if (!g) {
    mainQuest.openPanel();
    return;
  }
  const playerMap = world.player?.mapKey ?? null;

  if (g.kind === 'building' && g.tile) {
    travelToMap(g.mapKey, g.tile);
    mainQuest.onBuildingVisited(g.mapKey, g.tile.x, g.tile.y);
  } else if (g.teleport && playerMap !== g.mapKey) {
    travelToMap(g.mapKey, null);
    if (g.openDialog) {
      // 地图重建后再唤起 NPC 对话，避免与 BootScene 加载抢镜。
      window.setTimeout(() => openNpcDialog(g.npcKey), 900);
    }
  } else if (g.openDialog) {
    openNpcDialog(g.npcKey);
  }

  toast.show(`已为你指路：${g.stepText}`, 'info');
}

function toggleCollapsed(): void {
  playUiClick();
  collapsed.value = !collapsed.value;
}

function onKey(event: KeyboardEvent): void {
  // 仅在没有 modifier 且未聚焦到输入框 / canvas 时响应 H。
  if (event.altKey || event.ctrlKey || event.metaKey) return;
  const target = event.target;
  if (target instanceof HTMLElement) {
    const tag = target.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable) return;
  }
  if (target instanceof HTMLCanvasElement) {
    // canvas 聚焦时把 H 让给游戏，避免抢走可能的玩家动作
    return;
  }
  if (event.key.toLowerCase() === 'h') {
    toggleCollapsed();
    event.preventDefault();
  }
}

onMounted(() => {
  if (typeof window !== 'undefined') {
    window.addEventListener('keydown', onKey);
  }
});
onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onKey);
  }
});
</script>

<template>
  <!-- 隐藏后只显示小按钮用于恢复 -->
  <button
    v-if="hidden"
    type="button"
    class="lop-restore-btn"
    :style="{ position: 'fixed', top: '96px', left: '24px', zIndex: 101 }"
    data-testid="lop-restore"
    aria-label="显示学习目标"
    @click="unhide"
  >
    目标
  </button>
  <aside
    v-else
    ref="rootRef"
    class="learning-objective-panel"
    :class="{ 'learning-objective-panel--collapsed': collapsed }"
    :style="dragStyle"
    data-testid="learning-objective-panel"
    aria-label="当前学习目标"
    @pointerdown="onDragStart"
  >
    <button
      type="button"
      class="lop-header"
      :aria-expanded="!collapsed"
      :aria-label="collapsed ? '展开学习目标（H）' : '折叠学习目标（H）'"
      @click="toggleCollapsed"
    >
      <span class="lop-header__title">学习目标</span>
      <span class="lop-header__hint">{{ currentMapLabel }} · H</span>
      <span class="lop-header__chev">{{ collapsed ? '▸' : '▾' }}</span>
      <button
        type="button"
        class="lop-close"
        aria-label="隐藏学习目标面板"
        @click.stop="hide"
      >
        ✕
      </button>
    </button>
    <div
      v-show="!collapsed"
      class="lop-body"
    >
      <div class="lop-row">
        <span class="lop-row__label">当前知识点</span>
        <span class="lop-row__value lop-row__value--accent">{{ currentKcName }}</span>
      </div>
      <div class="lop-row">
        <span class="lop-row__label">当前任务</span>
        <span class="lop-row__value">{{ currentTaskTitle }}</span>
      </div>
      <div class="lop-section">
        <span class="lop-section__label">学习目标</span>
        <ul class="lop-goal-list">
          <li
            v-for="goal in learningGoals"
            :key="goal"
            class="lop-goal-item"
          >
            <span
              class="lop-goal-dot"
              aria-hidden="true"
            >◆</span>
            <span class="lop-goal-text">{{ goal }}</span>
          </li>
        </ul>
      </div>
      <div class="lop-row">
        <span class="lop-row__label">奖励</span>
        <span class="lop-row__value lop-row__value--reward">{{ rewardText }}</span>
      </div>
      <div
        v-if="guidance"
        class="lop-next"
        data-testid="lop-next-step"
      >
        <div class="lop-next__head">
          <span
            class="lop-next__badge"
            aria-hidden="true"
          >➤</span>
          <span class="lop-next__label">下一步</span>
        </div>
        <p class="lop-next__text">
          {{ guidance.stepText }}
        </p>
        <button
          type="button"
          class="lop-next__cta"
          data-testid="lop-go-next"
          :aria-label="`前往：${guidance.stepText}`"
          @click="goNext"
        >
          <span
            class="lop-next__cta-icon"
            aria-hidden="true"
          >▶</span>
          <span class="lop-next__cta-text">{{ guidance.ctaText }}</span>
        </button>
      </div>
      <p class="lop-tip">
        [TIP] 提示：{{ currentTaskHint }}
      </p>
    </div>
  </aside>
</template>

<style scoped>
.learning-objective-panel {
  position: fixed;
  top: 96px;
  left: 24px;
  width: 300px;
  max-width: calc(100vw - 48px);
  padding: 12px 14px;
  background: var(--pyt-panel-bg);
  border: 3px solid var(--pyt-panel-border);
  border-radius: 2px;
  box-shadow: 4px 4px 0 var(--pyt-panel-border-dark), 0 8px 24px var(--pyt-panel-shadow);
  color: var(--pyt-text-primary);
  font-family: var(--pyt-font-pixel);
  font-size: 13px;
  z-index: 80;
  pointer-events: auto;
  image-rendering: pixelated;
  animation: lop-fade-in 360ms ease-out;
}

.learning-objective-panel--collapsed {
  padding: 8px 12px;
  width: max-content;
}

@keyframes lop-fade-in {
  from { opacity: 0; transform: translateY(-6px); }
  to { opacity: 1; transform: translateY(0); }
}

.lop-header {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 0;
  background: none;
  border: none;
  color: inherit;
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  text-align: left;
}
.lop-header:hover {
  color: var(--pyt-text-accent);
}
.lop-header:focus-visible {
  outline: 2px solid var(--pyt-text-gold);
  outline-offset: 2px;
  border-radius: 2px;
}
.lop-header__icon { font-size: 16px; }
.lop-header__title {
  font-weight: 700;
  letter-spacing: 0.4px;
  color: var(--pyt-text-accent);
  flex: 0 0 auto;
}
.lop-header__hint {
  flex: 1 1 auto;
  font-size: 11px;
  color: var(--pyt-text-secondary);
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.lop-header__chev {
  color: var(--pyt-text-gold);
  font-size: 12px;
  margin-left: 4px;
}

.lop-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 2px solid var(--pyt-panel-border-dark);
}

.lop-row {
  display: grid;
  grid-template-columns: 76px 1fr;
  gap: 8px;
  align-items: baseline;
  font-size: 13px;
}
.lop-row__label {
  color: var(--pyt-text-secondary);
  font-size: 11px;
}
.lop-row__value {
  color: var(--pyt-text-primary);
  line-height: 1.4;
}
.lop-row__value--accent {
  color: var(--pyt-text-accent);
  font-weight: 700;
  animation: lop-breathing 2.6s ease-in-out infinite;
}
.lop-row__value--reward {
  color: var(--pyt-bar-energy);
  font-weight: 600;
}

@keyframes lop-breathing {
  0%, 100% { text-shadow: 0 0 0 transparent; }
  50% { text-shadow: 0 0 8px rgba(241, 196, 83, 0.6); }
}

.lop-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.lop-section__label {
  color: var(--pyt-text-secondary);
  font-size: 11px;
}
.lop-goal-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.lop-goal-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12px;
  color: var(--pyt-text-primary);
  line-height: 1.4;
}
.lop-goal-dot {
  color: var(--pyt-text-gold);
  font-size: 10px;
  flex: 0 0 auto;
}
.lop-goal-text {
  flex: 1 1 auto;
}

/* ── 下一步指路块 ──────────────────────────────────────────────── */
.lop-next {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 10px;
  background: rgba(241, 196, 83, 0.1);
  border: 1px solid rgba(241, 196, 83, 0.45);
  border-radius: 2px;
}
.lop-next__head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.lop-next__badge {
  color: var(--pyt-text-gold);
  font-size: 11px;
  animation: lop-next-nudge 1.6s ease-in-out infinite;
}
.lop-next__label {
  color: var(--pyt-text-accent);
  font-weight: 700;
  font-size: 11px;
  letter-spacing: 0.4px;
}
.lop-next__text {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--pyt-text-primary);
}
.lop-next__cta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 7px 10px;
  font-family: inherit;
  font-size: 12px;
  font-weight: 700;
  color: var(--pyt-color-shadow);
  background: linear-gradient(180deg, var(--pyt-text-accent, var(--pyt-color-gold-bright)) 0%, var(--pyt-text-gold, var(--pyt-color-gold)) 100%);
  border: 1px solid var(--pyt-panel-border-dark);
  border-radius: 2px;
  box-shadow: 2px 2px 0 var(--pyt-panel-border-dark);
  cursor: pointer;
  transition: filter 120ms, transform 80ms, box-shadow 80ms;
}
.lop-next__cta:hover {
  filter: brightness(1.08);
}
.lop-next__cta:active {
  transform: translateY(2px);
  box-shadow: 0 0 0 var(--pyt-panel-border-dark);
}
.lop-next__cta:focus-visible {
  outline: 2px solid var(--pyt-text-gold);
  outline-offset: 2px;
}
.lop-next__cta-icon {
  font-size: 10px;
}

@keyframes lop-next-nudge {
  0%, 100% { transform: translateX(0); }
  50% { transform: translateX(3px); }
}

.lop-tip {
  margin: 0;
  padding: 6px 8px;
  background: rgba(0, 0, 0, 0.15);
  border-left: 2px solid var(--pyt-text-gold);
  border-radius: 2px;
  font-size: 11px;
  color: var(--pyt-text-secondary);
  line-height: 1.4;
}

@media (max-width: 1280px) {
  .learning-objective-panel {
    width: 260px;
    top: 88px;
    left: 16px;
  }
}

@media (max-width: 1024px) {
  .learning-objective-panel {
    width: 240px;
    font-size: 12px;
  }
  .lop-row {
    grid-template-columns: 64px 1fr;
  }
}

@media (max-width: 760px) {
  .learning-objective-panel {
    top: auto;
    bottom: 100px;
    left: 8px;
    right: 8px;
    width: auto;
    max-width: calc(100vw - 16px);
    opacity: 0.96;
  }
  .learning-objective-panel--collapsed {
    width: max-content;
  }
}

/* ── 关闭按钮 ──────────────────────────────────────────────── */
.lop-close {
  margin-left: auto;
  background: transparent;
  border: 1px solid rgba(255, 247, 230, 0.3);
  color: var(--pyt-text-secondary);
  font-size: 11px;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-radius: 2px;
  transition: background 120ms;
}
.lop-close:hover { background: rgba(230, 57, 70, 0.3); color: #fff; }

/* ── 恢复按钮 ──────────────────────────────────────────────── */
.lop-restore-btn {
  pointer-events: auto;
  background: var(--pyt-hud-bg, rgba(25, 28, 18, 0.82));
  border: 2px solid var(--pyt-panel-border, #d4af37);
  color: var(--pyt-text-accent, var(--pyt-color-gold-bright));
  font-family: var(--pyt-font-pixel);
  font-size: 11px;
  padding: 6px 10px;
  cursor: pointer;
  box-shadow: 2px 2px 0 var(--pyt-panel-border-dark, var(--pyt-color-text-hint));
  transition: background 120ms;
  border-radius: 2px;
}
.lop-restore-btn:hover { background: var(--pyt-btn-hover-bg, rgba(241, 196, 83, 0.25)); }

@media (prefers-reduced-motion: reduce) {
  .learning-objective-panel,
  .lop-row__value--accent,
  .lop-next__badge {
    animation: none;
  }
}
</style>
