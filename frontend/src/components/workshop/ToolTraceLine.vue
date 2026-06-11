<script setup lang="ts">
/**
 * ToolTraceLine —— 折叠「⚙ 工具调用」行（渐进披露，默认折叠）。
 *
 * summary：「⚙ 工具调用 ×{n}」+ 总耗时；展开后逐条：node 中文化 + 状态点
 * （ok 绿 / retry 黄 / error 红）+ 耗时 + note 含 fallback / budget_skipped 时
 * 显示「已降级 / 预算跳过」徽。traces 为空时整行不渲染（v-if）。
 */
import { computed } from 'vue';

interface ToolTrace {
  node: string;
  status: string;
  elapsed_ms: number;
  note?: string | null;
}

const props = defineProps<{ traces: ToolTrace[] }>();

/** node → 中文名（执行蓝本 §6 给定映射；未知 node 原样显示）。 */
const NODE_LABELS: Record<string, string> = {
  evidence_fetcher: '证据检索',
  TutorAgent: '导师作答',
  'TutorAgent.revise': '导师返工',
  grounding_critic: '引用审查',
  hallucination_detector: '防幻觉核查',
  safety_critic: '安全审查',
  code_runner: '沙箱验证',
  debate: '辩论复核',
};

function nodeLabel(node: string): string {
  return NODE_LABELS[node] ?? node;
}

/** 状态点档位：error/fail 红、retry 黄，其余按 ok 绿。 */
function dotClass(status: string): string {
  const s = (status || '').toLowerCase();
  if (s.includes('error') || s.includes('fail')) return 'trace__dot--error';
  if (s.includes('retry')) return 'trace__dot--retry';
  return 'trace__dot--ok';
}

function safeElapsed(ms: number): number {
  return Number.isFinite(ms) && ms > 0 ? Math.round(ms) : 0;
}

const totalMs = computed<number>(() =>
  props.traces.reduce((sum, t) => sum + safeElapsed(t.elapsed_ms), 0),
);

function fmtMs(ms: number): string {
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`;
}

interface TraceBadge {
  key: string;
  label: string;
}

/** note 命中治理关键词 → 徽章（可同时命中多枚）。 */
function badgesOf(note: string | null | undefined): TraceBadge[] {
  if (!note) return [];
  const out: TraceBadge[] = [];
  if (note.includes('fallback')) out.push({ key: 'fallback', label: '已降级' });
  if (note.includes('budget_skipped')) out.push({ key: 'budget', label: '预算跳过' });
  return out;
}

/** 无治理关键词的 note 以小字原样披露（有徽章时不重复展示原文）。 */
function plainNote(note: string | null | undefined): string {
  if (!note) return '';
  return badgesOf(note).length > 0 ? '' : note;
}
</script>

<template>
  <details
    v-if="traces.length > 0"
    class="trace"
    data-testid="tool-trace-line"
  >
    <summary class="trace__summary">
      <span
        class="trace__caret"
        aria-hidden="true"
      >▸</span>
      ⚙ 工具调用 ×{{ traces.length }} · {{ fmtMs(totalMs) }}
    </summary>
    <ul class="trace__rows">
      <li
        v-for="(t, i) in traces"
        :key="`${i}-${t.node}`"
        class="trace__row"
        data-testid="tool-trace-row"
      >
        <span
          class="trace__dot"
          :class="dotClass(t.status)"
          aria-hidden="true"
        />
        <span class="trace__label">{{ nodeLabel(t.node) }}</span>
        <span
          v-if="safeElapsed(t.elapsed_ms) > 0"
          class="trace__ms"
        >{{ safeElapsed(t.elapsed_ms) }}ms</span>
        <span
          v-for="b in badgesOf(t.note)"
          :key="b.key"
          class="trace__badge"
          :class="`trace__badge--${b.key}`"
          data-testid="tool-trace-badge"
        >{{ b.label }}</span>
        <span
          v-if="plainNote(t.note)"
          class="trace__note"
        >{{ plainNote(t.note) }}</span>
      </li>
    </ul>
  </details>
</template>

<style scoped>
.trace {
  align-self: flex-start;
  font-family: var(--pyt-font-pixel);
  font-size: 11px;
  color: var(--pyt-color-text-muted);
}
.trace__summary {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  list-style: none;
  padding: 2px 8px;
  border: 1px dashed rgba(140, 106, 35, 0.45);
  border-radius: var(--pyt-radius-full);
  background: rgba(255, 247, 230, 0.7);
}
.trace__summary::-webkit-details-marker {
  display: none;
}
.trace__summary:hover {
  color: var(--pyt-color-text-primary);
  background: #fff8df;
}
.trace__caret {
  display: inline-block;
  font-size: 9px;
  transition: transform var(--pyt-motion-base) ease;
}
.trace[open] .trace__caret {
  transform: rotate(90deg);
}
.trace__rows {
  list-style: none;
  margin: 6px 0 0;
  padding: 6px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: rgba(255, 247, 230, 0.7);
  border: 1px dashed rgba(140, 106, 35, 0.4);
  border-radius: var(--pyt-radius-md);
}
.trace__row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.trace__dot {
  flex: none;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  border: 1px solid rgba(42, 26, 5, 0.35);
}
.trace__dot--ok {
  background: #4d6e1d;
}
.trace__dot--retry {
  background: #d4af37;
}
.trace__dot--error {
  background: #d9534f;
}
.trace__label {
  font-weight: 700;
  color: var(--pyt-color-text-hint);
}
.trace__ms {
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}
.trace__badge {
  font-size: 9px;
  font-weight: 700;
  padding: 0 6px;
  border-radius: var(--pyt-radius-full);
}
.trace__badge--fallback {
  background: #f5e2c1;
  border: 1px solid #c2871f;
  color: #7a5210;
}
.trace__badge--budget {
  background: #ede9fe;
  border: 1px solid #7b2cbf;
  color: #5a1da8;
}
.trace__note {
  flex: 1 1 100%;
  min-width: 0;
  font-size: 10px;
  line-height: 1.4;
  padding-left: 13px;
  word-break: break-word;
}
@media (prefers-reduced-motion: reduce) {
  .trace__caret {
    transition: none;
  }
}
</style>
