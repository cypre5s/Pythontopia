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
  listWorkshopChatHistoryTurns: vi.fn(),
  rememberWorkshopChatHistoryTurn: vi.fn(),
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

vi.mock('@/api/workshopHistory', () => {
  return {
    listWorkshopChatHistoryTurns: apiMocks.listWorkshopChatHistoryTurns,
    rememberWorkshopChatHistoryTurn: apiMocks.rememberWorkshopChatHistoryTurn,
    recordWorkshopChatHistoryTurn: vi.fn(),
  };
});

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
import WorkshopChat from '@/components/workshop/WorkshopChat.vue';
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
    apiMocks.listWorkshopChatHistoryTurns.mockResolvedValue([]);
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

  // 离开工坊再返回保留布局/对话靠 App.vue 的 <KeepAlive :include="['WorldPage','WorkshopPage']">；
  // 该机制按组件 name 匹配。此处守住「组件 name === 'WorkshopPage'」契约：一旦被重命名，
  // KeepAlive 会静默失效（页面被销毁、状态丢失），此断言即报警。
  it('registers component name WorkshopPage so App KeepAlive include caches it across navigation', async () => {
    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.vm.$options.name).toBe('WorkshopPage');
  });

  it('defaults to a centered single chat shell with hidden drawers', async () => {
    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.find('[data-testid="workshop-chat-pane"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="workshop-body"]').classes()).toContain('workshop-body--solo');
    expect(wrapper.find('[data-testid="workshop-open-history"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="workshop-open-study"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="workshop-open-calls"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="workshop-drawer-history"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="workshop-drawer-study"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="workshop-drawer-calls"]').exists()).toBe(false);
  });

  it('opens history, study space and call-process drawers from the top bar', async () => {
    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[data-testid="workshop-open-history"]').trigger('click');
    expect(wrapper.find('[data-testid="workshop-drawer-history"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="workshop-body"]').classes()).toContain(
      'workshop-body--with-history',
    );

    await wrapper.get('[data-testid="workshop-open-study"]').trigger('click');
    expect(wrapper.find('[data-testid="workshop-drawer-study"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="workshop-drawer-history"]').exists()).toBe(true);

    await wrapper.get('[data-testid="workshop-open-calls"]').trigger('click');
    expect(wrapper.find('[data-testid="workshop-drawer-calls"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="workshop-drawer-study"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="workshop-drawer-history"]').exists()).toBe(true);
  });

  it('places history on the leading side and study or calls on the trailing side', async () => {
    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[data-testid="workshop-open-history"]').trigger('click');
    await wrapper.get('[data-testid="workshop-open-study"]').trigger('click');

    const body = wrapper.get('[data-testid="workshop-body"]');
    const history = wrapper.get('[data-testid="workshop-drawer-history"]');
    const chat = wrapper.get('[data-testid="workshop-chat-pane"]');
    const study = wrapper.get('[data-testid="workshop-drawer-study"]');
    const children = Array.from(body.element.children);

    expect(history.attributes('data-panel-side')).toBe('left');
    expect(study.attributes('data-panel-side')).toBe('right');
    expect(children.indexOf(history.element)).toBeLessThan(children.indexOf(chat.element));
    expect(children.indexOf(study.element)).toBeGreaterThan(children.indexOf(chat.element));
  });

  it('merges a saved history turn into the open drawer without reloading from the database', async () => {
    apiMocks.listWorkshopChatHistoryTurns.mockResolvedValueOnce([]);
    const savedTurn = {
      id: 'workshop-99',
      conversationId: 'conv-99',
      userText: '我想学循环，从哪开始',
      intent: 'PLAN',
      responseKind: 'plan',
      toolLabel: '规划学习',
      responseText: '计划如下',
      responsePayload: { planSteps: [{ id: 's1', title: '先看循环概念' }] },
      evidencePageIds: [],
      groundingStatus: 'PASSED',
      createdAt: '2026-06-18T10:00:00Z',
    };

    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[data-testid="workshop-open-history"]').trigger('click');
    await flushPromises();
    expect(apiMocks.listWorkshopChatHistoryTurns).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('还没有问答记录');

    wrapper.findComponent(WorkshopChat).vm.$emit('history-saved', savedTurn);
    await flushPromises();

    expect(apiMocks.listWorkshopChatHistoryTurns).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('我想学循环，从哪开始');
    expect(wrapper.text()).toContain('引用充分');
    expect(wrapper.text()).toContain('PLAN');
  });

  it('remembers saved history turns even when the history drawer is closed', async () => {
    const savedTurn = {
      id: 'workshop-100',
      conversationId: 'conv-100',
      userText: '我想学循环，从哪开始',
      intent: 'PLAN',
      responseKind: 'plan',
      toolLabel: '规划学习',
      responseText: '计划如下',
      responsePayload: { planSteps: [] },
      evidencePageIds: [],
      groundingStatus: null,
      createdAt: '2026-06-18T10:00:00Z',
    };

    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.find('[data-testid="workshop-drawer-history"]').exists()).toBe(false);
    wrapper.findComponent(WorkshopChat).vm.$emit('history-saved', savedTurn);

    expect(apiMocks.rememberWorkshopChatHistoryTurn).toHaveBeenCalledWith(savedTurn);
  });
});
