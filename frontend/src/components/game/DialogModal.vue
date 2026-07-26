<script setup lang="ts">
/**
 * 场景内立绘对话模态框（左右立绘 + 居中文本框），由 store.isOpen 驱动（open(npcKey) 路径），
 * 兼容旧 lines 占位入口，附带打字机 / 分支选项 / 键盘导航。
 *
 * 与 DialogTree.vue 分工：DialogTree 是 store.visible（openWithTree 路径）驱动的底部 9-slice 面板，
 * 两者共用同一 dialog store，但入口与版式不同。
 *
 * <b>当前状态</b>：本组件挂在 WorldPage.vue，但仅在 `store.isOpen`（= openNpcKey 非空，旧 lines 入口）
 * 时渲染，而生产代码目前没有任何地方写 openNpcKey——所有 NPC 交互都走 openWithTree/DialogTree。
 * 保留而不删除的理由：挂载点 WorldPage.vue 与本组件不属同一改动范围，删组件会留下悬空 import；
 * 且它是旧 lines 占位入口的唯一渲染器。为避免两条 UI 路径的条件过滤各写各的而漂移，
 * 选项过滤已统一收敛到 store：本组件读的 `branchChoices` 由 `markTypingDone` 从
 * `store.visibleChoices` 快照而来，与 DialogTree 渲染的是同一份过滤结果。
 *
 * 关键约定：
 *   - 键盘：E/Enter 跳过打字机或确认选项，↑/↓ 或 W/S 切换高亮，ESC 关闭；
 *   - 立绘 portraitLeft/Right 经 props 指定；portraitLeft 缺省且有 npcKey 时回退到
 *     /assets/pixel/portraits/heads/portrait_<key>_head.png，加载失败隐藏 <img> 但保留版面；
 *   - 打字机用普通 setTimeout（不依赖 Phaser sound），便于 vi.useFakeTimers 推进。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useDialogStore, type DialogChoice } from '@stores/dialog';
import { useGameSettingsStore, type TextSpeed } from '@stores/gameSettings';
import { useSessionStore } from '@stores/session';
import { createDialogContext, renderDialogText } from '@/game/dialogTemplate';

interface Props {
  /** 左侧立绘 URL（一般为 NPC）；为空时尝试用 store.npcKey 约定路径回退。 */
  portraitLeft?: string;
  /** 右侧立绘 URL（一般为玩家 / 同伴）；为空时不渲染右侧立绘。 */
  portraitRight?: string;
  /**
   * 打字机推进间隔，毫秒。显式传入时覆盖玩家全局文字速度设置；
   * 未传入时回退到 gameSettings.education.textSpeed（默认 normal=35ms）。
   * 故意不设默认值：undefined 表示「跟随全局设置」，且 exactOptionalPropertyTypes
   * 下 withDefaults 不允许显式 undefined 默认值。
   */
  // eslint-disable-next-line vue/require-default-prop
  typingSpeedMs?: number;
}

const props = withDefaults(defineProps<Props>(), {
  portraitLeft: '',
  portraitRight: '',
});

const dialog = useDialogStore();
const session = useSessionStore();
const gameSettings = useGameSettingsStore();

const SPEED_MAP: Record<TextSpeed, number> = {
  slow: 60, normal: 35, fast: 15, instant: 0,
};
// 优先级：显式 prop > 玩家全局文字速度设置 > 30ms 兜底。
// 生产中无调用方传 prop，行为等同读 gameSettings；prop 主要给测试/特殊场景做确定性覆盖。
const effectiveTypingMs = computed(() =>
  props.typingSpeedMs ?? SPEED_MAP[gameSettings.education.textSpeed] ?? 30,
);

/**
 * 与 DialogTree 共用同一渲染上下文：旧 lines 入口多为纯文本，渲染器对其是 no-op；
 * 新对话树入口的 stats / profileAdaptive 占位符会被实际替换。
 */
