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
import { getArchivedLearningPaths } from '@/api/path';

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

  it('runEvaluationNow keeps the already loaded weekly history instead of truncating it', async () => {
    // 复盘大厅按周分组渲染，进页面加载 20 条；点「立即评估」不能把历史静默截断到 8 条。
    const history: EvaluationDto[] = Array.from({ length: 20 }, (_, i) => ({
      ...SAMPLE,
      id: 200 + i,
    }));
    vi.mocked(listEvaluations).mockResolvedValue(history);

    const store = useLearningStore();
    await store.loadEvaluations(20);
    expect(store.evaluation.list).toHaveLength(20);

    await store.runEvaluationNow({ periodDays: 7 });

    expect(store.evaluation.list[0]?.id).toBe(102);
    expect(store.evaluation.list).toHaveLength(20);
    expect(store.evaluation.list.map((entry) => entry.id)).toContain(218);
  });

  it('acceptEvaluationPath refreshes the archived path list', async () => {
    // 采纳新路径 = 旧路径被归档；不刷新的话「历史路径」页签仍是旧快照。
    const store = useLearningStore();
    await store.loadEvaluations(4);
    store.evaluation.current = SAMPLE;
    vi.mocked(getArchivedLearningPaths).mockClear();

    await store.acceptEvaluationPath(101);

    expect(getArchivedLearningPaths).toHaveBeenCalledTimes(1);
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
