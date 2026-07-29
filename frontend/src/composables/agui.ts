// AG-UI 语义事件协议（前端协议层）。
//
// Pythontopia 自研 SSE 与 AG-UI（Agent-User Interaction Protocol）语义对齐：agent↔前端的
// 流式事件统一收敛为一组判别式联合类型 AguiEvent。useSseStream 作为「双语兼容层」，同时识别
//   - AG-UI 官方事件名：RUN_STARTED / RUN_FINISHED / RUN_ERROR / STEP_STARTED / STEP_FINISHED /
//     TEXT_MESSAGE_START / TEXT_MESSAGE_CONTENT / TEXT_MESSAGE_END /
//     TOOL_CALL_START / TOOL_CALL_ARGS / TOOL_CALL_END / TOOL_CALL_RESULT /
//     STATE_SNAPSHOT / STATE_DELTA / CUSTOM
//   - 项目历史事件名：plan / trace / token / result / error
// 归一化为 AguiEvent 后，再由 useSseStream 投影回既有 refs（traces/tokens/result/plan/error），
// 确保后端无论发新名还是旧名，现有面板零回归。
//
// 仅用 `import type` 引用 useSseStream 的历史类型，编译期擦除、无运行时循环依赖
// （运行时依赖单向：useSseStream → agui）。

import type { SsePlan, SseTraceEvent } from './useSseStream';
import type { Operation as JsonPatchOperation } from 'fast-json-patch';

/** AG-UI 核心事件类型（判别式联合的 tag）。 */
export type AguiEventType =
  | 'RUN_STARTED'
  | 'RUN_FINISHED'
  | 'RUN_ERROR'
  | 'STEP_STARTED'
  | 'STEP_FINISHED'
  | 'TEXT_MESSAGE_START'
  | 'TEXT_MESSAGE_CONTENT'
  | 'TEXT_MESSAGE_END'
  | 'TOOL_CALL_START'
  | 'TOOL_CALL_ARGS'
  | 'TOOL_CALL_END'
  | 'TOOL_CALL_RESULT'
  | 'STATE_SNAPSHOT'
  | 'STATE_DELTA'
  | 'CUSTOM';

/** 运行开始（一次 agent run 的生命周期起点）。 */
export interface AguiRunStarted {
  type: 'RUN_STARTED';
}

/** 运行成功结束，携带最终结构化结果（投影到 useSseStream.result）。 */
export interface AguiRunFinished {
  type: 'RUN_FINISHED';
  result: unknown;
}

/** 运行失败（投影到 useSseStream.error）。 */
export interface AguiRunError {
  type: 'RUN_ERROR';
  code: string;
  message: string;
}

/**
 * 节点/步骤开始。step 沿用历史 SseTraceEvent 形状（node/status/elapsed_ms/role_key…），
 * 投影到 useSseStream.traces，让既有思考链/工作室人物 UI 零改动继续点亮。
 */
export interface AguiStepStarted {
  type: 'STEP_STARTED';
  step: SseTraceEvent;
}

/** 节点/步骤结束（同样投影到 traces）。 */
export interface AguiStepFinished {
  type: 'STEP_FINISHED';
  step: SseTraceEvent;
}

/** 文本消息开始（分帧，支撑一条流承载多条消息；不影响 tokens 累积）。 */
export interface AguiTextMessageStart {
  type: 'TEXT_MESSAGE_START';
  messageId: string;
  role?: string;
}

/** 文本消息增量内容（投影到 useSseStream.tokens，等价历史 token 事件）。 */
export interface AguiTextMessageContent {
  type: 'TEXT_MESSAGE_CONTENT';
  delta: string;
  messageId?: string;
}

/** 文本消息结束。 */
export interface AguiTextMessageEnd {
  type: 'TEXT_MESSAGE_END';
  messageId?: string;
}

/** 工具调用开始（模块 C 工具卡生命周期用；不污染既有 traces）。 */
export interface AguiToolCallStart {
  type: 'TOOL_CALL_START';
  toolCallId: string;
  toolName: string;
}

