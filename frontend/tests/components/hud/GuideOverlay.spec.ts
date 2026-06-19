/**
 * GuideOverlay（AI 助教悬浮窗）组件测试 —— R-14 向导加固 TDD。
 *
 * 覆盖：
 *   1. 同一时刻只允许一个进行中请求（流式期间再次提交不触发第二次 start）；
 *   2. 流式失败后已收到的部分回答仍可见 + 清晰失败态；
 *   3. 重试只重发失败那一轮用户输入（message=原文、history 截到该轮之前、不加新用户气泡）；
 *   4. 关闭再打开悬浮窗保留会话历史；
 *   5. 计划步骤「前往」只对白名单内部面板渲染并跳转；
 *   6. prefers-reduced-motion 降级 + 键盘交互（Enter 发送 / Shift+Enter 换行 / ESC 关闭）；
 *   7. 状态与错误文本可被无障碍朗读（role=status aria-live / role=alert）；
 *   8. 建议问题随对话状态变化（起步 / 计划后 / 澄清追问态各不相同）；
 *   9. 流式 Markdown + trace 展示与 workshopActivity 共享状态回归。
 *
 * SSE 流用确定性假实例替身（与 WorkshopChat.spec 同款 registry 模式），不发真网络请求。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { nextTick } from 'vue';

// ---------- 基础设施 mock ----------
const apiMocks = vi.hoisted(() => ({
  getMainlineOverview: vi.fn(),
  postVideoProgress: vi.fn(),
  postPageRead: vi.fn(),
  postExampleDone: vi.fn(),
  postEndlessSummary: vi.fn(),
  postRecoveryErrorbook: vi.fn(),
}));

vi.mock('@api/mainline', () => apiMocks);

vi.mock('@/composables/useStardewFx', () => ({
  playUiClick: vi.fn(),
  playUiHover: vi.fn(),
}));

/** 假 SSE 实例注册表（按 URL），GuideOverlay 的 orchestrator 流从这里取控制柄。 */
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
      inst.start.mockImplementation(async () => {
        inst.status.value = 'done';
        inst.result.value = {
          reply_md: '默认回答',
          clarifying_question: null,
          needs_user_input: false,
          plan: [],
          dispatch_results: [],
          profile_updates: [],
          trace: [],
          done: true,
        };
      });
      sseRegistry.set(url, inst);
      return inst;
    },
  };
});

import GuideOverlay from '@/components/hud/GuideOverlay.vue';
import { useGuideStore } from '@stores/guide';
import { useWorkshopActivityStore } from '@stores/workshopActivity';
import type {
  OrchestratorPlanStep,
  OrchestratorResult,
  OrchestratorTurnRequest,
} from '@/api/orchestrator';

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

const ORCH_URL = '/api/orchestrator/turn/stream';

function sse(): FakeSse {
  const inst = sseRegistry.get(ORCH_URL);
  if (!inst) throw new Error(`没有 ${ORCH_URL} 的假 SSE 实例（组件还没挂载？）`);
  return inst as FakeSse;
}

function okResult(partial: Partial<OrchestratorResult> = {}): OrchestratorResult {
  return {
    reply_md: '默认回答',
    clarifying_question: null,
    needs_user_input: false,
    plan: [],
    dispatch_results: [],
    profile_updates: [],
    trace: [],
    done: true,
    ...partial,
  };
}

function planStep(partial: Partial<OrchestratorPlanStep> = {}): OrchestratorPlanStep {
  return {
    id: 's1',
    phase: 'learn',
    title: '读讲义',
    detail: '先把概念过一遍',
    agent: 'ResourceDesignerAgent',
    kc_codes: ['PY43-KC-031'],
    panel: 'library',
    status: 'pending',
    resource_type: 'HANDOUT',
    ...partial,
  };
}

/** 复刻真实 useSseStream.start 的开场重置：清掉上一轮的 result/error/tokens/traces。 */
function resetStreamRefs(orch: FakeSse): void {
  orch.traces.value = [];
  orch.tokens.value = '';
  orch.result.value = null;
  orch.error.value = null;
  orch.plan.value = null;
}

