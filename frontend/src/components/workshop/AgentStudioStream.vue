<script setup lang="ts">
/**
 * AgentStudioStream —— 多智能体「工作室」双层思考链（SA4 增强：动画/错误态/边角态）。
 *
 * 设计目标（用户定）：
 *   - 缩略态（默认）：只显示「当前工作室人物 + 在干什么」一张人物卡（头像/emoji + 角色名 + 动作 + 耗时）。
 *   - 详尽态（点击「详情」）：展开全员流水线，每行 = 人物 + 动作 + 状态(▸/✓/✗) + 耗时 + 中间产物摘要(note)。
 *   - 只展示公开的「任务进度 / 工具调用 / 中间产物 / 执行状态」，绝不渲染 LLM 原始 chain-of-thought。
 *
 * 数据：复用现有 SSE plan/trace（useSseStream），零新增管线。后端发 role_key 直接用；
 * 未发则按 node 名经 agentActors.nodeToRoleKey 回退推断角色。两用：答疑 / 资源 / 编排 / 视频概述 / 视频合成。
 *
 * 边角与健壮性（SA4）：头像缺图 @error → emoji 兜底；percent 防 NaN/越界；逐行入场动画 +
 * 当前步脉冲，全部尊重 prefers-reduced-motion；error 末步标红、空态给占位行（永不白屏）。
 */
import { computed, ref } from 'vue';
import type { SsePlan, SseTraceEvent } from '@/composables/useSseStream';
import {
  resolveActor,
  actorPortraitUrl,
  type AgentActor,
} from '@/data/agentActors';

const props = withDefaults(
  defineProps<{
    traces: SseTraceEvent[];
    status: 'idle' | 'streaming' | 'done' | 'error';
    /** 顶部任务标题（如「合成短视频」「视频领航」）。 */
    label?: string;
    /** 流式产物预览（token 尾巴）；可空，仅展开态显示。 */
    tokenPreview?: string;
    /** 开场工位清单（用于「共 N 步」）。 */
    plan?: SsePlan | null;
    errorMessage?: string | null;
    /** 是否默认展开（缺省缩略）。 */
    defaultExpanded?: boolean;
  }>(),
  {
    label: '智能体协同中',
    tokenPreview: '',
    plan: null,
    errorMessage: null,
    defaultExpanded: false,
  },
);

const expanded = ref(props.defaultExpanded);
function toggle(): void {
  expanded.value = !expanded.value;
}

const isStreaming = computed<boolean>(() => props.status === 'streaming');
const isError = computed<boolean>(() => props.status === 'error');

// 头像加载失败的 npcKey 集合（同一 npcKey 头像 URL 相同：一处 404 即可全员回退 emoji，与 DialogTree 同款降级）。
const erroredFaces = ref<Set<string>>(new Set());
function markFaceError(npcKey: string): void {
  if (erroredFaces.value.has(npcKey)) return;
  const next = new Set(erroredFaces.value);
  next.add(npcKey);
  erroredFaces.value = next;
}
function faceErrored(npcKey: string): boolean {
  return erroredFaces.value.has(npcKey);
}
function portraitUrl(actor: AgentActor): string {
  return actorPortraitUrl(actor);
}

interface StudioStep {
  key: string;
  actor: AgentActor;
  message: string;
  status: string;
  elapsedMs: number;
  note: string;
}

/** 取这条 trace 的人话动作：message > actor_label > 角色默认 doing；空白回退默认。 */
function traceMessage(t: SseTraceEvent, actor: AgentActor): string {
  const raw = (t.message ?? t.actor_label ?? actor.doing).trim();
  return raw.length > 0 ? raw : actor.doing;
}

/** 耗时健壮化：非数字/NaN/负数一律归 0（不渲染），正数四舍五入。 */
function safeElapsed(ms: number | undefined): number {
  return typeof ms === 'number' && Number.isFinite(ms) && ms > 0 ? Math.round(ms) : 0;
}

/**
 * 把 traces 折叠成「逐人物流水线」：相邻同角色合并为一行（start/finish 两相位、重试归一）；
 * finish 相位回填耗时 / 状态 / 中间产物(note)。行随 traces 增长逐条追加。
 */
