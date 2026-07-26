import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useLearningStore } from '@stores/learning';
import type { BugMineErrorBookItemDto, BugMineSessionDto } from '@/api/dto/BugMine';

vi.mock('@/api/bugMine', () => ({
  openBugMineSession: vi.fn(),
  getBugMineErrorBook: vi.fn(),
  getBugMineEchoes: vi.fn(),
  getBugMineSession: vi.fn(),
  openBugMineEcho: vi.fn(),
  revealBugMineCard: vi.fn(),
  retryBugMineSubmit: vi.fn(),
  variantBugMineSubmit: vi.fn(),
}));
vi.mock('@/api/problems', () => ({
  getPublicProblem: vi.fn(),
}));
vi.mock('@/api/quests', () => ({
  getTodayQuests: vi.fn(async () => []),
  listQuests: vi.fn(),
  startQuest: vi.fn(),
  completeQuest: vi.fn(),
  skipQuest: vi.fn(),
}));
vi.mock('@/api/classroomQuests', () => ({
  getMyClassroomQuests: vi.fn(),
}));
vi.mock('@/api/path', () => ({
  planLearningPath: vi.fn(),
  getActiveLearningPath: vi.fn(),
  getArchivedLearningPaths: vi.fn(),
}));

import {
  getBugMineErrorBook,
  getBugMineSession,
  openBugMineEcho,
} from '@/api/bugMine';

function errorBookItem(overrides: Partial<BugMineErrorBookItemDto> = {}): BugMineErrorBookItemDto {
  return {
    sessionId: 1,
    submissionId: 11,
    problemId: 476,
    questId: null,
    kcCode: 'PY43-KC-016',
    problemTitle: '圆面积',
    status: 'OPEN',
    retryCount: 0,
    depthLayer: 1,
    profileDepthTarget: 4,
    profileGuidance: '',
    masteryScore: 0.3,
    minePriority: 0.8,
    decayRisk: 0.5,
    recommendationReason: '',
    mistakePatterns: [],
    patternMatched: false,
    openedAt: '2026-06-01T10:00:00Z',
    variantProblem: null,
    depthSteps: [],
    ...overrides,
  };
}

function session(id: number): BugMineSessionDto {
  return {
    id,
    userId: 7,
    submissionId: 11,
    problemId: 476,
    questId: null,
    status: 'OPEN',
    retryCount: 0,
    depthLayer: 1,
    profileDepthTarget: 4,
    masteryConfirmed: false,
    variantSubmissionId: null,
    openedAt: '2026-06-01T10:00:00Z',
    closedAt: null,
    variantProblem: null,
    profileGuidance: '',
    depthSteps: [],
    cards: [],
    problem: null,
  };
}

describe('learning store bug mine slice', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    vi.mocked(getBugMineErrorBook).mockResolvedValue([]);
    vi.mocked(getBugMineSession).mockImplementation(async (id: number) => session(id));
  });

  it('lets the latest error book request win when several fire concurrently', async () => {
    // 进 /bug-mine 时页面/面板/入矿动作会并发拉取；旧响应后到不能覆盖新响应
    // （否则最后落地的可能是不含新建会话的那一份）。
    const stale = [errorBookItem({ sessionId: 1 })];
    const fresh = [errorBookItem({ sessionId: 1 }), errorBookItem({ sessionId: 2, kcCode: 'PY43-KC-020' })];
    let releaseStale: (() => void) | null = null;
    vi.mocked(getBugMineErrorBook)
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            releaseStale = () => resolve(stale);
          }),
      )
      .mockResolvedValueOnce(fresh);

    const store = useLearningStore();
    const first = store.loadBugMineErrorBook();
    const second = store.loadBugMineErrorBook();
    await second;
    expect(store.bugMine.errorBook).toHaveLength(2);

    releaseStale?.();
    await first;

    expect(store.bugMine.errorBook.map((item) => item.sessionId)).toEqual([1, 2]);
    expect(store.bugMine.errorBookLoading).toBe(false);
  });

  it('reuses the existing OPEN session instead of mining the same echo KC twice', async () => {
    const store = useLearningStore();
    store.bugMine.errorBook = [errorBookItem({ sessionId: 7, kcCode: 'PY43-KC-016' })];

    const opened = await store.openBugMineEchoByKc('PY43-KC-016');

    expect(openBugMineEcho).not.toHaveBeenCalled();
    expect(getBugMineSession).toHaveBeenCalledWith(7);
    expect(opened?.id).toBe(7);
  });

  it('still opens a fresh echo session for a KC that has no OPEN entry yet', async () => {
    vi.mocked(openBugMineEcho).mockResolvedValue(session(9));
    const store = useLearningStore();
    store.bugMine.errorBook = [errorBookItem({ sessionId: 7, kcCode: 'PY43-KC-016' })];

    const opened = await store.openBugMineEchoByKc('PY43-KC-020');

    expect(openBugMineEcho).toHaveBeenCalledWith({ kcCode: 'PY43-KC-020' });
    expect(opened?.id).toBe(9);
  });
});
