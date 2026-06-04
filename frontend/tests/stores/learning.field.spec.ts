import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useLearningStore } from '@stores/learning';

const mocks = vi.hoisted(() => ({
  fetchMastery: vi.fn(),
  bootstrapCurriculum: vi.fn(),
}));

vi.mock('@/api/field', () => mocks);

describe('learning.field substate', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.fetchMastery.mockReset();
    mocks.bootstrapCurriculum.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loadField populates mastery snapshots', async () => {
    mocks.fetchMastery.mockResolvedValue([
      { kcCode: 'PY43-KC-001', masteryScore: 0.1, confidence: 0.05, lastEventAt: null, latestSource: 'BOOTSTRAP' },
    ]);

    const store = useLearningStore();
    await store.loadField();

    expect(store.field.masterySnapshot).toHaveLength(1);
    expect(store.field.masterySnapshot[0].kcCode).toBe('PY43-KC-001');
    expect(store.field.bootstrapped).toBe(true);
  });

  it('bootstrapField updates signature and triggers reload', async () => {
    mocks.bootstrapCurriculum.mockResolvedValue({ totalKcs: 65, unlockedCount: 8, signature: 'abc' });
    mocks.fetchMastery.mockResolvedValue([]);

    const store = useLearningStore();
    const result = await store.bootstrapField();
    expect(result.totalKcs).toBe(65);
    expect(store.field.lastSignature).toBe('abc');
    expect(mocks.fetchMastery).toHaveBeenCalledTimes(1);
  });

  it('refreshAfterEvent updates active kc and snapshots', async () => {
    const store = useLearningStore();
    store.field.activeKcCode = 'PY43-KC-001';
    mocks.fetchMastery.mockResolvedValue([
      { kcCode: 'PY43-KC-001', masteryScore: 0.3, confidence: 0.15, lastEventAt: '2026-05-21T08:00:00Z', latestSource: 'RESOURCE_READ' },
    ]);

    await store.refreshAfterEvent('PY43-KC-001');
    expect(store.field.activeKcCode).toBe('PY43-KC-001');
    expect(store.activeKcMastery?.latestSource).toBe('RESOURCE_READ');
  });
});
