import { ref, type Ref } from 'vue';
import { useSessionStore } from '@stores/session';

export interface SseTraceEvent {
  node: string;
  status: string;
  elapsed_ms: number;
  /** evidence_fetcher：命中的课件证据段数。 */
  chunks?: number;
  /** image_understanding：当前/总图片序号（看图答疑多图进度）。 */
  index?: number;
  total?: number;
  /** image_understanding：识别描述字符数。 */
  chars?: number;
}

export interface SseStreamState<T = unknown> {
  traces: Ref<SseTraceEvent[]>;
  tokens: Ref<string>;
  result: Ref<T | null>;
  error: Ref<{ code: string; message: string } | null>;
  status: Ref<SseStatus>;
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
      case 'trace': {
        try {
          const t = JSON.parse(raw) as SseTraceEvent;
          traces.value = [...traces.value, t];
        } catch { /* malformed trace */ }
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
        } catch { /* malformed result */ }
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

  return { traces, tokens, result, error, status, abort, start };
}