const templateContext = computed(() => {
  const ctx = dialog.context;
  return createDialogContext({
    playerName: ctx?.playerName ?? session.displayName,
    profileType: ctx?.profileType ?? 'cautious',
    cognitiveStyle: ctx?.cognitiveStyle ?? 'unknown',
    stats: ctx?.stats ?? {},
  });
});

/** 文本来源优先级：currentNode.text > 旧 lines 换行合并 > 空串（兼容树入口与旧 lines 入口）。 */
const fullText = computed<string>(() => {
  const node = dialog.currentNode;
  const raw = node ? node.text : dialog.lines.length > 0 ? dialog.lines.join('\n') : '';
  if (!raw) return '';
  return renderDialogText(raw, templateContext.value);
});

const renderedBranchLabels = computed<string[]>(() =>
  dialog.branchChoices.map((c) => renderDialogText(c.label, templateContext.value)),
);

const resolvedPortraitLeft = computed<string>(() => {
  if (props.portraitLeft) return props.portraitLeft;
  if (dialog.npcKey) {
    // 原 portrait 是 LPC sprite sheet（48x72，3x3 朝向×帧），直接 <img> 会显示整张 9 个小头；
    // 改用 crop_portrait_heads.py 预切的单帧头像（heads/*_head.png，左上 idle-down 帧 2x 放大）。
    return `/assets/pixel/portraits/heads/portrait_${dialog.npcKey}_head.png`;
  }
  return '';
});

const resolvedPortraitRight = computed<string>(() => props.portraitRight);

/** 当前 DOM 中已展示的文本（打字机推进的中间结果）。 */
const displayedText = ref('');
const highlightedIndex = ref(0);
let typeTimer: ReturnType<typeof setTimeout> | null = null;
let autoAdvanceTimer: ReturnType<typeof setTimeout> | null = null;

function clearTypeTimer(): void {
  if (typeTimer !== null) {
    clearTimeout(typeTimer);
    typeTimer = null;
  }
}

function clearAutoAdvanceTimer(): void {
  if (autoAdvanceTimer !== null) {
    clearTimeout(autoAdvanceTimer);
    autoAdvanceTimer = null;
  }
}

function startTypewriter(text: string): void {
  clearTypeTimer();
  clearAutoAdvanceTimer();
  displayedText.value = '';
  const delay = effectiveTypingMs.value;
  if (text.length === 0 || delay === 0) {
    displayedText.value = text;
    dialog.markTypingDone();
    return;
  }
  let i = 0;
  const tick = (): void => {
    i += 1;
    displayedText.value = text.substring(0, i);
    if (i >= text.length) {
      typeTimer = null;
      dialog.markTypingDone();
      return;
    }
    typeTimer = setTimeout(tick, delay);
  };
  typeTimer = setTimeout(tick, delay);
}

function skipTypewriter(): void {
  clearTypeTimer();
  displayedText.value = fullText.value;
  dialog.markTypingDone();
}

function onTextClick(): void {
  if (!dialog.isTypingDone) {
    skipTypewriter();
  }
}

function onChooseBranch(choice: DialogChoice): void {
  dialog.chooseBranch(choice.label);
}

function onCloseButton(): void {
  dialog.close();
}

function isAdvanceKey(key: string): boolean {
  return key === 'Enter' || key === 'e' || key === 'E';
}

function isUpKey(key: string): boolean {
  return key === 'ArrowUp' || key === 'w' || key === 'W';
}

function isDownKey(key: string): boolean {
  return key === 'ArrowDown' || key === 's' || key === 'S';
}

function onKeyDown(e: KeyboardEvent): void {
  if (!dialog.isOpen) return;
  const key = e.key;
  if (key === 'Escape') {
    e.preventDefault();
    dialog.close();
    return;
  }
  if (isAdvanceKey(key)) {
    if (!dialog.isTypingDone) {
      e.preventDefault();
      skipTypewriter();
      return;
    }
    if (dialog.hasBranches) {
      const choice = dialog.branchChoices[highlightedIndex.value];
      if (choice) {
        e.preventDefault();
        onChooseBranch(choice);
      }
    }
    return;
  }
  if (!dialog.hasBranches) return;
  const len = dialog.branchChoices.length;
  if (len === 0) return;
  if (isUpKey(key)) {
    e.preventDefault();
    highlightedIndex.value = (highlightedIndex.value - 1 + len) % len;
  } else if (isDownKey(key)) {
    e.preventDefault();
    highlightedIndex.value = (highlightedIndex.value + 1) % len;
  }
}

