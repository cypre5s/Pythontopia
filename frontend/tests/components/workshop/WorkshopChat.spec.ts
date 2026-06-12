/**
 * WorkshopChat 16 工具分发中枢测试（WP5）。
 *
 * 覆盖：
 *   1. 意图分发表：mock routeIntent 各 intent → 走对管线（tutor / workshop / orchestrator / 纯前端）
 *      并渲染对应卡片（answer / resource / video / plan / progress / mystudy / navigate）。
 *   2. 置信分档：<0.45 出 ClarifyBubble（pick 后执行）；0.45~0.7 答后纠错 chips（改派 + sessionCorrections）。
 *   3. ASKME 考核模式状态机：开考/评卷 directive、轮次推进、3 轮后退出、模式中不再路由。
 *   4. secondaryIntent「接着」chip、错误重试、流式停止、多轮路由上下文、换讲法 style 重问。
 *
 * WP6 新组件（Avatar/Welcome/Clarify/QuizBar/Progress/Plan/TraceLine/Feedback）一律 vi.mock 成
 * 确定性 stub——组件本体行为由 WP6 自己的 spec 验证，这里只测分发逻辑与 props/emits 接线。
 */
/* eslint-disable vue/one-component-per-file, vue/require-prop-types -- 测试桩组件集中定义 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';

// ---------- API / 基础设施 mock ----------
const apiMocks = vi.hoisted(() => ({
  routeIntent: vi.fn(),
  listChapters: vi.fn(),
  listKnowledgeComponents: vi.fn(),
  listWorkshopResources: vi.fn(),
  getWorkshopResource: vi.fn(),
  fetchMastery: vi.fn(),
  styleEngaged: vi.fn(),
  postLearningEvent: vi.fn(),
}));

/** 假 SSE 实例注册表（按 URL），WorkshopChat 的 3 条流都从这里取控制柄。 */
const sseRegistry = vi.hoisted(() => new Map<string, unknown>());

vi.mock('@/composables/useSseStream', async () => {
  const { ref } = await import('vue');
  return {
    useSseStream: (url: string) => {
      const inst = {
        url,
        traces: ref<Array<Record<string, unknown>>>([]),
        tokens: ref(''),
        result: ref<unknown>(null),
        error: ref<{ code: string; message: string } | null>(null),
        status: ref('idle'),
        plan: ref<unknown>(null),
        events: ref<Array<Record<string, unknown>>>([]),
        state: ref<Record<string, unknown>>({}),
        abort: vi.fn(),
        start: vi.fn(),
      };
      inst.abort.mockImplementation(() => {
        if (inst.status.value === 'streaming' || inst.status.value === 'connecting') {
          inst.status.value = 'done';
        }
      });
      // 默认：按 URL 给一份「成功」结果；具体测试可覆盖 start 实现。
      inst.start.mockImplementation(async () => {
        inst.status.value = 'done';
        if (url.includes('/api/tutor/ask')) {
          inst.result.value = { answer_md: '默认答案。' };
        } else if (url.includes('/api/workshop/')) {
          inst.result.value = { runId: 1, resourceId: 101, status: 'SUCCESS' };
        } else {
          inst.result.value = {
            reply_md: '默认计划',
            clarifying_question: null,
            needs_user_input: false,
            plan: [],
            dispatch_results: [],
            profile_updates: [],
            trace: [],
            done: true,
          };
        }
      });
      sseRegistry.set(url, inst);
      return inst;
    },
  };
});

vi.mock('@/api/intent', async () => {
  const actual = await vi.importActual<typeof import('@/api/intent')>('@/api/intent');
  return { ...actual, routeIntent: apiMocks.routeIntent };
});

vi.mock('@/api/tutor', async () => {
  const actual = await vi.importActual<typeof import('@/api/tutor')>('@/api/tutor');
  return { ...actual, styleEngaged: apiMocks.styleEngaged };
});

vi.mock('@/api/learningEvent', () => ({
  postLearningEvent: apiMocks.postLearningEvent,
  postLearningEventBatch: vi.fn(),
}));

vi.mock('@/api/course', () => ({
  listChapters: apiMocks.listChapters,
  listKnowledgeComponents: apiMocks.listKnowledgeComponents,
  getCoursePack: vi.fn(),
  listKcPages: vi.fn(),
  getProblem: vi.fn(),
}));

vi.mock('@/api/workshop', () => ({
  createWorkshopResource: vi.fn(),
  listWorkshopResources: apiMocks.listWorkshopResources,
  getWorkshopResource: apiMocks.getWorkshopResource,
  submitWorkshopFeedback: vi.fn(),
}));