/** 工具调用入参增量（流式 JSON 片段）。 */
export interface AguiToolCallArgs {
  type: 'TOOL_CALL_ARGS';
  toolCallId: string;
  delta: string;
}

/** 工具调用结束。 */
export interface AguiToolCallEnd {
  type: 'TOOL_CALL_END';
  toolCallId: string;
}

/** 工具调用结果。 */
export interface AguiToolCallResult {
  type: 'TOOL_CALL_RESULT';
  toolCallId: string;
  content: unknown;
}

/** 状态全量快照（模块 D：覆盖 useSseStream.state）。 */
export interface AguiStateSnapshot {
  type: 'STATE_SNAPSHOT';
  snapshot: Record<string, unknown>;
}

/** AG-UI 只接受 RFC 6902 标准操作；排除 fast-json-patch 的内部 `_get` 扩展。 */
export type AguiJsonPatchOperation = Exclude<
  JsonPatchOperation,
  { op: '_get' }
>;

/** 状态增量（模块 D：RFC 6902 JSON Patch 应用到 useSseStream.state）。 */
export interface AguiStateDelta {
  type: 'STATE_DELTA';
  delta: AguiJsonPatchOperation[];
}

/** 自定义事件（承载无官方对应的语义，如历史 plan「工位清单」）。 */
export interface AguiCustom {
  type: 'CUSTOM';
  name: string;
  value: unknown;
}

export type AguiEvent =
  | AguiRunStarted
  | AguiRunFinished
  | AguiRunError
  | AguiStepStarted
  | AguiStepFinished
  | AguiTextMessageStart
  | AguiTextMessageContent
  | AguiTextMessageEnd
  | AguiToolCallStart
  | AguiToolCallArgs
  | AguiToolCallEnd
  | AguiToolCallResult
  | AguiStateSnapshot
  | AguiStateDelta
  | AguiCustom;

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : {};
}

function escapeJsonPointerToken(value: string): string {
  return value.replaceAll('~', '~0').replaceAll('/', '~1');
}

/**
 * 旧 Graph 曾把 STATE_DELTA.delta 作为顶层浅合并对象发送。
 * 只在输入边界转成等价的根属性 add，支持前后端滚动发布；新生产端不再输出该形状。
 */
function legacyObjectDeltaToPatch(
  delta: Record<string, unknown>,
): AguiJsonPatchOperation[] {
  return Object.entries(delta).map(([key, value]) => ({
    op: 'add',
    path: `/${escapeJsonPointerToken(key)}`,
    value,
  }));
}

/**
 * 把一条 SSE (eventName, rawData) 归一化为类型化 AguiEvent。
 *
 * 同时接受 AG-UI 官方事件名与项目历史事件名；token / error 维持历史容错语义
 * （JSON 解析失败时把原文当增量 / 错误消息），其余事件解析失败返回 null（被忽略）。
 */
