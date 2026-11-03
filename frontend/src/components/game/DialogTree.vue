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

/**
 * 立绘 URL。
 *
 * `store.portrait` 是相对 `/assets/pixel/portraits/` 的路径，现在由 `data/npcs/<key>/meta.json`
 * 统一给出 `galgame/<npcKey>.png`（R-14 换图后旧 LPC 头像与 SRW 角色完全两套画风，
 * 用户实机反馈「对话框里的人和地图上的人不是同一个人」）。
 *
 * 换图不需要改代码：
 *   - 占位图 = `python scripts/portraits.py placeholders`（SRW 角色表放大，同源）
 *   - 成品图 = `python scripts/portraits.py import <图> <npcKey>`（规格化成 768×1024 覆盖同名文件）
 */
const portraitSrc = computed<string | null>(() =>
  store.portrait ? `/assets/pixel/portraits/${store.portrait}` : null,
);
/** 立绘加载失败时直接不渲染 <img>，而不是留一个 broken-image 占位框。 */
const portraitFailed = ref(false);
watch(portraitSrc, () => {
  portraitFailed.value = false;
});

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

function onPortraitError(): void {
  // 不再做「头像 → 全身图」二级回退：新约定下只有一张立绘文件。图拉不到时
  // 直接收起，避免 broken-image 图标和空金框挡住版面（文本/选项照常可用）。
  portraitFailed.value = true;
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
      @keydown.esc.stop.prevent="onEscape"
    >
      <div class="dialog-stage">
        <img
          v-if="portraitSrc && !portraitFailed"
          :src="portraitSrc"
          :alt="store.npcName"
          class="dialog-portrait pixel-portrait"
          @error="onPortraitError"
        >
      </div>
      <div class="dialog-frame pyt-pixel-text">
        <header
          class="dialog-head"
          style="color: #111111;"
        >
          <span
            class="name-tag"
            style="color: #111111;"
          >{{ store.npcName || '???' }}</span>
        </header>
        <section class="dialog-body">
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
  /* galgame 版式：下三分之二压暗把视线收到立绘与对话条上；
     全黑遮罩会把像素小镇整个吃掉，暖棕渐变保留上半屏看场景。 */
  background: linear-gradient(
    to bottom,
    rgba(34, 24, 14, 0.28) 0%,
    rgba(34, 24, 14, 0.62) 62%,
    rgba(34, 24, 14, 0.78) 100%
  );
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  padding: 0 24px 24px;
  z-index: var(--pyt-z-modal, 1000);
  /* 多弹层重叠时（如暂停菜单上弹出 NPC 对话），全屏遮罩不吞点击：
     对话框本体仍可交互（下方 .dialog-frame 恢复 auto），遮罩外的下层弹层照常可点。 */
  pointer-events: none;
}

/* 立绘舞台：与对话条同宽，立绘贴底、靠左，脚底与对话条上沿齐平。 */
.dialog-stage {
  width: min(1080px, 100%);
  display: flex;
  align-items: flex-end;
  justify-content: flex-start;
  padding-left: 28px;
  pointer-events: none;
}

.dialog-portrait {
  display: block;
  height: min(56vh, 512px);
  width: auto;
  max-width: 46vw;
  /* 立绘运行时高 512px（成品 1024 高 → 正好 1/2 整数缩，最近邻不失真）；
     宽高比由生图比例定（常见 9:16），所以 width: auto。 */
  image-rendering: pixelated;
  -ms-interpolation-mode: nearest-neighbor;
  filter: drop-shadow(0 4px 0 rgba(0, 0, 0, 0.28));
}

.dialog-frame {
  position: relative;
  pointer-events: auto;
  width: min(1080px, 100%);
  min-height: 180px;
  padding: 26px 30px 22px;
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

/* 名字牌：压在对话条上沿的小铜牌（galgame 标准位置），跨在 24px 边框外沿上。 */
.dialog-head {
  position: absolute;
  left: 26px;
  top: -38px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  padding: 3px 16px;
  background-color: #f2d8a8;
  border: 3px solid #8c6a23;
  border-radius: 5px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.35);
  color: #111111;
  font-size: 16px;
  letter-spacing: 0.5px;
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  /* 正文收成一列并在对话条内水平居中：面板宽 1080 时，
     若正文贴左排，右侧会空出一大块（用户实机反馈「都堆到左边」）。 */
  width: 100%;
  max-width: 860px;
  margin: 0 auto;
  min-width: 0;
}

.name-tag {
  font-weight: 600;
}

.dialog-text {
  margin: 0;
  font-size: 15px;
  line-height: 1.75;
  text-align: center;
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
  justify-content: center;
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
