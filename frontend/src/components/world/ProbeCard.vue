<script setup lang="ts">
// ③玩中建画像：世界内探针卡（中性、可跳过、低风险）。数据驱动 PROBE_CATALOG，
// 一张组件承载多类探针，但任一时刻只展示一张：
//   - cog_intro（trigger 'watch-phase'）：进入主线「看」阶段时弹 → cognitive_style；
//   - fail_react（trigger 'requested'）：练习失败后由 learning.submitToServer 置 requested
//     标记（失败发生在训练页、探针卡在世界页，借 store 跨页传信号），玩家回到世界即弹
//     → emotion_persistence。
// 设计约束（见 docs/plans/2026-06-04-profile-loop-trilogy-design.md §4.1）：
//   - 选项美术等价、措辞中性、无优劣暗示；可随时跳过；
//   - 提交走 fire-and-forget，失败静默降级，绝不阻塞玩家主线。
// 仅依赖 probe/mainQuest/dialog store（只读主线），不改 dialog/mainQuest 状态。
import { computed, ref, watch } from 'vue';
import { useMainQuestStore } from '@stores/mainQuest';
import { useProbeStore } from '@stores/probe';
import { useDialogStore } from '@stores/dialog';
import { mainSubQuestPhase } from '@/data/mainQuests';
import { PROBE_CATALOG, REQUESTED_PROBES } from '@/data/probeCatalog';

// C4：首次引导（帮助弹窗 + 镇长对话）期间由父级 WorldPage 置 hold，避免探针叠成第三层弹窗。
const props = withDefaults(defineProps<{ hold?: boolean }>(), { hold: false });

// 进入主线「看」阶段触发的探针 key（首发只有 cog_intro）。
const WATCH_PROBE_KEY = 'cog_intro';

const mainQuest = useMainQuestStore();
const probe = useProbeStore();
const dialog = useDialogStore();

// 当前正在展示的探针 key（一次只展示一张卡）；null 表示无卡。
const activeKey = ref<string | null>(null);
const activeProbe = computed(() =>
  activeKey.value ? PROBE_CATALOG[activeKey.value] ?? null : null,
);

function show(key: string): void {
  probe.markShown(key);
  activeKey.value = key;
}

// 统一复评入口：各触发源都走这里。一次只弹一张；引导期（hold）/对话在前台时先压住。
function reevaluate(): void {
  if (activeKey.value) return;
  if (props.hold) return;
  if (dialog.visible) return;
  // 1) 外部事件请求的探针优先（如练习失败 → fail_react）。
  for (const def of REQUESTED_PROBES) {
    if (probe.isRequested(def.key) && probe.shouldShow(def.key)) {
      show(def.key);
      return;
    }
  }
  // 2) 主线「看」阶段触发的探针（cog_intro）。
  const quest = mainQuest.currentSubQuest;
  if (quest && mainSubQuestPhase(quest).phase === 'watch' && probe.shouldShow(WATCH_PROBE_KEY)) {
    show(WATCH_PROBE_KEY);
  }
}

// 主线推进 / 引导结束 / 对话关闭 / 探针被请求后都复评一次。
// immediate：进入世界即评估首个「看」阶段任务，并兜底「回到世界时已有 requested 探针待弹」。
watch(() => mainQuest.currentSubQuest, reevaluate, { immediate: true });
watch(() => props.hold, reevaluate);
watch(() => dialog.visible, reevaluate);
// requested 在已挂载期间被置位时（如停留世界内时收到失败信号）也即时复评。
watch(() => probe.requested.size, reevaluate);

function choose(choiceId: string): void {
  const key = activeKey.value;
  if (!key) return;
  // 先提交（fire-and-forget，store 内部吞错 + 标记已答），再立即收卡，不阻塞玩家。
  void probe.submit(key, choiceId);
  activeKey.value = null;
}

function dismiss(): void {
  const key = activeKey.value;
  if (!key) return;
  probe.markAnswered(key);
  activeKey.value = null;
}
</script>

<template>
  <div
    v-if="activeProbe"
    class="pyt-probe"
    role="dialog"
    aria-modal="true"
    aria-labelledby="pyt-probe-title"
    data-testid="probe-card"
    :data-probe-key="activeProbe.key"
  >
    <div
      class="pyt-probe__backdrop"
      @click="dismiss"
    />
    <div class="pyt-probe__panel">
      <p class="pyt-probe__eyebrow">
        {{ activeProbe.eyebrow }}
      </p>
      <h2
        id="pyt-probe-title"
        class="pyt-probe__title"
      >
        {{ activeProbe.title }}
      </h2>
      <p class="pyt-probe__lede">
        {{ activeProbe.lede }}
      </p>
      <div class="pyt-probe__options">
        <button
          v-for="opt in activeProbe.options"
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
        @click="dismiss"
      >
        {{ activeProbe.dismissLabel }}
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
