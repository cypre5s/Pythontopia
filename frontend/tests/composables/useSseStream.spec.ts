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
    // 非资源流（无 plan 事件）：plan 恒为 null，行为与既有完全一致。
    expect(sse.plan.value).toBeNull();
  });

  it('parses a plan event and phased trace events without disturbing other streams', async () => {
    const encoder = new TextEncoder();
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(
          encoder.encode(
            'event: plan\ndata: {"type":"STORYBOARD","nodes":[' +
              '{"node":"evidence_fetcher","agent_label":"证据检索·馆员","kind":"evidence"},' +
              '{"node":"StoryboardAgent.attempt0","agent_label":"动画分镜师","kind":"designer"},' +
              '{"node":"grounding_critic","agent_label":"引用审查·审查官","kind":"grounding"}' +
              ']}\n\n',
          ),
        );
        controller.enqueue(
          encoder.encode(
            'event: trace\ndata: {"node":"evidence_fetcher","phase":"start","status":"running","elapsed_ms":0,"note":null}\n\n',
          ),
        );
        controller.enqueue(
          encoder.encode(
            'event: trace\ndata: {"node":"evidence_fetcher","phase":"finish","status":"ok","elapsed_ms":12,"note":null}\n\n',
          ),
        );
        controller.enqueue(
          encoder.encode(
            'event: result\ndata: {"runId":1,"resourceId":2,"status":"SUCCESS"}\n\n',
          ),
        );
        controller.close();
      },
    });
    const fetchMock = vi.fn(async () => new Response(body, { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    const sse = useSseStream<{ runId: number; resourceId: number; status: string }>('/stream');

    await sse.start({ kcCodes: ['PY43-KC-001'], type: 'STORYBOARD' });

    expect(sse.plan.value).not.toBeNull();
    expect(sse.plan.value?.type).toBe('STORYBOARD');
    expect(sse.plan.value?.nodes).toHaveLength(3);
    const designer = sse.plan.value?.nodes.find((n) => n.kind === 'designer');
    expect(designer?.node).toBe('StoryboardAgent.attempt0');
    expect(designer?.agent_label).toBe('动画分镜师');

    expect(sse.traces.value).toHaveLength(2);
    expect(sse.traces.value[0]?.phase).toBe('start');
    expect(sse.traces.value[0]?.status).toBe('running');
    expect(sse.traces.value[1]?.phase).toBe('finish');
    expect(sse.traces.value[1]?.status).toBe('ok');
    expect(sse.status.value).toBe('done');
    expect(sse.result.value).toEqual({ runId: 1, resourceId: 2, status: 'SUCCESS' });
  });

  it('normalizes AG-UI official event names into legacy refs and typed events', async () => {
    const encoder = new TextEncoder();
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('event: RUN_STARTED\ndata: {}\n\n'));
        controller.enqueue(
          encoder.encode(
            'event: STEP_STARTED\ndata: {"node":"evidence_fetcher","phase":"start","status":"running","elapsed_ms":0}\n\n',
          ),
        );
        controller.enqueue(
          encoder.encode(
            'event: STEP_FINISHED\ndata: {"node":"evidence_fetcher","phase":"finish","status":"ok","elapsed_ms":7}\n\n',
          ),
        );
        controller.enqueue(encoder.encode('event: TEXT_MESSAGE_CONTENT\ndata: {"delta":"你好"}\n\n'));
        controller.enqueue(encoder.encode('event: TEXT_MESSAGE_CONTENT\ndata: {"delta":"世界"}\n\n'));
        controller.enqueue(
          encoder.encode(
            'event: RUN_FINISHED\ndata: {"result":{"runId":9,"resourceId":3,"status":"SUCCESS"}}\n\n',
          ),
        );
        controller.close();
      },
    });
    const fetchMock = vi.fn(async () => new Response(body, { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    const sse = useSseStream<{ runId: number; resourceId: number; status: string }>('/stream');
    await sse.start({});

    // 官方名归一化后投影回既有 refs，行为与旧 token/trace/result 完全一致。
    expect(sse.tokens.value).toBe('你好世界');
    expect(sse.traces.value).toHaveLength(2);
    expect(sse.traces.value[0]?.node).toBe('evidence_fetcher');
    expect(sse.traces.value[1]?.status).toBe('ok');
    expect(sse.result.value).toEqual({ runId: 9, resourceId: 3, status: 'SUCCESS' });
    expect(sse.status.value).toBe('done');
    // 类型化事件流：RUN_STARTED + 2×STEP + 2×TEXT + RUN_FINISHED = 6。
    expect(sse.events.value).toHaveLength(6);
    expect(sse.events.value[0]?.type).toBe('RUN_STARTED');
    expect(sse.events.value.at(-1)?.type).toBe('RUN_FINISHED');
  });

  it('accumulates STATE_SNAPSHOT and STATE_DELTA into the state bag', async () => {
    const encoder = new TextEncoder();
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(
          encoder.encode(
            'event: STATE_SNAPSHOT\ndata: {"snapshot":{"masteryAvg":40,"energy":90}}\n\n',
          ),
        );
        controller.enqueue(encoder.encode('event: STATE_DELTA\ndata: {"delta":{"masteryAvg":55}}\n\n'));
        controller.enqueue(encoder.encode('event: result\ndata: {"ok":true}\n\n'));
        controller.close();
      },
    });
    const fetchMock = vi.fn(async () => new Response(body, { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    const sse = useSseStream('/stream');
    await sse.start({});

    // 快照打底 + 增量浅合并：masteryAvg 被覆盖为 55，energy 保留 90。
    expect(sse.state.value).toEqual({ masteryAvg: 55, energy: 90 });
    expect(sse.status.value).toBe('done');
  });

  it('keeps legacy event names working and mirrors them into typed events', async () => {
    const encoder = new TextEncoder();
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('event: token\ndata: {"delta":"abc"}\n\n'));
        controller.enqueue(encoder.encode('event: result\ndata: {"ok":true}\n\n'));
        controller.close();
      },
    });
    const fetchMock = vi.fn(async () => new Response(body, { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    const sse = useSseStream('/stream');
    await sse.start({});

    expect(sse.tokens.value).toBe('abc');
    expect(sse.events.value[0]?.type).toBe('TEXT_MESSAGE_CONTENT');
    expect(sse.events.value[1]?.type).toBe('RUN_FINISHED');
  });
});
