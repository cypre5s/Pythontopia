/**
 * 调试剧场 · 诊断焦点定位（纯逻辑层）。
 *
 * 输入一次失败提交的 {@link Trace}（契约 §5.2），定位「发散行 / 出错行」并产出
 * {@link DiagnosticFocus}：出错 step + line + 当时变量快照 + 一句话现象描述。
 * 再用 {@link sliceTraceAroundFocus} 把轨迹截到「出错前后」，供舞台回放高亮。
 *
 * 设计原则（与 types.ts 一致）：无副作用、可 fixture 单测，只读 §5 契约类型，不依赖 Phaser。
 * 定位优先级：异常绊倒 > 死循环（超步数预算被截断）> 与期望输出首个偏离 > 落到最后一步。
 */

import { MAX_TRACE_STEPS } from '@/services/codeTheater/types';
import type {
  ExceptionEvent,
  LoopIterEvent,
  OutputEvent,
  Trace,
  TraceEvent,
} from '@/services/codeTheater/types';

/** 诊断焦点的类别：决定一句话现象的口吻与舞台演出方式。 */
export type DiagnosticFocusKind =
  | 'exception' // 运行时异常：傀儡在某行绊倒
  | 'infinite_loop' // 死循环 / 无限执行：超步数预算被截断
  | 'output_divergence' // 与期望输出对比的首个偏离点
  | 'last_step' // 无明显错误：落在最后一步，提示回看变量轨迹
  | 'none'; // 空轨迹，无可定位焦点

/** 一次失败执行的诊断焦点结构。 */
export interface DiagnosticFocus {
  /** 是否定位到可用焦点（空轨迹为 false）。 */
  found: boolean;
  kind: DiagnosticFocusKind;
  /** 焦点事件的 step（对齐 TraceEvent.step / RenderCommand.step）；无焦点为 -1。 */
  step: number;
  /** 1 基出错 / 发散源码行号；无焦点为 0。 */
  line: number;
  /** 焦点时刻的变量快照（重放 assign / loop_iter 累积得到，最新值优先）。 */
  variables: Readonly<Record<string, string>>;
  /** 一句话现象描述（中文，面向初学者）。 */
  phenomenon: string;
  /** 异常类型（kind === 'exception'）。 */
  excType?: string;
  /** 异常信息原文（kind === 'exception'）。 */
  message?: string;
  /** 死循环涉及的循环 id（kind === 'infinite_loop' 且能定位到循环时）。 */
  loopId?: string;
  /** 观测到的循环圈数（kind === 'infinite_loop'）。 */
  observedIterations?: number;
  /** 期望输出首个偏离处的期望文本（kind === 'output_divergence'）。 */
  expectedLine?: string;
  /** 期望输出首个偏离处的实际文本（kind === 'output_divergence'）。 */
  actualLine?: string;
}

/** {@link locateDiagnosticFocus} 选项。 */
export interface LocateFocusOptions {
  /**
   * 期望标准输出（来自判题样例）。提供后，在无异常 / 非死循环时可定位首个输出偏离点。
   * 注意：这是「期望行为」对照，不是答案；不会写回任何课件 evidence。
   */
  expectedStdout?: string;
}

/** 截到「出错前后」的子轨迹切片，供舞台回放高亮。 */
export interface FocusedTraceSlice {
  /** 子轨迹；事件保留原始 step 值以对齐 RenderCommand，不重排步号。 */
  trace: Trace;
  /** 焦点 step（== focus.step）；无焦点为 -1。 */
  focusStep: number;
  /** 焦点事件在 slice.trace.events 中的下标；不在切片内为 -1。 */
  focusIndex: number;
  /** 切片首事件的原始 step（空切片为 -1）。 */
  startStep: number;
  /** 切片尾事件的原始 step（空切片为 -1）。 */
  endStep: number;
}

/** {@link sliceTraceAroundFocus} 选项。 */
export interface SliceOptions {
  /** 焦点前保留的事件数（默认 8）。 */
  before?: number;
  /** 焦点后保留的事件数（默认 6）。 */
  after?: number;
}

const DEFAULT_BEFORE = 8;
const DEFAULT_AFTER = 6;

/**
 * 定位一次失败执行的诊断焦点。
 *
 * 优先级：异常 → 死循环（截断）→ 期望输出首个偏离 → 最后一步。空轨迹返回 found=false。
 */
