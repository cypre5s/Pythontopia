<script setup lang="ts">
/**
 * ThinkingStream —— Claude Code 式「逐行跳动思考文字」。
 *
 * 吃 SSE 数据（traces / status / tokenPreview / plan），
 * 把多智能体协同渲染成简洁的实时思考流：
 *   - 随 traces 增长逐条追加「正在检索课件证据 / 正在生成学习资源 / …」人话行（复用 nodeLabel 逻辑）；
 *   - 末行为「当前阶段」，附流式 token 尾巴 + 闪烁光标，营造「边想边写」观感；
 *   - 相邻同标签的 trace（start/finish、attempt 重试）折叠为一行，避免抖动。
 *
 * 纯展示组件：答疑(/api/tutor/ask)与资源生成两条流都发 plan/trace/token，本组件两用。
 */
import { computed } from 'vue';
import type { SsePlan, SseTraceEvent } from '@/composables/useSseStream';

const props = defineProps<{
  traces: SseTraceEvent[];
  status: 'idle' | 'streaming' | 'done' | 'error';
  /** 流式生成内容预览（token 尾巴）；可空。 */
  tokenPreview?: string;
  /** 工位清单（资源流 plan 事件）：用于显示「共 N 步」，可空。 */
  plan?: SsePlan | null;
  errorMessage?: string | null;
  /** 顶部任务标题（如「生成讲义」/「答疑：…」），可空。 */
  label?: string;
}>();

/**
 * graph 节点名 → 「正在…」人话标签。
 * 资源流/答疑流的节点都覆盖；未知节点回退「正在处理」。
 */
function nodeLabel(node: string): string {
  if (!node) return '正在思考';
  if (node === 'evidence_fetcher') return '正在检索课件证据';
  if (node === 'grounding_critic') return '正在核对课本引用';
  if (node === 'hallucination_detector') return '正在防幻觉核查';
  if (node === 'safety_critic') return '正在安全审查';
  if (node === 'TutorAgent' || node === 'image_understanding') return '正在作答（课件 RAG）';
  if (node.startsWith('dispatch.StoryboardAgent')) return '正在制作教学动画';
  if (node.startsWith('dispatch.ResourceDesignerAgent')) return '正在生成学习资源';
  if (node.startsWith('dispatch.PracticeAgent')) return '正在出练习题';
  if (node.startsWith('dispatch.PathPlannerAgent')) return '正在规划学习路径';
  if (node.startsWith('dispatch.DebugAgent')) return '正在分析调试（矿洞补练）';
  if (node === 'replan' || node.startsWith('replan')) return '总管重规划中';
  if (node === 'debate' || node.startsWith('debate')) return '正在辩论复核';
  if (/attempt/i.test(node) || /agent/i.test(node)) return '正在生成学习资源';
  return '正在处理';
}

interface ThinkLine {
  key: string;
  label: string;
  elapsedMs: number;
}

/**
 * 把 traces 折叠成「逐行思考」：相邻同标签合并为一行（start/finish 两相位、attempt 重试归一），
 * 行随 traces 增长逐条追加 → Claude Code 式逐行跳动。finish 相位的耗时回填到该行。
 */
const lines = computed<ThinkLine[]>(() => {
  const out: ThinkLine[] = [];
  for (const t of props.traces) {
    const label = nodeLabel(t.node);
    const prev = out.length > 0 ? out[out.length - 1] : undefined;
    if (prev && prev.label === label) {
      if (typeof t.elapsed_ms === 'number' && t.elapsed_ms > 0) {
        prev.elapsedMs = t.elapsed_ms;
      }
      continue;
    }
    out.push({ key: `${out.length}-${t.node}`, label, elapsedMs: t.elapsed_ms ?? 0 });
  }
  return out;
});

const isStreaming = computed<boolean>(() => props.status === 'streaming');

/** 当前阶段标签（traces 末条；尚无 trace 时给「正在思考」起手）。 */
const activeLabel = computed<string>(() => {
  const last = lines.value.length > 0 ? lines.value[lines.value.length - 1] : undefined;
  return last ? last.label : '正在思考';
});

/** 流式 token 尾巴：仅取末尾一小段，避免长文撑爆思考块。 */
const tokenTail = computed<string>(() => (props.tokenPreview ?? '').slice(-180));

