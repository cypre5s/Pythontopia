import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';

vi.mock('@/api/courseLibrary', () => ({
  listAllExamples: vi.fn(),
  listKcFallbacks: vi.fn(),
}));
vi.mock('@/api/course', () => ({
  listKnowledgeComponents: vi.fn(),
}));

import ExampleListPage from '@/pages/ExampleListPage.vue';
import { listAllExamples, listKcFallbacks } from '@/api/courseLibrary';
import { listKnowledgeComponents } from '@/api/course';
import type { CourseExampleSummary } from '@/api/dto/Library';

const APP_LAYOUT_STUB = { template: '<div data-testid="layout-stub"><slot /></div>' };

const EXAMPLES: CourseExampleSummary[] = [
  {
    exampleId: 2251,
    packId: 43,
    documentId: 187,
    kcCode: 'PY43-KC-016',
    title: '圆面积示例',
    code: 'r = float(input())\nprint(3.1415 * r * r)',
    explanationMarkdown: '圆面积计算公式实现',
    inputDescription: 'r',
    outputDescription: 'area',
    pageRefs: [10771],
  },
  {
    exampleId: 2252,
    packId: 43,
    documentId: 188,
    kcCode: 'PY43-KC-025',
    title: '个十百千',
    code: 'n = int(input())',
    explanationMarkdown: '位运算示例',
    inputDescription: 'n',
    outputDescription: 'parts',
    pageRefs: [10836],
  },
];

const KCS = [
  {
    kcId: 1847,
    kcCode: 'PY43-KC-016',
    kcName: '基础数值运算',
    chapterId: 180,
    chapterOrder: 2,
    difficulty: 'basic',
    summaryMarkdown: '',
  },
  {
    kcId: 1854,
    kcCode: 'PY43-KC-025',
    kcName: '整数位提取',
    chapterId: 181,
    chapterOrder: 3,
    difficulty: 'basic',
    summaryMarkdown: '',
  },
];

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/examples', name: 'examples', component: { template: '<div />' } },
      { path: '/library', name: 'library', component: { template: '<div />' } },
      { path: '/world', name: 'world', component: { template: '<div />' } },
    ],
  });
}

describe('ExampleListPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(listAllExamples).mockResolvedValue(EXAMPLES);
    vi.mocked(listKnowledgeComponents).mockResolvedValue(KCS);
    vi.mocked(listKcFallbacks).mockResolvedValue(
      KCS.map((kc) => ({
        kcId: kc.kcId,
        packId: 43,
        kcCode: kc.kcCode,
        kcName: kc.kcName,
        chapterId: kc.chapterId,
        chapterOrder: kc.chapterOrder,
        difficulty: kc.difficulty,
        summary: kc.summaryMarkdown,
        pageRefs: [],
      })),
    );
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('渲染示例卡片并按 KC 分桶', async () => {
    const router = createTestRouter();
    await router.push('/examples');
    await router.isReady();
    const wrapper = mount(ExampleListPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    const cards = wrapper.findAll('[data-testid="example-card"]');
    expect(cards.length).toBe(EXAMPLES.length);
    expect(wrapper.text()).toContain('圆面积示例');
    expect(wrapper.text()).toContain('PY43-KC-016');
    expect(wrapper.text()).toContain('个十百千');
  });

  it('点击 KC chip 过滤示例', async () => {
    const router = createTestRouter();
    await router.push('/examples');
    await router.isReady();
    const wrapper = mount(ExampleListPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    const chips = wrapper.findAll('.chip');
    const kc25 = chips.find((c) => c.text() === 'PY43-KC-025');
    expect(kc25).toBeDefined();
    await kc25!.trigger('click');
    await flushPromises();

    const cards = wrapper.findAll('[data-testid="example-card"]');
    expect(cards.length).toBe(1);
    expect(wrapper.text()).toContain('个十百千');
    expect(wrapper.text()).not.toContain('圆面积示例');
  });

  it('"复制代码" 调用 navigator.clipboard.writeText', async () => {
    const writeText = vi.fn(async () => undefined);
    Object.defineProperty(globalThis, 'navigator', {
      configurable: true,
      writable: true,
      value: { clipboard: { writeText } },
    });

    const router = createTestRouter();
    await router.push('/examples');
    await router.isReady();
    const wrapper = mount(ExampleListPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    const copyButtons = wrapper.findAll('[data-testid="copy-code"]');
    expect(copyButtons.length).toBe(EXAMPLES.length);
    await copyButtons[0]!.trigger('click');
    await flushPromises();
    expect(writeText).toHaveBeenCalledWith(EXAMPLES[0]!.code);
  });

  it('"看上下文" router.push 到 /library 并带上 docId / page / kcCode', async () => {
    const router = createTestRouter();
    const pushSpy = vi.spyOn(router, 'push');
    await router.push('/examples');
    await router.isReady();
    const wrapper = mount(ExampleListPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    pushSpy.mockClear();
    const contextButtons = wrapper.findAll('[data-testid="open-context"]');
    await contextButtons[0]!.trigger('click');
    expect(pushSpy).toHaveBeenCalledTimes(1);
    const payload = pushSpy.mock.calls[0]?.[0] as {
      path?: string;
      query?: Record<string, unknown>;
    };
    expect(payload.path).toBe('/library');
    expect(payload.query?.docId).toBe('187');
    expect(payload.query?.kcCode).toBe('PY43-KC-016');
    expect(payload.query?.page).toBe('10771');
  });
});
