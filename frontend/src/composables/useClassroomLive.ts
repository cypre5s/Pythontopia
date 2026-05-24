import { onBeforeUnmount, ref, type Ref } from 'vue';

export interface LiveEvent {
  type: string;
  data: Record<string, unknown>;
  timestamp: string;
}

export interface UseClassroomLiveReturn {
  connected: Ref<boolean>;
  events: Ref<LiveEvent[]>;
  connect: (classroomId: number) => void;
  disconnect: () => void;
}

const MAX_EVENTS = 50;

/**
 * C2: SSE-based live feed for teacher classroom view.
 * Connects to GET /api/classroom/{classroomId}/live.
 */
export function useClassroomLive(): UseClassroomLiveReturn {
  const connected = ref(false);
  const events = ref<LiveEvent[]>([]);
  let source: EventSource | null = null;

  function connect(classroomId: number): void {
    disconnect();
    source = new EventSource(`/api/classroom/${classroomId}/live`);

    source.addEventListener('connected', () => {
      connected.value = true;
    });

    source.addEventListener('submission', (e: MessageEvent) => {
      try {
        const data = JSON.parse(e.data) as Record<string, unknown>;
        events.value = [
          { type: 'submission', data, timestamp: new Date().toISOString() },
          ...events.value,
        ].slice(0, MAX_EVENTS);
      } catch { /* malformed */ }
    });

    source.addEventListener('quest_complete', (e: MessageEvent) => {
      try {
        const data = JSON.parse(e.data) as Record<string, unknown>;
        events.value = [
          { type: 'quest_complete', data, timestamp: new Date().toISOString() },
          ...events.value,
        ].slice(0, MAX_EVENTS);
      } catch { /* malformed */ }
    });

    source.onerror = () => {
      connected.value = false;
    };
  }

  function disconnect(): void {
    if (source) {
      source.close();
      source = null;
    }
    connected.value = false;
  }

  onBeforeUnmount(() => {
    disconnect();
  });

  return { connected, events, connect, disconnect };
}
