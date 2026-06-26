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
  fetchAiRuntimeSummary: vi.fn(),
  fetchWeeklyReport: vi.fn(),
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

vi.mock('@/api/aiRuntime', () => ({
  fetchAiRuntimeSummary: apiMocks.fetchAiRuntimeSummary,
}));

vi.mock('@/api/twin', () => ({
  fetchWeeklyReport: apiMocks.fetchWeeklyReport,
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
    // 工坊布局现持久化到 localStorage（刷新/重开后恢复面板布局）；测试间须清空以隔离，
    // 否则前一用例开过的抽屉会被后一用例的全新挂载 hydrate，污染默认布局断言。
    localStorage.clear();
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
    apiMocks.fetchAiRuntimeSummary.mockResolvedValue({
      protocol: {
        mcpToolCount: 6,
        toolsWithEvidenceFieldCount: 2,
        mcpResourceCount: 3,
        aguiEventCount: 14,
        skillPackageCount: 4,
        evidenceSkillPackageCount: 4,
        toolsWithEvidenceFields: ['workshop.resource.check.submit'],
        evidenceSkillPackages: ['evidence_grounded_workshop', 'learner_twin_memory'],
      },
      evidence: {
        nodeCount: 18,
        edgeCount: 24,
        touchedKcCount: 4,
        groundedPageCount: 5,
        checkCoverageRate: 0.75,
        graphCompletenessScore: 0.66,
        graphMemoryScore: 0.82,
        directSignalCount: 2,
        propagatedSignalCount: 3,
        retainedEvidenceCount: 6,
      },
      learnerTwin: {
        memoryEventCount: 9,
        failedPracticeCount: 3,
        succeededPracticeCount: 4,
        insightCount: 1,
        masteryKcCount: 1,
        resourceRunCount: 1,
      },
      externalResearch: {
        allowedDomains: ['docs.python.org', 'peps.python.org', 'pypi.org'],
        defaultMaxSteps: 6,
        citationPolicy: '外部内容，非课件',
      },
      gates: [
        {
          key: 'evidence.graph_completeness',
          label: '学习证据图完整度',
          status: 'PASS',
          value: 0.66,
          threshold: 0.45,
          evidenceRefs: ['learner.evidence.graph'],
        },
      ],
    });
    apiMocks.fetchWeeklyReport.mockResolvedValue({
      periodStart: '2026-06-19',
      periodEnd: '2026-06-26',
      memoryBrief: {
        totalEvents: 12,
        failedPractices: 3,
        succeededPractices: 5,
        topKcCodes: ['PY43-KC-001'],
        highlights: ['列表别名易错'],
      },
      insights: [
        {
          id: 1,
          text: '加强错误处理',
          dimensionKey: 'error_pattern',
          tags: ['PY43-KC-001'],
          createdAt: '2026-06-25T10:00:00Z',
        },
      ],
      masteryDeltas: [
        {
          kcCode: 'PY43-KC-001',
          currentScore: 0.64,
          confidence: 0.76,
          recentEventReasons: [],
        },
      ],
      resourceRuns: [],
    });
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

  it('defaults to a ChatGPT-like workbench with the history rail visible', async () => {
    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.find('[data-testid="workshop-chat-pane"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="workshop-body"]').attributes('data-layout')).toBe(
      'assistant-workbench',
    );
    expect(wrapper.get('[data-testid="workshop-body"]').classes()).toContain(
      'workshop-body--with-history',
    );
    expect(wrapper.find('[data-testid="workshop-drawer-history"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="workshop-open-history"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="workshop-open-study"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="workshop-open-calls"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="workshop-drawer-study"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="workshop-drawer-calls"]').exists()).toBe(false);
  });

  it('opens history, study space and call-process drawers from the top bar', async () => {
    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.find('[data-testid="workshop-drawer-history"]').exists()).toBe(true);
    await wrapper.get('[data-testid="workshop-open-history"]').trigger('click');
    expect(wrapper.find('[data-testid="workshop-drawer-history"]').exists()).toBe(false);
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

  it('shows AI runtime gates inside the call-process drawer', async () => {
    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[data-testid="workshop-open-calls"]').trigger('click');
    await flushPromises();

    expect(apiMocks.fetchAiRuntimeSummary).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[data-testid="runtime-summary"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('学习证据图完整度');
    expect(wrapper.text()).toContain('外部内容，非课件');
    expect(apiMocks.fetchWeeklyReport).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('Learner Twin');
  });

  it('prefills the workshop composer from a Learner Twin insight', async () => {
    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[data-testid="workshop-open-calls"]').trigger('click');
    await flushPromises();
    await wrapper.get('[data-testid="twin-insight-prompt-1"]').trigger('click');
    await flushPromises();

    const input = wrapper.get('[data-testid="composer-input"]').element as HTMLTextAreaElement;
    expect(input.value).toContain('加强错误处理');
    expect(input.value).toContain('Learner Twin');
  });

  it('places history on the leading side and study or calls on the trailing side', async () => {
    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

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

    expect(wrapper.find('[data-testid="workshop-drawer-history"]').exists()).toBe(true);
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

    await wrapper.get('[data-testid="workshop-open-history"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="workshop-drawer-history"]').exists()).toBe(false);
    wrapper.findComponent(WorkshopChat).vm.$emit('history-saved', savedTurn);

    expect(apiMocks.rememberWorkshopChatHistoryTurn).toHaveBeenCalledWith(savedTurn);
  });
});
