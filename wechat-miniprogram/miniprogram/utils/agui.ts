// AG-UI 语义事件归一化（小程序协议层，镜像 frontend/src/composables/agui.ts 的子集）。
//
// 后端 SSE 已迁移到 AG-UI（Agent-User Interaction Protocol）事件名：
//   RUN_STARTED / RUN_FINISHED / RUN_ERROR / STEP_STARTED / STEP_FINISHED /
//   TEXT_MESSAGE_START / TEXT_MESSAGE_CONTENT / TEXT_MESSAGE_END /
//   TOOL_CALL_* / STATE_SNAPSHOT / STATE_DELTA / CUSTOM
// 同时兼容项目历史事件名：plan / trace / token / result / error。
// 把一条 (eventName, rawData) 归一为类型化 AguiEvent，页面据此驱动答疑/工坊流式 UI。
// 解析失败 / 暂不消费的事件（TEXT_MESSAGE_START/END、TOOL_CALL_*）返回 null（忽略）。

export type AguiEvent =
  | { type: 'RUN_STARTED' }
  | { type: 'RUN_FINISHED'; result: unknown }
  | { type: 'RUN_ERROR'; code: string; message: string }
  | { type: 'STEP_STARTED'; step: Record<string, unknown> }
  | { type: 'STEP_FINISHED'; step: Record<string, unknown> }
  | { type: 'TEXT_MESSAGE_CONTENT'; delta: string }
  | { type: 'STATE_SNAPSHOT'; snapshot: Record<string, unknown> }
  | { type: 'STATE_DELTA'; delta: Record<string, unknown> }
  | { type: 'CUSTOM'; name: string; value: unknown };

function asRecord(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' ? (value as Record<string, unknown>) : {};
}

export function toAguiEvent(eventName: string, raw: string): AguiEvent | null {
  const name = eventName.trim();

  // 文本增量：历史 token 与官方 TEXT_MESSAGE_CONTENT 同义；JSON 失败时退化为「原文即增量」。
  if (name === 'token' || name === 'TEXT_MESSAGE_CONTENT') {
    try {
      const parsed = JSON.parse(raw) as { delta?: string };
      return { type: 'TEXT_MESSAGE_CONTENT', delta: typeof parsed.delta === 'string' ? parsed.delta : '' };
    } catch {
      return { type: 'TEXT_MESSAGE_CONTENT', delta: raw };
    }
  }

  // 运行错误：历史 error 与官方 RUN_ERROR 同义。
  if (name === 'error' || name === 'RUN_ERROR') {
    try {
      const parsed = JSON.parse(raw) as { code?: string; message?: string };
      return { type: 'RUN_ERROR', code: parsed.code ?? 'UNKNOWN', message: parsed.message ?? raw };
    } catch {
      return { type: 'RUN_ERROR', code: 'UNKNOWN', message: raw };
    }
  }

  // 运行结束：历史 result（data 即结果）与官方 RUN_FINISHED（data.result 或 data 即结果）同义。
  if (name === 'result' || name === 'RUN_FINISHED') {
    try {
      const parsed: unknown = JSON.parse(raw);
      if (
        name === 'RUN_FINISHED' &&
        parsed !== null &&
        typeof parsed === 'object' &&
        'result' in parsed
      ) {
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

  if (name === 'plan') {
    return { type: 'CUSTOM', name: 'plan', value: data };
  }
  if (name === 'step_error') {
    return { type: 'CUSTOM', name: 'step_error', value: data };
  }
  if (name === 'trace') {
    const step = asRecord(data);
    return step.phase === 'finish'
      ? { type: 'STEP_FINISHED', step }
      : { type: 'STEP_STARTED', step };
  }
  if (name === 'RUN_STARTED') {
    return { type: 'RUN_STARTED' };
  }
  if (name === 'STEP_STARTED') {
    return { type: 'STEP_STARTED', step: asRecord(data) };
  }
  if (name === 'STEP_FINISHED') {
    return { type: 'STEP_FINISHED', step: asRecord(data) };
  }
  if (name === 'STATE_SNAPSHOT') {
    const d = asRecord(data);
    return { type: 'STATE_SNAPSHOT', snapshot: asRecord(d.snapshot ?? data) };
  }
  if (name === 'STATE_DELTA') {
    const d = asRecord(data);
    return { type: 'STATE_DELTA', delta: asRecord(d.delta ?? data) };
  }
  if (name === 'CUSTOM') {
    const d = asRecord(data);
    return { type: 'CUSTOM', name: typeof d.name === 'string' ? d.name : '', value: d.value };
  }

  return null;
}
