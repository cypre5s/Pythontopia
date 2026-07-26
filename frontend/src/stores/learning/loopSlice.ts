import { computed, ref, type ComputedRef, type Ref } from 'vue';
import type { AguiEvent } from '@/composables/agui';
import type {
  CitationKind,
  LoopCitation,
  LoopPhase,
  LoopPlan,
  LoopStep,
  LoopStepStatus,
} from '@/stores/learning/loopTypes';

/**
 * loopSlice —— Agentic Loop（有界自主智能体闭环）前端状态机切片（F2-loop-ui）。
 *
 * 职责：消费既有 orchestrator SSE（经 useSseStream 归一化为 AG-UI 事件）累积一条有界
 * `LoopStep[]`，对外暴露 `LoopPlan`（§3.8 冻结契约）供 `LoopProgress.vue` 渲染。状态机仅做
 * 前向单调迁移（pending → active → done/failed），抵御乱序到达的步骤事件造成的回退闪烁。
 *
 * 设计约束（对齐 §0 / §3.8 红线）：
 *   - 类型一律引用 `@/stores/learning/loopTypes` 冻结契约，禁止重定义；TS strict，禁 `any`；
 *   - 仅累积可审计的 `reasonSummary`，结构上不承载任何原始 CoT 字段（loopTypes 无该字段）；
 *   - 脏 / 乱序 / 未知事件一律 fail-soft 忽略，不抛错拖垮渲染主链路；
 *   - 纯 refs + 闭包，无 pinia 依赖，可独立创建用于测试，亦可被 learning 聚合 store 组装。
 *
 * 本切片不直接订阅 HTTP；Phase 2 由父级把 `useSseStream(...).events` 通过
 * `syncFromEvents` 喂入（增量消费累积数组尾部）。
 */

/** 闭环本轮运行状态（idle 未开始 / streaming 进行中 / done 完成 / error 失败）。 */
export type LoopRunStatus = 'idle' | 'streaming' | 'done' | 'error';

export interface LoopSlice {
  steps: Ref<LoopStep[]>;
  maxSteps: Ref<number>;
  status: Ref<LoopRunStatus>;
  runError: Ref<string | null>;
  interrupted: Ref<boolean>;
  plan: ComputedRef<LoopPlan>;
  isStreaming: ComputedRef<boolean>;
  isComplete: ComputedRef<boolean>;
  isFailed: ComputedRef<boolean>;
  activeStep: ComputedRef<LoopStep | null>;
  reset: () => void;
  startPlan: (input: { steps: LoopStep[]; maxSteps?: number }) => void;
  applyStepUpdate: (update: LoopStep) => void;
  markStepStatus: (id: string, next: LoopStepStatus, patch?: StepPatch) => void;
  markActive: (id: string, patch?: StepPatch) => void;
  markDone: (id: string, patch?: StepPatch) => void;
  markFailed: (id: string, patch?: StepPatch) => void;
  finishRun: () => void;
  failRun: (error: string) => void;
  stop: () => void;
  ingestSseEvent: (event: AguiEvent) => void;
  syncFromEvents: (events: AguiEvent[]) => void;
}

/** 步骤可选产物补丁（迁移时附带耗时 / 工具 / 引用 / 摘要 / 错误）。 */
export type StepPatch = Partial<Omit<LoopStep, 'id' | 'phase' | 'status'>>;

/** 与 S-spine `AgenticLoopState.max_loop_steps` 默认一致（§4 S.1）。 */
const DEFAULT_MAX_STEPS = 4;

/** 状态机秩：done / failed 同为终态（秩 2），保证前向单调比较。 */
const STATUS_RANK: Record<LoopStepStatus, number> = {
  pending: 0,
  active: 1,
  done: 2,
  failed: 2,
};

const LOOP_PHASES: readonly LoopPhase[] = ['plan', 'recall', 'retrieve', 'act', 'critique', 'compose'];
const LOOP_STATUSES: readonly LoopStepStatus[] = ['pending', 'active', 'done', 'failed'];
const CITATION_KINDS: readonly CitationKind[] = ['courseware', 'external'];

function isLoopPhase(value: unknown): value is LoopPhase {
  return typeof value === 'string' && (LOOP_PHASES as readonly string[]).includes(value);
}