/** 让假流「成功」一轮：可注入 tokens / traces / result。 */
function armSuccess(partial: Partial<OrchestratorResult> = {}, tokens = ''): void {
  const orch = sse();
  orch.start.mockImplementation(async () => {
    resetStreamRefs(orch);
    orch.status.value = 'streaming';
    if (tokens) {
      orch.tokens.value = tokens;
      await nextTick();
    }
    orch.result.value = okResult(partial);
    orch.status.value = 'done';
  });
}

/** 让假流「中途失败」：先吐部分 token，再报错。 */
function armFailure(message: string, partialTokens = ''): void {
  const orch = sse();
  orch.start.mockImplementation(async () => {
    resetStreamRefs(orch);
    orch.status.value = 'streaming';
    if (partialTokens) {
      orch.tokens.value = partialTokens;
      orch.traces.value = [{ node: 'dispatch', status: 'running', elapsed_ms: 5 }];
      await nextTick();
    }
    orch.error.value = { code: 'UPSTREAM', message };
    orch.status.value = 'error';
  });
}

function mockMatchMedia(matches: boolean): void {
  const factory = (query: string): MediaQueryList =>
    ({
      matches,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(() => true),
    }) as unknown as MediaQueryList;
  vi.stubGlobal('matchMedia', vi.fn(factory));
}

let router: Router;

function buildRouter(): Router {
  const stub = { template: '<div />' };
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: stub },
      { path: '/library', component: stub },
      { path: '/training', component: stub },
      { path: '/workshop', component: stub },
      { path: '/problems', component: stub },
      { path: '/examples', component: stub },
      { path: '/review-hall', component: stub },
      { path: '/lecture/:chapter(\\d+)', component: stub },
    ],
  });
}

async function mountOverlay(): Promise<VueWrapper> {
  const wrapper = mount(GuideOverlay, {
    global: {
      plugins: [router],
      stubs: { MarkdownView: true },
    },
  });
  useGuideStore().openPanel();
  await flushPromises();
  return wrapper;
}

/** 通过输入框 + 表单提交发一条消息（与真实用户路径一致）。 */
async function sendMessage(wrapper: VueWrapper, text: string): Promise<void> {
  await wrapper.find('[data-testid="guide-input"]').setValue(text);
  await wrapper.find('form.guide-input').trigger('submit');
  await flushPromises();
}

function chipTexts(wrapper: VueWrapper): string[] {
  return wrapper.findAll('.guide-suggest__chip').map((c) => c.text());
}

function requestBody(callIndex: number): OrchestratorTurnRequest {
  const body = sse().start.mock.calls[callIndex]?.[0];
  if (!body) throw new Error(`start 第 ${callIndex} 次调用没有请求体`);
  return body as OrchestratorTurnRequest;
}

enableAutoUnmount(afterEach);

