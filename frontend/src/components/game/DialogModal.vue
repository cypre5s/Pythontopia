<script setup lang="ts">
/**
 * DialogModal.vue —— Pythontopia 升级版对话模态框（W2-dialog-upgrade）。
 *
 * 与 DialogTree.vue 的分工：
 *   - DialogTree.vue：星露谷式 9-slice 底部对话面板，App.vue 全局挂载，
 *     由 store.visible（openWithTree 路径）驱动；
 *   - DialogModal.vue：场景内立绘对话模态框（左右立绘 + 居中文本框），
 *     由 store.isOpen（旧 open(npcKey) 路径）驱动；
 *     兼容旧 lines 占位入口的同时，附带打字机 / 分支选项 / 键盘导航能力。
 *
 * 核心交互：
 *   1. 打字机：默认 30ms/字符，setTimeout 链式逐字推进；
 *   2. 跳过：玩家按 E / Enter / 点击文本 → store.markTypingDone()，立即全显；
 *   3. 分支：打字机完毕后 store.hasBranches=true 时展开选项列表，
 *      支持 ↑/↓ 或 W/S 键移动高亮，Enter / E / 点击确认，调 store.chooseBranch(label)；
 *   4. ESC：随时关闭对话（store.close）；
 *   5. 无分支时（旧占位入口）保留「知道了」按钮兜底关闭。
 *
 * 立绘：
 *   - portraitLeft / portraitRight 通过 props 显式指定（任意 URL）；
 *   - portraitLeft 未传时若 store.npcKey 有值，回退到 /assets/pixel/portraits/portrait_<key>.png
 *     约定路径，让旧 open(npcKey) 调用方零配置展示头像；
 *   - 图片加载失败时隐藏 <img> 但保留版面，避免出现 broken-image 红 X。
 *
 * 测试友好：
 *   - 不依赖 Phaser sound / playSfx，避免 jsdom 环境额外 mock；
 *   - 打字机使用普通 setTimeout，配合 vi.useFakeTimers + advanceTimersByTime 易于推进；
 *   - 所有可断言节点带 data-testid。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useDialogStore, type DialogChoice } from '@stores/dialog';

interface Props {
  /** 左侧立绘 URL（一般为 NPC）；为空时尝试用 store.npcKey 约定路径回退。 */
  portraitLeft?: string;
  /** 右侧立绘 URL（一般为玩家 / 同伴）；为空时不渲染右侧立绘。 */
  portraitRight?: string;
  /** 打字机推进间隔，毫秒。默认 30，符合星露谷阅读节奏。 */
  typingSpeedMs?: number;
}

const props = withDefaults(defineProps<Props>(), {
  portraitLeft: '',
  portraitRight: '',
  typingSpeedMs: 30,
});

const dialog = useDialogStore();

/**
 * 文本来源优先级：currentNode.text > 旧 lines 换行合并 > 空串。
 *
 * 这样既覆盖了未来「场景内立绘对话也走 openWithTree 树」的扩展，
 * 也兼容旧的 open(npcKey) + lines 占位入口。
 */
const fullText = computed<string>(() => {
  const node = dialog.currentNode;
  if (node) return node.text;
  if (dialog.lines.length > 0) return dialog.lines.join('\n');
  return '';
});

const resolvedPortraitLeft = computed<string>(() => {
  if (props.portraitLeft) return props.portraitLeft;
  if (dialog.npcKey) return `/assets/pixel/portraits/portrait_${dialog.npcKey}.png`;
  return '';
});

const resolvedPortraitRight = computed<string>(() => props.portraitRight);

/** 当前 DOM 中已展示的文本（打字机推进的中间结果）。 */
const displayedText = ref('');
const highlightedIndex = ref(0);
let typeTimer: ReturnType<typeof setTimeout> | null = null;

function clearTypeTimer(): void {
  if (typeTimer !== null) {
    clearTimeout(typeTimer);
    typeTimer = null;
  }
}

function startTypewriter(text: string): void {
  clearTypeTimer();
  displayedText.value = '';
  if (text.length === 0) {
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
    typeTimer = setTimeout(tick, props.typingSpeedMs);
  };
  typeTimer = setTimeout(tick, props.typingSpeedMs);
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

onMounted(() => {
  window.addEventListener('keydown', onKeyDown);
  if (dialog.isOpen && fullText.value) {
    startTypewriter(fullText.value);
  }
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeyDown);
  clearTypeTimer();
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
        <span class="name-tag">{{ dialog.npcName || dialog.npcKey }}</span>
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
          {{ choice.label }}
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
  width: 128px;
  height: 160px;
  background-color: rgba(0, 0, 0, 0.35);
  border: 3px solid #d4af37;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  box-shadow: 0 4px 0 rgba(0, 0, 0, 0.45);
}

.dialog-portrait--left {
  align-self: flex-end;
}

.dialog-portrait--right {
  align-self: flex-end;
}

.pixel-portrait {
  width: 100%;
  height: 100%;
  image-rendering: pixelated;
  -ms-interpolation-mode: nearest-neighbor;
  object-fit: cover;
}

.dialog-panel {
  flex: 0 1 640px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px 22px;
  background-color: rgba(56, 39, 18, 0.96);
  background-image: url('/assets/pixel/ui/kenney-adventure/Default/panel_brown.png');
  background-repeat: stretch;
  background-size: 100% 100%;
  border-image-source: url('/assets/pixel/ui/kenney-adventure/Default/panel_brown.png');
  border-image-slice: 24 fill;
  border-image-width: 24px;
  border-image-repeat: stretch;
  border: 24px solid transparent;
  border-radius: 10px;
  color: #fff7e6;
  box-shadow: var(--pyt-shadow-modal, 0 6px 16px rgba(0, 0, 0, 0.4));
}

.dialog-head {
  color: #ffe098;
  font-size: 16px;
  letter-spacing: 0.5px;
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
  font-family: var(--pyt-font-pixel, monospace);
  font-size: 14px;
  color: #2a1a08;
  background-color: #f7d774;
  border: 3px solid #8c6a23;
  padding: 6px 14px;
  border-radius: 4px;
  box-shadow: 0 3px 0 #8c6a23;
  cursor: pointer;
  transition: background-color 120ms ease, transform 80ms ease;
}

.choice-btn:hover,
.choice-btn--highlight {
  background-color: #ffe098;
}

.choice-btn--highlight {
  outline: 2px solid #4d3613;
  outline-offset: 2px;
}

.choice-btn:active {
  transform: translateY(2px);
  box-shadow: 0 1px 0 #8c6a23;
}

.choice-btn:focus-visible {
  outline: 2px solid #4d3613;
  outline-offset: 2px;
}
</style>
