import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useSseStream } from '@/composables/useSseStream';

describe('useSseStream', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('finishes after a result event even when the proxy keeps the SSE response open', async () => {
    const encoder = new TextEncoder();
    let cancelCalled = false;
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(
          encoder.encode(
            'event: result\ndata: {"runId":42,"resourceId":7,"status":"SUCCESS"}\n\n',
          ),
        );
      },
      cancel() {
        cancelCalled = true;
      },
    });
    const fetchMock = vi.fn(async () => new Response(body, { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    const sse = useSseStream<{ runId: number; resourceId: number; status: string }>('/stream');

    await sse.start({ kcCodes: ['PY43-KC-001'] });

    expect(sse.status.value).toBe('done');
    expect(sse.result.value).toEqual({ runId: 42, resourceId: 7, status: 'SUCCESS' });
    expect(cancelCalled).toBe(true);
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});
