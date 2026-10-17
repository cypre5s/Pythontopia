import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { QuestDto } from '@/api/dto/Quest';
import type { SubmissionDto } from '@/api/dto/Submission';
import { useWorkshopUiStore } from '@stores/workshopUi';
import TodayQuestsTab from '../TodayQuestsTab.vue';
import ClassroomQuestsTab from '../ClassroomQuestsTab.vue';

const api = vi.hoisted(() => ({
  getTodayQuests: vi.fn(),
  startQuest: vi.fn(),
  completeQuest: vi.fn(),
  skipQuest: vi.fn(),
  getMyClassroomQuests: vi.fn(),
  listSubmissions: vi.fn(),
  listWorkshopResources: vi.fn(),
  routerPush: vi.fn(),
}));

vi.mock('@/api/quests', () => ({
  getTodayQuests: api.getTodayQuests,
  startQuest: api.startQuest,
  completeQuest: api.completeQuest,
  skipQuest: api.skipQuest,
}));
vi.mock('@/api/classroomQuests', () => ({
  getMyClassroomQuests: api.getMyClassroomQuests,
}));
vi.mock('@/api/submissions', () => ({ listSubmissions: api.listSubmissions }));
vi.mock('@/api/workshop', () => ({ listWorkshopResources: api.listWorkshopResources }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: api.routerPush }) }));

function pathQuest(
  requiredEvidenceType: QuestDto['requiredEvidenceType'],
): QuestDto {
  return {
    id: 41,
    userId: 7,
    source: 'PATH_PLANNER',
    sourceRefId: 500,
    title: '巩固 PY43-KC-001',
    description: null,
    kcCode: 'PY43-KC-001',
    nodeType: requiredEvidenceType === 'resource' ? 'READ_RESOURCE' : 'SOLVE_PROBLEM',
    payload: {},
    dueDate: '2026-10-17',
    priority: 5,
    status: 'IN_PROGRESS',
    requiredEvidenceType,
    requiredMasteryDelta: null,
    targetProblemIds: [],
    targetResourceId: null,
    deepLinkRoute: requiredEvidenceType === 'resource' ? '/workshop' : '/training',
    deepLinkParams: {},
    createdAt: '2026-10-17T00:00:00Z',
    updatedAt: '2026-10-17T00:00:00Z',
  };
}

const passedSubmission: SubmissionDto = {
  id: 901,
  userId: 7,
  problemId: 77,
  questId: null,
  source: 'TRAINING',
  language: 'PYTHON_3_11',
  code: 'print(1)',
  submittedAt: '2026-10-17T10:00:00Z',
  status: 'PASSED',
  runtimeMs: 1,
  memoryKb: null,
};