export function locateDiagnosticFocus(
  trace: Trace,
  options: LocateFocusOptions = {},
): DiagnosticFocus {
  const events = trace.events;
  if (events.length === 0) {
    return emptyFocus();
  }

  const exceptionEvent = events.find((event): event is ExceptionEvent => event.kind === 'exception');
  if (exceptionEvent) {
    return buildExceptionFocus(events, exceptionEvent);
  }

  if (trace.truncated) {
    return buildInfiniteLoopFocus(events);
  }

  if (options.expectedStdout != null) {
    const divergence = locateOutputDivergence(events, options.expectedStdout);
    if (divergence) {
      return divergence;
    }
  }

  return buildLastStepFocus(events);
}

/**
 * 把整条轨迹截到焦点「前 before / 后 after」事件，供回放只演关键片段。
 * 保留事件原始 step，不重排——这样高亮 / seek 仍能对齐 RenderCommand.step。
 */
export function sliceTraceAroundFocus(
  trace: Trace,
  focus: DiagnosticFocus,
  options: SliceOptions = {},
): FocusedTraceSlice {
  const before = options.before ?? DEFAULT_BEFORE;
  const after = options.after ?? DEFAULT_AFTER;
  const events = trace.events;

  if (events.length === 0) {
    return {
      trace: rebuildTrace(trace, []),
      focusStep: -1,
      focusIndex: -1,
      startStep: -1,
      endStep: -1,
    };
  }

  let centerIndex = focus.found ? indexOfStep(events, focus.step) : events.length - 1;
  if (centerIndex < 0) {
    centerIndex = events.length - 1;
  }

  const start = Math.max(0, centerIndex - Math.max(0, before));
  const end = Math.min(events.length, centerIndex + Math.max(0, after) + 1);
  const sliced = events.slice(start, end);

  const focusIndex = focus.found ? sliced.findIndex((event) => event.step === focus.step) : -1;
  const first = sliced[0];
  const last = sliced[sliced.length - 1];

  return {
    trace: rebuildTrace(trace, sliced),
    focusStep: focus.found ? focus.step : -1,
    focusIndex,
    startStep: first ? first.step : -1,
    endStep: last ? last.step : -1,
  };
}

function rebuildTrace(source: Trace, events: TraceEvent[]): Trace {
  return {
    version: 1,
    source: source.source,
    events,
    stdout: source.stdout,
    truncated: source.truncated,
    stepCount: events.length,
  };
}

function emptyFocus(): DiagnosticFocus {
  return {
    found: false,
    kind: 'none',
    step: -1,
    line: 0,
    variables: {},
    phenomenon: '这条轨迹是空的，没有可定位的执行步骤。',
  };
}

function buildExceptionFocus(events: readonly TraceEvent[], event: ExceptionEvent): DiagnosticFocus {
  const index = indexOfStep(events, event.step);
  const variables = snapshotVariablesUpTo(events, index);
  const message = truncate(event.message, 80);
  const phenomenon =
    `运行时绊倒：傀儡在第 ${event.line} 行被 ${event.excType} 绊倒` +
    (message ? `（${message}）` : '') +
    '。';
  return {
    found: true,
    kind: 'exception',
    step: event.step,
    line: event.line,
    variables,
    phenomenon,
    excType: event.excType,
    message: event.message,
  };
}

function buildInfiniteLoopFocus(events: readonly TraceEvent[]): DiagnosticFocus {
  const lastLoop = findLastLoopIter(events);
  if (lastLoop) {
    const observed = maxIterationOf(events, lastLoop.loopId) + 1;
    const index = indexOfStep(events, lastLoop.step);
    const variables = snapshotVariablesUpTo(events, index);
    const loopTypeLabel = lastLoop.loopType === 'while' ? 'while' : 'for';
    const phenomenon =
      `死循环：傀儡在第 ${lastLoop.line} 行的 ${loopTypeLabel} 循环里原地踱步，` +
      `已绕 ${observed}+ 圈仍停不下来（超 ${MAX_TRACE_STEPS} 步预算被强制截断）。`;
    return {
      found: true,
      kind: 'infinite_loop',
      step: lastLoop.step,
      line: lastLoop.line,
      variables,
      phenomenon,
      loopId: lastLoop.loopId,
      observedIterations: observed,
    };
  }

  const last = events[events.length - 1]!;
  const variables = snapshotVariablesUpTo(events, events.length - 1);
  const phenomenon =
    `疑似停不下来：脚本执行到第 ${last.line} 行附近仍未结束，` +
    `超 ${MAX_TRACE_STEPS} 步预算被强制截断（可能是无限递归或规模过大）。`;
  return {
    found: true,
    kind: 'infinite_loop',
    step: last.step,
    line: last.line,
    variables,
    phenomenon,
  };
}

