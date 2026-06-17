<script setup lang="ts">
/**
 * LoopProgress —— Agentic Loop（有界自主智能体闭环）进度可视化（F2-loop-ui）。
 *
 * 按 §3.8「对齐 Claude Code 网页版」的可读结构渲染闭环（看 / 学 / 练 / 测 / 查证）：
 *   1. 可见有界计划：开头「我打算分 N 步陪你处理」+ 每步 checkbox 状态（pending/active/done/failed）；
 *   2. 工具调用时间线：带 toolName 的步骤投影进**既有** ToolCallTimeline（复用，不另造）；
 *   3. 证据引用 chips：课件引用（页码可点，跳书房）与外部引用（BROWSE，标「外部内容，非课件」）**分区**；
 *   4. 流式可中断：streaming 时提供「停止」；
 *   5. 仅 reasonSummary：每步一句可审计摘要，**绝不渲染原始 CoT**（结构上只读 reasonSummary）；
 *   6. 错误态显式：失败步红徽章 + 可读 error + fallback 提示（如 BROWSE→视频），不静默吞。
 *
 * 无障碍（WCAG 2.2 AA · §8.4）：状态用「文字标签 + 图标 + 颜色」三通道（不靠颜色单通道）；
 * 当前进度经 role=status / aria-live=polite 播报；每步标题为原生 button（键盘可达）可折叠；
 * 动效经 F1 的 AnimAdapter 接口注入（业务层不直接 import gsap/rive），并尊重 prefers-reduced-motion。
 *
 * 数据驱动：纯 props 组件（plan/status/runError/anim 由父级注入）。Phase 2 由父级用
 * `createLoopSlice()` 消费 `useSseStream(...).events` 后把响应式状态喂进来，并注入 F1 的真实 adapter。
 * 引用类型一律来自 `@/stores/learning/loopTypes`（冻结契约）/ `@/stores/learning/loopSlice`，禁止重定义。
 */
import { computed, onMounted, ref, watch } from 'vue';
import ToolCallTimeline from '@/components/workshop/ToolCallTimeline.vue';
import { useReducedMotion } from '@/composables/useReducedMotion';
import type { SseTraceEvent } from '@/composables/useSseStream';
import type { LoopRunStatus } from '@/stores/learning/loopSlice';
import type { AnimAdapter } from '@game/adapters/anim/types';
import type {
  LoopCitation,
  LoopPhase,
  LoopPlan,
  LoopStep,
  LoopStepStatus,
} from '@/stores/learning/loopTypes';

/*
 * 动效隔离（§3.7 / §4 F.1）：业务层只依赖 F1 冻结的 `AnimAdapter` 接口（`@game/adapters/anim/types`，
 * 只读依赖），**绝不直接 import gsap / motion / @rive-app/canvas**——三方动态对象全部隔离在 F1
 * 的 adapter 实现里。本组件以 type-only 引用该接口（运行时擦除、零耦合），Phase 2 由父级注入 F1
 * 的真实实现；单测以同接口的 mock 解耦验证。
 */

interface Props {
  plan: LoopPlan | null;
  status?: LoopRunStatus;
  runError?: string | null;
  interrupted?: boolean;
  anim?: AnimAdapter | null;
}

const props = withDefaults(defineProps<Props>(), {
  status: 'idle',
  runError: null,
  interrupted: false,
  anim: null,
});

const emit = defineEmits<{
  (e: 'stop'): void;
  (e: 'select-citation', citation: LoopCitation): void;
}>();

/** 6 个技术相位 → 中文可读名 + 装饰图标（图标 aria-hidden，语义靠文字）。 */
const PHASE_META: Record<LoopPhase, { name: string; icon: string }> = {
  plan: { name: '规划', icon: '🗺' },
  recall: { name: '回忆', icon: '🧠' },
  retrieve: { name: '查证', icon: '📚' },
  act: { name: '执行', icon: '🛠' },
  critique: { name: '审查', icon: '🛡' },
  compose: { name: '作答', icon: '✍' },
};

/** 步骤状态 → checkbox 记号 + 文字标签（文字标签确保「不靠颜色单通道」WCAG）。 */
const STATUS_META: Record<LoopStepStatus, { label: string; mark: string }> = {
  pending: { label: '待办', mark: '☐' },
  active: { label: '进行中', mark: '◐' },
  done: { label: '已完成', mark: '☑' },
  failed: { label: '失败', mark: '✗' },
};