function onPortraitError(event: Event): void {
  const target = event.target as HTMLImageElement | null;
  if (target) {
    target.style.visibility = 'hidden';
  }
}

watch(fullText, (text, oldText) => {
  if (text === oldText) return;
  if (!dialog.isOpen) return;
  highlightedIndex.value = 0;
  startTypewriter(text);
});

watch(
  () => dialog.isOpen,
  (open, wasOpen) => {
    if (open && !wasOpen) {
      highlightedIndex.value = 0;
      startTypewriter(fullText.value);
    } else if (!open) {
      clearTypeTimer();
      clearAutoAdvanceTimer();
      displayedText.value = '';
      highlightedIndex.value = 0;
    }
  },
);

watch(
  () => dialog.branchChoices.length,
  (len) => {
    if (highlightedIndex.value >= len) {
      highlightedIndex.value = 0;
    }
  },
);

watch(
  () => dialog.isTypingDone,
  (done) => {
    clearAutoAdvanceTimer();
    if (done && !dialog.hasBranches && gameSettings.education.autoAdvanceDialog) {
      autoAdvanceTimer = setTimeout(() => {
        autoAdvanceTimer = null;
        dialog.close();
      }, gameSettings.education.autoAdvanceDelayMs);
    }
  },
);

onMounted(() => {
  window.addEventListener('keydown', onKeyDown);
  if (dialog.isOpen && fullText.value) {
    startTypewriter(fullText.value);
  }
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeyDown);
  clearTypeTimer();
  clearAutoAdvanceTimer();
});
</script>

<template>
  <section
    v-if="dialog.isOpen"
    class="dialog-modal"
    role="dialog"
    aria-modal="true"
    aria-live="polite"
    data-testid="dialog-modal"
  >
    <aside
      v-if="resolvedPortraitLeft"
      class="dialog-portrait dialog-portrait--left"
      data-testid="dialog-portrait-left"
    >
      <img
        :src="resolvedPortraitLeft"
        :alt="dialog.npcName || dialog.npcKey || 'NPC'"
        class="pixel-portrait"
        @error="onPortraitError"
      >
    </aside>

    <div
      class="dialog-panel pyt-pixel-text"
      data-testid="dialog-panel"
    >
      <header
        v-if="dialog.npcName || dialog.npcKey"
        class="dialog-head"
      >
        <span
          class="name-tag"
        >{{ dialog.npcName || dialog.npcKey }}</span>
      </header>

      <p
        class="dialog-text"
        data-testid="dialog-text"
        @click="onTextClick"
      >
        {{ displayedText
        }}<span
          v-if="!dialog.isTypingDone"
          class="typing-cursor"
          aria-hidden="true"
        >▏</span>
      </p>

      <div
        v-if="dialog.isTypingDone && dialog.hasBranches"
        class="dialog-choices"
        data-testid="dialog-choices"
      >
        <button
          v-for="(choice, idx) in dialog.branchChoices"
          :key="`${idx}-${choice.label}`"
          type="button"
          class="choice-btn"
          :class="{ 'choice-btn--highlight': idx === highlightedIndex }"
          :data-testid="`dialog-choice-${idx}`"
          @click="onChooseBranch(choice)"
          @mouseenter="highlightedIndex = idx"
        >
          {{ renderedBranchLabels[idx] ?? choice.label }}
        </button>
      </div>

      <div
        v-else-if="dialog.isTypingDone && !dialog.hasBranches"
        class="dialog-choices"
      >
        <button
          type="button"
          class="choice-btn"
          data-testid="dialog-close-btn"
          @click="onCloseButton"
        >
          知道了
        </button>
      </div>
    </div>

    <aside
      v-if="resolvedPortraitRight"
      class="dialog-portrait dialog-portrait--right"
      data-testid="dialog-portrait-right"
    >
      <img
        :src="resolvedPortraitRight"
        alt="同伴"
        class="pixel-portrait"
        @error="onPortraitError"
      >
    </aside>
  </section>
