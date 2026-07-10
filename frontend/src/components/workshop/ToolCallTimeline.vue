<script setup lang="ts">
/**
 * ToolCallTimeline —— 智能体「工具调用生命周期」实时时间轴。
 *
 * 消费 SSE STEP_STARTED/STEP_FINISHED 投影出的 traces，按 step（step_id 优先，回退 node）配对成
 * 一条条工具调用，渲染带生命周期的竖向时间轴：
 *   运行中（脉冲圆环）→ 完成 ✓ / 返工 ⟳ / 失败 ✗，附中文工位名、实时耗时、治理徽章（已降级/预算跳过）。
 *
 * live=true：尚未收到 finish 的工具显示「运行中…」动画——用于答案流式期间在答案区内嵌实时展示，
 * 让学生看见「系统正按 证据检索 → 作答 → 三审 推进」，替代无意义的转圈。完成后由 ToolTraceLine
 * 折叠回顾承担。
 */
import { computed } from 'vue';
import type { SseTraceEvent } from '@/composables/useSseStream';

const props = withDefaults(defineProps<{ traces: SseTraceEvent[]; live?: boolean }>(), {
  live: false,
});

/** node → 中文工位名（trace 自带 agent_label/actor_label 优先；否则查表；再否则原样）。 */
const NODE_LABELS: Record<string, string> = {
  evidence_fetcher: '证据检索',
  TutorAgent: '导师作答',
  'TutorAgent.revise': '导师返工',
  grounding_critic: '引用审查',
  hallucination_detector: '防幻觉核查',
  safety_critic: '安全审查',
  code_runner: '沙箱验证',
  debate: '辩论复核',
  // 辩论子图与尾部并行节点（后端已随事件下发中文 agent_label，这里作兜底，确保永不显英文）。
  solver: '求解者',
  critic: '质疑者',
  referee: '仲裁者',
  tutor_diagram: '图解生成',
  tutor_evidence_note: '证据导读',
  quiz_question: '考官出题',
  quiz_judge: '考官评卷',
  understand: '理解意图',
  decompose: '拆解计划',
  replan: '动态重排',
  extract_profile: '画像更新',
  compose_reply: '组织回复',
};

type CallStatus = 'running' | 'ok' | 'retry' | 'error';

interface ToolCall {
  key: string;
  label: string;
  status: CallStatus;
  elapsedMs: number;
  note: string;
  result: string;
}

const ICONS: Record<CallStatus, string> = { running: '', ok: '✓', retry: '⟳', error: '✗' };

/** 从 trace 结构化产物字段拼工具调用 result 摘要（证据命中段数 / 看图识别进度，零破坏增强）。 */
function resultSummary(t: SseTraceEvent): string {
  const parts: string[] = [];
  if (typeof t.chunks === 'number' && t.chunks >= 0) parts.push(`命中 ${t.chunks} 段课件`);
  if (typeof t.index === 'number' && typeof t.total === 'number') {
    parts.push(`第 ${t.index}/${t.total} 张图`);
  }
  if (typeof t.chars === 'number' && t.chars > 0) parts.push(`识别 ${t.chars} 字`);
  return parts.join(' · ');
}

function labelOf(t: SseTraceEvent): string {
  return t.actor_label || t.agent_label || NODE_LABELS[t.node] || t.node;
}

function statusOf(raw: string): CallStatus {
  const s = (raw || '').toLowerCase();
  if (s.includes('error') || s.includes('fail')) return 'error';
  if (s.includes('retry')) return 'retry';
  return 'ok';
}

function safeMs(ms: number): number {
  return Number.isFinite(ms) && ms > 0 ? Math.round(ms) : 0;
}

const calls = computed<ToolCall[]>(() => {
  const order: string[] = [];
  const map = new Map<string, ToolCall>();
  for (const t of props.traces) {
    const key = (t.step_id ?? '') || t.node;
    let call = map.get(key);
    if (!call) {
      call = { key, label: labelOf(t), status: 'running', elapsedMs: 0, note: '', result: '' };
      map.set(key, call);
      order.push(key);
    }
    call.label = labelOf(t);
    const summary = resultSummary(t);
    if (summary) call.result = summary;
    // finish（或无 phase 的一次性完成 trace，如 understand/compose_reply）→ 落定终态。
    if (t.phase === 'finish' || !t.phase) {
      call.status = statusOf(t.status);
      call.elapsedMs = safeMs(t.elapsed_ms);
      call.note = t.note ?? '';
    }
  }
  // 非 live（完成快照）下仍标 running 的，按已完成兜底，避免历史回放残留转圈。
  if (!props.live) {
    for (const c of map.values()) {
      if (c.status === 'running') c.status = 'ok';
    }
  }
  return order.map((k) => map.get(k) as ToolCall);
});

interface CallBadge {
  key: string;
  label: string;
}

