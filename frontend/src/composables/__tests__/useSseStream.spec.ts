import { nextTick, watch } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('@stores/session', () => ({ useSessionStore: () => ({ token: null }) }));

import { useSseStream } from '../useSseStream';

describe('useSseStream', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('appends stream events in place while notifying existing watchers', async () => {
    vi.stubGlobal('TextDecoderStream', class TextDecoderStreamMock {});
    const chunks = [
      'event: RUN_STARTED\ndata: {}\n\n',
      'event: STEP_STARTED\ndata: {"node":"planner","status":"running","elapsed_ms":1}\n\n',
      'event: STEP_FINISHED\ndata: {"node":"planner","status":"done","elapsed_ms":2}\n\n',
      'event: RUN_FINISHED\ndata: {"result":{"ok":true}}\n\n',
    ];
    let resolveFetch: ((response: Response) => void) | undefined;
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>((resolve) => {
      resolveFetch = resolve;
    })));

    const stream = useSseStream('/stream');
    const eventLengths: number[] = [];
    const traceLengths: number[] = [];
    watch(stream.events, (events) => eventLengths.push(events.length));
    watch(stream.traces, (traces) => traceLengths.push(traces.length));

    const start = stream.start({});
    await nextTick();
    eventLengths.length = 0;
    traceLengths.length = 0;
    const eventArray = stream.events.value;
    const traceArray = stream.traces.value;

    let chunkIndex = 0;
    const reader = {
      read: async () => chunkIndex < chunks.length
        ? { done: false, value: chunks[chunkIndex++] }
        : { done: true, value: undefined },
      cancel: vi.fn(async () => undefined),
    };
    resolveFetch?.({
      ok: true,
      body: { pipeThrough: () => ({ getReader: () => reader }) },
    } as unknown as Response);
    await start;

    expect(stream.events.value).toBe(eventArray);
    expect(stream.traces.value).toBe(traceArray);
    expect(stream.events.value).toHaveLength(4);
    expect(stream.traces.value).toHaveLength(2);
    expect(eventLengths.at(-1)).toBe(4);
    expect(traceLengths.at(-1)).toBe(2);
  });
});
