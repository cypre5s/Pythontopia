import { ref, type Ref } from 'vue';
import { useSessionStore } from '@stores/session';

export interface SseTraceEvent {
  node: string;
  status: string;
  elapsed_ms: number;
  /**
   * 资源流「虚拟工作室」相位：start=节点开工，finish=节点完工。
   * 仅资源流（/api/workshop/resources/stream）发送；其它流不带，按既有逻辑处理。
   */
  phase?: 'start' | 'finish';
  /** evidence_fetcher：命中的课件证据段数。 */
  chunks?: number;
  /** image_understanding：当前/总图片序号（看图答疑多图进度）。 */
  index?: number;
  total?: number;
  /** image_understanding：识别描述字符数。 */
  chars?: number;
}

/** 资源流开场「工位清单」事件（仅资源流发送）。 */
export interface SsePlanNode {
  node: string;
  agent_label: string;
  kind: string;
}

export interface SsePlan {
  type: string;
  nodes: SsePlanNode[];
}

export interface SseStreamState<T = unknown> {
  traces: Ref<SseTraceEvent[]>;
  tokens: Ref<string>;
  result: Ref<T | null>;
  error: Ref<{ code: string; message: string } | null>;
  status: Ref<SseStatus>;
  /** 资源流开场工位清单；其它流恒为 null（不发 plan 事件，走默认分支）。 */
  plan: Ref<SsePlan | null>;
  abort: () => void;
}

type SseStatus = 'idle' | 'connecting' | 'streaming' | 'done' | 'error';

export function useSseStream<T = unknown>(url: string): {
  start: (body: unknown) => Promise<void>;
} & SseStreamState<T> {
  const traces = ref<SseTraceEvent[]>([]) as Ref<SseTraceEvent[]>;
  const tokens = ref('');
  const result = ref<T | null>(null) as Ref<T | null>;
  const error = ref<{ code: string; message: string } | null>(null);
  const status = ref<SseStatus>('idle');
  const plan = ref<SsePlan | null>(null);

  let controller: AbortController | null = null;

  function abort(): void {
    controller?.abort();
    controller = null;
    if (status.value === 'connecting' || status.value === 'streaming') {
      status.value = 'done';
    }
  }

  function hasTerminalStatus(): boolean {
    return status.value === 'done' || status.value === 'error';
  }

  async function start(body: unknown): Promise<void> {
    abort();

    traces.value = [];
    tokens.value = '';
    result.value = null;
    error.value = null;
    plan.value = null;
    status.value = 'connecting';

    controller = new AbortController();
    const session = useSessionStore();

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
    };
    if (session.token) {
      headers['Authorization'] = `Bearer ${session.token}`;
    }

    let response: Response;
    try {
      response = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
        signal: controller.signal,
      });
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === 'AbortError') return;
      error.value = {
        code: 'NETWORK_ERROR',
        message: err instanceof Error ? err.message : String(err),
      };
      status.value = 'error';
      return;
    }

    if (!response.ok) {
      error.value = {
        code: `HTTP_${response.status}`,
        message: response.statusText || `HTTP ${response.status}`,
      };
      status.value = 'error';
      return;
    }

    if (!response.body) {
      error.value = { code: 'NO_BODY', message: 'Response body is empty' };
      status.value = 'error';
      return;
    }

    status.value = 'streaming';
    const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
    let buffer = '';

    try {
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += value;
        const blocks = buffer.split('\n\n');
        buffer = blocks.pop() ?? '';

        for (const block of blocks) {
          if (!block.trim()) continue;
          const parsed = parseSseBlock(block);
          if (!parsed) continue;
          dispatch(parsed.event, parsed.data);
          if (hasTerminalStatus()) {
            await reader.cancel();
            controller = null;
            return;
          }
        }
      }
      if (buffer.trim()) {
        const parsed = parseSseBlock(buffer);
        if (parsed) {
          dispatch(parsed.event, parsed.data);
          if (hasTerminalStatus()) {
            await reader.cancel();
            controller = null;
            return;
          }
        }
      }

      if (status.value === 'streaming') {
        status.value = 'done';
      }
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === 'AbortError') return;
      error.value = {
        code: 'STREAM_ERROR',
        message: err instanceof Error ? err.message : String(err),
      };
      status.value = 'error';
    }
  }

  function parseSseBlock(block: string): { event: string; data: string } | null {
    let event = 'message';
    const dataLines: string[] = [];

    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart());
      }
    }

    if (dataLines.length === 0) return null;
    return { event, data: dataLines.join('\n') };
  }

  function dispatch(event: string, raw: string): void {
    switch (event) {
      case 'plan': {
        try {
          plan.value = JSON.parse(raw) as SsePlan;
        } catch { /* plan 格式错误，忽略 */ }
        break;
      }
      case 'trace': {
        try {
          const t = JSON.parse(raw) as SseTraceEvent;
          traces.value = [...traces.value, t];
        } catch { /* trace 格式错误，忽略 */ }
        break;
      }
      case 'token': {
        try {
          const parsed = JSON.parse(raw) as { delta: string };
          tokens.value += parsed.delta;
        } catch {
          tokens.value += raw;
        }
        break;
      }
      case 'result': {
        try {
          result.value = JSON.parse(raw) as T;
        } catch { /* result 格式错误，忽略 */ }
        status.value = 'done';
        break;
      }
      case 'error': {
        try {
          const parsed = JSON.parse(raw) as { code: string; message: string };
          error.value = parsed;
        } catch {
          error.value = { code: 'UNKNOWN', message: raw };
        }
        status.value = 'error';
        break;
      }
    }
  }

  return { traces, tokens, result, error, status, plan, abort, start };
}