function locateOutputDivergence(
  events: readonly TraceEvent[],
  expectedStdout: string,
): DiagnosticFocus | null {
  const expectedLines = splitLines(expectedStdout);
  const outputs = events.filter((event): event is OutputEvent => event.kind === 'output');

  for (let i = 0; i < outputs.length; i += 1) {
    const output = outputs[i]!;
    const expected = expectedLines[i];
    if (expected === undefined) {
      const index = indexOfStep(events, output.step);
      return {
        found: true,
        kind: 'output_divergence',
        step: output.step,
        line: output.line,
        variables: snapshotVariablesUpTo(events, index),
        phenomenon:
          `输出跑偏：期望只到第 ${expectedLines.length} 行，傀儡却在第 ${output.line} 行` +
          `又多喊了一句「${truncate(output.text, 60)}」。`,
        expectedLine: '',
        actualLine: output.text,
      };
    }
    if (rstrip(output.text) !== rstrip(expected)) {
      const index = indexOfStep(events, output.step);
      return {
        found: true,
        kind: 'output_divergence',
        step: output.step,
        line: output.line,
        variables: snapshotVariablesUpTo(events, index),
        phenomenon:
          `输出跑偏：第一处不符在第 ${i + 1} 句输出（第 ${output.line} 行）——` +
          `期望「${truncate(expected, 60)}」，傀儡却喊出「${truncate(output.text, 60)}」。`,
        expectedLine: expected,
        actualLine: output.text,
      };
    }
  }

  if (outputs.length < expectedLines.length) {
    const lastOutput = outputs[outputs.length - 1];
    if (lastOutput) {
      const index = indexOfStep(events, lastOutput.step);
      return {
        found: true,
        kind: 'output_divergence',
        step: lastOutput.step,
        line: lastOutput.line,
        variables: snapshotVariablesUpTo(events, index),
        phenomenon:
          `输出跑偏：期望 ${expectedLines.length} 行输出，实际只喊了 ${outputs.length} 句，` +
          `第 ${outputs.length + 1} 句之后就没下文了。`,
        expectedLine: expectedLines[outputs.length] ?? '',
        actualLine: '',
      };
    }
    const first = events[0]!;
    return {
      found: true,
      kind: 'output_divergence',
      step: first.step,
      line: first.line,
      variables: {},
      phenomenon: `输出跑偏：期望 ${expectedLines.length} 行输出，但傀儡一句都没喊出来。`,
      expectedLine: expectedLines[0] ?? '',
      actualLine: '',
    };
  }

  return null;
}

function buildLastStepFocus(events: readonly TraceEvent[]): DiagnosticFocus {
  const last = events[events.length - 1]!;
  const variables = snapshotVariablesUpTo(events, events.length - 1);
  return {
    found: true,
    kind: 'last_step',
    step: last.step,
    line: last.line,
    variables,
    phenomenon:
      `跑到第 ${last.line} 行就停了：没有抛错，但结果可能与期望不符——` +
      '回看变量快照，找出哪一步开始偏离你的预期。',
  };
}

/** 重放 assign / loop_iter（含到 focusIndex），累积每个变量的最新可显示值。 */
function snapshotVariablesUpTo(
  events: readonly TraceEvent[],
  focusIndex: number,
): Record<string, string> {
  const variables: Record<string, string> = {};
  const upTo = Math.min(focusIndex, events.length - 1);
  for (let i = 0; i <= upTo; i += 1) {
    const event = events[i];
    if (!event) {
      continue;
    }
    if (event.kind === 'assign') {
      variables[event.varName] = event.value;
    } else if (event.kind === 'loop_iter' && event.varName != null && event.value != null) {
      variables[event.varName] = event.value;
    }
  }
  return variables;
}

function findLastLoopIter(events: readonly TraceEvent[]): LoopIterEvent | null {
  for (let i = events.length - 1; i >= 0; i -= 1) {
    const event = events[i];
    if (event && event.kind === 'loop_iter') {
      return event;
    }
  }
  return null;
}

function maxIterationOf(events: readonly TraceEvent[], loopId: string): number {
  let max = 0;
  for (const event of events) {
    if (event.kind === 'loop_iter' && event.loopId === loopId && event.iter > max) {
      max = event.iter;
    }
  }
  return max;
}

function indexOfStep(events: readonly TraceEvent[], step: number): number {
  return events.findIndex((event) => event.step === step);
}

function splitLines(text: string): string[] {
  const lines = text.split(/\r?\n/);
  if (lines.length > 0 && lines[lines.length - 1] === '') {
    lines.pop();
  }
  return lines;
}

/** 去掉行尾空白，避免「期望多一个换行 / 空格」造成的伪偏离。 */
function rstrip(text: string): string {
  return text.replace(/\s+$/u, '');
}

function truncate(text: string, max: number): string {
  const trimmed = text.trim();
  return trimmed.length > max ? `${trimmed.slice(0, max)}…` : trimmed;
}
