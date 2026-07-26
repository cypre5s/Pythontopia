<script setup lang="ts">
/**
 * 像素对话树面板：由 useDialogStore 的 visible / currentNode 驱动（openWithTree 路径），
 * 渲染 NPC 头像 + 9-slice 面板 + 打字机文本 + 分支选项。
 *
 * 关键约定：
 *   - 9-slice 面板用 border-image 引 Kenney panel_brown.png，缺图退化为实色 + 金边；
 *   - portrait 优先取 store.portrait，缺失或加载失败时占位保留版面，避免 broken-image；
 *   - 打字机为单条 setTimeout 链，切换节点 / 卸载时必须清理，避免上一节点回调泄露。
 */
import { computed, onUnmounted, ref, watch } from 'vue';
import { useDialogStore, type DialogNode } from '@stores/dialog';
import { useSessionStore } from '@stores/session';
import NpcGreetingBanner from '@/components/dialog/NpcGreetingBanner.vue';
import { playSfx } from '@/composables/useAudio';
import { createDialogContext, renderDialogText } from '@/game/dialogTemplate';

const store = useDialogStore();
const session = useSessionStore();

const visible = computed<boolean>(() => store.visible);
const currentNode = computed<DialogNode | null>(() => store.currentNode);
const loading = computed<boolean>(() => store.loading);
const loadError = computed<string | null>(() => store.loadError);

const useFullPortraitFallback = ref(false);

const portraitFileName = computed<string | null>(() => {
  const portrait = store.portrait;
  if (portrait) {
    return portrait.split('/').pop() ?? portrait;
  }
  if (store.npcKey) {
    return `portrait_${store.npcKey}.png`;
  }
  return null;
});

const fullPortraitSrc = computed<string | null>(() => {
  const fileName = portraitFileName.value;
  return fileName ? `/assets/pixel/portraits/${fileName}` : null;
});

const headPortraitSrc = computed<string | null>(() => {
  const fileName = portraitFileName.value;
  if (!fileName) return null;
  const stem = fileName.replace(/\.png$/i, '');
  const headFileName = stem.endsWith('_head') ? `${stem}.png` : `${stem}_head.png`;
  return `/assets/pixel/portraits/heads/${headFileName}`;
});

const portraitSrc = computed<string | null>(() =>
  useFullPortraitFallback.value ? fullPortraitSrc.value : headPortraitSrc.value,
);

const typedText = ref('');
const typing = ref(false);
let typeTimer: ReturnType<typeof setTimeout> | null = null;
const TYPE_INTERVAL_MS = 24;

const templateContext = computed(() => {
  // {{playerName}} / {{stats.*}} / {{profileAdaptive:..}} 交给统一渲染器；
  // store.context 缺失时用 displayName 兜底，stats 取不到走 '—'。
  const ctx = store.context;
  return createDialogContext({
    playerName: ctx?.playerName ?? session.displayName,
    profileType: ctx?.profileType ?? 'cautious',
    cognitiveStyle: ctx?.cognitiveStyle ?? 'unknown',
    stats: ctx?.stats ?? {},
  });
});

const currentText = computed<string>(() => {
  const rawText = currentNode.value?.text ?? '';
  if (!rawText) return '';
  return renderDialogText(rawText, templateContext.value);
});

/** 当前是否停在对话首节点（脚本 root）。首节点的脚本首句常与 AI 寒暄内容重叠。 */
const isRootNode = computed<boolean>(
  () => !!store.tree && store.currentNodeId === store.tree.rootNodeId,
);

/**
 * 首节点遇到 AI 个性化寒暄时，隐藏脚本首句（只保留 ✦ 寒暄 + 分支选项），避免「寒暄 + 脚本首句」
 * 两段重复把右侧撑出框（R-15 反馈）。仅 loading/ready 隐藏（loading 先不显脚本，避免「先显脚本再隐藏」
 * 的闪烁）；fallback/none/idle 时仍显示脚本首句兜底，保证内容不空。非首节点恒显示（知识内容不重复）。
 */
const hideScriptText = computed<boolean>(
  () =>
    isRootNode.value &&
    (store.greetingStatus === 'loading' || store.greetingStatus === 'ready'),
);

/**
 * 可见选项 = store.visibleChoices（已按目标节点 conditions 过滤）+ 模板渲染后的文案。
 *
 * `rawIndex` 必须保持为 `currentNode.choices` 的原始下标：`selectChoice` 只认原始下标，
 * 用过滤后数组的下标会整体错位选到隔壁分支。
 */
const renderedChoices = computed<Array<{ label: string; rawIndex: number }>>(() => {
  if (!currentNode.value) return [];
  const ctx = templateContext.value;
  return store.visibleChoices.map((entry) => ({
    label: renderDialogText(entry.choice.label, ctx),
    rawIndex: entry.rawIndex,
  }));
});

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
  typedText.value = currentText.value;
  typing.value = false;
}

watch(
  [currentText, hideScriptText],
  ([text, hide]) => {
    // 隐藏脚本首句（让位给 AI 寒暄）时不启动打字机，分支选项立即可选（typing=false）。
    if (currentNode.value && !hide) {
      startTypewriter(text);
    } else {
      clearTypeTimer();
      typedText.value = '';
      typing.value = false;
    }
  },
  { immediate: true },
);

watch(
  () => [store.npcKey, store.portrait],
  () => {
    useFullPortraitFallback.value = false;
  },
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
  // 文本已读完但只有单个「可见」选项时，点击空白等同选它（保持星露谷"一直回车推进"手感）。
  // 必须按可见项判断并用其 rawIndex：条件隐藏后剩一项时，硬点 0 会选到被隐藏的那条。
  const only = renderedChoices.value.length === 1 ? renderedChoices.value[0] : undefined;
  if (only) {
    chooseOption(only.rawIndex);
  }
}

function onEscape(): void {
  store.close();
}

function onPortraitError(event: Event): void {
  if (!useFullPortraitFallback.value && fullPortraitSrc.value) {
    useFullPortraitFallback.value = true;
    return;
  }
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
          <header
            class="dialog-head"
            style="color: #111111;"
          >
            <span
              class="name-tag"
              style="color: #111111;"
            >{{ store.npcName || '???' }}</span>
          </header>

          <NpcGreetingBanner />

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
            v-else-if="currentNode && !hideScriptText"
            class="dialog-text"
            style="color: #111111;"
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
              v-for="choice in renderedChoices"
              :key="`${currentNode.id}-${choice.rawIndex}`"
              type="button"
              class="choice-btn pixel-btn"
              @click="chooseOption(choice.rawIndex)"
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
  /* 多弹层重叠时（如暂停菜单上弹出 NPC 对话），全屏遮罩不吞点击：
     对话框本体仍可交互（下方 .dialog-frame 恢复 auto），遮罩外的下层弹层照常可点。 */
  pointer-events: none;
}

.dialog-frame {
  pointer-events: auto;
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
  border-radius: var(--pyt-radius-lg);
  box-shadow: var(--pyt-shadow-modal, 0 6px 16px rgba(0, 0, 0, 0.35));
  color: #111111;
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
  object-fit: contain;
  object-position: center;
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
  color: #111111;
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
  cursor: pointer;
  min-height: 1.75em;
}

.dialog-text--meta {
  color: #111111;
  cursor: default;
  font-style: italic;
}

.dialog-text--error {
  color: #111111;
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

.pixel-btn {
  font-family: var(--pyt-font-pixel);
  font-size: 14px;
  color: #111111;
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
  outline: 2px solid var(--pyt-color-text-primary);
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
