import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';

// 真实 fastTravel 数据，不 mock
import { FAST_TRAVEL_ROUTES } from '@/data/fastTravel';

const apiMocks = vi.hoisted(() => ({
  listChapters: vi.fn(),
  listKnowledgeComponents: vi.fn(),
  createWorkshopResource: vi.fn(),
  listWorkshopResources: vi.fn(),
  getWorkshopResource: vi.fn(),
  submitWorkshopFeedback: vi.fn(),
  fetchCrops: vi.fn(),
  fetchMastery: vi.fn(),
  getLearnerProfile: vi.fn(),
  getTodayQuests: vi.fn(),
}));

vi.mock('@/api/course', () => ({
  listChapters: apiMocks.listChapters,
  listKnowledgeComponents: apiMocks.listKnowledgeComponents,
  getCoursePack: vi.fn(),
  listKcPages: vi.fn(),
  getProblem: vi.fn(),
}));

vi.mock('@/api/workshop', () => ({
  createWorkshopResource: apiMocks.createWorkshopResource,
  listWorkshopResources: apiMocks.listWorkshopResources,
  getWorkshopResource: apiMocks.getWorkshopResource,
  submitWorkshopFeedback: apiMocks.submitWorkshopFeedback,
}));

vi.mock('@/api/field', () => ({
  bootstrapCurriculum: vi.fn(),
  fetchCrops: apiMocks.fetchCrops,
  fetchMastery: apiMocks.fetchMastery,
}));

vi.mock('@/api/quests', () => ({
  completeQuest: vi.fn(),
  getTodayQuests: apiMocks.getTodayQuests,
  skipQuest: vi.fn(),
  startQuest: vi.fn(),
}));

vi.mock('@/api/learner', () => ({
  getLearnerProfile: apiMocks.getLearnerProfile,
  openProfileInterview: vi.fn(),
  sendProfileTurn: vi.fn(),
  finishProfileInterview: vi.fn(),
}));

vi.mock('@/composables/useStardewFx', () => ({
  playUiClick: vi.fn(),
  playChime: vi.fn(),
  playUiHover: vi.fn(),
  playCoinJingle: vi.fn(),
}));

vi.mock('@/composables/useAudio', async () => {
  const actual = await vi.importActual<typeof import('@/composables/useAudio')>(
    '@/composables/useAudio',
  );
  return {
    ...actual,
    playSfx: vi.fn(),
  };
});

import WorkshopPage from '@/pages/WorkshopPage.vue';
import { useWorldStore } from '@stores/world';

const APP_LAYOUT_STUB = { template: '<div data-testid="layout-stub"><slot /></div>' };

let router: Router;

function setupRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/workshop', component: WorkshopPage },
      { path: '/world', component: { template: '<div data-testid="world-stub"/>' } },
      { path: '/library', component: { template: '<div data-testid="library-stub"/>' } },
    ],
  });
}

describe('WorkshopPage R-13 改造', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    router = setupRouter();
    Object.values(apiMocks).forEach((fn) => fn.mockReset?.());
    apiMocks.listChapters.mockResolvedValue([
      { chapterId: 179, orderIndex: 1, title: '第一章', summaryMarkdown: null },
    ]);
    apiMocks.listKnowledgeComponents.mockResolvedValue([
      {
        kcId: 1,
        kcCode: 'PY43-KC-001',
        kcName: 'a',
        chapterId: 179,
        chapterOrder: 1,
        difficulty: null,
        summaryMarkdown: null,
      },
    ]);
    apiMocks.listWorkshopResources.mockResolvedValue([]);
    apiMocks.fetchCrops.mockResolvedValue([]);
    apiMocks.fetchMastery.mockResolvedValue([]);
    apiMocks.getLearnerProfile.mockResolvedValue(null);
    apiMocks.getTodayQuests.mockResolvedValue([]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  function mountPage() {
    return mount(WorkshopPage, {
      global: {
        plugins: [router],
        stubs: { AppLayout: APP_LAYOUT_STUB },
      },
    });
  }

  it('exposes all 5 fast-travel chips + library quick access', async () => {
    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    for (const route of FAST_TRAVEL_ROUTES) {
      expect(
        wrapper.find(`[data-testid="workshop-travel-${route.key}"]`).exists(),
        `chip ${route.key} missing`,
      ).toBe(true);
    }
    expect(wrapper.find('[data-testid="workshop-open-library"]').exists()).toBe(true);
  });

  it('clicking fast-travel chip writes to world.player and pushes /world', async () => {
    const world = useWorldStore();
    // R-14 单 hub：玩家位于（历史遗留的）外围图时，点击 hub chip 把 player 拉回 town_hub。
    world.player = {
      mapKey: 'port_west',
      tileX: 2,
      tileY: 15,
      direction: 'DOWN',
    };

    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.find('[data-testid="workshop-travel-hub"]').trigger('click');
    await flushPromises();

    expect(world.player.mapKey).toBe('town_hub');
    expect(router.currentRoute.value.path).toBe('/world');
  });

  it('current map chip is disabled (cannot travel to where you already are)', async () => {
    const world = useWorldStore();
    world.player = {
      mapKey: 'town_hub',
      tileX: 25,
      tileY: 20,
      direction: 'DOWN',
    };

    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    const hubChip = wrapper.find('[data-testid="workshop-travel-hub"]');
    expect(hubChip.attributes('disabled')).toBeDefined();
  });

  it('opens library via the quick access chip', async () => {
    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.find('[data-testid="workshop-open-library"]').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.path).toBe('/library');
  });

  it('back-to-world button pushes /world', async () => {
    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.find('[data-testid="workshop-back-world"]').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.path).toBe('/world');
  });

  it('page root has data-testid workshop-page (R-13 marker)', async () => {
    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.find('[data-testid="workshop-page"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="workshop-travel-bar"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="workshop-body"]').exists()).toBe(true);
  });
});
