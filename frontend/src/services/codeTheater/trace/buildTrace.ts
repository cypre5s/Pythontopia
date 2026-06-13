/**
 * `buildTrace` —— 纯函数：把插桩 harness 产出的原始记录整形为契约 §5.2 的 `Trace`。
 *
 * 职责（无副作用、可 fixture 单测）：
 * - 给每条记录分配自增 `step`（从 0 开始）并保留 1 基 `line`；
 * - 把松散的 Python JSON 防御式校验并对齐到 `types.ts` 各 `TraceEvent` kind 的严格字段；
 * - 字段缺失 / 类型不符 / 未知 kind 一律**优雅降级**为 `line` 事件（红线：不假装支持任意语法）；
 * - 遵守 `MAX_TRACE_STEPS`：超限置 `truncated=true` 并停止记录（死循环防护，配合 harness 侧硬中断）。
 *
 * 这是 Python ↔ TS 的**信任边界**：输入来自 Pyodide 的 JSON，可能不完整，故全程防御式取值。
 */
import { MAX_TRACE_STEPS } from '../types';
import type { DictOpEvent, ListOpEvent, LoopIterEvent, Trace, TraceEvent } from '../types';
import type { RawTracePayload, RawTraceRecord } from './rawTypes';

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {};
}

/** 行号：强制为 ≥1 的整数，非法回退 1。 */
function coerceLine(value: unknown): number {
  const n = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(n)) return 1;
  const truncated = Math.trunc(n);
  return truncated >= 1 ? truncated : 1;
}

function coerceInt(value: unknown, fallback: number): number {
  const n = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.trunc(n);
}

function coerceString(value: unknown): string {
  if (typeof value === 'string') return value;
  if (value === null || value === undefined) return '';
  return String(value);
}

function coerceNonEmptyString(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 ? value : null;
}

function coerceStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.map(coerceString) : [];
}

const LIST_OPS: ReadonlySet<string> = new Set([
  'index',
  'append',
  'pop',
  'insert',
  'set',
  'remove',
]);
const DICT_OPS: ReadonlySet<string> = new Set(['get', 'set', 'del']);

function coerceListOp(value: unknown): ListOpEvent['op'] | null {
  return typeof value === 'string' && LIST_OPS.has(value) ? (value as ListOpEvent['op']) : null;
}

function coerceDictOp(value: unknown): DictOpEvent['op'] | null {
  return typeof value === 'string' && DICT_OPS.has(value) ? (value as DictOpEvent['op']) : null;
}

function lineEvent(step: number, line: number): TraceEvent {
  return { step, line, kind: 'line' };
}

/**
 * 把单条原始记录整形为严格 `TraceEvent`；任何不达标的输入降级为 `line` 事件。
 */
function normalizeRecord(raw: RawTraceRecord, step: number): TraceEvent {
  const r = asRecord(raw);
  const line = coerceLine(r.line);
  const kind = coerceString(r.kind);

  switch (kind) {
    case 'assign': {
      const varName = coerceNonEmptyString(r.varName);
      if (varName === null) return lineEvent(step, line);
      return {
        step,
        line,
        kind: 'assign',
        varName,
        value: coerceString(r.value),
        scope: r.scope === 'local' ? 'local' : 'global',
      };
    }
    case 'loop_iter': {
      const loopId = coerceNonEmptyString(r.loopId);
      if (loopId === null) return lineEvent(step, line);
      const event: LoopIterEvent = {
        step,
        line,
        kind: 'loop_iter',
        loopId,
        loopType: r.loopType === 'while' ? 'while' : 'for',
        iter: Math.max(coerceInt(r.iter, 0), 0),
      };
      const varName = coerceNonEmptyString(r.varName);
      if (varName !== null) event.varName = varName;
      if (typeof r.value === 'string') event.value = r.value;
      return event;
    }
    case 'branch': {
      return {
        step,
        line,
        kind: 'branch',
        cond: coerceString(r.cond),
        result: r.result === true,
        lineTaken: coerceLine(r.lineTaken),
      };
    }
    case 'call': {
      const funcName = coerceNonEmptyString(r.funcName);
      if (funcName === null) return lineEvent(step, line);
      return {
        step,
        line,
        kind: 'call',
        funcName,
        args: coerceStringArray(r.args),
        depth: Math.max(coerceInt(r.depth, 1), 0),
      };
    }
    case 'return': {
      const funcName = coerceNonEmptyString(r.funcName);
      if (funcName === null) return lineEvent(step, line);
      return {
        step,
        line,
        kind: 'return',
        funcName,
        value: coerceString(r.value),
        depth: Math.max(coerceInt(r.depth, 0), 0),
      };
    }
    case 'list_op': {
      const op = coerceListOp(r.op);
      const target = coerceNonEmptyString(r.target);
      if (op === null || target === null) return lineEvent(step, line);
      const event: ListOpEvent = {
        step,
        line,
        kind: 'list_op',
        op,
        target,
        lengthAfter: Math.max(coerceInt(r.lengthAfter, 0), 0),
      };
      if (typeof r.position === 'number' && Number.isFinite(r.position)) {
        event.position = Math.trunc(r.position);
      }
      if (typeof r.value === 'string') event.value = r.value;
      return event;
    }
    case 'dict_op': {
      const op = coerceDictOp(r.op);
      const target = coerceNonEmptyString(r.target);
      if (op === null || target === null) return lineEvent(step, line);
      const event: DictOpEvent = {
        step,
        line,
        kind: 'dict_op',
        op,
        target,
        key: coerceString(r.key),
      };
      if (typeof r.value === 'string') event.value = r.value;
      return event;
    }
    case 'output': {
      return { step, line, kind: 'output', text: coerceString(r.text) };
    }
    case 'input': {
      return {
        step,
        line,
        kind: 'input',
        prompt: coerceString(r.prompt),
        value: coerceString(r.value),
      };
    }
    case 'exception': {
      return {
        step,
        line,
        kind: 'exception',
        excType: coerceNonEmptyString(r.excType) ?? 'Exception',
        message: coerceString(r.message),
      };
    }
    case 'line':
    default:
      return lineEvent(step, line);
  }
}

/**
 * 把 harness 原始产出整形为权威 `Trace`。
 *
 * @param raw    harness 输出（sentinel JSON 反序列化）；字段不可信，逐条防御式整形。
 * @param source 学生原始代码（原样回填到 `Trace.source`）。
 */
export function buildTrace(raw: RawTracePayload, source: string): Trace {
  const records: RawTraceRecord[] = Array.isArray(raw.records) ? raw.records : [];
  const events: TraceEvent[] = [];
  let truncated = raw.truncated === true;

  for (const record of records) {
    if (events.length >= MAX_TRACE_STEPS) {
      truncated = true;
      break;
    }
    events.push(normalizeRecord(record, events.length));
  }

  return {
    version: 1,
    source: coerceString(source),
    events,
    stdout: coerceString(raw.stdout),
    truncated,
    stepCount: events.length,
  };
}