const steps = computed<StudioStep[]>(() => {
  const out: StudioStep[] = [];
  for (const t of props.traces) {
    const actor = resolveActor(t.role_key ?? t.node);
    const message = traceMessage(t, actor);
    const prev = out.length > 0 ? out[out.length - 1] : undefined;
    if (prev && prev.actor.roleKey === actor.roleKey) {
      const el = safeElapsed(t.elapsed_ms);
      if (el > 0) prev.elapsedMs = el;
      if (t.note) prev.note = t.note;
      if (t.message) prev.message = message;
      if (t.status) prev.status = t.status;
      continue;
    }
    out.push({
      key: `${out.length}-${actor.roleKey}`,
      actor,
      message,
      status: t.status || 'running',
      elapsedMs: safeElapsed(t.elapsed_ms),
      note: t.note ?? '',
    });
  }
  return out;
});

/** 起手/兜底人物：学习总管（统筹）。 */
const WARMUP_ACTOR: AgentActor = resolveActor('orchestrator');

/** 当前人物（流水线末步；尚无 trace 时给学习总管起手 / 待命）。 */
const current = computed<StudioStep>(() => {
  const last = steps.value.length > 0 ? steps.value[steps.value.length - 1] : undefined;
  if (last) return last;
  return {
    key: '__warmup',
    actor: WARMUP_ACTOR,
    message: isStreaming.value ? WARMUP_ACTOR.doing : '智能体待命中',
    status: isStreaming.value ? 'running' : props.status,
    elapsedMs: 0,
    note: '',
  };
});

const totalSteps = computed<number>(() => props.plan?.nodes?.length ?? 0);

/** 整体进度：合成作业等流会在 trace 带 percent；否则用步数。percent 防 NaN/越界。 */
const latestPercent = computed<number | null>(() => {
  for (let i = props.traces.length - 1; i >= 0; i -= 1) {
    const p = props.traces[i]?.percent;
    if (typeof p === 'number' && Number.isFinite(p)) {
      return Math.max(0, Math.min(100, Math.round(p)));
    }
  }
  return null;
});

const progressText = computed<string>(() => {
  if (latestPercent.value !== null) return `${latestPercent.value}%`;
  if (totalSteps.value > 0) {
    return `${Math.min(steps.value.length, totalSteps.value)}/${totalSteps.value} 步`;
  }
  return '';
});

const tokenTail = computed<string>(() => (props.tokenPreview ?? '').slice(-180));

/** 失败状态识别（后端 status 字符串包含 fail/error/abort 视为失败）。 */
function isFailStatus(s: string): boolean {
  const v = (s || '').toLowerCase();
  return v.includes('fail') || v.includes('error') || v.includes('abort');
}

/** 末步是否判失败：自身 status 失败，或整体 error 落在末步。 */
function lineFailed(step: StudioStep, isLast: boolean): boolean {
  return isFailStatus(step.status) || (isError.value && isLast);
}

function statusIcon(step: StudioStep, isLast: boolean): string {
  if (lineFailed(step, isLast)) return '✗';
  if (isStreaming.value && isLast) return '▸';
  return '✓';
}
</script>