/** 工具失败时的可读 fallback 提示（§3.1 BROWSE 失败回退 VIDEO）。 */
const TOOL_FALLBACK: Record<string, string> = {
  BROWSE: '已回退到视频讲解（VIDEO）',
};

const steps = computed<LoopStep[]>(() => props.plan?.steps ?? []);
const plannedCount = computed<number>(() => steps.value.length);
const maxSteps = computed<number>(() => props.plan?.maxSteps ?? steps.value.length);
const isStreaming = computed<boolean>(() => props.status === 'streaming');
const isComplete = computed<boolean>(() => props.status === 'done');
const hasFailedStep = computed<boolean>(() => steps.value.some((s) => s.status === 'failed'));

/** prefers-reduced-motion：优先问注入的 adapter（单一动画事实源，reduceMotion 为只读属性），
 * 无 adapter 时回退系统媒体查询。 */
const localReduced = useReducedMotion();
const reduced = computed<boolean>(() => (props.anim ? props.anim.reduceMotion : localReduced.value));

/** 带 toolName 的步骤投影成 ToolCallTimeline 可消费的 trace（复用既有工具卡生命周期 UI）。 */
const toolTraces = computed<SseTraceEvent[]>(() =>
  steps.value.filter((s) => !!s.toolName).map((s) => toTrace(s)),
);

function toTrace(step: LoopStep): SseTraceEvent {
  const terminal = step.status === 'done' || step.status === 'failed';
  const status = step.status === 'failed' ? 'fail' : step.status === 'done' ? 'ok' : 'running';
  return {
    node: step.toolName ?? step.phase,
    step_id: step.id,
    status,
    elapsed_ms: step.durationMs ?? 0,
    phase: terminal ? 'finish' : 'start',
    agent_label: step.label,
  };
}

function dedupeCitations(kind: LoopCitation['kind']): LoopCitation[] {
  const seen = new Set<string>();
  const out: LoopCitation[] = [];
  for (const step of steps.value) {
    for (const cite of step.citations ?? []) {
      if (cite.kind !== kind) continue;
      const key = `${cite.ref}|${cite.label}`;
      if (seen.has(key)) continue;
      seen.add(key);
      out.push(cite);
    }
  }
  return out;
}

const courseCitations = computed<LoopCitation[]>(() => dedupeCitations('courseware'));
const externalCitations = computed<LoopCitation[]>(() => dedupeCitations('external'));
const hasCitations = computed<boolean>(
  () => courseCitations.value.length > 0 || externalCitations.value.length > 0,
);

/** 无障碍播报文本：失败 / 完成（含中断）/ 进行中当前步骤 / 待命。 */
const liveStatus = computed<string>(() => {
  if (props.status === 'error') return `闭环失败：${props.runError ?? '未达证据标准'}`;
  if (props.status === 'done') return props.interrupted ? '闭环已停止' : '闭环已完成';
  const active = steps.value.find((s) => s.status === 'active');
  if (active) return `进行中：${active.label}`;
  return isStreaming.value ? '闭环进行中' : '';
});

/** 折叠状态：默认展开；记录被折叠的步骤 id。 */
const collapsed = ref<Record<string, boolean>>({});
function isExpanded(id: string): boolean {
  return !collapsed.value[id];
}
function toggleStep(id: string): void {
  collapsed.value = { ...collapsed.value, [id]: !collapsed.value[id] };
}
function detailId(id: string): string {
  return `loop-step-detail-${id}`;
}

function fallbackHint(step: LoopStep): string {
  if (step.status !== 'failed' || !step.toolName) return '';
  return TOOL_FALLBACK[step.toolName] ?? '';
}