const totalSteps = computed<number>(() => props.plan?.nodes.length ?? 0);
const doneSteps = computed<number>(() => Math.min(lines.value.length, totalSteps.value));
</script>

<template>
  <section
    class="think"
    :class="{ 'think--error': status === 'error' }"
    data-testid="thinking-stream"
    aria-live="polite"
  >
    <header class="think__head">
      <span
        class="think__spark"
        :class="{ 'think__spark--spin': isStreaming }"
        aria-hidden="true"
      />
      <span class="think__title">{{ label || '智能体思考中' }}</span>
      <span
        v-if="totalSteps > 0"
        class="think__steps"
      >{{ doneSteps }}/{{ totalSteps }} 步</span>
    </header>

    <TransitionGroup
      name="think-line"
      tag="ol"
      class="think__lines"
    >
      <li
        v-for="(line, i) in lines"
        :key="line.key"
        class="think__line"
        :class="{ 'think__line--active': isStreaming && i === lines.length - 1 }"
      >
        <span
          class="think__bullet"
          aria-hidden="true"
        >{{ isStreaming && i === lines.length - 1 ? '▸' : '✓' }}</span>
        <span class="think__text">{{ line.label }}</span>
        <span
          v-if="line.elapsedMs > 0"
          class="think__ms"
        >{{ line.elapsedMs }}ms</span>
      </li>
      <li
        v-if="isStreaming && lines.length === 0"
        key="__warmup"
        class="think__line think__line--active"
      >
        <span
          class="think__bullet"
          aria-hidden="true"
        >▸</span>
        <span class="think__text">{{ activeLabel }}…</span>
      </li>
    </TransitionGroup>

    <p
      v-if="isStreaming"
      class="think__token"
      data-testid="thinking-token"
    >
      <span class="think__token-text">{{ tokenTail }}</span><span class="think__cursor">▌</span>
    </p>
    <p
      v-else-if="status === 'error'"
      class="think__error-line"
    >
      ⚠ {{ errorMessage ?? '出了点状况，请重试' }}
    </p>
  </section>
</template>

<style scoped>
.think {
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
.think--error {
  border-color: var(--pyt-color-danger);
}
.think__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.think__spark {
  width: 12px;
  height: 12px;
  flex: none;
  border: 2px solid rgba(42, 111, 151, 0.25);
  border-top-color: var(--pyt-color-info);
  border-radius: 50%;
}
.think__spark--spin {
  animation: think-spin 0.9s linear infinite;
}
.think__title {
  font-size: 12px;
  font-weight: 700;
  color: var(--pyt-color-text-hint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1 1 auto;
  min-width: 0;
}
.think__steps {
  flex: none;
  font-size: 11px;
  color: var(--pyt-color-text-muted);
  font-variant-numeric: tabular-nums;
}

/* ── 逐行思考 ── */
.think__lines {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.think__line {
  display: flex;
  align-items: baseline;
  gap: 7px;
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--pyt-color-text-muted);
}
.think__line--active {
  color: var(--pyt-color-info);
  font-weight: 600;
}
.think__bullet {
  flex: none;
  width: 12px;
  text-align: center;
  color: #4d6e1d;
}
.think__line--active .think__bullet {
  color: var(--pyt-color-info);
}
.think__text {
  flex: 1 1 auto;
  min-width: 0;
  word-break: break-word;
}
.think__ms {
  flex: none;
  font-size: 10px;
  color: var(--pyt-color-text-muted);
  font-variant-numeric: tabular-nums;
}

/* ── 流式 token 尾巴 + 闪烁光标 ── */
.think__token {
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
.think__token-text {
  opacity: 0.85;
}
.think__cursor {
  color: #d4af37;
  font-weight: 700;
  animation: think-blink 0.8s steps(2) infinite;
}
.think__error-line {
  margin: 2px 0 0;
  font-size: 12px;
  color: #b3402a;
}

/* 行进入：逐行「跳动」追加。 */
.think-line-enter-active {
  transition: opacity 0.28s ease, transform 0.28s ease;
}
.think-line-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

@keyframes think-spin {
  to { transform: rotate(360deg); }
}
@keyframes think-blink {
  0% { opacity: 1; }
  50% { opacity: 0; }
}
@media (prefers-reduced-motion: reduce) {
  .think__spark--spin,
  .think__cursor,
  .think-line-enter-active {
    animation: none;
    transition: none;
  }
}
</style>