<template>
  <section
    class="studio"
    :class="{ 'studio--error': isError }"
    data-testid="agent-studio-stream"
    aria-live="polite"
  >
    <header class="studio__head">
      <span
        class="studio__spark"
        :class="{ 'studio__spark--spin': isStreaming, 'studio__spark--error': isError }"
        aria-hidden="true"
      />
      <span class="studio__title">{{ label }}</span>
      <span
        v-if="progressText"
        class="studio__progress"
        data-testid="agent-studio-progress"
      >{{ progressText }}</span>
      <button
        type="button"
        class="studio__toggle"
        data-testid="agent-studio-toggle"
        :aria-expanded="expanded"
        :aria-label="expanded ? '收起全员流水线' : '展开全员流水线'"
        @click="toggle"
      >
        {{ expanded ? '收起 ▲' : '详情 ▼' }}
      </button>
    </header>

    <!-- 缩略态：当前人物卡（头像 + 角色 + 在干啥） -->
    <div
      v-if="!expanded"
      class="studio__brief"
      data-testid="agent-studio-brief"
    >
      <span class="studio__face">
        <img
          v-if="!faceErrored(current.actor.npcKey)"
          :src="portraitUrl(current.actor)"
          :alt="current.actor.label"
          class="studio__face-img"
          @error="markFaceError(current.actor.npcKey)"
        >
        <span
          v-else
          class="studio__face-emoji"
          aria-hidden="true"
        >{{ current.actor.emoji }}</span>
      </span>
      <span class="studio__brief-body">
        <span class="studio__brief-name">{{ current.actor.label }}</span>
        <span class="studio__brief-doing">
          {{ current.message }}<span
            v-if="isStreaming"
            class="studio__cursor"
            aria-hidden="true"
          >▌</span>
        </span>
      </span>
      <span
        v-if="current.elapsedMs > 0"
        class="studio__ms"
      >{{ current.elapsedMs }}ms</span>
    </div>

    <!-- 详尽态：全员流水线（逐行入场动画） -->
    <TransitionGroup
      v-else
      name="studio-line"
      tag="ol"
      class="studio__lines"
      data-testid="agent-studio-lines"
    >
      <li
        v-for="(step, i) in steps"
        :key="step.key"
        class="studio__line"
        :class="{
          'studio__line--active': isStreaming && i === steps.length - 1,
          'studio__line--failed': lineFailed(step, i === steps.length - 1),
        }"
      >
        <span class="studio__face studio__face--sm">
          <img
            v-if="!faceErrored(step.actor.npcKey)"
            :src="portraitUrl(step.actor)"
            :alt="step.actor.label"
            class="studio__face-img"
            @error="markFaceError(step.actor.npcKey)"
          >
          <span
            v-else
            class="studio__face-emoji"
            aria-hidden="true"
          >{{ step.actor.emoji }}</span>
        </span>
        <span class="studio__line-body">
          <span class="studio__line-head">
            <span class="studio__bullet">{{ statusIcon(step, i === steps.length - 1) }}</span>
            <span class="studio__line-name">{{ step.actor.label }}</span>
            <span class="studio__line-msg">{{ step.message }}</span>
            <span
              v-if="step.elapsedMs > 0"
              class="studio__ms"
            >{{ step.elapsedMs }}ms</span>
          </span>
          <span
            v-if="step.note"
            class="studio__note"
          >{{ step.note }}</span>
        </span>
      </li>
      <li
        v-if="isStreaming && steps.length === 0"
        key="__warmup"
        class="studio__line studio__line--active"
      >
        <span class="studio__face studio__face--sm">
          <span
            class="studio__face-emoji"
            aria-hidden="true"
          >{{ current.actor.emoji }}</span>
        </span>
        <span class="studio__line-body">
          <span class="studio__line-head">
            <span class="studio__bullet">▸</span>
            <span class="studio__line-name">{{ current.actor.label }}</span>
            <span class="studio__line-msg">{{ current.message }}…</span>
          </span>
        </span>
      </li>
      <li
        v-else-if="steps.length === 0"
        key="__empty"
        class="studio__empty"
        data-testid="agent-studio-empty"
      >
        暂无执行记录
      </li>
    </TransitionGroup>

    <p
      v-if="expanded && isStreaming && tokenTail"
      class="studio__token"
      data-testid="agent-studio-token"
    >
      <span class="studio__token-text">{{ tokenTail }}</span><span class="studio__cursor">▌</span>
    </p>
    <p
      v-if="isError"
      class="studio__error-line"
      data-testid="agent-studio-error"
    >
      ⚠ {{ errorMessage ?? '出了点状况，请重试' }}
    </p>
  </section>
</template>