function isLoopStatus(value: unknown): value is LoopStepStatus {
  return typeof value === 'string' && (LOOP_STATUSES as readonly string[]).includes(value);
}

function isCitationKind(value: unknown): value is CitationKind {
  return typeof value === 'string' && (CITATION_KINDS as readonly string[]).includes(value);
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' ? (value as Record<string, unknown>) : null;
}

function coerceCitations(value: unknown): LoopCitation[] | null {
  if (!Array.isArray(value)) return null;
  const out: LoopCitation[] = [];
  for (const raw of value) {
    const r = asRecord(raw);
    if (!r) continue;
    if (!isCitationKind(r.kind)) continue;
    if (typeof r.ref !== 'string') continue;
    const label = typeof r.label === 'string' ? r.label : r.ref;
    out.push({ kind: r.kind, ref: r.ref, label });
  }
  return out.length > 0 ? out : null;
}

/** 把不可信的 SSE 载荷收敛成合法 `LoopStep`；任一必填字段非法 → null（fail-soft 丢弃）。 */
function coerceStep(value: unknown): LoopStep | null {
  const r = asRecord(value);
  if (!r) return null;
  if (typeof r.id !== 'string' || r.id.length === 0) return null;
  if (!isLoopPhase(r.phase)) return null;
  if (!isLoopStatus(r.status)) return null;

  const out: LoopStep = {
    id: r.id,
    phase: r.phase,
    label: typeof r.label === 'string' && r.label.length > 0 ? r.label : r.phase,
    status: r.status,
  };
  if (typeof r.toolName === 'string') out.toolName = r.toolName;
  if (typeof r.durationMs === 'number' && Number.isFinite(r.durationMs)) out.durationMs = r.durationMs;
  if (typeof r.reasonSummary === 'string') out.reasonSummary = r.reasonSummary;
  if (typeof r.error === 'string') out.error = r.error;
  const citations = coerceCitations(r.citations);
  if (citations) out.citations = citations;
  return out;
}

function coercePlan(value: unknown): { steps: LoopStep[]; maxSteps?: number } {
  const r = asRecord(value);
  if (!r) return { steps: [] };
  const rawSteps = Array.isArray(r.steps) ? r.steps : [];
  const steps: LoopStep[] = [];
  for (const raw of rawSteps) {
    const s = coerceStep(raw);
    if (s) steps.push(s);
  }
  if (typeof r.maxSteps === 'number' && Number.isFinite(r.maxSteps)) {
    return { steps, maxSteps: r.maxSteps };
  }
  return { steps };
}

