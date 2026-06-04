<script setup lang="ts">
// ②情绪化小镇：世界内「氛围 + 助教台阶」薄层。
//
// 职责（轻量 + 反应式，见 docs/plans/2026-06-04-profile-loop-trilogy-design.md §4.3）：
//   1. 挂载即 refresh()：拉世界情绪状态，驱动 WorldScene 的氛围叠加层（背景级 tint）。
//   2. 连挫导致 mood==='rainy'（低情绪）时，弹出一个「需要我搭把手吗？」的助教台阶——
//      小、可关闭、非阻塞、背景级，绝不打断玩家操作；点「看看助教」跳百晓工坊（导师）。
//   3. 提供「关闭氛围」开关 → setDisabled(true)，玩家可一键关停情绪推断（透明可纠偏）。
// 不渲染天气粒子（那是 weather adapter 的事），本组件只负责 DOM 层的助教台阶与开关。
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useMoodStore } from '@stores/mood';

defineOptions({ name: 'MoodOverlay' });

const TUTOR_ROUTE = '/workshop';

const router = useRouter();
const mood = useMoodStore();

// 本次会话内是否已收起台阶（不持久化：换天/再次连挫仍可再递台阶）。
const dismissed = ref(false);

// 仅在「低情绪（rainy）+ 未关闭氛围 + 未收起」时露出，且 mood 随 refresh 实时变化。
const showNudge = computed(
  () => mood.mood === 'rainy' && !mood.disabled && !dismissed.value,
);

onMounted(() => {
  void mood.refresh();
});

function goToTutor(): void {
  dismissed.value = true;
  void router.push(TUTOR_ROUTE);
}

function dismiss(): void {
  dismissed.value = true;
}

function disableAmbience(): void {
  mood.setDisabled(true);
}
</script>

<template>
  <transition name="mood-nudge-fade">
    <aside
      v-if="showNudge"
      class="mood-nudge"
      data-testid="mood-nudge"
      role="status"
      aria-live="polite"
    >
      <button
        type="button"
        class="mood-nudge__close"
        data-testid="mood-nudge-dismiss"
        aria-label="收起"
        @click="dismiss"
      >
        ×
      </button>
      <p class="mood-nudge__eyebrow">
        小镇下起了细雨
      </p>
      <p class="mood-nudge__title">
        需要我搭把手吗？
      </p>
      <p class="mood-nudge__lede">
        连着几次没过没关系，先看一步台阶，慢慢来。
      </p>
      <div class="mood-nudge__actions">
        <button
          type="button"
          class="mood-nudge__cta"
          data-testid="mood-nudge-help"
          @click="goToTutor"
        >
          看看助教 →
        </button>
        <button
          type="button"
          class="mood-nudge__ghost"
          data-testid="mood-nudge-disable"
          @click="disableAmbience"
        >
          关闭氛围
        </button>
      </div>
    </aside>
  </transition>
</template>

<style scoped>
.mood-nudge {
  position: fixed;
  left: 16px;
  bottom: 16px;
  z-index: var(--pyt-z-popover);
  width: min(300px, calc(100vw - 32px));
  padding: var(--pyt-space-4) var(--pyt-space-4) var(--pyt-space-3);
  background: rgba(28, 24, 40, 0.92);
  border: 2px solid var(--pyt-color-accent);
  border-radius: var(--pyt-radius-lg);
  color: var(--pyt-color-text-on-dark);
  font-family: var(--pyt-font-pixel);
  box-shadow: var(--pyt-shadow-modal);
}

.mood-nudge__close {
  position: absolute;
  top: 4px;
  right: 6px;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  background: transparent;
  color: rgba(255, 247, 230, 0.7);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.mood-nudge__close:hover {
  color: var(--pyt-color-bg-elevated);
}

.mood-nudge__eyebrow {
  margin: 0 0 var(--pyt-space-1);
  font-size: var(--pyt-font-size-xs);
  letter-spacing: 0.5px;
  color: var(--pyt-color-accent-bg);
}

.mood-nudge__title {
  margin: 0 0 var(--pyt-space-1);
  font-size: var(--pyt-font-size-lg);
  font-weight: 700;
  color: var(--pyt-color-gold-bright);
}

.mood-nudge__lede {
  margin: 0 0 var(--pyt-space-3);
  font-size: var(--pyt-font-size-sm);
  line-height: 1.5;
  color: rgba(255, 247, 230, 0.85);
}

.mood-nudge__actions {
  display: flex;
  align-items: center;
  gap: var(--pyt-space-2);
}

.mood-nudge__cta {
  flex: 1;
  min-height: 36px;
  padding: var(--pyt-space-2) var(--pyt-space-3);
  border: 2px solid var(--pyt-color-gold-bright);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-accent);
  color: var(--pyt-color-bg-elevated);
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-sm);
  font-weight: 700;
  cursor: pointer;
  transition: transform var(--pyt-motion-fast) ease;
}

.mood-nudge__cta:hover {
  transform: translateY(-1px);
}

.mood-nudge__ghost {
  padding: var(--pyt-space-1) var(--pyt-space-2);
  border: none;
  background: transparent;
  color: rgba(255, 247, 230, 0.7);
  font-size: var(--pyt-font-size-xs);
  text-decoration: underline;
  cursor: pointer;
}

.mood-nudge__ghost:hover {
  color: var(--pyt-color-bg-elevated);
}

.mood-nudge-fade-enter-active,
.mood-nudge-fade-leave-active {
  transition: opacity var(--pyt-motion-slow) ease, transform var(--pyt-motion-slow) ease;
}

.mood-nudge-fade-enter-from {
  opacity: 0;
  transform: translateY(12px);
}

.mood-nudge-fade-leave-to {
  opacity: 0;
  transform: translateY(12px);
}

@media (prefers-reduced-motion: reduce) {
  .mood-nudge-fade-enter-active,
  .mood-nudge-fade-leave-active {
    transition: opacity var(--pyt-motion-base) linear;
  }
  .mood-nudge-fade-enter-from,
  .mood-nudge-fade-leave-to {
    transform: none;
  }
  .mood-nudge__cta {
    transition: none;
  }
}
</style>
