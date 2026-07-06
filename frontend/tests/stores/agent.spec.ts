import { createPinia, setActivePinia } from 'pinia';
import { describe, expect, it } from 'vitest';
import { useAgentStore } from '@/stores/agent';

describe('agent store', () => {
  it('starts without active generated runs', () => {
    setActivePinia(createPinia());
    const store = useAgentStore();

    expect(store.runs).toEqual([]);
    expect(store.activeRun).toBeNull();
    expect(store.logs).toEqual([]);
  });
});
