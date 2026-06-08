import { describe, it, expect, beforeEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useCompanionContext } from '@/composables/useCompanionContext';
import { useLearningStore } from '@stores/learning';

describe('useCompanionContext', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('derives kcCodes + surface from active field KC and route', () => {
    const learning = useLearningStore();
    learning.field.activeKcCode = 'PY43-KC-016';
    const ctx = useCompanionContext(() => ({ path: '/training', query: { problemId: '5' } }));
    expect(ctx.value.kcCodes).toContain('PY43-KC-016');
    expect(ctx.value.surface).toBe('training');
    expect(ctx.value.context).toContain('training');
  });

  it('falls back to empty kcCodes when nothing active', () => {
    const ctx = useCompanionContext(() => ({ path: '/world', query: {} }));
    expect(ctx.value.kcCodes).toEqual([]);
    expect(ctx.value.surface).toBe('world');
  });
});
