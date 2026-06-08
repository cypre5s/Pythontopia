import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ref, nextTick } from 'vue';
import { createPinia, setActivePinia } from 'pinia';

const startSpy = vi.fn(async () => {});
const tokens = ref('');
const result = ref<unknown>(null);
const status = ref('idle');

vi.mock('@/composables/useSseStream', () => ({
  useSseStream: () => ({
    traces: ref([]),
    tokens,
    result,
    error: ref(null),
    status,
    plan: ref(null),
    abort: vi.fn(),
    start: startSpy,
  }),
}));

import { useCompanionStore } from '@/stores/companion';

describe('companion store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    startSpy.mockClear();
    tokens.value = '';
    result.value = null;
    status.value = 'idle';
  });

  it('sends question + kcCodes + context to /api/tutor/ask', async () => {
    const store = useCompanionStore();
    await store.ask('什么是列表推导', { kcCodes: ['PY43-KC-016'], surface: 'training', context: 'c' });
    expect(startSpy).toHaveBeenCalledTimes(1);
    expect(startSpy.mock.calls[0][0]).toMatchObject({
      question: '什么是列表推导',
      kcCodes: ['PY43-KC-016'],
      context: 'c',
    });
  });

  it('accumulates streamed tokens into the last assistant message', async () => {
    const store = useCompanionStore();
    await store.ask('hi', { kcCodes: [], surface: 'world', context: '' });
    tokens.value = '部分答案';
    await nextTick();
    const last = store.messages[store.messages.length - 1];
    expect(last.role).toBe('assistant');
    expect(last.content).toBe('部分答案');
  });

  it('caps images at 3', async () => {
    const store = useCompanionStore();
    await store.ask('看图', { kcCodes: [], surface: 'world', context: '' }, ['a', 'b', 'c', 'd']);
    expect((startSpy.mock.calls[0][0] as { images: string[] }).images).toHaveLength(3);
  });
});