function badgesOf(note: string): CallBadge[] {
  const out: CallBadge[] = [];
  if (note.includes('fallback')) out.push({ key: 'fallback', label: '已改用备用方式' });
  if (note.includes('budget_skipped')) out.push({ key: 'budget', label: '已跳过非必要步骤' });
  return out;
}

function fmtMs(ms: number): string {
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`;
}
</script>

<template>
  <ol
    v-if="calls.length > 0"
    class="timeline"
    data-testid="tool-call-timeline"
  >
    <li
      v-for="c in calls"
      :key="c.key"
      class="timeline__step"
      :class="`timeline__step--${c.status}`"
      data-testid="tool-call-step"
    >
      <span
        class="timeline__rail"
        aria-hidden="true"
      >
        <span class="timeline__node">
          <span
            v-if="c.status === 'running'"
            class="timeline__spin"
          />
          <span
            v-else
            class="timeline__icon"
          >{{ ICONS[c.status] }}</span>
        </span>
      </span>
      <span class="timeline__body">
        <span class="timeline__head">
          <span class="timeline__label">{{ c.label }}</span>
          <span
            v-if="c.status === 'running'"
            class="timeline__state"
          >运行中…</span>
          <span
            v-else-if="c.elapsedMs > 0"
            class="timeline__ms"
          >{{ fmtMs(c.elapsedMs) }}</span>
          <span
            v-if="c.result"
            class="timeline__result"
            data-testid="tool-call-result"
          >{{ c.result }}</span>
          <span
            v-for="b in badgesOf(c.note)"
            :key="b.key"
            class="timeline__badge"
            :class="`timeline__badge--${b.key}`"
            data-testid="tool-call-badge"
          >{{ b.label }}</span>
        </span>
        <span
          v-if="c.status === 'error'"
          class="timeline__note timeline__note--error"
        >这一步未完成，请重试。</span>
      </span>
    </li>
  </ol>
</template>

<style scoped>
.timeline {
  align-self: flex-start;
  width: 100%;
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  font-family: var(--pyt-font-pixel);
}
.timeline__step {
  display: grid;
  grid-template-columns: 20px 1fr;
  gap: 8px;
  animation: tl-in 0.18s ease;
}
@keyframes tl-in {
  from {
    opacity: 0;
    transform: translateY(3px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.timeline__rail {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.timeline__node {
  flex: none;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: 2px solid var(--pyt-color-border);
  background: var(--pyt-color-bg-soft);
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
}
/* 时间轴连接线：除最后一个步骤外，节点下方延伸竖线。 */
.timeline__step:not(:last-child) .timeline__rail::after {
  content: '';
  width: 2px;
  flex: 1;
  min-height: 8px;
  background: rgba(140, 106, 35, 0.4);
}
.timeline__spin {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  border: 2px solid rgba(194, 135, 31, 0.35);
  border-top-color: #c2871f;
  animation: tl-spin 0.7s linear infinite;
}
@keyframes tl-spin {
  to {
    transform: rotate(360deg);
  }
}
.timeline__step--ok .timeline__node {
  border-color: #4d6e1d;
  color: #2d5b1b;
  background: #d6ead1;
}
.timeline__step--retry .timeline__node {
  border-color: #c2871f;
  color: #7a5210;
  background: #f5e2c1;
}
.timeline__step--error .timeline__node {
  border-color: #b3261e;
  color: #b3261e;
  background: #f7d9d6;
}
.timeline__step--running .timeline__node {
  border-color: #c2871f;
}
.timeline__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding-bottom: 8px;
}
.timeline__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.timeline__label {
  font-size: 12px;
  font-weight: 700;
  color: var(--pyt-color-text-hint);
}
.timeline__state {
  font-size: 10px;
  color: #c2871f;
}
.timeline__ms {
  font-size: 10px;
  color: var(--pyt-color-text-muted);
  font-variant-numeric: tabular-nums;
}
.timeline__result {
  font-size: 10px;
  font-weight: 700;
  color: #2d5b1b;
  padding: 0;
}
.timeline__badge {
  font-size: 9px;
  font-weight: 700;
  padding: 0 6px;
  border-radius: var(--pyt-radius-full);
}
.timeline__badge--fallback {
  background: #f5e2c1;
  border: 1px solid #c2871f;
  color: #7a5210;
}
.timeline__badge--budget {
  background: #f5e2c1;
  border: 1px solid #c2871f;
  color: #7a5210;
}
.timeline__note {
  font-size: 10px;
  line-height: 1.4;
  color: var(--pyt-color-text-muted);
  word-break: break-word;
}
.timeline__note--error {
  color: #9a342b;
}
@media (prefers-reduced-motion: reduce) {
  .timeline__step {
    animation: none;
  }
  .timeline__spin {
    animation-duration: 1.6s;
  }
}
</style>
