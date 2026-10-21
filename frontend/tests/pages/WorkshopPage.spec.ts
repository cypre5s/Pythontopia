import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';

// fastTravel 默认透传真实数据（R-14 单 hub）；置 ftRoutes.routes 可模拟「恢复多图」。
import { FAST_TRAVEL_ROUTES } from '@/data/fastTravel';
import {
  RESTORED_FAST_TRAVEL_ROUTES,
  type FastTravelRouteOverride,
} from '../_helpers/fastTravelRoutes';

const ftRoutes: FastTravelRouteOverride = vi.hoisted(() => ({ routes: null }));
vi.mock('@/data/fastTravel', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/data/fastTravel')>();
  const { createFastTravelModuleMock } = await import('../_helpers/fastTravelRoutes');
  return createFastTravelModuleMock(actual, ftRoutes);
});

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
  fetchAiRuntimeOperations: vi.fn(),
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
    clearWorkshopChatHistoryCache: vi.fn(),
    listWorkshopChatHistoryTurns: apiMocks.listWorkshopChatHistoryTurns,
    rememberWorkshopChatHistoryTurn: apiMocks.rememberWorkshopChatHistoryTurn,
    recordWorkshopChatHistoryTurn: vi.fn(),
  };
});

vi.mock('@/api/aiRuntime', () => ({
  fetchAiRuntimeSummary: apiMocks.fetchAiRuntimeSummary,
  fetchAiRuntimeOperations: apiMocks.fetchAiRuntimeOperations,
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
import ChatHistoryRail from '@/components/workshop/ChatHistoryRail.vue';
import { useSessionStore } from '@stores/session';
import { useWorldStore } from '@stores/world';
import { useWorkshopUiStore } from '@stores/workshopUi';

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
    useSessionStore().user = {
      id: 7,
      username: 'student-7',
      displayName: '学生七',
      role: 'STUDENT',
    };
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
    apiMocks.fetchAiRuntimeOperations.mockResolvedValue({
      subject: {
        scope: 'SELF',
        label: '本人学习证据',
        learnerCount: 1,
        evidenceLearnerCount: 1,
      },
      loops: [
        {
          loopType: 'LEARNER',
          label: '学生学习闭环',
          status: 'PASS',
          latestTraceId: 'trace-learner',
          nextAction: 'complete:student_learning_loop',
          evidenceRefs: ['learner.evidence.graph'],
        },
        {
          loopType: 'RESOURCE',
          label: '资源生成闭环',
          status: 'INSUFFICIENT_EVIDENCE',
          latestTraceId: '',
          nextAction: 'evaluate:resource_feedback_or_check',
          evidenceRefs: [],
        },
      ],
      governance: [
        {
          key: 'policy_canary_review',
          label: '策略灰度审核',
          status: 'PASS',
          action: '查看 canary dry-run 与 rollback 状态',
          reviewRoute: '/teacher/review',
          evidenceRefs: ['agentic-m3-system-evolution-v1'],
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
    // 用例间必须还原成真实单 hub 路线表，否则 gate 的默认态会被上一条用例污染。
    ftRoutes.routes = null;
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

  // R-14 单 hub：唯一一条路线的目的地就是玩家所在地，chip 会永久 disabled。
  // 工坊这条 travel-list 与 HUD 传送入口共用 FAST_TRAVEL_ENABLED 这条 gate，
  // 返回小镇由左侧「← 返回小镇」承担，故此处应整体不渲染。
  it('suppresses fast-travel chips while only one route exists, keeping library access', async () => {
    expect(FAST_TRAVEL_ROUTES.length).toBe(1);

    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    for (const route of FAST_TRAVEL_ROUTES) {
      expect(
        wrapper.find(`[data-testid="workshop-travel-${route.key}"]`).exists(),
        `chip ${route.key} should be suppressed under the single hub`,
      ).toBe(false);
    }
    // 同一列表里的图书馆/远征入口与路线表无关，必须保留。
    expect(wrapper.find('[data-testid="workshop-open-library"]').exists()).toBe(true);
  });

  it('consumes a Learner Twin prompt into the chat draft on entry', async () => {
    const ui = useWorkshopUiStore();
    ui.setPendingPrompt('基于本周证据复习 PY43-KC-001');

    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.get('[data-testid="composer-input"]').element.value).toBe(
      '基于本周证据复习 PY43-KC-001',
    );
    expect(ui.pendingPrompt).toBeNull();
  });

  it('restores fast-travel chips once more than one route exists', async () => {
    ftRoutes.routes = RESTORED_FAST_TRAVEL_ROUTES;

    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    for (const route of RESTORED_FAST_TRAVEL_ROUTES) {
      expect(
        wrapper.find(`[data-testid="workshop-travel-${route.key}"]`).exists(),
        `chip ${route.key} missing after routes restored`,
      ).toBe(true);
    }
  });

  it('clicking a fast-travel chip writes to world.player and pushes /world', async () => {
    ftRoutes.routes = RESTORED_FAST_TRAVEL_ROUTES;
    const target = RESTORED_FAST_TRAVEL_ROUTES.find((r) => r.toMap !== 'town_hub');
    expect(target).toBeDefined();

    const world = useWorldStore();
    world.player = { mapKey: 'town_hub', tileX: 2, tileY: 15, direction: 'DOWN' };

    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.find(`[data-testid="workshop-travel-${target!.key}"]`).trigger('click');
    await flushPromises();

    expect(world.player.mapKey).toBe(target!.toMap);
    expect(router.currentRoute.value.path).toBe('/world');
  });

  it('disables the chip for the map the player is already on', async () => {
    ftRoutes.routes = RESTORED_FAST_TRAVEL_ROUTES;
    const here = RESTORED_FAST_TRAVEL_ROUTES.find((r) => r.toMap === 'town_hub');
    expect(here).toBeDefined();

    const world = useWorldStore();
    world.player = { mapKey: 'town_hub', tileX: 25, tileY: 20, direction: 'DOWN' };

    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    expect(
      wrapper.find(`[data-testid="workshop-travel-${here!.key}"]`).attributes('disabled'),
    ).toBeDefined();
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

  it('把真实登录用户 id 同时传给历史栏与工坊对话', async () => {
    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.findComponent(ChatHistoryRail).props('ownerId')).toBe(7);
    expect(wrapper.findComponent(WorkshopChat).props('ownerId')).toBe(7);
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

  it('shows only student-facing progress and does not fetch internal runtime diagnostics', async () => {
    await router.push('/workshop');
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[data-testid="workshop-open-calls"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="workshop-process"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('还没有处理记录');
    expect(wrapper.text()).toContain('课件命中情况');
    expect(apiMocks.fetchAiRuntimeSummary).not.toHaveBeenCalled();
    expect(apiMocks.fetchAiRuntimeOperations).not.toHaveBeenCalled();
    expect(apiMocks.fetchWeeklyReport).not.toHaveBeenCalled();
    expect(wrapper.text()).not.toContain('MCP 工具');
    expect(wrapper.text()).not.toContain('策略灰度审核');
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
    expect(apiMocks.listWorkshopChatHistoryTurns).toHaveBeenCalledWith(
      101,
      50,
      expect.objectContaining({ force: false }),
    );
    expect(wrapper.text()).toContain('还没有问答记录');

    wrapper.findComponent(WorkshopChat).vm.$emit('history-saved', savedTurn);
    await flushPromises();

    expect(apiMocks.listWorkshopChatHistoryTurns).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('我想学循环，从哪开始');
    expect(wrapper.text()).not.toContain('引用充分');
    expect(wrapper.text()).not.toContain('PLAN');
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

    expect(apiMocks.rememberWorkshopChatHistoryTurn).toHaveBeenCalledWith(7, savedTurn);
  });
});