<style scoped>
.studio {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-self: flex-start;
  width: 100%;
  max-width: 90%;
  padding: 10px 12px;
  font-family: var(--pyt-font-pixel);
  color: var(--pyt-color-text-primary);
  background: rgba(255, 247, 230, 0.7);
  border: 2px dashed rgba(110, 79, 29, 0.45);
  border-radius: var(--pyt-radius-lg);
}
.studio--error {
  border-color: var(--pyt-color-danger);
}
.studio__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.studio__spark {
  width: 12px;
  height: 12px;
  flex: none;
  border: 2px solid rgba(42, 111, 151, 0.25);
  border-top-color: var(--pyt-color-info);
  border-radius: 50%;
}
.studio__spark--spin {
  animation: studio-spin 0.9s linear infinite;
}
.studio__spark--error {
  border-color: var(--pyt-color-danger);
  border-top-color: var(--pyt-color-danger);
  animation: none;
}
.studio__title {
  font-size: 12px;
  font-weight: 700;
  color: var(--pyt-color-text-hint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1 1 auto;
  min-width: 0;
}
.studio__progress {
  flex: none;
  font-size: 11px;
  color: var(--pyt-color-text-muted);
  font-variant-numeric: tabular-nums;
}
.studio__toggle {
  flex: none;
  font-family: inherit;
  font-size: 11px;
  padding: 2px 8px;
  background: #fff8df;
  border: 1px solid #8c6a23;
  border-radius: 999px;
  color: #2a1a08;
  cursor: pointer;
}
.studio__toggle:hover {
  background: #ffe098;
}
.studio__toggle:focus-visible {
  outline: 2px solid var(--pyt-color-info);
  outline-offset: 1px;
}

/* ── 缩略态：当前人物卡 ── */
.studio__brief {
  display: flex;
  align-items: center;
  gap: 10px;
}
.studio__brief-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1 1 auto;
}
.studio__brief-name {
  font-size: 12px;
  font-weight: 700;
  color: var(--pyt-color-info);
}
.studio--error .studio__brief-name {
  color: var(--pyt-color-danger);
}
.studio__brief-doing {
  font-size: 12.5px;
  line-height: 1.45;
  color: var(--pyt-color-text-muted);
  word-break: break-word;
}

/* ── 人物头像（缺图回退 emoji） ── */
.studio__face {
  flex: none;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 2px solid #8c6a23;
  border-radius: 8px;
  background: #fff8df;
  image-rendering: pixelated;
}
.studio__face--sm {
  width: 26px;
  height: 26px;
  border-radius: 6px;
}
.studio__face-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  image-rendering: pixelated;
}
.studio__face-emoji {
  font-size: 16px;
  line-height: 1;
}

/* ── 详尽态：全员流水线 ── */
.studio__lines {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.studio__line {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.studio__line-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1 1 auto;
}
.studio__line-head {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex-wrap: wrap;
}
.studio__bullet {
  flex: none;
  width: 12px;
  text-align: center;
  color: #4d6e1d;
}
.studio__line--active .studio__bullet,
.studio__line--active .studio__line-name {
  color: var(--pyt-color-info);
}
.studio__line--active .studio__bullet {
  animation: studio-pulse 1.1s ease-in-out infinite;
}
.studio__line--failed .studio__bullet,
.studio__line--failed .studio__line-name {
  color: var(--pyt-color-danger);
  animation: none;
}
.studio__line-name {
  font-size: 12px;
  font-weight: 700;
  color: var(--pyt-color-text-primary);
}
.studio__line-msg {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 12px;
  color: var(--pyt-color-text-muted);
  word-break: break-word;
}
.studio__note {
  font-size: 11px;
  line-height: 1.4;
  color: var(--pyt-color-text-hint);
  padding-left: 18px;
  word-break: break-word;
}
.studio__empty {
  font-size: 12px;
  color: var(--pyt-color-text-hint);
  font-style: italic;
}
.studio__ms {
  flex: none;
  font-size: 10px;
  color: var(--pyt-color-text-muted);
  font-variant-numeric: tabular-nums;
}

/* ── token 尾巴 + 闪烁光标 ── */
.studio__token {
  margin: 2px 0 0;
  padding: 6px 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--pyt-color-text-hint);
  background: var(--pyt-color-bg-soft);
  border: 1px dashed rgba(110, 79, 29, 0.35);
  border-radius: var(--pyt-radius-md);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 64px;
  overflow: hidden;
}
.studio__token-text {
  opacity: 0.85;
}
.studio__cursor {
  color: #d4af37;
  font-weight: 700;
  animation: studio-blink 0.8s steps(2) infinite;
}
.studio__error-line {
  margin: 2px 0 0;
  font-size: 12px;
  color: #b3402a;
}

/* 行进入：逐行「跳动」追加（与 ThinkingStream 同款观感）。 */
.studio-line-enter-active {
  transition: opacity 0.28s ease, transform 0.28s ease;
}
.studio-line-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

@keyframes studio-spin {
  to { transform: rotate(360deg); }
}
@keyframes studio-blink {
  0% { opacity: 1; }
  50% { opacity: 0; }
}
@keyframes studio-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
@media (prefers-reduced-motion: reduce) {
  .studio__spark--spin,
  .studio__cursor,
  .studio__line--active .studio__bullet {
    animation: none;
  }
  .studio-line-enter-active {
    transition: none;
  }
}
</style>
