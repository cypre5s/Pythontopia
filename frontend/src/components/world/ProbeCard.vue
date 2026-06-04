<script setup lang="ts">
// ③玩中建画像：世界内认知风格探针卡（中性、可跳过、低风险）。
//
// 触发：watch 主线当前子任务，当其处于「看」阶段（mainSubQuestPhase==='watch'）
// 且该探针尚未回答时弹出。首发只接 cog_intro → cognitive_style（visual/logical/trial）。
// 设计约束（见 docs/plans/2026-06-04-profile-loop-trilogy-design.md §4.1）：
//   - 三选项美术等价、措辞中性、无优劣暗示；可随时跳过；
//   - 提交走 fire-and-forget，失败静默降级，绝不阻塞玩家主线。
// 仅依赖 probe/mainQuest store（只读主线），不改 dialog/mainQuest 状态。
import { ref, watch } from 'vue';
import { useMainQuestStore } from '@stores/mainQuest';
import { useProbeStore } from '@stores/probe';
import { mainSubQuestPhase } from '@/data/mainQuests';

interface ProbeOption {
  /** 提交给后端的 choiceId（与 cognitive_style 取值对齐）。 */
  id: string;
  label: string;
}

const PROBE_KEY = 'cog_intro';

// 视觉等价的三种「入门顺手度」，仅描述偏好、不暗示优劣。
const OPTIONS: readonly ProbeOption[] = [
  { id: 'visual', label: '先看图解' },
  { id: 'logical', label: '先读代码' },
  { id: 'trial', label: '先跑一个最小例子' },
];

const mainQuest = useMainQuestStore();
const probe = useProbeStore();

const visible = ref(false);

function maybeShow(): void {
  const quest = mainQuest.currentSubQuest;
  if (!quest) return;
  if (mainSubQuestPhase(quest).phase !== 'watch') return;
  if (!probe.shouldShow(PROBE_KEY)) return;
  probe.markShown(PROBE_KEY);
  visible.value = true;
}

// 主线推进到新的子任务时复评一次；immediate 让进入世界即评估首个「看」阶段任务。
watch(() => mainQuest.currentSubQuest, maybeShow, { immediate: true });

function choose(choiceId: string): void {
  // 先提交（fire-and-forget，store 内部吞错 + 标记已答），再立即收卡，不阻塞玩家。
  void probe.submit(PROBE_KEY, choiceId);
  visible.value = false;
}

function skip(): void {
  probe.markAnswered(PROBE_KEY);
  visible.value = false;
}
</script>

<template>
  <div
    v-if="visible"
    class="pyt-probe"
    role="dialog"
    aria-modal="true"
    aria-labelledby="pyt-probe-title"
    data-testid="probe-card"
  >
    <div
      class="pyt-probe__backdrop"
      @click="skip"
    />
    <div class="pyt-probe__panel">
      <p class="pyt-probe__eyebrow">
        悄悄话 · 随时可跳过
      </p>
      <h2
        id="pyt-probe-title"
        class="pyt-probe__title"
      >
        初次进镇 · 你更想先怎么入门？
      </h2>
      <p class="pyt-probe__lede">
        没有标准答案，选此刻最顺手的那种就好，小镇会照你的节奏来。
      </p>
      <div class="pyt-probe__options">
        <button
          v-for="opt in OPTIONS"
          :key="opt.id"
          type="button"
          class="pyt-probe__option"
          :data-testid="`probe-option-${opt.id}`"
          @click="choose(opt.id)"
        >
          {{ opt.label }}
        </button>
      </div>
      <button
        type="button"
        class="pyt-probe__skip"
        data-testid="probe-skip"
        @click="skip"
      >
        跳过
      </button>
    </div>
  </div>
</template>

<style scoped>
.pyt-probe {
  position: fixed;
  inset: 0;
  z-index: var(--pyt-z-modal);
  display: flex;
  align-items: center;
  justify-content: center;
}

.pyt-probe__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(28, 43, 24, 0.6);
  cursor: pointer;
}

.pyt-probe__panel {
  position: relative;
  width: min(440px, calc(100vw - 32px));
  padding: var(--pyt-space-5) var(--pyt-space-5) var(--pyt-space-4);
  border: 3px solid var(--pyt-color-text-primary);
  border-radius: var(--pyt-radius-lg);
  background: var(--pyt-color-bg-elevated);
  box-shadow: var(--pyt-shadow-modal);
  animation: pyt-probe-in var(--pyt-motion-slow) ease-out;
}

.pyt-probe__eyebrow {
  margin: 0 0 var(--pyt-space-2);
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-xs);
  letter-spacing: 1px;
  color: var(--pyt-color-text-muted);
}

.pyt-probe__title {
  margin: 0 0 var(--pyt-space-2);
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-xl);
  color: var(--pyt-color-text-primary);
  text-shadow: 2px 2px 0 var(--pyt-color-gold);
}

.pyt-probe__lede {
  margin: 0 0 var(--pyt-space-4);
  font-size: var(--pyt-font-size-sm);
  line-height: 1.6;
  color: var(--pyt-color-text-hint);
}

.pyt-probe__options {
  display: grid;
  gap: var(--pyt-space-2);
  margin-bottom: var(--pyt-space-4);
}

/* 三选项完全同款样式，刻意不区分主次，避免暗示「哪种入门更好」。 */
.pyt-probe__option {
  width: 100%;
  min-height: 44px;
  padding: var(--pyt-space-3);
  border: 2px solid var(--pyt-color-text-primary);
  border-radius: var(--pyt-radius-md);
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-primary);
  font-family: var(--pyt-font-pixel);
  font-size: var(--pyt-font-size-base);
  font-weight: 700;
  cursor: pointer;
  box-shadow: 3px 3px 0 var(--pyt-color-text-primary);
  transition: transform var(--pyt-motion-fast) ease, background var(--pyt-motion-fast) ease;
}

.pyt-probe__option:hover {
  background: var(--pyt-color-gold-bright);
}

.pyt-probe__option:active {
  transform: translate(2px, 2px);
  box-shadow: 1px 1px 0 var(--pyt-color-text-primary);
}

.pyt-probe__skip {
  display: block;
  margin: 0 auto;
  padding: var(--pyt-space-1) var(--pyt-space-3);
  border: none;
  background: transparent;
  color: var(--pyt-color-text-muted);
  font-size: var(--pyt-font-size-sm);
  text-decoration: underline;
  cursor: pointer;
}

.pyt-probe__skip:hover {
  color: var(--pyt-color-text-primary);
}

@keyframes pyt-probe-in {
  from {
    opacity: 0;
    transform: translateY(8px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .pyt-probe__panel {
    animation: none;
  }
  .pyt-probe__option {
    transition: none;
  }
}
</style>
