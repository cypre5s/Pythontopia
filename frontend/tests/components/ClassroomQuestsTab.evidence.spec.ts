import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia, type Pinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import ClassroomQuestsTab from '@/components/board/ClassroomQuestsTab.vue';
import { useLearningStore } from '@stores/learning';
import { useToastStore } from '@stores/toast';
import type { ClassroomQuestDto } from '@/api/dto/ClassroomQuest';
import type { SubmissionDto } from '@/api/dto/Submission';

vi.mock('@/api/path', () => ({
  planLearningPath: vi.fn(),
  getActiveLearningPath: vi.fn(),
  getArchivedLearningPaths: vi.fn(),
}));
vi.mock('@/api/quests', () => ({
  getTodayQuests: vi.fn().mockResolvedValue([]),
  listQuests: vi.fn(),
  startQuest: vi.fn(),
  completeQuest: vi.fn(),
  skipQuest: vi.fn(),
}));
vi.mock('@/api/classroomQuests', () => ({
  getMyClassroomQuests: vi.fn().mockResolvedValue([]),
}));
vi.mock('@/api/submissions', () => ({
  listSubmissions: vi.fn(),
  createSubmission: vi.fn(),
  getSubmission: vi.fn(),
}));

import { completeQuest } from '@/api/quests';
import { listSubmissions } from '@/api/submissions';

const sampleSubmission: SubmissionDto = {
  id: 900,
  userId: 7,
  problemId: 476,
  questId: null,
  source: 'TRAINING',
  language: 'PYTHON_3_11',
  code: 'print(1)',
  submittedAt: '2026-05-21T17:30:00Z',
  status: 'PASSED',
  runtimeMs: 12,
  memoryKb: null,
};

function quest(partial: Partial<ClassroomQuestDto>): ClassroomQuestDto {
  return {
    id: 1,
    classroomId: 9,
    title: '完成 for 循环练习',
    description: '阶段 12 演示',
    kcCodes: ['PY43-KC-002'],
    nodeType: 'SOLVE_PROBLEM',
    payload: {},
    dueDate: '2026-05-25',
    createdBy: 42,
    createdAt: '2026-05-21T18:00:00Z',
    status: 'ACTIVE',
    targetProblemId: 476,
    targetResourceType: null,
    studentQuestId: 1001,
    studentQuestStatus: 'OPEN',
    requiredEvidenceType: 'submission',
    targetProblemIds: [476],
    deepLinkRoute: '/training',
    deepLinkParams: { problemId: 476 },
    ...partial,
  };
}

let router: Router;
let pinia: Pinia;

function mountTab() {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/board', name: 'board', component: { template: '<div />' } },
      { path: '/training', name: 'training', component: { template: '<div />' } },
      { path: '/workshop', name: 'workshop', component: { template: '<div />' } },
      { path: '/world', name: 'world', component: { template: '<div />' } },
      { path: '/profile-room', name: 'profile-room', component: { template: '<div />' } },
    ],
  });
  return mount(ClassroomQuestsTab, {
    global: {
      plugins: [pinia, router],
    },
  });
}

describe('ClassroomQuestsTab evidence-gated completion', () => {
  beforeEach(() => {
    pinia = createPinia();
    setActivePinia(pinia);
    vi.mocked(listSubmissions).mockResolvedValue([sampleSubmission]);
    vi.mocked(completeQuest).mockResolvedValue({
      id: 1001,
      userId: 7,
      source: 'CLASSROOM',
      sourceRefId: 1,
      title: '班级任务 · 完成 for 循环练习',
      description: '阶段 12 演示',
      kcCode: 'PY43-KC-002',
      nodeType: 'SOLVE_PROBLEM',
      payload: {},
      dueDate: '2026-05-25',
      priority: 9,
      status: 'COMPLETED',
      requiredEvidenceType: 'submission',
      requiredMasteryDelta: null,
      targetProblemIds: [476],
      targetResourceId: null,
      deepLinkRoute: '/training',
      deepLinkParams: { problemId: 476 },
      createdAt: '2026-05-21T18:00:00Z',
      updatedAt: '2026-05-21T18:05:00Z',
    });
  });

  async function setupWith(quests: ClassroomQuestDto[]) {
    const learning = useLearningStore();
    learning.quests.classroom = quests;
    const wrapper = mountTab();
    await flushPromises();
    return wrapper;
  }

  it('"去做题" routes to /training with problemId', async () => {
    const wrapper = await setupWith([quest({})]);

    await wrapper.find('[data-testid="classroom-quest-go-1"]').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.path).toBe('/training');
    expect(router.currentRoute.value.query.problemId).toBe('476');
  });

  it('"提交完成" 在未选择 submission 时报错并不调用 API', async () => {
    const wrapper = await setupWith([quest({})]);
    const toast = useToastStore();

    await wrapper.find('[data-testid="classroom-quest-complete-1"]').trigger('click');
    await flushPromises();

    expect(completeQuest).not.toHaveBeenCalled();
    expect(toast.message).toContain('选择一次已通过的提交');
    expect(toast.kind).toBe('error');
  });

  it('选择提交后 "提交完成" 调用 completeQuest with submissionId', async () => {
    const wrapper = await setupWith([quest({})]);

    await wrapper.find('[data-testid="classroom-quest-load-1"]').trigger('click');
    await flushPromises();

    expect(listSubmissions).toHaveBeenCalledWith({
      problemId: 476,
      status: 'PASSED',
      limit: 10,
    });

    await wrapper.find('[data-testid="classroom-quest-complete-1"]').trigger('click');
    await flushPromises();

    expect(completeQuest).toHaveBeenCalledWith(1001, {
      note: '在公告板提交完成',
      submissionId: 900,
    });
  });

  it('quest 无 requiredEvidenceType 时直接 complete 无需 evidence', async () => {
    const wrapper = await setupWith([
      quest({
        id: 2,
        studentQuestId: 1002,
        requiredEvidenceType: null,
        targetProblemId: null,
        targetProblemIds: [],
        deepLinkRoute: '/world',
        deepLinkParams: { focus: 'kc_py43-kc-002' },
        nodeType: 'PLANT_KC',
      }),
    ]);

    await wrapper.find('[data-testid="classroom-quest-complete-2"]').trigger('click');
    await flushPromises();

    expect(completeQuest).toHaveBeenCalledWith(1002, { note: '在公告板提交完成' });
  });

  it('未生成学生 quest 副本时禁用提交按钮', async () => {
    const wrapper = await setupWith([
      quest({ id: 3, studentQuestId: null, studentQuestStatus: null }),
    ]);

    expect(wrapper.find('[data-testid="classroom-quest-complete-3"]').exists()).toBe(false);
  });
});
