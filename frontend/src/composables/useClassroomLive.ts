import { onBeforeUnmount, ref, type Ref } from 'vue';
import { useSessionStore } from '@stores/session';

export interface LiveEvent {
  type: string;
  data: Record<string, unknown>;
  timestamp: string;
}

export interface UseClassroomLiveReturn {
  connected: Ref<boolean>;
  events: Ref<LiveEvent[]>;
  /** 权限错误（401/403）提示；非空时已停止自动重连。 */
  error: Ref<string | null>;
  connect: (classroomId: number) => void;
  disconnect: () => void;
}

const MAX_EVENTS = 50;
const RECONNECT_DELAY_MS = 5000;

/**
 * 教师课堂视图的 SSE 实时事件流，连接 GET /api/classroom/{classroomId}/live。
 *
 * 后端该端点要求 JWT（Authorization: Bearer）且原生 EventSource 无法携带请求头，
 * 故此处用 fetch + ReadableStream 手动解析 text/event-stream（同 useSseStream 的做法）。
 * 断线后每 5 秒自动重连；401/403 视为权限错误，不再重连并暴露 error。
 */
export function useClassroomLive(): UseClassroomLiveReturn {
  const connected = ref(false);
  const events = ref<LiveEvent[]>([]);
  const error = ref<string | null>(null);

  let controller: AbortController | null = null;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  let activeClassroomId: number | null = null;
  // 代数计数器：disconnect/重新 connect 后递增，让旧的读取循环与重连定时器失效。
  let generation = 0;

  function pushEvent(type: string, raw: string): void {
    try {
      const data = JSON.parse(raw) as Record<string, unknown>;
      events.value = [
        { type, data, timestamp: new Date().toISOString() },
        ...events.value,
      ].slice(0, MAX_EVENTS);
    } catch { /* 格式错误，忽略 */ }
  }

  function handleBlock(block: string): void {
    let event = 'message';
    const dataLines: string[] = [];
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart());
      }
    }

    if (event === 'connected') {
      connected.value = true;
      return;
    }
    if (dataLines.length === 0) return;
    if (event === 'submission' || event === 'quest_complete') {
      pushEvent(event, dataLines.join('\n'));
    }
  }

  function scheduleReconnect(classroomId: number, gen: number): void {
    if (gen !== generation) return;
    connected.value = false;
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      if (gen === generation) connect(classroomId);
    }, RECONNECT_DELAY_MS);
  }

  async function run(classroomId: number, gen: number): Promise<void> {
    const session = useSessionStore();
    const headers: Record<string, string> = { Accept: 'text/event-stream' };
    if (session.token) {
      headers['Authorization'] = `Bearer ${session.token}`;
    }

    let response: Response;
    try {
      response = await fetch(`/api/classroom/${classroomId}/live`, {
        method: 'GET',
        headers,
        signal: controller?.signal,
      });
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === 'AbortError') return;
      scheduleReconnect(classroomId, gen);
      return;
    }

    if (gen !== generation) return;

    if (response.status === 401 || response.status === 403) {
      connected.value = false;
      error.value = '无权订阅课堂直播：请确认已使用教师账号登录。';
      controller = null;
      return;
    }

    if (!response.ok || !response.body) {
      scheduleReconnect(classroomId, gen);
      return;
    }

    const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
    let buffer = '';
    try {
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        if (gen !== generation) {
          await reader.cancel();
          return;
        }
        buffer += value;
        const blocks = buffer.split('\n\n');
        buffer = blocks.pop() ?? '';
        for (const block of blocks) {
          if (block.trim()) handleBlock(block);
        }
      }
      if (buffer.trim() && gen === generation) handleBlock(buffer);
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === 'AbortError') return;
    }

    // 服务端关闭或网络中断：与原 EventSource 行为一致，5 秒后重连。
    scheduleReconnect(classroomId, gen);
  }

  function connect(classroomId: number): void {
    if (activeClassroomId !== classroomId) {
      events.value = [];
      activeClassroomId = classroomId;
    }
    disconnect();
    error.value = null;
    const gen = ++generation;
    controller = new AbortController();
    void run(classroomId, gen);
  }

  function disconnect(): void {
    generation += 1;
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    controller?.abort();
    controller = null;
    connected.value = false;
  }

  onBeforeUnmount(() => {
    disconnect();
  });

  return { connected, events, error, connect, disconnect };
}
