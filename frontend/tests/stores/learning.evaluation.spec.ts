import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useLearningStore } from '@/stores/learning';
import type { EvaluationDto } from '@/api/dto/Evaluation';

vi.mock('@/api/evaluation', () => ({
  listEvaluations: vi.fn(),
  getEvaluation: vi.fn(),
  runEvaluation: vi.fn(),
  acceptEvaluationPath: vi.fn(),
  keepOldEvaluationPath: vi.fn(),
}));

vi.mock('@/api/path', () => ({
  getActiveLearningPath: vi.fn().mockResolvedValue(null),
  getArchivedLearningPaths: vi.fn().mockResolvedValue([]),
  planLearningPath: vi.fn(),
}));

vi.mock('@/api/quests', () => ({
  getTodayQuests: vi.fn().mockResolvedValue([]),
  startQuest: vi.fn(),
  completeQuest: vi.fn(),
  skipQuest: vi.fn(),
}));

import {
  listEvaluations,
  runEvaluation,
  acceptEvaluationPath,
  keepOldEvaluationPath,
} from '@/api/evaluation';

const SAMPLE: EvaluationDto = {
  id: 101,
  userId: 7,
  packId: 43,
  periodStart: '2026-06-06T00:00:00Z',
  periodEnd: '2026-06-13T00:00:00Z',
  narrativeSummary: '本周复盘',
  pathDiffSummary: '差异说明',
  draftPathId: 999,
  activePathId: 888,
  createdAt: '2026-06-13T10:00:00Z',
  actedStatus: 'PENDING',
  metrics: [],
  draftPath: null,
};

describe('learning store evaluation actions', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(listEvaluations).mockResolvedValue([SAMPLE]);
    vi.mocked(runEvaluation).mockResolvedValue({ ...SAMPLE, id: 102 });
    vi.mocked(acceptEvaluationPath).mockResolvedValue({ ...SAMPLE, actedStatus: 'ACCEPTED' });
    vi.mocked(keepOldEvaluationPath).mockResolvedValue({ ...SAMPLE, actedStatus: 'KEPT_OLD' });
  });

  it('loadEvaluations sets list and clears error', async () => {
    const store = useLearningStore();
    await store.loadEvaluations(4);
    expect(store.evaluation.list).toHaveLength(1);
    expect(store.evaluation.lastError).toBeNull();
  });

  it('runEvaluationNow prepends result and toggles phase', async () => {
    const store = useLearningStore();
    await store.loadEvaluations(4);
    const result = await store.runEvaluationNow({ periodDays: 7 });
    expect(result).not.toBeNull();
    expect(store.evaluation.phase).toBe('ready');
    expect(store.evaluation.list[0]?.id).toBe(102);
  });

  it('acceptEvaluationPath updates list entry and current', async () => {
    const store = useLearningStore();
    await store.loadEvaluations(4);
    store.evaluation.current = SAMPLE;
    const updated = await store.acceptEvaluationPath(101);
    expect(updated?.actedStatus).toBe('ACCEPTED');
    expect(store.evaluation.current?.actedStatus).toBe('ACCEPTED');
  });

  it('keepCurrentEvaluationPath marks KEPT_OLD', async () => {
    const store = useLearningStore();
    await store.loadEvaluations(4);
    store.evaluation.current = SAMPLE;
    const updated = await store.keepCurrentEvaluationPath(101);
    expect(updated?.actedStatus).toBe('KEPT_OLD');
    expect(store.evaluation.current?.actedStatus).toBe('KEPT_OLD');
  });
});
