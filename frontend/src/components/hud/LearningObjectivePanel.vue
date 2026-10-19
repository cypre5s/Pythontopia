<script setup lang="ts">
// 左侧「当前学习目标」面板：读后端权威 mainline overview + KC 掌握度 + 画像信号，
// 经 useNextSteps 推导出针对本人的「下一步」行动（主线断点 / 弱项补强 / 补星或偏好），
// 数据未就绪时回退到「去冒险地图」通用入口与静态章节要点。
// 默认折叠（只露标题 + CTA），点击标题或按 H 展开/收起。
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useMainlineStore, clampStars } from '@stores/mainline';
import { useDraggable } from '@/composables/useDraggable';
import { useNextSteps, type NextStep } from '@/composables/useNextSteps';
import { playUiClick } from '@/composables/useStardewFx';

const router = useRouter();
const mainline = useMainlineStore();
const { steps } = useNextSteps();
const primaryStep = computed<NextStep | null>(() => steps.value[0] ?? null);
const secondarySteps = computed<NextStep[]>(() => steps.value.slice(1));

function goStep(step: NextStep): void {
  playUiClick();
  void router.push(
    step.route.query
      ? { path: step.route.path, query: step.route.query }
      : { path: step.route.path },
  );
}

const rootRef = ref<HTMLElement | null>(null);
const { style: dragStyle, onPointerDown: onDragStart } = useDraggable(
  rootRef,
  'pyt_lop_pos',
  { top: 96, left: 24 },
);

// 渐进披露：默认折叠，只露标题 + 单一「去冒险地图」CTA；其余细节按需展开。
const collapsed = ref(true);
const hidden = ref(localStorage.getItem('pyt_lop_hidden') === '1');

function hide(): void {
  playUiClick();
  hidden.value = true;
  try { localStorage.setItem('pyt_lop_hidden', '1'); } catch { /* 隐私模式忽略 */ }
}
function unhide(): void {
  playUiClick();
  hidden.value = false;
  try { localStorage.removeItem('pyt_lop_hidden'); } catch { /* 隐私模式忽略 */ }
}

const currentChapter = computed<number>(() => mainline.firstActionableChapter);
const currentChapterView = computed(() => mainline.chapterByNumber(currentChapter.value));
const currentChapterTitle = computed<string>(
  () => currentChapterView.value?.title ?? 'Python 学习之旅',
);
const chapterStars = computed<number>(() => clampStars(currentChapterView.value?.stars ?? 0));
const chapterStarText = computed<string>(
  () => '★'.repeat(chapterStars.value) + '☆'.repeat(3 - chapterStars.value),
);
const totalStars = computed<number>(() => mainline.totalStars);

// 世界收缩为单一中央小镇 town_hub，面板标题副标签恒为「小镇」。
const currentMapLabel = 'Pythontopia 小镇';

// 各章节默认教学要点（对齐 course_chapter.json 真实主题），用于面板展开态。
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

function goMap(): void {
  playUiClick();
  void router.push('/mainline-map');
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
    <div
      class="lop-header"
      role="button"
      tabindex="0"
      :aria-expanded="!collapsed"
      :aria-label="collapsed ? '展开学习目标（H）' : '折叠学习目标（H）'"
      @click="toggleCollapsed"
      @keydown.enter.prevent="toggleCollapsed"
      @keydown.space.prevent="toggleCollapsed"
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
    </div>
    <!-- 唯一注意力线：个人化「下一步」（数据未就绪时回退冒险地图入口），折叠态也常驻 -->
    <div
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
      <template v-if="primaryStep">
        <button
          type="button"
          class="lop-next__cta"
          data-testid="lop-step-primary"
          :aria-label="primaryStep.label"
          @click="goStep(primaryStep)"
        >
          <span
            class="lop-next__cta-icon"
            aria-hidden="true"
          >▶</span>
          <span class="lop-next__cta-text">{{ primaryStep.label }}</span>
        </button>
        <p class="lop-next__why">
          {{ primaryStep.hint }}
        </p>
      </template>
      <button
        v-else
        type="button"
        class="lop-next__cta"
        data-testid="lop-go-next"
        aria-label="前往冒险地图"
        @click="goMap"
      >
        <span
          class="lop-next__cta-icon"
          aria-hidden="true"
        >▶</span>
        <span class="lop-next__cta-text">去冒险地图</span>
      </button>
    </div>
    <div
      v-show="!collapsed"
      class="lop-body"
    >
      <div class="lop-row">
        <span class="lop-row__label">当前章节</span>
        <span class="lop-row__value lop-row__value--accent">第{{ currentChapter }}章 · {{ currentChapterTitle }}</span>
      </div>
      <div class="lop-row">
        <span class="lop-row__label">本章星级</span>
        <span class="lop-row__value lop-row__value--reward">{{ chapterStarText }} · 总星 {{ totalStars }} / 21</span>
      </div>
      <div
        v-if="secondarySteps.length > 0"
        class="lop-section"
      >
        <span class="lop-section__label">也可以</span>
        <button
          v-for="s in secondarySteps"
          :key="s.kind"
          type="button"
          class="lop-alt"
          :data-testid="`lop-step-${s.kind}`"
          @click="goStep(s)"
        >
          <span class="lop-alt__label">{{ s.label }}</span>
          <span class="lop-alt__hint">{{ s.hint }}</span>
        </button>
      </div>
      <div
        v-else-if="!primaryStep"
        class="lop-section"
      >
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
}
.lop-row__value--reward {
  color: var(--pyt-bar-energy);
  font-weight: 600;
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
  margin-top: 10px;
  padding: 8px 10px;
  background: rgba(241, 196, 83, 0.1);
  border: 1px solid rgba(241, 196, 83, 0.45);
  border-radius: 2px;
}
/* 前台有对话/弹窗时，下一步降为静态提示（不脉冲），只留一条注意力线。 */
.lop-next--quiet .lop-next__badge {
  animation: none;
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

.lop-next__why {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--pyt-text-secondary);
  line-height: 1.4;
}

.lop-alt {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 100%;
  margin-top: 6px;
  padding: 6px 8px;
  background: rgba(0, 0, 0, 0.12);
  border: 1px solid var(--pyt-panel-border);
  border-radius: 2px;
  color: inherit;
  font-family: inherit;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}
.lop-alt:hover {
  border-color: var(--pyt-text-gold);
  color: var(--pyt-text-accent);
}
.lop-alt__hint {
  font-size: 11px;
  color: var(--pyt-text-secondary);
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
  .lop-next__badge {
    animation: none;
  }
}
</style>
