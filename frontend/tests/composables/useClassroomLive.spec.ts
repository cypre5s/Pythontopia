import { defineComponent, h } from 'vue';
import { mount, type VueWrapper } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useClassroomLive, type UseClassroomLiveReturn } from '@/composables/useClassroomLive';
import { useSessionStore } from '@stores/session';

const encoder = new TextEncoder();

/** 在真实组件 setup 中创建 composable，确保 onBeforeUnmount 生效。 */
function mountLive(): { live: UseClassroomLiveReturn; wrapper: VueWrapper } {
  let live!: UseClassroomLiveReturn;
  const wrapper = mount(
    defineComponent({
      setup() {
        live = useClassroomLive();
        return () => h('div');
      },
    }),
  );
  return { live, wrapper };
}

function sseResponse(blocks: string[], { keepOpen = false } = {}): Response {
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const block of blocks) {
        controller.enqueue(encoder.encode(block));
      }
      if (!keepOpen) controller.close();
    },
  });
  return new Response(body, { status: 200 });
}

describe('useClassroomLive', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('用 fetch 带 Bearer token 订阅，并解析 connected/submission/quest_complete 事件', async () => {
    const session = useSessionStore();
    session.token = 'test-token';

    const fetchMock = vi.fn(async () =>
      sseResponse(
        [
          'event: connected\ndata: {}\n\n',
          'event: submission\ndata: {"studentId":11,"status":"PASSED"}\n\n',
          'event: quest_complete\ndata: {"questId":7}\n\n',
        ],
        { keepOpen: true },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const { live, wrapper } = mountLive();
    live.connect(9);

    await vi.waitFor(() => {
      expect(live.events.value).toHaveLength(2);
    });

    expect(fetchMock).toHaveBeenCalledOnce();
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/classroom/9/live');
    expect(init.method).toBe('GET');
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer test-token');
    expect((init.headers as Record<string, string>)['Accept']).toBe('text/event-stream');

    expect(live.connected.value).toBe(true);
    expect(live.error.value).toBeNull();
    // 新事件在前
    expect(live.events.value[0]).toMatchObject({ type: 'quest_complete', data: { questId: 7 } });
    expect(live.events.value[1]).toMatchObject({
      type: 'submission',
      data: { studentId: 11, status: 'PASSED' },
    });

    wrapper.unmount();
    expect(live.connected.value).toBe(false);
  });

  it('401 → 设置权限错误并停止重连（不再无限重试）', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn(async () => new Response(null, { status: 401 }));
    vi.stubGlobal('fetch', fetchMock);

    const { live, wrapper } = mountLive();
    live.connect(9);

    await vi.waitFor(() => {
      expect(live.error.value).not.toBeNull();
    });
    expect(live.connected.value).toBe(false);
    expect(live.error.value).toContain('教师');

    await vi.advanceTimersByTimeAsync(20000);
    expect(fetchMock).toHaveBeenCalledOnce();
    wrapper.unmount();
  });

  it('流被服务端关闭 → 5 秒后自动重连', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn(async () => sseResponse(['event: connected\ndata: {}\n\n']));
    vi.stubGlobal('fetch', fetchMock);

    const { live, wrapper } = mountLive();
    live.connect(9);

    await vi.waitFor(() => {
      expect(fetchMock).toHaveBeenCalledOnce();
    });
    // 等待读取循环消费完整个流并安排重连
    await vi.advanceTimersByTimeAsync(0);
    expect(live.connected.value).toBe(false);

    await vi.advanceTimersByTimeAsync(5000);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });

  it('disconnect 取消订阅并阻止已计划的重连', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn(async () => sseResponse(['event: connected\ndata: {}\n\n']));
    vi.stubGlobal('fetch', fetchMock);

    const { live, wrapper } = mountLive();
    live.connect(9);

    await vi.waitFor(() => {
      expect(fetchMock).toHaveBeenCalledOnce();
    });
    await vi.advanceTimersByTimeAsync(0);

    live.disconnect();
    await vi.advanceTimersByTimeAsync(20000);
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(live.connected.value).toBe(false);
    wrapper.unmount();
  });
});