export function toAguiEvent(eventName: string, raw: string): AguiEvent | null {
  const name = eventName.trim();

  // 文本增量：历史 token 与官方 TEXT_MESSAGE_CONTENT 同义，JSON 失败时退化为「原文即增量」。
  if (name === 'token' || name === 'TEXT_MESSAGE_CONTENT') {
    try {
      const parsed = JSON.parse(raw) as { delta?: string; messageId?: string };
      return {
        type: 'TEXT_MESSAGE_CONTENT',
        delta: parsed.delta ?? '',
        messageId: parsed.messageId,
      };
    } catch {
      return { type: 'TEXT_MESSAGE_CONTENT', delta: raw };
    }
  }

  // 运行错误：历史 error 与官方 RUN_ERROR 同义，JSON 失败时仍产出 UNKNOWN 错误（与旧行为一致）。
  if (name === 'error' || name === 'RUN_ERROR') {
    try {
      const parsed = JSON.parse(raw) as { code?: string; message?: string };
      return {
        type: 'RUN_ERROR',
        code: parsed.code ?? 'UNKNOWN',
        message: parsed.message ?? raw,
      };
    } catch {
      return { type: 'RUN_ERROR', code: 'UNKNOWN', message: raw };
    }
  }

  // 运行结束：历史 result（data 即结果）与官方 RUN_FINISHED（data.result 或 data 即结果）同义。
  // JSON 失败时仍产出 RUN_FINISHED（result=null），保持「收到结束事件即终止流」的历史语义。
  if (name === 'result' || name === 'RUN_FINISHED') {
    try {
      const parsed = JSON.parse(raw);
      if (name === 'RUN_FINISHED' && parsed && typeof parsed === 'object' && 'result' in parsed) {
        return { type: 'RUN_FINISHED', result: (parsed as { result: unknown }).result };
      }
      return { type: 'RUN_FINISHED', result: parsed };
    } catch {
      return { type: 'RUN_FINISHED', result: null };
    }
  }

  let data: unknown;
  try {
    data = JSON.parse(raw);
  } catch {
    return null;
  }

  switch (name) {
    // ── 历史事件名 ──
    case 'plan':
      return { type: 'CUSTOM', name: 'plan', value: data as SsePlan };
    // 单步派发失败（非终止）：计划中某个 agent 失败，整轮继续、result 仍会到达；
    // 不映射 RUN_ERROR（那会把整轮置为失败态），步骤失败展示由 STATE_DELTA 承担。
    case 'step_error':
      return { type: 'CUSTOM', name: 'step_error', value: data };
    case 'trace': {
      const step = data as SseTraceEvent;
      return step.phase === 'finish'
        ? { type: 'STEP_FINISHED', step }
        : { type: 'STEP_STARTED', step };
    }

    // ── AG-UI 官方事件名 ──
    case 'RUN_STARTED':
      return { type: 'RUN_STARTED' };
    case 'STEP_STARTED':
      return { type: 'STEP_STARTED', step: data as SseTraceEvent };
    case 'STEP_FINISHED':
      return { type: 'STEP_FINISHED', step: data as SseTraceEvent };
    case 'TEXT_MESSAGE_START': {
      const d = data as { messageId?: string; role?: string };
      return { type: 'TEXT_MESSAGE_START', messageId: d.messageId ?? '', role: d.role };
    }
    case 'TEXT_MESSAGE_END': {
      const d = data as { messageId?: string };
      return { type: 'TEXT_MESSAGE_END', messageId: d.messageId };
    }
    case 'TOOL_CALL_START': {
      const d = data as { toolCallId?: string; toolName?: string };
      return { type: 'TOOL_CALL_START', toolCallId: d.toolCallId ?? '', toolName: d.toolName ?? '' };
    }
    case 'TOOL_CALL_ARGS': {
      const d = data as { toolCallId?: string; delta?: string };
      return { type: 'TOOL_CALL_ARGS', toolCallId: d.toolCallId ?? '', delta: d.delta ?? '' };
    }
    case 'TOOL_CALL_END': {
      const d = data as { toolCallId?: string };
      return { type: 'TOOL_CALL_END', toolCallId: d.toolCallId ?? '' };
    }
    case 'TOOL_CALL_RESULT': {
      const d = data as { toolCallId?: string; content?: unknown };
      return { type: 'TOOL_CALL_RESULT', toolCallId: d.toolCallId ?? '', content: d.content };
    }
    case 'STATE_SNAPSHOT': {
      const d = data as { snapshot?: Record<string, unknown> };
      return { type: 'STATE_SNAPSHOT', snapshot: d.snapshot ?? asRecord(data) };
    }
    case 'STATE_DELTA': {
      const d = data as { delta?: unknown };
      const delta = d.delta;
      return {
        type: 'STATE_DELTA',
        delta: Array.isArray(delta)
          ? (delta as AguiJsonPatchOperation[])
          : legacyObjectDeltaToPatch(asRecord(delta)),
      };
    }
    case 'CUSTOM': {
      const d = data as { name?: string; value?: unknown };
      return { type: 'CUSTOM', name: d.name ?? '', value: d.value };
    }
    default:
      return null;
  }
}