function fmtMs(ms: number): string {
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`;
}

function onStop(): void {
  emit('stop');
}
function onCitation(citation: LoopCitation): void {
  emit('select-citation', citation);
}

// ── 动效编排（经注入 adapter，业务层零第三方动画依赖）────────────────────────────
const rootEl = ref<HTMLElement | null>(null);
const prevStatus = new Map<string, LoopStepStatus>();

function stepElement(id: string): HTMLElement | null {
  return rootEl.value?.querySelector<HTMLElement>(`[data-step-id="${id}"]`) ?? null;
}

function syncStepAnimations(): void {
  const adapter = props.anim;
  for (const step of steps.value) {
    const prev = prevStatus.get(step.id);
    if (prev === step.status) continue;
    prevStatus.set(step.id, step.status);
    if (!adapter) continue;
    const el = stepElement(step.id);
    if (!el) continue;
    // adapter 方法返回 Promise（fire-and-forget，错误由 adapter 内部 fail-soft 兜底）。
    if (step.status === 'active') void adapter.playStepIn(el);
    else if (step.status === 'done') void adapter.playStepDone(el, 'done');
    else if (step.status === 'failed') void adapter.playStepDone(el, 'failed');
  }
}

// 初始入场动效在挂载后跑（此时 rootEl 已就位，querySelector 可命中步骤元素）；
// 后续状态变化由 flush:'post' watcher 在 DOM 更新后驱动。immediate watcher 会在 setup 阶段
// （DOM 未挂载）先跑一次拿不到元素，故改用 onMounted 显式触发初始同步。
onMounted(() => syncStepAnimations());
watch(
  () => steps.value.map((s) => `${s.id}:${s.status}`).join('|'),
  () => syncStepAnimations(),
  { flush: 'post' },
);

watch(
  isComplete,
  (done, was) => {
    if (done && !was && props.anim && !hasFailedStep.value) {
      void props.anim.celebrate({ expression: 'celebrate' });
    }
  },
  { flush: 'post' },
);
</script>

<template>
  <section
    ref="rootEl"
    class="loop-progress"
    :class="{ 'loop-progress--reduced': reduced }"
    aria-label="智能体闭环进度"
    data-testid="loop-progress"
  >
    <!-- 1. 可见有界计划开场 -->
    <header
      v-if="plan && plannedCount > 0"
      class="loop-progress__intro"
      data-testid="loop-plan-intro"
    >
      <span class="loop-progress__intro-main">我打算分 {{ plannedCount }} 步陪你处理</span>
      <span class="loop-progress__intro-bound">· 有界 ≤ {{ maxSteps }} 步</span>
    </header>

    <!-- 1+5+6. 计划清单（checkbox 状态 + 可折叠 + reasonSummary + 错误态） -->
    <ol
      v-if="plan && plannedCount > 0"
      class="loop-progress__plan"
      data-testid="loop-plan"
    >
      <li
        v-for="step in steps"
        :key="step.id"
        class="loop-step"
        :class="`loop-step--${step.status}`"
        :data-step-id="step.id"
        data-testid="loop-step"
      >
        <button
          type="button"
          class="loop-step__head"
          :aria-expanded="isExpanded(step.id)"
          :aria-controls="detailId(step.id)"
          data-testid="loop-step-toggle"
          @click="toggleStep(step.id)"
        >
          <span
            class="loop-step__check"
            aria-hidden="true"
          >{{ STATUS_META[step.status].mark }}</span>
          <span
            class="loop-step__phase"
            :class="`loop-step__phase--${step.phase}`"
          >
            <span aria-hidden="true">{{ PHASE_META[step.phase].icon }}</span>
            {{ PHASE_META[step.phase].name }}
          </span>
          <span class="loop-step__label">{{ step.label }}</span>
          <span
            class="loop-step__status"
            data-testid="loop-step-status"
          >{{ STATUS_META[step.status].label }}</span>
          <span
            v-if="step.status === 'done' && step.durationMs"
            class="loop-step__ms"
          >{{ fmtMs(step.durationMs) }}</span>
        </button>

        <div
          :id="detailId(step.id)"
          class="loop-step__detail"
          :hidden="!isExpanded(step.id)"
          data-testid="loop-step-detail"
        >
          <p
            v-if="step.reasonSummary"
            class="loop-step__reason"
            data-testid="loop-step-reason"
          >
            {{ step.reasonSummary }}
          </p>
          <p
            v-if="step.status === 'failed' && step.error"
            class="loop-step__error"
            data-testid="loop-step-error"
          >
            ⚠ {{ step.error }}
          </p>
          <p
            v-if="fallbackHint(step)"
            class="loop-step__fallback"
            data-testid="loop-step-fallback"
          >
            ↪ {{ fallbackHint(step) }}
          </p>
        </div>
      </li>
    </ol>

    <!-- 2. 工具调用时间线（复用既有 ToolCallTimeline） -->
    <section
      v-if="toolTraces.length > 0"
      class="loop-progress__tools"
      aria-label="工具调用时间线"
    >
      <h3 class="loop-progress__subtitle">
        工具调用
      </h3>
      <ToolCallTimeline
        :traces="toolTraces"
        :live="isStreaming"
      />
    </section>

    <!-- 3. 证据引用 chips（课件 / 外部 分区，不混淆） -->
    <section
      v-if="hasCitations"
      class="loop-progress__evidence"
      aria-label="证据引用"
      data-testid="loop-evidence"
    >
      <h3 class="loop-progress__subtitle">
        证据引用
      </h3>
      <div
        v-if="courseCitations.length > 0"
        class="loop-evidence__group"
        data-testid="loop-evidence-courseware"
      >
        <span class="loop-evidence__tag loop-evidence__tag--course">课件</span>
        <button
          v-for="cite in courseCitations"
          :key="`cw-${cite.ref}-${cite.label}`"
          type="button"
          class="loop-cite loop-cite--course"
          :title="`跳转书房 · 第 ${cite.ref} 页`"
          data-testid="loop-cite-courseware"
          @click="onCitation(cite)"
        >
          📖 {{ cite.label }}
        </button>
      </div>
      <div
        v-if="externalCitations.length > 0"
        class="loop-evidence__group"
        data-testid="loop-evidence-external"
      >
        <span class="loop-evidence__tag loop-evidence__tag--external">外部内容，非课件</span>
        <a
          v-for="cite in externalCitations"
          :key="`ex-${cite.ref}-${cite.label}`"
          class="loop-cite loop-cite--external"
          :href="cite.ref"
          target="_blank"
          rel="noopener noreferrer"
          data-testid="loop-cite-external"
        >
          🔗 {{ cite.label }}
        </a>
      </div>
    </section>

    <!-- 4. 流式可中断 -->
    <div
      v-if="isStreaming"
      class="loop-progress__stream"
    >
      <span
        class="loop-progress__pulse"
        aria-hidden="true"
      />
      <span class="loop-progress__stream-text">闭环进行中…</span>
      <button
        type="button"
        class="loop-progress__stop"
        data-testid="loop-stop"
        @click="onStop"
      >
        停止
      </button>
    </div>

    <!-- 6. run 级错误态显式 -->
    <p
      v-if="runError"
      class="loop-progress__error"
      role="alert"
      data-testid="loop-error"
    >
      {{ runError }}
    </p>

    <!-- 无障碍：仅供读屏的实时状态播报区 -->
    <p
      class="loop-progress__live"
      role="status"
      aria-live="polite"
      data-testid="loop-live"
    >
      {{ liveStatus }}
    </p>
  </section>
</template>

<style scoped>
/* 像素小镇风 · 高对比（正文/标签 ≥ 4.5:1，状态用文字+图标+颜色三通道，不靠颜色单通道）。 */
.loop-progress {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text, #2b2118);
  background: var(--pyt-color-bg-soft, #f3e9d2);
  border: 2px solid var(--pyt-color-border, #8c6a23);
  border-radius: 4px;
}
.loop-progress__intro {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
}
.loop-progress__intro-bound {
  font-size: 11px;
  font-weight: 700;
  color: #7a5210;
}
.loop-progress__plan {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.loop-step {
  border: 1px solid rgba(140, 106, 35, 0.4);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.45);
  overflow: hidden;
}
.loop-step__head {
  width: 100%;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 7px 9px;
  background: transparent;
  border: 0;
  font-family: inherit;
  font-size: 12px;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.loop-step__head:hover {
  background: rgba(194, 135, 31, 0.12);
}
/* 键盘焦点环（WCAG 2.4.7 焦点可见）。 */
.loop-step__head:focus-visible {
  outline: 2px solid #7a5210;
  outline-offset: -2px;
}
.loop-step__check {
  font-size: 14px;
  line-height: 1;
}
.loop-step__phase {
  flex: 0 0 auto;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 2px;
  border: 1px solid currentColor;
}
.loop-step__phase--plan {
  color: #1f5673;
}
.loop-step__phase--recall {
  color: #5a1da8;
}
.loop-step__phase--retrieve {
  color: #2d5b1b;
}
.loop-step__phase--act {
  color: #a8520a;
}
.loop-step__phase--critique {
  color: #7a5210;
}
.loop-step__phase--compose {
  color: #1f5673;
}
.loop-step__label {
  flex: 1 1 auto;
  min-width: 0;
  font-weight: 700;
}
.loop-step__status {
  flex: 0 0 auto;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 7px;
  border-radius: var(--pyt-radius-full, 999px);
  border: 1px solid currentColor;
}
.loop-step__ms {
  flex: 0 0 auto;
  font-size: 10px;
  color: #5b4a30;
  font-variant-numeric: tabular-nums;
}
/* 状态色（始终与文字标签并存，颜色仅作增强）。对比度均 ≥ 4.5:1。 */
.loop-step--pending .loop-step__status {
  color: #5b4a30;
}
.loop-step--active .loop-step__status {
  color: #8a4b00;
  background: #fbeccb;
}
.loop-step--done .loop-step__status {
  color: #235018;
  background: #d6ead1;
}
.loop-step--failed .loop-step__status {
  color: #a3170f;
  background: #f7d9d6;
}
.loop-step--active {
  border-color: #c2871f;
}
.loop-step--failed {
  border-color: #a3170f;
}
.loop-step__detail {
  padding: 0 9px 8px 32px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
/* 折叠：[hidden] + class 提升特异性，确保压过上面的 display:flex（原生 hidden 同时移出无障碍树）。 */
.loop-step__detail[hidden] {
  display: none;
}
.loop-step__reason {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: #4a3c26;
}
.loop-step__error {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: #a3170f;
}
.loop-step__fallback {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: #8a4b00;
}
.loop-progress__subtitle {
  margin: 0 0 4px;
  font-size: 11px;
  font-weight: 700;
  color: #5b4a30;
}
.loop-progress__evidence {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.loop-evidence__group {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.loop-evidence__tag {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 2px;
}
.loop-evidence__tag--course {
  color: #235018;
  background: #d6ead1;
  border: 1px solid #4d6e1d;
}
.loop-evidence__tag--external {
  color: #8a4b00;
  background: #fbeccb;
  border: 1px solid #c2871f;
}
.loop-cite {
  font-family: inherit;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: var(--pyt-radius-full, 999px);
  cursor: pointer;
  text-decoration: none;
}
.loop-cite--course {
  color: #235018;
  background: #e7f0df;
  border: 1px solid #4d6e1d;
}
.loop-cite--external {
  color: #1f4f6b;
  background: #e1eef5;
  border: 1px solid #2c6c8c;
}
.loop-cite:focus-visible {
  outline: 2px solid #7a5210;
  outline-offset: 1px;
}
.loop-progress__stream {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #5b4a30;
}
.loop-progress__pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c2871f;
  animation: loop-pulse 1.1s ease-in-out infinite;
}
.loop-progress__stream-text {
  flex: 1 1 auto;
}
.loop-progress__stop {
  flex: 0 0 auto;
  font-family: inherit;
  font-size: 11px;
  padding: 3px 12px;
  background: var(--pyt-color-bg-soft, #f3e9d2);
  color: #a3170f;
  border: 2px solid #a3170f;
  border-radius: 2px;
  cursor: pointer;
}
.loop-progress__stop:hover {
  background: #f7d9d6;
}
.loop-progress__stop:focus-visible {
  outline: 2px solid #7a5210;
  outline-offset: 1px;
}
.loop-progress__error {
  margin: 0;
  padding: 6px 8px;
  font-size: 11px;
  line-height: 1.5;
  color: #a3170f;
  background: #f7d9d6;
  border: 1px solid #a3170f;
  border-radius: 4px;
}
/* 读屏专用、视觉隐藏的 aria-live 状态区。 */
.loop-progress__live {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  border: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
@keyframes loop-pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.85);
  }
  50% {
    opacity: 1;
    transform: scale(1.1);
  }
}
/* prefers-reduced-motion 退化为瞬时（JS adapter 退化 + 纯 CSS 双保险）。 */
.loop-progress--reduced .loop-progress__pulse {
  animation: none;
}
@media (prefers-reduced-motion: reduce) {
  .loop-progress__pulse {
    animation: none;
  }
}
</style>
