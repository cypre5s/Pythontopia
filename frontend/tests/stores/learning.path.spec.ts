import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useLearningStore } from '@stores/learning';

vi.mock('@/api/path', () => ({
  planLearningPath: vi.fn(),
  getActiveLearningPath: vi.fn(),
  getArchivedLearningPaths: vi.fn(),
}));
vi.mock('@/api/quests', () => ({
  getTodayQuests: vi.fn(),
  listQuests: vi.fn(),
  startQuest: vi.fn(),
  completeQuest: vi.fn(),
  skipQuest: vi.fn(),
}));
vi.mock('@/api/classroomQuests', () => ({
  getMyClassroomQuests: vi.fn(),
}));

import {
  planLearningPath,
  getActiveLearningPath,
} from '@/api/path';
import {
  startQuest,
  completeQuest,
  getTodayQuests,
  skipQuest,
} from '@/api/quests';

describe('learning store path/quests slice', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(getActiveLearningPath).mockResolvedValue(null);
    vi.mocked(getTodayQuests).mockResolvedValue([]);
  });

  it('loadActivePath toggles idle when no path returned', async () => {
    const store = useLearningStore();
    await store.loadActivePath();
    expect(store.pathState.phase).toBe('idle');
    expect(store.pathState.active).toBeNull();
  });

  it('planPath sets ready phase and refreshes today quests', async () => {
    vi.mocked(planLearningPath).mockResolvedValue({
      path: {
        id: 1,
        userId: 7,
        packId: 43,
        version: 1,
        status: 'ACTIVE',
        goalSummary: 'demo',
        generatedAt: '2026-05-21T18:00:00Z',
        nextReviewAt: null,
        sourceRunId: null,
        nodes: [],
      },
      archivedPathIds: [],
      generatedQuestCount: 0,
      runStatus: 'SUCCESS',
      groundingMessage: 'OK',
    });
    const store = useLearningStore();
    await store.planPath();
    expect(store.pathState.phase).toBe('ready');
    expect(store.pathState.active?.id).toBe(1);
    expect(getTodayQuests).toHaveBeenCalled();
  });

  it('startQuestById replaces today quest with IN_PROGRESS state', async () => {
    vi.mocked(startQuest).mockResolvedValue({
      id: 10,
      userId: 7,
      source: 'PATH_PLANNER',
      sourceRefId: 1,
      title: 'test',
      description: null,
      kcCode: 'PY43-KC-001',
      nodeType: 'READ_RESOURCE',
      payload: {},
      dueDate: '2026-05-22',
      priority: 5,
      status: 'IN_PROGRESS',
      createdAt: '2026-05-21T18:00:00Z',
      updatedAt: '2026-05-21T18:00:00Z',
    });
    const store = useLearningStore();
    store.quests.today = [
      {
        id: 10,
        userId: 7,
        source: 'PATH_PLANNER',
        sourceRefId: 1,
        title: 'test',
        description: null,
        kcCode: 'PY43-KC-001',
        nodeType: 'READ_RESOURCE',
        payload: {},
        dueDate: '2026-05-22',
        priority: 5,
        status: 'OPEN',
        createdAt: '2026-05-21T18:00:00Z',
        updatedAt: '2026-05-21T18:00:00Z',
      },
    ];
    await store.startQuestById(10);
    expect(store.quests.today[0]?.status).toBe('IN_PROGRESS');
  });

  it('completeQuestById removes the quest from todays list', async () => {
    vi.mocked(completeQuest).mockResolvedValue({
      id: 11,
      userId: 7,
      source: 'PATH_PLANNER',
      sourceRefId: 2,
      title: 'test',
      description: null,
      kcCode: 'PY43-KC-001',
      nodeType: 'SOLVE_PROBLEM',
      payload: {},
      dueDate: '2026-05-22',
      priority: 5,
      status: 'COMPLETED',
      createdAt: '2026-05-21T18:00:00Z',
      updatedAt: '2026-05-21T18:00:00Z',
    });
    const store = useLearningStore();
    store.quests.today = [
      {
        id: 11,
        userId: 7,
        source: 'PATH_PLANNER',
        sourceRefId: 2,
        title: 'test',
        description: null,
        kcCode: 'PY43-KC-001',
        nodeType: 'SOLVE_PROBLEM',
        payload: {},
        dueDate: '2026-05-22',
        priority: 5,
        status: 'IN_PROGRESS',
        createdAt: '2026-05-21T18:00:00Z',
        updatedAt: '2026-05-21T18:00:00Z',
      },
    ];
    await store.completeQuestById(11);
    expect(store.quests.today.find((quest) => quest.id === 11)).toBeUndefined();
  });

  it('skipQuestById removes quest from todays list', async () => {
    vi.mocked(skipQuest).mockResolvedValue({
      id: 12,
      userId: 7,
      source: 'PATH_PLANNER',
      sourceRefId: 3,
      title: 'test',
      description: null,
      kcCode: 'PY43-KC-001',
      nodeType: 'PROFILE_UPDATE',
      payload: {},
      dueDate: '2026-05-22',
      priority: 5,
      status: 'SKIPPED',
      createdAt: '2026-05-21T18:00:00Z',
      updatedAt: '2026-05-21T18:00:00Z',
    });
    const store = useLearningStore();
    store.quests.today = [
      {
        id: 12,
        userId: 7,
        source: 'PATH_PLANNER',
        sourceRefId: 3,
        title: 'test',
        description: null,
        kcCode: 'PY43-KC-001',
        nodeType: 'PROFILE_UPDATE',
        payload: {},
        dueDate: '2026-05-22',
        priority: 5,
        status: 'OPEN',
        createdAt: '2026-05-21T18:00:00Z',
        updatedAt: '2026-05-21T18:00:00Z',
      },
    ];
    await store.skipQuestById(12);
    expect(store.quests.today.length).toBe(0);
  });
});