</template>

<style scoped>
.dialog-modal {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 16px;
  padding: 0 24px 48px 24px;
  pointer-events: none;
  z-index: var(--pyt-z-modal, 1000);
}

.dialog-modal > * {
  pointer-events: auto;
}

.dialog-portrait {
  flex: 0 0 auto;
  width: 72px;
  height: 108px;
  background-color: var(--pyt-panel-bg);
  border: 3px solid var(--pyt-panel-border);
  border-radius: 2px;
  overflow: hidden;
  display: grid;
  place-items: center;
  box-shadow: 4px 4px 0 var(--pyt-panel-border-dark);
}

.dialog-portrait--left,
.dialog-portrait--right {
  align-self: flex-end;
}

.pixel-portrait {
  width: 100%;
  height: 100%;
  image-rendering: pixelated;
  image-rendering: crisp-edges;
  -ms-interpolation-mode: nearest-neighbor;
  object-fit: contain;
  object-position: center bottom;
}

.dialog-panel {
  flex: 1 1 auto;
  width: min(640px, calc(100vw - 240px));
  max-width: calc(100vw - 240px);
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 20px 28px;
  background: var(--pyt-panel-bg);
  border: 3px solid var(--pyt-panel-border);
  border-radius: 2px;
  color: var(--pyt-text-primary);
  box-shadow: 4px 4px 0 var(--pyt-panel-border-dark), 0 8px 24px var(--pyt-panel-shadow);
  image-rendering: pixelated;
}

.dialog-head {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--pyt-text-accent);
  font-size: 16px;
  letter-spacing: 0.5px;
  font-weight: 700;
  border-bottom: 2px solid var(--pyt-panel-border);
  padding-bottom: 6px;
  text-shadow: 1px 1px 0 var(--pyt-color-shadow);
}

@media (max-width: 760px) {
  .dialog-modal {
    padding: 0 12px 24px;
    gap: 8px;
  }
  .dialog-panel {
    width: calc(100vw - 32px);
    max-width: calc(100vw - 32px);
    padding: 12px 16px;
  }
  .dialog-portrait {
    width: 48px;
    height: 72px;
  }
}

.name-tag {
  font-weight: 600;
}

.dialog-text {
  margin: 0;
  font-size: 15px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
  min-height: 1.75em;
  cursor: pointer;
  color: var(--pyt-text-primary);
}

.typing-cursor {
  margin-left: 2px;
  animation: dialog-cursor-blink 0.6s steps(1) infinite;
}

@keyframes dialog-cursor-blink {
  0%, 50% {
    opacity: 1;
  }
  51%, 100% {
    opacity: 0;
  }
}

.dialog-choices {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.choice-btn {
  font-family: var(--pyt-font-pixel);
  font-size: 14px;
  color: var(--pyt-text-primary);
  background: var(--pyt-btn-bg);
  border: 2px solid var(--pyt-btn-border);
  padding: 6px 14px;
  border-radius: 2px;
  box-shadow: 2px 2px 0 var(--pyt-panel-border-dark);
  cursor: pointer;
  transition: background 120ms ease, transform 80ms ease;
}

.choice-btn:hover,
.choice-btn--highlight {
  background: var(--pyt-btn-hover-bg);
}

.choice-btn--highlight {
  outline: 2px solid var(--pyt-panel-border);
  outline-offset: 2px;
}

.choice-btn:active {
  transform: translateY(2px);
  box-shadow: 0 0 0 var(--pyt-panel-border-dark);
}

.choice-btn:focus-visible {
  outline: 2px solid var(--pyt-panel-border);
  outline-offset: 2px;
}
</style>