export function createLoopSlice(): LoopSlice {
  const steps = ref<LoopStep[]>([]);
  const maxSteps = ref<number>(DEFAULT_MAX_STEPS);
  const status = ref<LoopRunStatus>('idle');
  const runError = ref<string | null>(null);
  const interrupted = ref<boolean>(false);
  /** syncFromEvents 已消费的累积事件数（增量消费游标）。 */
  const consumed = ref<number>(0);

  const plan = computed<LoopPlan>(() => ({
    steps: steps.value,
    bounded: true,
    maxSteps: maxSteps.value,
  }));
  const isStreaming = computed<boolean>(() => status.value === 'streaming');
  const isComplete = computed<boolean>(() => status.value === 'done');
  const isFailed = computed<boolean>(() => status.value === 'error');
  const activeStep = computed<LoopStep | null>(
    () => steps.value.find((s) => s.status === 'active') ?? null,
  );

  function reset(): void {
    steps.value = [];
    maxSteps.value = DEFAULT_MAX_STEPS;
    status.value = 'idle';
    runError.value = null;
    interrupted.value = false;
    consumed.value = 0;
  }

  function startPlan(input: { steps: LoopStep[]; maxSteps?: number }): void {
    steps.value = input.steps.map((s) => ({ ...s }));
    // 维持「有界」不变式：steps.length ≤ maxSteps。S 给出的上界与步数取较大者。
    const requested = input.maxSteps ?? DEFAULT_MAX_STEPS;
    maxSteps.value = Math.max(requested, input.steps.length);
    status.value = 'streaming';
    runError.value = null;
    interrupted.value = false;
  }

  function applyStepUpdate(update: LoopStep): void {
    const idx = steps.value.findIndex((s) => s.id === update.id);
    if (idx === -1) {
      steps.value = [...steps.value, { ...update }];
      return;
    }
    const current = steps.value[idx];
    if (!current) return;
    const forward = STATUS_RANK[update.status] >= STATUS_RANK[current.status];
    const merged: LoopStep = {
      id: current.id,
      phase: update.phase,
      label: update.label || current.label,
      status: forward ? update.status : current.status,
    };
    const toolName = update.toolName ?? current.toolName;
    if (toolName !== undefined) merged.toolName = toolName;
    const durationMs = update.durationMs ?? current.durationMs;
    if (durationMs !== undefined) merged.durationMs = durationMs;
    const reasonSummary = update.reasonSummary ?? current.reasonSummary;
    if (reasonSummary !== undefined) merged.reasonSummary = reasonSummary;
    const error = update.error ?? current.error;
    if (error !== undefined) merged.error = error;
    const citations = update.citations ?? current.citations;
    if (citations !== undefined) merged.citations = citations;

    const next = steps.value.slice();
    next[idx] = merged;
    steps.value = next;
  }

  function markStepStatus(id: string, nextStatus: LoopStepStatus, patch: StepPatch = {}): void {
    const current = steps.value.find((s) => s.id === id);
    if (!current) return; // 不创建幽灵步骤：仅推进计划内已存在的步骤
    applyStepUpdate({ ...current, ...patch, id, phase: current.phase, status: nextStatus });
  }

  function markActive(id: string, patch?: StepPatch): void {
    markStepStatus(id, 'active', patch);
  }
  function markDone(id: string, patch?: StepPatch): void {
    markStepStatus(id, 'done', patch);
  }
  function markFailed(id: string, patch?: StepPatch): void {
    markStepStatus(id, 'failed', patch);
  }

  function finishRun(): void {
    if (status.value === 'error') return; // 失败态不被「完成」覆盖
    status.value = 'done';
  }

  function failRun(error: string): void {
    runError.value = error;
    status.value = 'error';
    // 兜底：把仍在进行中的步骤落定为失败，让错误态在时间线上有明确归属。
    const active = steps.value.find((s) => s.status === 'active');
    if (active) {
      markStepStatus(active.id, 'failed', { error: active.error ?? error });
    }
  }

  function stop(): void {
    interrupted.value = true;
    if (status.value === 'streaming') {
      status.value = 'done'; // 与 useSseStream.abort 一致：中断按「优雅结束」处理
    }
  }

  function ingestSseEvent(event: AguiEvent): void {
    switch (event.type) {
      case 'RUN_STARTED':
        if (status.value === 'idle') status.value = 'streaming';
        break;
      case 'CUSTOM':
        if (event.name === 'loop_plan') {
          startPlan(coercePlan(event.value));
        } else if (event.name === 'loop_step') {
          const next = coerceStep(event.value);
          if (next) applyStepUpdate(next);
        }
        break;
      case 'RUN_FINISHED':
        finishRun();
        break;
      case 'RUN_ERROR':
        failRun(event.message || event.code || '闭环执行失败');
        break;
      default:
        break;
    }
  }

  function syncFromEvents(events: AguiEvent[]): void {
    // 累积数组缩短（如重新发起一轮使 events 被重置）→ 整体复位后重放。
    // 只把游标归零是不够的：steps / status / runError 会带着上一轮的「完成」态残留，
    // 而 RUN_STARTED 仅在 status==='idle' 时推进，新一轮的失败/进度就再也显示不出来。
    if (events.length < consumed.value) reset();
    for (let i = consumed.value; i < events.length; i += 1) {
      const ev = events[i];
      if (ev) ingestSseEvent(ev);
    }
    consumed.value = events.length;
  }

  return {
    steps,
    maxSteps,
    status,
    runError,
    interrupted,
    plan,
    isStreaming,
    isComplete,
    isFailed,
    activeStep,
    reset,
    startPlan,
    applyStepUpdate,
    markStepStatus,
    markActive,
    markDone,
    markFailed,
    finishRun,
    failRun,
    stop,
    ingestSseEvent,
    syncFromEvents,
  };
}
