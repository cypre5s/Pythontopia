<script setup lang="ts">
/**
 * PlanCard —— 学习计划卡（PLAN 意图 orchestrator 产物）。
 *
 * 标题「📋 你的学习计划」+ 竖向步骤列表：阶段徽章（watch=看 / learn=学 / practice=练 /
 * assess=测，各自配色；未知 phase 原样显示）+ title + detail 小字 + panel 非空时
 * 「前往 →」按钮 emit open(panel)。步骤间细虚线连接（rail 风格与 MarvisPanel 工位线一致）。
 */
interface PlanStep {
  id: string;
  phase: string;
  title: string;
  detail: string;
  panel: string | null;
  /**
   * 运行态进度（orchestrator dispatch 经 AG-UI STATE_DELTA 实时推送）：
   * pending / active / done / failed / skipped；缺省按 pending（历史回放/静态卡无此字段）。
   */
  status?: string;
}

defineProps<{ steps: PlanStep[] }>();

const emit = defineEmits<{ (e: 'open', panel: string): void }>();

interface PhaseMeta {
  label: string;
  cls: string;
}

const PHASE_META: Record<string, PhaseMeta> = {
  watch: { label: '看', cls: 'watch' },
  learn: { label: '学', cls: 'learn' },
  practice: { label: '练', cls: 'practice' },
  assess: { label: '测', cls: 'assess' },
};

/** 进度图标：done 打勾、active 进行、failed 叉、skipped 跳过；pending 不显示。 */
const STATUS_ICON: Record<string, string> = {
  done: '✓',
  active: '▶',
  failed: '✗',
  skipped: '⊘',
};

function phaseMeta(phase: string): PhaseMeta {
  return PHASE_META[phase] ?? { label: phase, cls: 'other' };
}

function stepStatus(step: PlanStep): string {
  return step.status ?? 'pending';
}

function statusIcon(step: PlanStep): string {
  return STATUS_ICON[stepStatus(step)] ?? '';
}

function onOpen(step: PlanStep): void {
  if (step.panel) emit('open', step.panel);
}
</script>

<template>
  <section
    class="plan"
    data-testid="plan-card"
  >
    <p class="plan__title">
      📋 你的学习计划
    </p>
    <ol class="plan__steps">
      <li
        v-for="(s, i) in steps"
        :key="s.id"
        class="plan__step"
        :class="`plan__step--${stepStatus(s)}`"
        data-testid="plan-step"
      >
        <span class="plan__rail">
          <span
            v-if="i > 0"
            class="plan__line"
            aria-hidden="true"
          />
          <span
            class="plan__badge"
            :class="`plan__badge--${phaseMeta(s.phase).cls}`"
          >{{ phaseMeta(s.phase).label }}</span>
        </span>
        <span class="plan__body">
          <span class="plan__step-title">
            <span
              v-if="statusIcon(s)"
              class="plan__status"
              :class="`plan__status--${stepStatus(s)}`"
              data-testid="plan-step-status"
              aria-hidden="true"
            >{{ statusIcon(s) }}</span>
            {{ s.title }}
          </span>
          <span class="plan__detail">{{ s.detail }}</span>
          <button
            v-if="s.panel"
            type="button"
            class="plan__open"
            data-testid="plan-open"
            @click="onOpen(s)"
          >
            前往 →
          </button>
        </span>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.plan {
  align-self: flex-start;
  width: 100%;
  max-width: 90%;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
  background: #fff8df;
  border: 2px solid var(--pyt-color-border);
  border-radius: var(--pyt-radius-lg);
  box-shadow: 2px 2px 0 rgba(140, 106, 35, 0.35);
}
.plan__title {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--pyt-color-text-hint);
}
.plan__steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.plan__step {
  display: grid;
  grid-template-columns: 24px 1fr;
  gap: 8px;
  align-items: stretch;
}
.plan__rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 24px;
}
/* 步骤间细虚线连接（位于下游步骤徽章上方）。 */
.plan__line {
  width: 0;
  height: 10px;
  border-left: 2px dashed rgba(140, 106, 35, 0.5);
}
.plan__badge {
  flex: none;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  border-radius: var(--pyt-radius-md);
  border: 2px solid #8c6a23;
  background: var(--pyt-color-bg-soft);
  color: var(--pyt-color-text-hint);
  overflow: hidden;
  white-space: nowrap;
}
.plan__badge--watch {
  background: #d0e3f1;
  border-color: #2a6f97;
  color: #1b4a6b;
}
.plan__badge--learn {
  background: #f5e2c1;
  border-color: #c2871f;
  color: #7a5210;
}
.plan__badge--practice {
  background: #d6ead1;
  border-color: #4d6e1d;
  color: #2d5b1b;
}
.plan__badge--assess {
  background: #ede9fe;
  border-color: #7b2cbf;
  color: #5a1da8;
}
.plan__body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-width: 0;
  padding: 1px 0 8px;
}
.plan__step-title {
  font-size: 12.5px;
  font-weight: 700;
  line-height: 1.4;
  word-break: break-word;
}
/* 运行态进度视觉：dispatch 经 AG-UI STATE_DELTA 实时点亮。done 降权、active 徽章脉冲。 */
.plan__step--done {
  opacity: 0.6;
}
.plan__step--active .plan__badge {
  box-shadow: 0 0 0 2px #ffd770;
  animation: plan-pulse 1.1s ease-in-out infinite;
}
@keyframes plan-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 2px rgba(255, 215, 112, 0.45);
  }
  50% {
    box-shadow: 0 0 0 3px rgba(255, 215, 112, 0.95);
  }
}
.plan__status {
  display: inline-flex;
  margin-right: 4px;
  font-weight: 700;
}
.plan__status--done {
  color: #4d6e1d;
}
.plan__status--active {
  color: #c2871f;
}
.plan__status--failed {
  color: #b3261e;
}
.plan__status--skipped {
  color: var(--pyt-color-text-muted);
}
@media (prefers-reduced-motion: reduce) {
  .plan__step--active .plan__badge {
    animation: none;
  }
}
.plan__detail {
  font-size: 11px;
  line-height: 1.5;
  color: var(--pyt-color-text-muted);
  word-break: break-word;
}
.plan__open {
  margin-top: 2px;
  font-family: inherit;
  font-size: 11px;
  padding: 2px 10px;
  background: #fff8df;
  border: 1px solid #8c6a23;
  border-radius: var(--pyt-radius-full);
  color: #2a1a08;
  cursor: pointer;
}
.plan__open:hover {
  background: #ffe098;
}
</style>