vi.mock('@/api/field', () => ({
  bootstrapCurriculum: vi.fn(),
  fetchCrops: vi.fn(),
  fetchMastery: apiMocks.fetchMastery,
}));

vi.mock('@/composables/useAudio', async () => {
  const actual =
    await vi.importActual<typeof import('@/composables/useAudio')>('@/composables/useAudio');
  return { ...actual, playSfx: vi.fn() };
});

// ---------- WP6 组件 → 确定性 stub（分发逻辑只看 props/emits 接线） ----------
vi.mock('@/components/workshop/WorkshopAvatar.vue', async () => {
  const { defineComponent } = await import('vue');
  return {
    default: defineComponent({
      name: 'WorkshopAvatar',
      props: { streaming: { type: Boolean, default: false } },
      template: '<div data-testid="stub-avatar" :data-streaming="String(streaming)" />',
    }),
  };
});

vi.mock('@/components/workshop/WelcomePanel.vue', async () => {
  const { defineComponent } = await import('vue');
  return {
    default: defineComponent({
      name: 'WelcomePanel',
      emits: ['quick'],
      template:
        '<div data-testid="stub-welcome">' +
        '<button data-testid="stub-welcome-quick" @click="$emit(\'quick\', \'for 循环怎么用\')">快捷</button>' +
        '</div>',
    }),
  };
});

vi.mock('@/components/workshop/ClarifyBubble.vue', async () => {
  const { defineComponent } = await import('vue');
  return {
    default: defineComponent({
      name: 'ClarifyBubble',
      props: ['options', 'reason'],
      emits: ['pick'],
      template:
        '<div data-testid="stub-clarify">' +
        '<span data-testid="stub-clarify-reason">{{ reason }}</span>' +
        '<button v-for="o in options" :key="o.intent" :data-testid="\'clarify-opt-\' + o.intent" ' +
        '@click="$emit(\'pick\', o.intent)">{{ o.label }}</button>' +
        '</div>',
    }),
  };
});

vi.mock('@/components/workshop/QuizModeBar.vue', async () => {
  const { defineComponent } = await import('vue');
  return {
    default: defineComponent({
      name: 'QuizModeBar',
      props: ['round', 'total', 'kcLabel'],
      emits: ['exit'],
      template:
        '<div data-testid="stub-quizbar">' +
        '<span data-testid="stub-quiz-round">{{ round }}/{{ total }}</span>' +
        '<span data-testid="stub-quiz-kc">{{ kcLabel }}</span>' +
        '<button data-testid="stub-quiz-exit" @click="$emit(\'exit\')">退出</button>' +
        '</div>',
    }),
  };
});

vi.mock('@/components/workshop/ProgressCard.vue', async () => {
  const { defineComponent } = await import('vue');
  return {
    default: defineComponent({
      name: 'ProgressCard',
      props: ['snapshot'],
      emits: ['practice'],
      template:
        '<div data-testid="stub-progress">' +
        '<span data-testid="stub-progress-count">{{ snapshot.length }}</span>' +
        '<button data-testid="stub-practice" ' +
        '@click="$emit(\'practice\', snapshot[0] ? snapshot[0].kcCode : \'PY43-KC-001\')">练</button>' +
        '</div>',
    }),
  };
});

vi.mock('@/components/workshop/PlanCard.vue', async () => {
  const { defineComponent } = await import('vue');
  return {
    default: defineComponent({
      name: 'PlanCard',
      props: ['steps'],
      emits: ['open'],
      template:
        '<div data-testid="stub-plan">' +
        '<span data-testid="stub-plan-count">{{ steps.length }}</span>' +
        '<button v-for="s in steps" :key="s.id" :data-testid="\'plan-open-\' + (s.panel || \'none\')" ' +
        '@click="$emit(\'open\', s.panel || \'\')">{{ s.title }}</button>' +
        '</div>',
    }),
  };
});

vi.mock('@/components/workshop/ToolTraceLine.vue', async () => {
  const { defineComponent } = await import('vue');
  return {
    default: defineComponent({
      name: 'ToolTraceLine',
      props: ['traces'],
      template: '<div data-testid="stub-traceline">{{ traces.length }}</div>',
    }),
  };
});

vi.mock('@/components/workshop/AnswerFeedback.vue', async () => {
  const { defineComponent } = await import('vue');
  return {
    default: defineComponent({
      name: 'AnswerFeedback',
      props: ['messageId'],
      emits: ['feedback'],
      template:
        '<button data-testid="stub-feedback" @click="$emit(\'feedback\', true)">👍</button>',
    }),
  };
});

