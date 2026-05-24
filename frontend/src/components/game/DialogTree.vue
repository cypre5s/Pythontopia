<script setup lang="ts">
/**
 * DialogTree.vue —— Pythontopia 像素对话树组件。
 *
 * 职责：
 *   - 监听 useDialogStore 的对话树状态，渲染 NPC 头像 + 9-slice 面板 + 选项；
 *   - 打字机逐字渲染当前节点 text，期间允许玩家点击 / 回车跳过到完整文本；
 *   - 文本播放完后展示选项按钮，点击调用 store.selectChoice；
 *   - ESC 直接关闭对话（store.close）。
 *
 * 视觉约定：
 *   - 9-slice 面板用 CSS border-image 引用 Kenney UI Pack 的 panel_brown.png，
 *     无该图时退化为深棕色实色 + 金色描边，不会破坏阅读；
 *   - portrait 路径优先取 store.portrait（meta.json 中 portrait 字段），
 *     缺失或加载失败时用空区块占位（保留版面），避免红 X；
 *   - 字体使用 var(--pyt-font-pixel)，与游戏画布统一像素观感。
 *
 * 性能：
 *   - 打字机使用单一 setTimeout 链，组件卸载时清理；
 *   - 切换节点时强制重置 typedText 与 timer，避免上一节点尾音泄露。
 */
import { computed, onUnmounted, ref, watch } from 'vue';
import { useDialogStore, type DialogNode } from '@stores/dialog';
import { playSfx } from '@/composables/useAudio';

const store = useDialogStore();

const visible = computed<boolean>(() => store.visible);
const currentNode = computed<DialogNode | null>(() => store.currentNode);
const loading = computed<boolean>(() => store.loading);
const loadError = computed<string | null>(() => store.loadError);

const heartDisplay = computed<string>(() => {
  const f = store.friendship;
  if (f <= 0) return '';
  const full = Math.min(Math.floor(f / 10), 10);
  const remainder = f % 10;
  if (full >= 10) return '❤'.repeat(10);
  return '❤'.repeat(full) + (remainder > 0 ? ` +${remainder}` : '');
});

const portraitSrc = computed<string | null>(() => {
  if (!store.portrait) return null;
  return `/assets/pixel/portraits/${store.portrait}`;
});

const typedText = ref('');
const typing = ref(false);
let typeTimer: ReturnType<typeof setTimeout> | null = null;
const TYPE_INTERVAL_MS = 24;

function clearTypeTimer(): void {
  if (typeTimer !== null) {
    clearTimeout(typeTimer);
    typeTimer = null;
  }
}

function startTypewriter(text: string): void {
  clearTypeTimer();
  typedText.value = '';
  typing.value = true;
  if (text.length === 0) {
    typing.value = false;
    return;
  }
  let i = 0;
  const tick = (): void => {
    i += 1;
    typedText.value = text.substring(0, i);
    // 轻量打字机声：在 jsdom / 无 Phaser game 环境下 playSfx 自动 no-op。
    if (i % 3 === 0) {
      playSfx('sfx_click', { volume: 0.04 });
    }
    if (i >= text.length) {
      typing.value = false;
      typeTimer = null;
      return;
    }
    typeTimer = setTimeout(tick, TYPE_INTERVAL_MS);
  };
  typeTimer = setTimeout(tick, TYPE_INTERVAL_MS);
}

function finishTypewriter(): void {
  clearTypeTimer();
  const text = currentNode.value?.text ?? '';
  typedText.value = text;
  typing.value = false;
}

watch(
  currentNode,
  (node) => {
    if (node) {
      startTypewriter(node.text);
    } else {
      clearTypeTimer();
      typedText.value = '';
      typing.value = false;
    }
  },
  { immediate: true },
);

onUnmounted(() => {
  clearTypeTimer();
});

function chooseOption(idx: number): void {
  if (typing.value) {
    finishTypewriter();
    return;
  }
  playSfx('sfx_success', { volume: 0.3 });
  store.selectChoice(idx);
}

function onAdvance(): void {
  if (typing.value) {
    finishTypewriter();
    return;
  }
  // 文本已读完但只有单选项时，点击空白等同选第一项（保持星露谷"一直回车推进"手感）。
  if (currentNode.value && currentNode.value.choices.length === 1) {
    chooseOption(0);
  }
}

function onEscape(): void {
  store.close();
}

function onPortraitError(event: Event): void {
  const target = event.target as HTMLImageElement | null;
  if (target) {
    target.style.visibility = 'hidden';
  }
}
</script>

