import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia, type Pinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import PathTreeTab from '@/components/board/PathTreeTab.vue';
import { useLearningStore } from '@stores/learning';
import type { LearningPathDto, LearningPathNodeDto } from '@/api/dto/Path';

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

function node(partial: Partial<LearningPathNodeDto>): LearningPathNodeDto {
  return {
    id: 1,
    pathId: 99,
    dayIndex: 1,
    orderInDay: 1,
    kcCode: 'PY43-KC-001',
    nodeType: 'READ_RESOURCE',
    reasonSummary: 'demo',
    evidenceSummary: 'evidence',
    suggestedResourceType: 'HANDOUT',
    suggestedProblemId: null,
    sourceClassroomQuestId: null,
    ...partial,
  };
}

function path(nodes: LearningPathNodeDto[]): LearningPathDto {
  return {
    id: 99,
    userId: 7,
    packId: 43,
    version: 1,
    status: 'ACTIVE',
    goalSummary: '本周聚焦 PY43-KC-001',
    generatedAt: '2026-05-21T18:00:00Z',
    nextReviewAt: '2026-05-28T18:00:00Z',
    sourceRunId: null,
    nodes,
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
  return mount(PathTreeTab, {
    global: {
      plugins: [pinia, router],
    },
  });
}

describe('PathTreeTab deep link', () => {
  beforeEach(() => {
    pinia = createPinia();
    setActivePinia(pinia);
  });

  async function setupNode(partial: Partial<LearningPathNodeDto>) {
    const learning = useLearningStore();
    learning.pathState.active = path([node(partial)]);
    learning.pathState.phase = 'ready';
    const wrapper = mountTab();
    await flushPromises();
    return wrapper;
  }

  it('SOLVE_PROBLEM node routes to /training?problemId=...', async () => {
    const wrapper = await setupNode({
      id: 1,
      nodeType: 'SOLVE_PROBLEM',
      kcCode: 'PY43-KC-002',
      suggestedProblemId: 476,
      suggestedResourceType: null,
    });

    await wrapper.find('[data-testid="path-node-link-1"]').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.path).toBe('/training');
    expect(router.currentRoute.value.query.problemId).toBe('476');
  });

  it('READ_RESOURCE node routes to /workshop?kc=&type= (the contract WorkshopChat consumes)', async () => {
    const wrapper = await setupNode({
      id: 2,
      nodeType: 'READ_RESOURCE',
      kcCode: 'PY43-KC-001',
      suggestedResourceType: 'HANDOUT',
    });

    await wrapper.find('[data-testid="path-node-link-2"]').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.path).toBe('/workshop');
    // WorkshopChat.consumeDeepLink 只认 kc + type；kcCode 会被整段忽略。
    expect(router.currentRoute.value.query.kc).toBe('PY43-KC-001');
    expect(router.currentRoute.value.query.type).toBe('HANDOUT');
  });

  it('FIX_BUG node routes to /world?goto=town_hub like the backend deep link', async () => {
    const wrapper = await setupNode({
      id: 3,
      nodeType: 'FIX_BUG',
      kcCode: 'PY43-KC-003',
      suggestedResourceType: null,
    });

    await wrapper.find('[data-testid="path-node-link-3"]').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.path).toBe('/world');
    expect(router.currentRoute.value.query.goto).toBe('town_hub');
  });

  it('PLANT_KC node routes to /world?focus=kc_... like the backend deep link', async () => {
    const wrapper = await setupNode({
      id: 4,
      nodeType: 'PLANT_KC',
      kcCode: 'PY43-KC-004',
      suggestedResourceType: null,
    });

    await wrapper.find('[data-testid="path-node-link-4"]').trigger('click');
    await flushPromises();

    // 后端 quest 深链是 /world?focus=kc_xxx；前端兜底曾跳 /workshop，同一节点两种行为。
    expect(router.currentRoute.value.path).toBe('/world');
    expect(router.currentRoute.value.query.focus).toBe('kc_py43-kc-004');
  });

  it('PROFILE_UPDATE node routes to /profile-room', async () => {
    const wrapper = await setupNode({
      id: 5,
      nodeType: 'PROFILE_UPDATE',
      kcCode: 'PY43-KC-005',
      suggestedResourceType: null,
    });

    await wrapper.find('[data-testid="path-node-link-5"]').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.path).toBe('/profile-room');
  });
});