describe('GuideOverlay（AI 助教悬浮窗）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    window.localStorage.clear();
    router = buildRouter();
    Object.values(apiMocks).forEach((fn) => fn.mockReset());
    apiMocks.getMainlineOverview.mockResolvedValue({ totalStars: 0, chapters: [] });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  describe('1. 同一时刻只允许一个进行中请求', () => {
    it('流式进行中再次提交：不触发第二次 start、不追加用户气泡、chips 与输入禁用', async () => {
      const wrapper = await mountOverlay();
      const orch = sse();
      let release: (() => void) | null = null;
      orch.start.mockImplementation(() => {
        resetStreamRefs(orch);
        orch.status.value = 'streaming';
        return new Promise<void>((resolve) => {
          release = resolve;
        });
      });

      await sendMessage(wrapper, '第一问');
      expect(orch.start).toHaveBeenCalledTimes(1);
      expect(wrapper.findAll('.guide-msg--user')).toHaveLength(1);

      // 流式中再次提交（textarea/按钮均禁用，表单兜底守卫也必须拦截）。
      await sendMessage(wrapper, '第二问');
      expect(orch.start).toHaveBeenCalledTimes(1);
      expect(wrapper.findAll('.guide-msg--user')).toHaveLength(1);
      expect(wrapper.find('[data-testid="guide-input"]').attributes('disabled')).toBeDefined();
      expect(wrapper.find('[data-testid="guide-send"]').attributes('disabled')).toBeDefined();
      expect(wrapper.find('.guide-suggest__chip').attributes('disabled')).toBeDefined();

      // 放行流式收尾，状态回 idle。
      orch.result.value = okResult({ reply_md: '收尾回答' });
      orch.status.value = 'done';
      release?.();
      await flushPromises();
      expect(useGuideStore().status).toBe('idle');
    });
  });

  describe('2. 流式失败后部分回答仍可见 + 清晰失败态', () => {
    it('失败气泡保留部分回答、标注中断说明，workshopActivity 同步 error', async () => {
      const wrapper = await mountOverlay();
      armFailure('上游超时', '前半段答案内容');

      await sendMessage(wrapper, '讲讲 while');

      // 已收到的部分回答仍然可见（经 MarkdownView 渲染，stub 后断言 source 透传）。
      expect(wrapper.html()).toContain('前半段答案内容');
      const failedBubble = wrapper.find('.guide-bubble--failed');
      expect(failedBubble.exists()).toBe(true);
      // 清晰失败态：失败气泡内有中断说明。
      expect(wrapper.find('[data-testid="guide-fail-note"]').exists()).toBe(true);
      expect(wrapper.find('[data-testid="guide-fail-note"]').text()).toContain('中断');
      // 共享活动状态收尾为 error（WorkshopChat 侧 ThinkingStream 依赖）。
      expect(useWorkshopActivityStore().status).toBe('error');
    });
  });

  describe('3. 重试只重发失败那一轮', () => {
    it('重试请求 message=失败轮原文、history 截到该轮之前、不追加用户气泡', async () => {
      const wrapper = await mountOverlay();

      armSuccess({ reply_md: '第一答' });
      await sendMessage(wrapper, '第一问');

      armFailure('上游超时', '半截回答');
      await sendMessage(wrapper, '第二问');
      expect(wrapper.find('[data-testid="guide-retry"]').exists()).toBe(true);

      armSuccess({ reply_md: '重试成功的回答' });
      await wrapper.find('[data-testid="guide-retry"]').trigger('click');
      await flushPromises();

      const orch = sse();
      expect(orch.start).toHaveBeenCalledTimes(3);
      const retryBody = requestBody(2);
      expect(retryBody.message).toBe('第二问');
      // 历史只到失败轮之前：不含「第二问」也不含失败占位。
      expect(retryBody.history).toEqual([
        { role: 'user', content: '第一问' },
        { role: 'assistant', content: '第一答' },
      ]);
      expect(retryBody.history).toEqual(requestBody(1).history);

      // 不追加新的用户气泡：仍是 2 条（第一问 + 第二问）。
      // 注：气泡正文经 MarkdownView 渲染（测试里 stub），内容在 source 属性上，用 html() 断言。
      const userBubbles = wrapper.findAll('.guide-msg--user');
      expect(userBubbles).toHaveLength(2);
      expect(userBubbles.filter((b) => b.html().includes('第二问'))).toHaveLength(1);
      // 失败占位被新回答替换。
      expect(wrapper.find('.guide-bubble--failed').exists()).toBe(false);
      expect(wrapper.html()).toContain('重试成功的回答');
      expect(useGuideStore().status).toBe('idle');
    });
  });

  describe('4. 关闭再打开保留会话历史', () => {
    it('closePanel 后 openPanel：气泡原样保留', async () => {
      const wrapper = await mountOverlay();
      armSuccess({ reply_md: '第一答' });
      await sendMessage(wrapper, '第一问');

      const guide = useGuideStore();
      guide.closePanel();
      await nextTick();
      expect(wrapper.find('[data-testid="guide-overlay"]').classes()).not.toContain('guide-overlay--open');

      guide.openPanel();
      await nextTick();
      expect(wrapper.find('[data-testid="guide-overlay"]').classes()).toContain('guide-overlay--open');
      expect(wrapper.html()).toContain('第一问');
      expect(wrapper.html()).toContain('第一答');
    });
  });

  describe('5. 计划步骤只能跳白名单内部面板', () => {
    it('白名单外的 panel 不渲染「前往」按钮；白名单内点击跳对应路由并收起悬浮窗', async () => {
      const wrapper = await mountOverlay();
      armSuccess({
        reply_md: '给你的计划',
        plan: [
          planStep({ id: 's1', panel: 'library', title: '读讲义' }),
          planStep({ id: 's2', panel: 'https://evil.example/phish', title: '可疑步骤' }),
          planStep({ id: 's3', panel: null, title: '纯说明步骤' }),
        ],
      });
      await sendMessage(wrapper, '帮我规划');

      const goButtons = wrapper.findAll('.guide-plan__go');
      expect(goButtons).toHaveLength(1);
      expect(goButtons[0]?.text()).toBe('去书房');

      await goButtons[0]?.trigger('click');
      await flushPromises();
      expect(router.currentRoute.value.path).toBe('/library');
      expect(useGuideStore().open).toBe(false);
    });
  });

  describe('6. prefers-reduced-motion 与键盘交互', () => {
    it('系统偏好减少动效时挂 reduced 类降级动画', async () => {
      mockMatchMedia(true);
      const wrapper = await mountOverlay();
      expect(wrapper.find('[data-testid="guide-overlay"]').classes()).toContain('guide-overlay--reduced');
    });

    it('未开启减少动效时不挂 reduced 类', async () => {
      mockMatchMedia(false);
      const wrapper = await mountOverlay();
      expect(wrapper.find('[data-testid="guide-overlay"]').classes()).not.toContain('guide-overlay--reduced');
    });

    it('Enter 发送、Shift+Enter 不发送', async () => {
      const wrapper = await mountOverlay();
      const orch = sse();
      const input = wrapper.find('[data-testid="guide-input"]');

      await input.setValue('shift 换行不该发送');
      await input.trigger('keydown', { key: 'Enter', shiftKey: true });
      await flushPromises();
      expect(orch.start).not.toHaveBeenCalled();

      await input.trigger('keydown', { key: 'Enter', shiftKey: false });
      await flushPromises();
      expect(orch.start).toHaveBeenCalledTimes(1);
    });

    it('焦点在输入框内按 ESC 也能关闭悬浮窗', async () => {
      const wrapper = await mountOverlay();
      expect(useGuideStore().open).toBe(true);
      await wrapper.find('[data-testid="guide-input"]').trigger('keydown', { key: 'Escape' });
      expect(useGuideStore().open).toBe(false);
    });
  });

  describe('7. 状态/错误文本可被无障碍朗读', () => {
    it('流式中 aria-live=polite 状态区朗读「正在回复」，收尾后清空', async () => {
      const wrapper = await mountOverlay();
      const orch = sse();
      let release: (() => void) | null = null;
      orch.start.mockImplementation(() => {
        resetStreamRefs(orch);
        orch.status.value = 'streaming';
        return new Promise<void>((resolve) => {
          release = resolve;
        });
      });
      await sendMessage(wrapper, '随便问问');

      const live = wrapper.find('[data-testid="guide-live-status"]');
      expect(live.exists()).toBe(true);
      expect(live.attributes('aria-live')).toBe('polite');
      expect(live.attributes('role')).toBe('status');
      expect(live.text()).toContain('正在回复');

      orch.result.value = okResult({ reply_md: '答完了' });
      orch.status.value = 'done';
      release?.();
      await flushPromises();
      expect(wrapper.find('[data-testid="guide-live-status"]').text()).toBe('');
    });

    it('失败时错误文案放在 role=alert 区域', async () => {
      const wrapper = await mountOverlay();
      armFailure('上游超时');
      await sendMessage(wrapper, '会失败的一问');

      const alert = wrapper.find('[data-testid="guide-error"]');
      expect(alert.exists()).toBe(true);
      expect(alert.attributes('role')).toBe('alert');
      expect(alert.text()).toContain('上游超时');
    });
  });

  describe('8. 建议问题随对话状态变化', () => {
    it('起步建议 → 计划后续建议：两组各有标志项且不相同', async () => {
      const wrapper = await mountOverlay();
      const starter = chipTexts(wrapper);
      expect(starter).toContain('帮我规划学习路径');

      armSuccess({ reply_md: '计划好了', plan: [planStep()] });
      await sendMessage(wrapper, '帮我规划');

      const afterPlan = chipTexts(wrapper);
      expect(afterPlan).toContain('继续下一步');
      expect(afterPlan).not.toEqual(starter);
    });

    it('澄清追问态给出回答型建议', async () => {
      const wrapper = await mountOverlay();
      armSuccess({
        reply_md: '',
        clarifying_question: '想从第几章开始？',
        needs_user_input: true,
      });
      await sendMessage(wrapper, '我想学 Python');

      expect(chipTexts(wrapper)).toContain('从当前章开始');
    });
  });

  describe('9. 流式 Markdown / trace 展示与共享活动状态（回归）', () => {
    it('闭环计划已开始但最终文本未到达时，气泡展示可读的进行中说明', async () => {
      const wrapper = await mountOverlay();
      const orch = sse();
      let release: (() => void) | null = null;
      orch.start.mockImplementation(() => {
        resetStreamRefs(orch);
        orch.status.value = 'streaming';
        orch.events.value = [
          {
            type: 'CUSTOM',
            name: 'loop_plan',
            value: {
              maxSteps: 4,
              steps: [
                {
                  id: 's1',
                  phase: 'plan',
                  label: '拆解学习目标',
                  status: 'active',
                  reasonSummary: '先把看、学、练、测拆成可执行动作',
                },
              ],
            },
          },
        ];
        return new Promise<void>((resolve) => {
          release = resolve;
        });
      });

      await sendMessage(wrapper, '帮我从第 1 章开始安排');

      await nextTick();
      const assistantBubbles = wrapper.findAll('.guide-msg--assistant .guide-bubble');
      const assistant = assistantBubbles[assistantBubbles.length - 1];
      expect(assistant).toBeTruthy();
      expect(assistant.text()).toContain('学习闭环');
      expect(assistant.text()).toContain('看、学、练、测');

      orch.result.value = okResult({ reply_md: '计划已经准备好' });
      orch.status.value = 'done';
      release?.();
      await flushPromises();
    });

    it('流式中渲染部分 Markdown 与「智能体协同中」trace，并镜像进 workshopActivity', async () => {
      const wrapper = await mountOverlay();
      const orch = sse();
      let release: (() => void) | null = null;
      orch.start.mockImplementation(() => {
        resetStreamRefs(orch);
        orch.status.value = 'streaming';
        orch.tokens.value = '部分 **Markdown** 内容';
        orch.traces.value = [{ node: 'profile_extractor', status: 'running', elapsed_ms: 8 }];
        return new Promise<void>((resolve) => {
          release = resolve;
        });
      });
      await sendMessage(wrapper, '讲讲列表');

      expect(wrapper.html()).toContain('部分 **Markdown** 内容');
      expect(wrapper.find('.guide-trace').exists()).toBe(true);
      expect(wrapper.find('.guide-trace').text()).toContain('profile_extractor');

      const activity = useWorkshopActivityStore();
      expect(activity.kind).toBe('orchestrator');
      expect(activity.isBusy).toBe(true);
      expect(activity.traces).toHaveLength(1);

      orch.result.value = okResult({ reply_md: '完整回答' });
      orch.status.value = 'done';
      release?.();
      await flushPromises();
      expect(activity.status).toBe('done');
    });
  });
});