// 输入框替身：只负责把 submit payload 透传给 WorkshopChat（真输入交互由 ChatComposer 自己的逻辑承担）。
vi.mock('@/components/workshop/ChatComposer.vue', async () => {
  const { defineComponent } = await import('vue');
  return {
    default: defineComponent({
      name: 'ChatComposer',
      props: ['mentionItems', 'busy'],
      emits: ['submit'],
      template: '<div data-testid="stub-composer" />',
    }),
  };
});

import WorkshopChat from '@/components/workshop/WorkshopChat.vue';
import { useWorkshopActivityStore } from '@stores/workshopActivity';
import type { IntentRouteResponse } from '@/api/intent';

interface FakeSse {
  url: string;
  traces: { value: Array<Record<string, unknown>> };
  tokens: { value: string };
  result: { value: unknown };
  error: { value: { code: string; message: string } | null };
  status: { value: string };
  plan: { value: unknown };
  events: { value: Array<Record<string, unknown>> };
  state: { value: Record<string, unknown> };
  abort: ReturnType<typeof vi.fn>;
  start: ReturnType<typeof vi.fn>;
}

interface AskBody {
  question: string;
  kcCodes: string[];
  context: string;
  conversationId?: string;
  style?: string;
}

function sse(url: string): FakeSse {
  const inst = sseRegistry.get(url);
  if (!inst) throw new Error(`没有 ${url} 的假 SSE 实例（组件还没挂载？）`);
  return inst as FakeSse;
}

const TUTOR_URL = '/api/tutor/ask';
const WORKSHOP_URL = '/api/workshop/resources/stream';
const ORCH_URL = '/api/orchestrator/turn/stream';

function armRoute(partial: Partial<IntentRouteResponse> = {}): void {
  apiMocks.routeIntent.mockResolvedValue({
    intent: 'ASK',
    kcCodes: [],
    focusHint: '',
    confidence: 0.9,
    reason: '',
    target: '',
    secondaryIntent: null,
    ...partial,
  });
}

const RESOURCE_DETAIL = {
  id: 101,
  userId: 1,
  packId: 43,
  kcCodes: ['PY43-KC-001'],
  chapterId: null,
  type: 'HANDOUT',
  title: '循环讲义',
  contentFormat: 'STRUCT_JSON',
  content: { sections: [] },
  evidencePageIds: [],
  summary: null,
  safetyStatus: 'PASSED',
  groundingStatus: 'PASSED',
  feedbackStatus: 'NONE',
  feedbackNote: null,
  createdAt: '2026-06-11T00:00:00Z',
};

let router: Router;

function buildRouter(): Router {
  const stub = { template: '<div />' };
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: stub },
      { path: '/world', component: stub },
      { path: '/library', component: stub },
      { path: '/bug-mine', component: stub },
      { path: '/training', component: stub },
      { path: '/workshop', component: stub },
      { path: '/arena/:chapter(\\d+)', component: stub },
      { path: '/review-hall', component: stub },
      { path: '/examples', component: stub },
      { path: '/problems', component: stub },
      { path: '/lecture/:chapter(\\d+)', component: stub },
    ],
  });
}

function mountChat(): VueWrapper {
  return mount(WorkshopChat, {
    global: {
      plugins: [router],
      stubs: {
        MarkdownView: true,
        BilibiliVideoCards: true,
        AgentStudioStream: true,
        CoursewareEvidence: true,
        TutorVisualAids: true,
        HandoutView: true,
        MindMapView: true,
        PracticeList: true,
        CodeLabView: true,
        ReadingList: true,
        StoryboardView: true,
      },
    },
  });
}

interface SubmitExtra {
  manualKcCodes?: string[];
  contextHint?: string;
  forcedIntent?: string | null;
}

async function submit(wrapper: VueWrapper, text: string, extra: SubmitExtra = {}): Promise<void> {
  wrapper.findComponent({ name: 'ChatComposer' }).vm.$emit('submit', {
    text,
    manualKcCodes: [],
    contextHint: '',
    forcedIntent: null,
    ...extra,
  });
  await flushPromises();
}

enableAutoUnmount(afterEach);

