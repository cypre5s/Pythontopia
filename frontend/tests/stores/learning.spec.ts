import { createPinia, setActivePinia } from 'pinia';
import { describe, expect, it } from 'vitest';
import { useLearningStore } from '@/stores/learning';

describe('learning store', () => {
  it('keeps course state independent and defaults to pack 43', () => {
    setActivePinia(createPinia());
    const store = useLearningStore();

    expect(store.activePackId).toBe(43);
    expect(store.kcs).toEqual([]);
    expect(store.path).toEqual([]);
  });
});
