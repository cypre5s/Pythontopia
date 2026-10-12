/**
 * WorkshopChat × 上传注入（契约 §3.5）集成测试。
 *
 * 覆盖「payload.attachments 注入」：ChatComposer 提交带附件 → WorkshopChat 对附件 GET extractedText →
 * 以 `【你上传的《…》】` 标签拼进**现有** context → 走现有 /api/tutor/ask（不改后端端点）。
 *
 * 红线校验：上传内容只进带标签 context；无附件且无会话上传时不发上传相关请求、context 不含上传标签。
 *
 * 后端端点此刻可能未就绪：用 mock http（axios 实例）驱动 getUpload；tutor 走假 SSE 捕获请求体。
 */
/* eslint-disable vue/one-component-per-file -- 测试桩组件集中定义 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';

// 假 SSE 实例注册表（按 URL）。
const sseRegistry = vi.hoisted(() => new Map<string, unknown>());

// http（axios）被 mock：上传访问层（getUpload / retrieveUploads）真实代码经此取数。
const httpMock = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), delete: vi.fn() }));
vi.mock('@/api/http', () => ({ http: httpMock }));

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
        if (url.includes('/api/tutor/ask')) inst.result.value = { answer_md: '答案。' };
        else inst.result.value = { runId: 1, resourceId: 101, status: 'SUCCESS' };
      });
      sseRegistry.set(url, inst);
      return inst;
    },
  };
});

vi.mock('@/api/course', () => ({
  listChapters: vi.fn().mockResolvedValue([]),
  listKnowledgeComponents: vi.fn().mockResolvedValue([]),
  getCoursePack: vi.fn(),
  listKcPages: vi.fn(),
  getProblem: vi.fn(),
}));
vi.mock('@/api/workshop', () => ({
  createWorkshopResource: vi.fn(),
  listWorkshopResources: vi.fn().mockResolvedValue([]),
  getWorkshopResource: vi.fn(),
  submitWorkshopFeedback: vi.fn(),
}));
vi.mock('@/api/intent', async () => {
  const actual = await vi.importActual<typeof import('@/api/intent')>('@/api/intent');
  return { ...actual, routeIntent: vi.fn() };
});
vi.mock('@/api/tutor', async () => {
  const actual = await vi.importActual<typeof import('@/api/tutor')>('@/api/tutor');
  return { ...actual, styleEngaged: vi.fn() };
});
vi.mock('@/api/learningEvent', () => ({
  postLearningEvent: vi.fn().mockResolvedValue(undefined),
  postLearningEventBatch: vi.fn(),
}));
vi.mock('@/composables/useAudio', async () => {
  const actual = await vi.importActual<typeof import('@/composables/useAudio')>('@/composables/useAudio');
  return { ...actual, playSfx: vi.fn() };
});

// ChatComposer 替身：只把 submit payload 透传给 WorkshopChat。
vi.mock('@/components/workshop/ChatComposer.vue', async () => {
  const { defineComponent } = await import('vue');
  return {
    default: defineComponent({
      name: 'ChatComposer',
      props: {
        mentionItems: { type: Array, default: () => [] },
        busy: { type: Boolean, default: false },
      },
      emits: ['submit'],
      template: '<div data-testid="stub-composer" />',
    }),
  };
});

import WorkshopChat from '@/components/workshop/WorkshopChat.vue';
import { routeIntent } from '@/api/intent';

interface FakeSse {
  start: ReturnType<typeof vi.fn>;
  status: { value: string };
  result: { value: unknown };
  traces: { value: Array<Record<string, unknown>> };
}

function sse(url: string): FakeSse {
  const inst = sseRegistry.get(url);
  if (!inst) throw new Error(`没有 ${url} 的假 SSE（组件未挂载？）`);
  return inst as FakeSse;
}

const TUTOR_URL = '/api/tutor/ask';

interface AskBody {
  question: string;
  kcCodes: string[];
  context: string;
}

interface SubmitPayload {
  text: string;
  manualKcCodes: string[];
  contextHint: string;
  forcedIntent: string | null;
  attachments: Array<{ id: number; filename: string; ext: string; charCount: number }>;
}

let router: Router;

function mountChat(): VueWrapper {
  return mount(WorkshopChat, {
    global: {
      plugins: [router],
      stubs: {
        ChatComposer: false,
        MarkdownView: true,
        BilibiliVideoCards: true,
        CoursewareEvidence: true,
        TutorVisualAids: true,
        ToolTraceLine: true,
        ToolCallTimeline: true,
        WorkshopAvatar: true,
        WelcomePanel: true,
        ClarifyBubble: true,
        QuizModeBar: true,
        ProgressCard: true,
        PlanCard: true,
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

async function submit(w: VueWrapper, payload: Partial<SubmitPayload>): Promise<void> {
  w.findComponent({ name: 'ChatComposer' }).vm.$emit('submit', {
    text: '讲讲这段代码',
    manualKcCodes: [],
    contextHint: '',
    forcedIntent: 'ASK',
    attachments: [],
    ...payload,
  });
  await flushPromises();
}

enableAutoUnmount(afterEach);

describe('WorkshopChat × 上传注入（A/B 通路 → 现有 context → /api/tutor/ask）', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/workshop', component: { template: '<div />' } },
      ],
    });
    Object.defineProperty(Element.prototype, 'scrollTo', {
      value: () => undefined,
      writable: true,
      configurable: true,
    });
    httpMock.get.mockReset();
    httpMock.post.mockReset();
    httpMock.delete.mockReset();
    vi.mocked(routeIntent).mockReset();
    vi.mocked(routeIntent).mockResolvedValue({
      intent: 'ASK',
      kcCodes: [],
      focusHint: '',
      confidence: 1,
      reason: '显式问答工具',
      target: '',
      secondaryIntent: null,
      namespace: 'ask',
      candidateIntents: ['ASK'],
      needsClarification: false,
      clarificationPrompt: '',
      riskClass: 'read_only',
      renderMode: 'answer',
      reasonCode: 'explicit_command',
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('带附件提交：GET extractedText → 以《…》标签注入现有 context，并随发用户气泡上方', async () => {
    httpMock.get.mockResolvedValue({
      data: {
        id: 1,
        filename: 'main.py',
        ext: '.py',
        byteSize: 30,
        charCount: 17,
        lineCount: 2,
        status: 'READY',
        extractedText: 'PRINT HELLO WORLD',
      },
    });

    const w = mountChat();
    await flushPromises();

    await submit(w, {
      text: '讲讲这段代码',
      attachments: [{ id: 1, filename: 'main.py', ext: '.py', charCount: 17 }],
    });

    // A 通路：取了全文。
    expect(httpMock.get).toHaveBeenCalledWith('/api/workshop/uploads/1');
    // 注入进现有 /api/tutor/ask 的 context（带《…》标签 + 全文）。
    const tutor = sse(TUTOR_URL);
    expect(tutor.start).toHaveBeenCalledTimes(1);
    const body = tutor.start.mock.calls[0]?.[0] as AskBody;
    expect(body.question).toBe('讲讲这段代码');
    expect(body.context).toContain('【你上传的《main.py》】');
    expect(body.context).toContain('PRINT HELLO WORLD');

    // 用户气泡上方渲染附件 pill。
    const att = w.find('[data-testid="user-attachments"]');
    expect(att.exists()).toBe(true);
    expect(att.text()).toContain('main.py');
    expect(att.text()).toContain('17 字');
  });

  it('B 通路（scoped）：本会话早先上传 → 后续提问 retrieve hits 注入 context', async () => {
    // 第一轮：带附件 id=1（记入会话上传）。
    httpMock.get.mockResolvedValue({
      data: {
        id: 1,
        filename: 'main.py',
        ext: '.py',
        byteSize: 30,
        charCount: 17,
        lineCount: 2,
        status: 'READY',
        extractedText: 'FIRST FULL TEXT',
      },
    });
    httpMock.post.mockResolvedValue({
      data: { hits: [{ uploadId: 1, filename: 'main.py', chunkIndex: 2, text: 'RELEVANT CHUNK', score: 0.9 }] },
    });

    const w = mountChat();
    await flushPromises();

    await submit(w, {
      text: '这段在做什么',
      attachments: [{ id: 1, filename: 'main.py', ext: '.py', charCount: 17 }],
    });
    // 第一轮本轮附件即全部会话上传 → 不检索。
    expect(httpMock.post).not.toHaveBeenCalled();

    // 第二轮：无附件，但会话已有上传 1 → 走 scoped 检索。
    await submit(w, { text: '它的输出顺序呢', attachments: [] });

    expect(httpMock.post).toHaveBeenCalledWith('/api/workshop/uploads/retrieve', {
      query: '它的输出顺序呢',
      uploadIds: [1],
      topK: 4,
    });
    const tutor = sse(TUTOR_URL);
    const secondBody = tutor.start.mock.calls[1]?.[0] as AskBody;
    expect(secondBody.context).toContain('【你上传的《main.py》相关片段】');
    expect(secondBody.context).toContain('RELEVANT CHUNK');
  });

  it('无附件且无会话上传：不发上传请求，context 不含上传标签', async () => {
    const w = mountChat();
    await flushPromises();

    await submit(w, { text: '什么是变量', attachments: [] });

    expect(httpMock.get).not.toHaveBeenCalled();
    expect(httpMock.post).not.toHaveBeenCalled();
    const tutor = sse(TUTOR_URL);
    const body = tutor.start.mock.calls[0]?.[0] as AskBody;
    expect(body.context).not.toContain('【你上传的');
    expect(w.find('[data-testid="user-attachments"]').exists()).toBe(false);
  });
});
