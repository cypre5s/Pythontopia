import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import BoardPanel from '@/components/board/BoardPanel.vue';
import { useLearningStore } from '@stores/learning';
import type { QuestDto } from '@/api/dto/Quest';

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
  getArchivedLearningPaths,
} from '@/api/path';
import {
  getTodayQuests,
  startQuest,
  completeQuest,
  skipQuest,
} from '@/api/quests';
import { getMyClassroomQuests } from '@/api/classroomQuests';

const SAMPLE_QUEST: QuestDto = {
  id: 1,
  userId: 7,
  source: 'PATH_PLANNER',
  sourceRefId: 11,
  title: '阅读 PY43-KC-001 资源',
  description: 'demo',
  kcCode: 'PY43-KC-001',
  nodeType: 'READ_RESOURCE',
  payload: {},
  dueDate: '2026-06-05',
  priority: 7,
  status: 'OPEN',
  createdAt: '2026-05-21T18:00:00Z',
  updatedAt: '2026-05-21T18:00:00Z',
};

function mountWithRouter() {
  const router: Router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/board', name: 'board', component: { template: '<div />' } }],
  });
  return mount(BoardPanel, {
    global: {
      plugins: [createPinia(), router],
    },
  });
}

describe('BoardPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(getActiveLearningPath).mockResolvedValue(null);
    vi.mocked(getArchivedLearningPaths).mockResolvedValue([]);
    vi.mocked(getTodayQuests).mockResolvedValue([SAMPLE_QUEST]);
    vi.mocked(getMyClassroomQuests).mockResolvedValue([]);
    vi.mocked(startQuest).mockResolvedValue({ ...SAMPLE_QUEST, status: 'IN_PROGRESS' });
    vi.mocked(completeQuest).mockResolvedValue({ ...SAMPLE_QUEST, status: 'COMPLETED' });
    vi.mocked(skipQuest).mockResolvedValue({ ...SAMPLE_QUEST, status: 'SKIPPED' });
    vi.mocked(planLearningPath).mockResolvedValue({
      path: {
        id: 99,
        userId: 7,
        packId: 43,
        version: 1,
        status: 'ACTIVE',
        goalSummary: 'demo goal',
        generatedAt: '2026-05-21T18:00:00Z',
        nextReviewAt: '2026-05-28T18:00:00Z',
        sourceRunId: null,
        nodes: [
          {
            id: 1,
            pathId: 99,
            dayIndex: 1,
            orderInDay: 1,
            kcCode: 'PY43-KC-001',
            nodeType: 'READ_RESOURCE',
            reasonSummary: '巩固',
            evidenceSummary: 'evidence',
            suggestedResourceType: 'HANDOUT',
            suggestedProblemId: null,
            sourceClassroomQuestId: null,
          },
        ],
      },
      archivedPathIds: [],
      generatedQuestCount: 1,
      runStatus: 'SUCCESS',
      groundingMessage: 'OK',
    });
  });

  it('renders todays quests by default and supports start/complete', async () => {
    const wrapper = mountWithRouter();
    const learning = useLearningStore();
    await learning.loadTodayQuests();
    await flushPromises();

    expect(wrapper.find('[data-testid="today-tab"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('阅读 PY43-KC-001 资源');

    const startButton = wrapper
      .findAll('button')
      .find((button) => button.text() === '开始');
    expect(startButton).toBeDefined();
    await startButton!.trigger('click');
    expect(startQuest).toHaveBeenCalledWith(1);

    const completeButton = wrapper
      .findAll('button')
      .find((button) => button.text() === '完成');
    expect(completeButton).toBeDefined();
    await completeButton!.trigger('click');
    expect(completeQuest).toHaveBeenCalledWith(1, { note: '在公告板完成' });
  });

  it('switches to path tab and triggers planning', async () => {
    const wrapper = mountWithRouter();
    await flushPromises();
    await wrapper.find('[data-testid="tab-path"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="path-tab"]').exists()).toBe(true);

    await wrapper.find('button.primary').trigger('click');
    await flushPromises();
    expect(planLearningPath).toHaveBeenCalledTimes(1);
    expect(getTodayQuests).toHaveBeenCalled();
  });
});