describe('WorkshopChat 16 工具分发', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    router = buildRouter();
    // jsdom 没有 Element.scrollTo，补一个空实现避免 nextTick 回调抛错。
    Object.defineProperty(Element.prototype, 'scrollTo', {
      value: () => undefined,
      writable: true,
      configurable: true,
    });
    Object.values(apiMocks).forEach((fn) => fn.mockReset());
    apiMocks.listChapters.mockResolvedValue([]);
    apiMocks.listKnowledgeComponents.mockResolvedValue([]);
    apiMocks.listWorkshopResources.mockResolvedValue([]);
    apiMocks.getWorkshopResource.mockResolvedValue(RESOURCE_DETAIL);
    apiMocks.fetchMastery.mockResolvedValue([]);
    apiMocks.styleEngaged.mockResolvedValue(undefined);
    apiMocks.postLearningEvent.mockResolvedValue(undefined);
    armRoute();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('欢迎屏（WelcomePanel）quick 事件直接走 onSubmit → 默认答疑管线', async () => {
    const wrapper = mountChat();
    await flushPromises();
    // 注意：宿主在 WelcomePanel 上挂了 data-testid="workshop-welcome"，attrs 透传会覆盖
    // stub 根节点的 testid，故存在性断言用宿主 testid，交互用 stub 内部按钮。
    expect(wrapper.find('[data-testid="workshop-welcome"]').exists()).toBe(true);

    await wrapper.find('[data-testid="stub-welcome-quick"]').trigger('click');
    await flushPromises();

    expect(apiMocks.routeIntent).toHaveBeenCalledTimes(1);
    expect(sse(TUTOR_URL).start).toHaveBeenCalledTimes(1);
    // 欢迎屏消失，对话流出现用户气泡 + 助手气泡（带像素头像）。
    expect(wrapper.find('[data-testid="workshop-welcome"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="stub-avatar"]').exists()).toBe(true);
  });

  it('ASK 高置信：answer 气泡 + trace 快照（ToolTraceLine）+ 为你定制标签 + 换讲法 chips', async () => {
    const wrapper = mountChat();
    await flushPromises();
    const tutor = sse(TUTOR_URL);
    tutor.start.mockImplementation(async () => {
      tutor.traces.value = [{ node: 'TutorAgent', status: 'ok', elapsed_ms: 5 }];
      tutor.status.value = 'done';
      tutor.result.value = {
        answer_md: '这是答案。',
        personalization_note: '按你的视觉偏好定制',
      };
    });
    armRoute({ intent: 'ASK', kcCodes: ['PY43-KC-001'] });

    await submit(wrapper, '什么是循环');

    expect(tutor.start).toHaveBeenCalledTimes(1);
    const body = tutor.start.mock.calls[0]?.[0] as AskBody;
    expect(body.question).toBe('什么是循环');
    expect(body.kcCodes).toEqual(['PY43-KC-001']);
    expect(wrapper.find('[data-testid="stub-traceline"]').text()).toBe('1');
    expect(wrapper.find('.msg__pnote').exists()).toBe(true);
    expect(wrapper.find('.msg__pnote').attributes('title')).toBe('按你的视觉偏好定制');
    expect(wrapper.find('[data-testid="style-chips"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stub-feedback"]').exists()).toBe(true);
  });

  it('换讲法 chips：点「图解」→ styleEngaged 回流 + 带 style 重问同一问题', async () => {
    const wrapper = mountChat();
    await flushPromises();
    armRoute({ intent: 'ASK', kcCodes: ['PY43-KC-001'] });
    await submit(wrapper, '什么是循环');

    const chips = wrapper.find('[data-testid="style-chips"]').findAll('button');
    expect(chips.length).toBe(3);
    await chips[0]?.trigger('click');
    await flushPromises();

    expect(apiMocks.styleEngaged).toHaveBeenCalledWith('visual', 'PY43-KC-001');
    const tutor = sse(TUTOR_URL);
    expect(tutor.start).toHaveBeenCalledTimes(2);
    const body = tutor.start.mock.calls[1]?.[0] as AskBody;
    expect(body.style).toBe('visual');
    expect(body.question).toBe('什么是循环');
  });

  it('EXPLAIN_CODE / FIX_BUG：context 前缀 directive + 工具徽标', async () => {
    const wrapper = mountChat();
    await flushPromises();
    const tutor = sse(TUTOR_URL);

    armRoute({ intent: 'EXPLAIN_CODE' });
    await submit(wrapper, '这段代码什么意思');
    let body = tutor.start.mock.calls[0]?.[0] as AskBody;
    expect(body.context.startsWith('【讲代码】')).toBe(true);
    expect(wrapper.html()).toContain('⌨ 讲代码');

    armRoute({ intent: 'FIX_BUG' });
    await submit(wrapper, '报错了帮我看看');
    body = tutor.start.mock.calls[1]?.[0] as AskBody;
    expect(body.context.startsWith('【修报错】')).toBe(true);
    expect(wrapper.html()).toContain('⚠ 修报错');
  });

  it('ASKME 考核模式状态机：开考/评卷 directive、轮次推进、模式中不路由、3 轮后退出', async () => {
    const wrapper = mountChat();
    await flushPromises();
    const tutor = sse(TUTOR_URL);
    armRoute({ intent: 'ASKME', kcCodes: ['PY43-KC-002'] });

    await submit(wrapper, '考考我循环');
    // 宿主在 QuizModeBar 上挂了 data-testid="quiz-mode-bar"（覆盖 stub 根 testid）。
    expect(wrapper.find('[data-testid="quiz-mode-bar"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stub-quiz-round"]').text()).toBe('1/3');
    expect(apiMocks.routeIntent).toHaveBeenCalledTimes(1);

    await submit(wrapper, '我的第 1 答');
    expect(wrapper.find('[data-testid="stub-quiz-round"]').text()).toBe('2/3');
    await submit(wrapper, '我的第 2 答');
    expect(wrapper.find('[data-testid="stub-quiz-round"]').text()).toBe('3/3');
    await submit(wrapper, '我的第 3 答');

    // 3 轮答完：退出考核模式，期间普通输入未再走路由。
    expect(wrapper.find('[data-testid="quiz-mode-bar"]').exists()).toBe(false);
    expect(apiMocks.routeIntent).toHaveBeenCalledTimes(1);
    expect(apiMocks.postLearningEvent).toHaveBeenCalledWith({
      eventType: 'quiz_finished',
      payload: { kcCodes: ['PY43-KC-002'], rounds: 3 },
    });

    const contexts = tutor.start.mock.calls.map((c) => (c[0] as AskBody).context);
    expect(contexts[0]).toContain('【考核模式·第1/3轮】');
    expect(contexts[1]).toContain('第1/3轮·评卷');
    expect(contexts[2]).toContain('第2/3轮·评卷');
    expect(contexts[3]).toContain('第3/3轮·评卷');
  });

  it('QuizModeBar exit 事件：手动退出考核模式', async () => {
    const wrapper = mountChat();
    await flushPromises();
    armRoute({ intent: 'ASKME', kcCodes: [] });
    await submit(wrapper, '考考我');
    expect(wrapper.find('[data-testid="quiz-mode-bar"]').exists()).toBe(true);

    await wrapper.find('[data-testid="stub-quiz-exit"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="quiz-mode-bar"]').exists()).toBe(false);
  });

  it('资源意图（HANDOUT 带 KC）→ workshop_sse 管线 + 资源卡', async () => {
    const wrapper = mountChat();
    await flushPromises();
    armRoute({ intent: 'HANDOUT', kcCodes: ['PY43-KC-001'], focusHint: '循环' });

    await submit(wrapper, '给我做份循环讲义');

    const workshop = sse(WORKSHOP_URL);
    expect(workshop.start).toHaveBeenCalledTimes(1);
    expect(workshop.start.mock.calls[0]?.[0]).toEqual({
      kcCodes: ['PY43-KC-001'],
      type: 'HANDOUT',
      focusHint: '循环',
    });
    expect(sse(TUTOR_URL).start).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="resource-card"]').exists()).toBe(true);
    expect(wrapper.html()).toContain('循环讲义');
  });

  it('资源意图但没锚定到 KC → 答案优先回退答疑', async () => {
    const wrapper = mountChat();
    await flushPromises();
    armRoute({ intent: 'MINDMAP', kcCodes: [] });

    await submit(wrapper, '画个导图呗');

    expect(sse(WORKSHOP_URL).start).not.toHaveBeenCalled();
    expect(sse(TUTOR_URL).start).toHaveBeenCalledTimes(1);
  });

  it('VIDEO → 纯前端视频卡片气泡（零 LLM）', async () => {
    const wrapper = mountChat();
    await flushPromises();
    armRoute({ intent: 'VIDEO', kcCodes: [], focusHint: '循环' });

    await submit(wrapper, '找个循环的视频');

    expect(sse(TUTOR_URL).start).not.toHaveBeenCalled();
    expect(wrapper.find('[data-kind="video"]').exists()).toBe(true);
  });

  it('REVIEW → 引导跳转错题矿洞 /bug-mine', async () => {
    const wrapper = mountChat();
    await flushPromises();
    armRoute({ intent: 'REVIEW', kcCodes: [] });

    await submit(wrapper, '带我复习一下');

    expect(router.currentRoute.value.path).toBe('/bug-mine');
  });

  it('PLAN → orchestrator_sse 管线：activity 点亮、PlanCard 渲染、open(panel) 映射跳转', async () => {
    const wrapper = mountChat();
    await flushPromises();
    const orch = sse(ORCH_URL);
    orch.start.mockImplementation(async () => {
      orch.status.value = 'done';
      orch.result.value = {
        reply_md: '计划如下',
        clarifying_question: null,
        needs_user_input: false,
        plan: [
          {
            id: 's1',
            phase: 'learn',
            title: '读讲义',
            detail: '先把概念过一遍',
            agent: 'ResourceDesignerAgent',
            kc_codes: ['PY43-KC-001'],
            panel: 'library',
            status: 'pending',
            resource_type: 'HANDOUT',
          },
        ],
        dispatch_results: [],
        profile_updates: [],
        trace: [],
        done: true,
      };
    });
    armRoute({ intent: 'PLAN' });

    await submit(wrapper, '帮我规划一下这周怎么学');

    expect(orch.start).toHaveBeenCalledTimes(1);
    const body = orch.start.mock.calls[0]?.[0] as Record<string, unknown>;
    expect(body.message).toBe('帮我规划一下这周怎么学');
    expect(Array.isArray(body.history)).toBe(true);

    const activity = useWorkshopActivityStore();
    expect(activity.kind).toBe('orchestrator');
    expect(activity.status).toBe('done');

    expect(wrapper.find('[data-testid="plan-card-msg"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stub-plan-count"]').text()).toBe('1');

    await wrapper.find('[data-testid="plan-open-library"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/library');
  });

  it('PROGRESS → 纯前端学情卡：空快照先 loadField；practice 事件跳训练场', async () => {
    const wrapper = mountChat();
    await flushPromises();
    apiMocks.fetchMastery.mockResolvedValue([
      { kcCode: 'PY43-KC-005', masteryScore: 0.3, confidence: 0.5, lastEventAt: null, latestSource: null },
      { kcCode: 'PY43-KC-006', masteryScore: 0.8, confidence: 0.6, lastEventAt: null, latestSource: null },
    ]);
    armRoute({ intent: 'PROGRESS' });

    await submit(wrapper, '我学得怎么样了');

    expect(apiMocks.fetchMastery).toHaveBeenCalled();
    expect(wrapper.find('[data-testid="progress-card-msg"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stub-progress-count"]').text()).toBe('2');

    await wrapper.find('[data-testid="stub-practice"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/training');
  });

  it('NAVIGATE：已知地标出确认气泡（点前往跳转）；未知 target 回退 ASK', async () => {
    const wrapper = mountChat();
    await flushPromises();
    armRoute({ intent: 'NAVIGATE', target: 'library' });

    await submit(wrapper, '带我去图书馆');
    expect(wrapper.find('[data-testid="navigate-msg"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="navigate-msg"]').text()).toContain('带你去图书馆');
    await wrapper.find('[data-testid="navigate-go"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/library');

    // 未知地标 → 回退答疑。
    armRoute({ intent: 'NAVIGATE', target: 'cafeteria' });
    await submit(wrapper, '带我去食堂');
    expect(sse(TUTOR_URL).start).toHaveBeenCalledTimes(1);
    expect(wrapper.findAll('[data-testid="navigate-msg"]').length).toBe(1);
  });

  it('NAVIGATE 地标映射表：arena/bugmine/world/workshop 全部指向真实路由', async () => {
    const wrapper = mountChat();
    await flushPromises();
    const cases: Array<[string, string]> = [
      ['arena', '/arena/1'],
      ['bugmine', '/bug-mine'],
      ['world', '/world'],
      ['workshop', '/workshop'],
    ];
    for (const [target, path] of cases) {
      armRoute({ intent: 'NAVIGATE', target });
      await submit(wrapper, `带我去${target}`);
      const goButtons = wrapper.findAll('[data-testid="navigate-go"]');
      await goButtons[goButtons.length - 1]?.trigger('click');
      await flushPromises();
      expect(router.currentRoute.value.path, `target=${target}`).toBe(path);
    }
  });

  it('MY_STUDY → loadResources 后按 focusHint 过滤书房资源，点击卡片 emit open-resource', async () => {
    const wrapper = mountChat();
    await flushPromises();
    apiMocks.listWorkshopResources.mockResolvedValue([
      {
        id: 1, type: 'HANDOUT', title: 'for 循环精讲', summary: null,
        kcCodes: ['PY43-KC-010'], groundingStatus: 'PASSED', feedbackStatus: 'NONE',
        createdAt: '2026-06-10T00:00:00Z',
      },
      {
        id: 2, type: 'PRACTICE', title: '函数小练', summary: null,
        kcCodes: ['PY43-KC-020'], groundingStatus: 'PASSED', feedbackStatus: 'NONE',
        createdAt: '2026-06-09T00:00:00Z',
      },
    ]);
    armRoute({ intent: 'MY_STUDY', focusHint: '循环' });

    await submit(wrapper, '找找我之前生成的循环资料');

    expect(apiMocks.listWorkshopResources).toHaveBeenCalled();
    const items = wrapper.findAll('[data-testid="mystudy-item"]');
    expect(items.length).toBe(1);
    expect(items[0]?.text()).toContain('for 循环精讲');

    await items[0]?.find('button').trigger('click');
    expect(wrapper.emitted('open-resource')?.[0]).toEqual([1]);
  });

  it('MY_STUDY 空结果 → 引导文案', async () => {
    const wrapper = mountChat();
    await flushPromises();
    apiMocks.listWorkshopResources.mockResolvedValue([]);
    armRoute({ intent: 'MY_STUDY', focusHint: '量子纠缠' });

    await submit(wrapper, '找找量子纠缠的资料');

    expect(wrapper.find('[data-testid="mystudy-msg"]').text()).toContain('还没找到相关的资源');
  });

  it('低置信（<0.45）→ ClarifyBubble（候选含路由猜测 + 常用工具）；pick 后按所选意图执行原文', async () => {
    const wrapper = mountChat();
    await flushPromises();
    armRoute({
      intent: 'PRACTICE',
      kcCodes: ['PY43-KC-001'],
      focusHint: '循环',
      confidence: 0.3,
      reason: '不确定是想做题还是想听讲',
    });

    await submit(wrapper, '来点循环的');

    // 未直接执行任何管线，先澄清。
    expect(sse(WORKSHOP_URL).start).not.toHaveBeenCalled();
    expect(sse(TUTOR_URL).start).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="stub-clarify"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stub-clarify-reason"]').text()).toContain('不确定');
    // 猜测意图置顶 + 凑满 5 个候选。
    expect(wrapper.find('[data-testid="clarify-opt-PRACTICE"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stub-clarify"]').findAll('button').length).toBe(5);

    await wrapper.find('[data-testid="clarify-opt-PRACTICE"]').trigger('click');
    await flushPromises();
    expect(sse(WORKSHOP_URL).start).toHaveBeenCalledTimes(1);
    expect(sse(WORKSHOP_URL).start.mock.calls[0]?.[0]).toMatchObject({
      type: 'PRACTICE',
      kcCodes: ['PY43-KC-001'],
    });
  });

  it('中置信（0.45~0.7）→ 正常执行 + 答后纠错 chips；点击改派并记入 sessionCorrections', async () => {
    const wrapper = mountChat();
    await flushPromises();
    armRoute({ intent: 'VIDEO', confidence: 0.55, focusHint: '循环' });

    await submit(wrapper, '来个循环的东西');

    // 已执行（视频卡）且挂出纠错 chips。
    expect(wrapper.find('[data-kind="video"]').exists()).toBe(true);
    const chips = wrapper.find('[data-testid="correction-chips"]');
    expect(chips.exists()).toBe(true);
    const labels = chips.findAll('button').map((b) => b.text());
    expect(labels.length).toBe(3);

    // 点「问答」改派 → tutor 执行原文本。
    await chips.findAll('button')[0]?.trigger('click');
    await flushPromises();
    expect(sse(TUTOR_URL).start).toHaveBeenCalledTimes(1);

    // 下一次路由请求带上 sessionCorrections（≤3 条 FIFO）。
    armRoute({ intent: 'ASK', confidence: 0.9 });
    await submit(wrapper, '随便问问');
    const lastCall = apiMocks.routeIntent.mock.calls.at(-1)?.[0] as Record<string, unknown>;
    expect(lastCall.sessionCorrections).toEqual([
      { message: '来个循环的东西', correctedIntent: 'ASK' },
    ]);
  });

  it('高置信（≥0.7）不挂纠错 chips', async () => {
    const wrapper = mountChat();
    await flushPromises();
    armRoute({ intent: 'ASK', confidence: 0.92 });
    await submit(wrapper, '什么是变量');
    expect(wrapper.find('[data-testid="correction-chips"]').exists()).toBe(false);
  });

  it('secondaryIntent → 主动作完成后出「接着」chip，点击按该意图执行（KC 沿用）', async () => {
    const wrapper = mountChat();
    await flushPromises();
    armRoute({
      intent: 'ASK',
      kcCodes: ['PY43-KC-003'],
      confidence: 0.9,
      secondaryIntent: 'PRACTICE',
      focusHint: '列表',
    });

    await submit(wrapper, '讲讲列表，讲完出几道题');

    const chip = wrapper.find('[data-testid="secondary-go"]');
    expect(chip.exists()).toBe(true);
    expect(chip.text()).toContain('练习题');

    await chip.trigger('click');
    await flushPromises();
    expect(sse(WORKSHOP_URL).start).toHaveBeenCalledTimes(1);
    expect(sse(WORKSHOP_URL).start.mock.calls[0]?.[0]).toMatchObject({
      type: 'PRACTICE',
      kcCodes: ['PY43-KC-003'],
    });
    // chip 用过即收，不再重复执行。
    expect(wrapper.find('[data-testid="secondary-go"]').exists()).toBe(false);
  });

  it('错误气泡「重试」chip：重放上次 runAsk 参数', async () => {
    const wrapper = mountChat();
    await flushPromises();
    const tutor = sse(TUTOR_URL);
    tutor.start.mockImplementation(async () => {
      tutor.status.value = 'error';
      tutor.error.value = { code: 'X', message: '上游超时' };
    });
    armRoute({ intent: 'ASK', kcCodes: ['PY43-KC-001'] });

    await submit(wrapper, '什么是字典');
    expect(wrapper.find('.msg--error').exists()).toBe(true);
    const retry = wrapper.find('[data-testid="retry-chip"]');
    expect(retry.exists()).toBe(true);

    await retry.trigger('click');
    await flushPromises();
    expect(tutor.start).toHaveBeenCalledTimes(2);
    expect(tutor.start.mock.calls[1]?.[0]).toEqual(tutor.start.mock.calls[0]?.[0]);
  });

  it('流式「⏹ 停止」：abort 中断并把气泡标记（已中断），不按错误处理', async () => {
    const wrapper = mountChat();
    await flushPromises();
    const tutor = sse(TUTOR_URL);
    let release: (() => void) | null = null;
    tutor.start.mockImplementation(() => {
      tutor.status.value = 'streaming';
      tutor.tokens.value = '讲到一半';
      return new Promise<void>((resolve) => {
        release = resolve;
      });
    });
    tutor.abort.mockImplementation(() => {
      tutor.status.value = 'done';
      release?.();
    });
    armRoute({ intent: 'ASK' });

    await submit(wrapper, '讲讲 while');
    const stop = wrapper.find('[data-testid="stop-stream"]');
    expect(stop.exists()).toBe(true);

    await stop.trigger('click');
    await flushPromises();

    expect(tutor.abort).toHaveBeenCalledTimes(1);
    expect(wrapper.find('.msg--error').exists()).toBe(false);
    // 答案正文经 MarkdownView（已 stub）渲染 → 从 source attr 断言中断标记与已生成片段。
    expect(wrapper.html()).toContain('（已中断）');
    expect(wrapper.html()).toContain('讲到一半');
    expect(wrapper.find('[data-testid="stop-stream"]').exists()).toBe(false);
  });

  it('多轮路由上下文：第二次提问带 recentTurns / lastIntent / scope', async () => {
    const wrapper = mountChat();
    await flushPromises();
    armRoute({ intent: 'ASK', kcCodes: ['PY43-KC-001'] });
    await submit(wrapper, '什么是循环');

    armRoute({ intent: 'ASK' });
    await submit(wrapper, '换个方式再讲一遍');

    const req = apiMocks.routeIntent.mock.calls.at(-1)?.[0] as Record<string, unknown>;
    expect(req.scope).toBe('default');
    expect(req.lastIntent).toBe('ASK');
    expect(req.lastKcCodes).toEqual(['PY43-KC-001']);
    const turns = req.recentTurns as Array<{ role: string; content: string }>;
    expect(turns.length).toBeGreaterThanOrEqual(2);
    expect(turns[0]?.role).toBe('user');
    expect(turns[0]?.content).toBe('什么是循环');
    expect(turns.at(-1)?.role).toBe('assistant');
  });

  it('forcedIntent（/ 命令）不走路由直接执行', async () => {
    const wrapper = mountChat();
    await flushPromises();
    await submit(wrapper, '随便聊聊', { forcedIntent: 'ASK' });
    expect(apiMocks.routeIntent).not.toHaveBeenCalled();
    expect(sse(TUTOR_URL).start).toHaveBeenCalledTimes(1);

    // 资源类强制 + 已 @KC：也不路由，直接生成。
    await submit(wrapper, '出题', { forcedIntent: 'PRACTICE', manualKcCodes: ['PY43-KC-001'] });
    expect(apiMocks.routeIntent).not.toHaveBeenCalled();
    expect(sse(WORKSHOP_URL).start).toHaveBeenCalledTimes(1);
  });
});