<template>
  <transition name="dialog-fade">
    <div
      v-if="visible"
      class="dialog-overlay"
      role="dialog"
      aria-modal="true"
      tabindex="-1"
      @click.self="onAdvance"
      @keydown.esc.prevent="onEscape"
    >
      <div class="dialog-frame pyt-pixel-text">
        <aside
          v-if="portraitSrc"
          class="dialog-portrait"
        >
          <img
            :src="portraitSrc"
            :alt="store.npcName"
            class="pixel-portrait"
            @error="onPortraitError"
          >
        </aside>
        <section class="dialog-body">
          <header class="dialog-head">
            <span class="name-tag">{{ store.npcName || '???' }}</span>
            <span
              v-if="store.friendship > 0"
              class="friendship"
              :aria-label="`好感度 ${store.friendship}`"
              data-testid="dialog-friendship"
            >{{ heartDisplay }}</span>
          </header>

          <p
            v-if="loading"
            class="dialog-text dialog-text--meta"
            data-testid="dialog-loading"
          >
            加载中……
          </p>

          <p
            v-else-if="loadError"
            class="dialog-text dialog-text--meta dialog-text--error"
            data-testid="dialog-error"
          >
            {{ loadError }}
          </p>

          <p
            v-else-if="currentNode"
            class="dialog-text"
            data-testid="dialog-text"
            @click="onAdvance"
          >
            {{ typedText
            }}<span
              v-if="typing"
              class="typing-cursor"
              aria-hidden="true"
            >▏</span>
          </p>

          <div
            v-if="loadError"
            class="dialog-choices"
          >
            <button
              type="button"
              class="choice-btn pixel-btn"
              @click="onEscape"
            >
              关闭
            </button>
          </div>

          <div
            v-else-if="currentNode && !typing"
            class="dialog-choices"
            data-testid="dialog-choices"
          >
            <button
              v-for="(choice, idx) in currentNode.choices"
              :key="`${currentNode.id}-${idx}`"
              type="button"
              class="choice-btn pixel-btn"
              @click="chooseOption(idx)"
            >
              {{ choice.label }}
            </button>
          </div>
        </section>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(34, 24, 14, 0.45);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 0 24px 48px 24px;
  z-index: var(--pyt-z-modal, 1000);
}

.dialog-frame {
  display: flex;
  gap: 16px;
  width: min(820px, 100%);
  padding: 18px 20px;
  /* 9-slice：Kenney UI panel_brown.png 拉伸边框；图缺失时由 background-color 兜底。 */
  background-color: rgba(56, 39, 18, 0.96);
  background-image: url('/assets/pixel/ui/kenney-adventure/Default/panel_brown.png');
  background-repeat: stretch;
  background-size: 100% 100%;
  border-image-source: url('/assets/pixel/ui/kenney-adventure/Default/panel_brown.png');
  border-image-slice: 24 fill;
  border-image-width: 24px;
  border-image-repeat: stretch;
  border: 24px solid transparent;
  border-radius: 12px;
  box-shadow: var(--pyt-shadow-modal, 0 6px 16px rgba(0, 0, 0, 0.35));
  color: #fff7e6;
}

.dialog-portrait {
  flex: 0 0 auto;
  width: 96px;
  height: 96px;
  background-color: rgba(0, 0, 0, 0.25);
  border: 3px solid #d4af37;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pixel-portrait {
  width: 100%;
  height: 100%;
  image-rendering: pixelated;
  -ms-interpolation-mode: nearest-neighbor;
  object-fit: cover;
}

.dialog-body {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #ffe098;
  font-size: 16px;
  letter-spacing: 0.5px;
}

.name-tag {
  font-weight: 600;
}

.friendship {
  font-size: 13px;
  color: #ffb3b3;
  background: rgba(255, 255, 255, 0.08);
  padding: 2px 8px;
  border-radius: 999px;
}

.dialog-text {
  margin: 0;
  font-size: 15px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
  cursor: pointer;
  min-height: 1.75em;
}

.dialog-text--meta {
  color: #ffe098;
  cursor: default;
  font-style: italic;
}

.dialog-text--error {
  color: #ffb3b3;
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

/* Kenney UI 风按钮：黄棕底 + 深棕粗边，hover 提亮。 */
.pixel-btn {
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

.pixel-btn:hover {
  background-color: #ffe098;
}

.pixel-btn:active {
  transform: translateY(2px);
  box-shadow: 0 1px 0 #8c6a23;
}

.pixel-btn:focus-visible {
  outline: 2px solid #4d3613;
  outline-offset: 2px;
}

.dialog-fade-enter-active,
.dialog-fade-leave-active {
  transition: opacity 200ms ease;
}

.dialog-fade-enter-from,
.dialog-fade-leave-to {
  opacity: 0;
}
</style>