describe('board quest loading states', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    api.getTodayQuests.mockResolvedValue([]);
    api.getMyClassroomQuests.mockResolvedValue([]);
    api.listSubmissions.mockResolvedValue([]);
    api.listWorkshopResources.mockResolvedValue([]);
    api.routerPush.mockResolvedValue(undefined);
  });

  it('completes a path quest with a selected passed submission', async () => {
    const quest = pathQuest('submission');
    api.getTodayQuests.mockResolvedValue([quest]);
    api.listSubmissions.mockResolvedValue([passedSubmission]);
    api.completeQuest.mockResolvedValue({ ...quest, status: 'COMPLETED' });

    const wrapper = mount(TodayQuestsTab);
    await flushPromises();
    await wrapper.get('[data-testid="today-quest-load-evidence-41"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="today-quest-complete-41"]').trigger('click');
    await flushPromises();

    expect(api.listSubmissions).toHaveBeenCalledWith({ status: 'PASSED', limit: 20 });
    expect(api.completeQuest).toHaveBeenCalledWith(41, {
      note: '在公告板完成',
      submissionId: 901,
    });
  });

  it('uses node type to collect evidence for a legacy path quest', async () => {
    const quest = { ...pathQuest('submission'), requiredEvidenceType: null };
    api.getTodayQuests.mockResolvedValue([quest]);
    api.listSubmissions.mockResolvedValue([passedSubmission]);
    api.completeQuest.mockResolvedValue({ ...quest, status: 'COMPLETED' });

    const wrapper = mount(TodayQuestsTab);
    await flushPromises();

    expect(wrapper.find('[data-testid="today-quest-load-evidence-41"]').exists()).toBe(true);
    expect(wrapper.find('button.primary').text()).not.toBe('完成');
    await wrapper.get('[data-testid="today-quest-load-evidence-41"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="today-quest-complete-41"]').trigger('click');
    await flushPromises();

    expect(api.completeQuest).toHaveBeenCalledWith(41, {
      note: '在公告板完成',
      submissionId: 901,
    });
  });

  it('keeps an unverifiable legacy profile task from being completed without evidence', async () => {
    const quest = { ...pathQuest(null), nodeType: 'PROFILE_UPDATE' as const };
    api.getTodayQuests.mockResolvedValue([quest]);

    const wrapper = mount(TodayQuestsTab);
    await flushPromises();

    expect(wrapper.find('button.primary').exists()).toBe(false);
    expect(wrapper.text()).toContain('请跳过并重新规划学习路径');
  });

  it('opens a matching resource so the student can consume it before completion', async () => {
    const quest = pathQuest('resource');
    api.getTodayQuests.mockResolvedValue([quest]);
    api.listWorkshopResources.mockResolvedValue([
      {
        id: 902,
        type: 'HANDOUT',
        title: '变量与类型讲义',
        summary: null,
        kcCodes: ['PY43-KC-001'],
        groundingStatus: 'PASSED',
        feedbackStatus: 'NONE',
        createdAt: '2026-10-17T10:00:00Z',
      },
    ]);
    api.completeQuest.mockResolvedValue({ ...quest, status: 'COMPLETED' });

    const wrapper = mount(TodayQuestsTab);
    await flushPromises();
    await wrapper.get('[data-testid="today-quest-load-evidence-41"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="today-quest-open-resource-41"]').trigger('click');
    await flushPromises();

    expect(api.listWorkshopResources).toHaveBeenCalledWith({
      kcCode: 'PY43-KC-001',
      limit: 20,
    });
    expect(useWorkshopUiStore().openResourceId).toBe(902);
    expect(api.routerPush).toHaveBeenCalledWith('/workshop');
    await wrapper.get('[data-testid="today-quest-complete-41"]').trigger('click');
    await flushPromises();
    expect(api.completeQuest).toHaveBeenCalledWith(41, {
      note: '在公告板完成',
      resourceId: 902,
    });
  });

  it('shows loading before deciding that there are no today quests', async () => {
    let resolveRequest!: (quests: never[]) => void;
    api.getTodayQuests.mockReturnValue(new Promise((resolve) => {
      resolveRequest = resolve;
    }));

    const wrapper = mount(TodayQuestsTab);
    await wrapper.vm.$nextTick();

    expect(wrapper.get('[role="status"]').text()).toContain('正在加载今日任务');
    expect(wrapper.text()).not.toContain('今日暂无待办任务');

    resolveRequest([]);
    await flushPromises();
    expect(wrapper.text()).toContain('今日暂无待办任务');
  });

  it('shows today quest load errors and retries to the real empty response', async () => {
    api.getTodayQuests
      .mockRejectedValueOnce(new Error('暂时无法连接'))
      .mockResolvedValueOnce([]);

    const wrapper = mount(TodayQuestsTab);
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('暂时无法连接');
    expect(wrapper.text()).not.toContain('今日暂无待办任务');

    await wrapper.get('[data-testid="today-quests-retry"]').trigger('click');
    await flushPromises();

    expect(api.getTodayQuests).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('今日暂无待办任务');
  });

  it('shows loading before deciding that there are no classroom quests', async () => {
    let resolveRequest!: (quests: never[]) => void;
    api.getMyClassroomQuests.mockReturnValue(new Promise((resolve) => {
      resolveRequest = resolve;
    }));

    const wrapper = mount(ClassroomQuestsTab);
    await wrapper.vm.$nextTick();

    expect(wrapper.get('[role="status"]').text()).toContain('正在加载班级任务');
    expect(wrapper.text()).not.toContain('暂时没有班级任务');

    resolveRequest([]);
    await flushPromises();
    expect(wrapper.text()).toContain('暂时没有班级任务');
  });

  it('shows classroom quest load errors and retries to the real empty response', async () => {
    api.getMyClassroomQuests
      .mockRejectedValueOnce(new Error('班级服务暂不可用'))
      .mockResolvedValueOnce([]);

    const wrapper = mount(ClassroomQuestsTab);
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain('班级服务暂不可用');
    expect(wrapper.text()).not.toContain('暂时没有班级任务');

    await wrapper.get('[data-testid="classroom-quests-retry"]').trigger('click');
    await flushPromises();

    expect(api.getMyClassroomQuests).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('暂时没有班级任务');
  });
});
