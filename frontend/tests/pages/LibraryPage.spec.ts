import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';

vi.mock('@/api/courseLibrary', () => ({
  listCourseDocuments: vi.fn(),
  listAllExamples: vi.fn(),
  listAllProblems: vi.fn(),
  listDocumentPages: vi.fn(),
  getDocumentPage: vi.fn(),
  searchCourseLibrary: vi.fn(),
  answerCourseware: vi.fn(),
  loadPlayerProblemProgress: vi.fn(),
  listChapterFallbacks: vi.fn(),
  listKcFallbacks: vi.fn(),
}));

vi.mock('@/api/course', () => ({
  listChapters: vi.fn(),
  listKnowledgeComponents: vi.fn(),
  getCoursePack: vi.fn(),
  listKcPages: vi.fn(),
  getProblem: vi.fn(),
}));

import LibraryPage from '@/pages/LibraryPage.vue';
import {
  getDocumentPage,
  listAllExamples,
  listAllProblems,
  listChapterFallbacks,
  listCourseDocuments,
  listDocumentPages,
  listKcFallbacks,
  searchCourseLibrary,
  answerCourseware,
} from '@/api/courseLibrary';
import { listChapters, listKnowledgeComponents } from '@/api/course';
import type {
  CourseDocumentSummary,
  CourseExampleSummary,
  CourseKcSummary,
  CoursePageDetail,
  CourseProblemSummary,
} from '@/api/dto/Library';

const APP_LAYOUT_STUB = { template: '<div data-testid="layout-stub"><slot /></div>' };

const DOCUMENTS: CourseDocumentSummary[] = [
  {
    documentId: 186,
    packId: 43,
    sortOrder: 1,
    filename: '第一章.pptx',
    title: '第一章：计算工具与计算思维',
    pageCount: 3,
  },
  {
    documentId: 187,
    packId: 43,
    sortOrder: 2,
    filename: '第二章.pptx',
    title: '第二章：Python 语言基础',
    pageCount: 2,
  },
];

const CHAPTERS = [
  { chapterId: 179, orderIndex: 1, title: '第一章：计算工具与计算思维', summaryMarkdown: null },
  { chapterId: 180, orderIndex: 2, title: '第二章：Python 语言基础', summaryMarkdown: null },
  { chapterId: 181, orderIndex: 3, title: '第三章：计算机信息表示及计算', summaryMarkdown: null },
  { chapterId: 182, orderIndex: 4, title: '第四章：流程自动化', summaryMarkdown: null },
  { chapterId: 183, orderIndex: 5, title: '第五章：数据结构', summaryMarkdown: null },
  { chapterId: 184, orderIndex: 6, title: '第六章：数据存取与分析', summaryMarkdown: null },
  { chapterId: 185, orderIndex: 7, title: '第七章：归纳与抽象', summaryMarkdown: null },
];

const KCS = [
  {
    kcId: 1809,
    kcCode: 'PY43-KC-001',
    kcName: '中国计算机发展成就',
    chapterId: 179,
    chapterOrder: 1,
    difficulty: 'basic',
    summaryMarkdown: '了解中国计算机事业的发展。',
  },
  {
    kcId: 1847,
    kcCode: 'PY43-KC-016',
    kcName: '基础数值运算',
    chapterId: 180,
    chapterOrder: 2,
    difficulty: 'basic',
    summaryMarkdown: 'Python 数值运算基础。',
  },
];

const PROBLEMS: CourseProblemSummary[] = [
  {
    problemId: 476,
    packId: 43,
    kcCode: 'PY43-KC-016',
    title: '圆面积计算',
    difficulty: 'Low',
    statementMarkdown: '计算圆面积',
    inputDescription: 'r',
    outputDescription: 'area',
    sourceLabel: '',
    sourcePages: [45],
    teachingExplanation: '',
    commonMistakes: [],
    samples: [{ input: '5', output: '78.5375' }],
  },
];

const EXAMPLES: CourseExampleSummary[] = [
  {
    exampleId: 2251,
    packId: 43,
    documentId: 187,
    kcCode: 'PY43-KC-016',
    title: '圆面积示例',
    code: 'print(3.1415 * r * r)',
    explanationMarkdown: '圆面积计算公式',
    inputDescription: 'r',
    outputDescription: 'area',
    pageRefs: [10771],
  },
];

const PAGE_DETAIL: CoursePageDetail = {
  pageId: 10707,
  documentId: 186,
  chapterId: 179,
  pageNo: 1,
  chunkIndex: 0,
  title: '计算思维与人工智能',
  contentMarkdown: '计算思维与人工智能（Python 语言）',
  excerpt: '计算思维与人工智能（Python 语言）',
};

const FLOW_DOC: CourseDocumentSummary = {
  documentId: 189,
  packId: 43,
  sortOrder: 4,
  filename: '第四章：流程自动化.pptx',
  title: '第四章：流程自动化',
  pageCount: 74,
};

const FLOW_COVER_PAGE: CoursePageDetail = {
  pageId: 10898,
  documentId: 189,
  chapterId: null,
  pageNo: 1,
  chunkIndex: 0,
  title: '计算思维与人工智能\u000b（Python 语言）',
  contentMarkdown: '计算思维与人工智能',
  excerpt: '计算思维与人工智能',
};

function buildKcs(): CourseKcSummary[] {
  return KCS.map((kc) => ({
    kcId: kc.kcId,
    packId: 43,
    kcCode: kc.kcCode,
    kcName: kc.kcName,
    chapterId: kc.chapterId,
    chapterOrder: kc.chapterOrder,
    difficulty: kc.difficulty,
    summary: kc.summaryMarkdown,
    pageRefs: kc.kcCode === 'PY43-KC-001' ? [10707] : [],
  }));
}

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/library', name: 'library', component: { template: '<div />' } },
      { path: '/world', name: 'world', component: { template: '<div />' } },
      { path: '/training', name: 'training', component: { template: '<div />' } },
      { path: '/examples', name: 'examples', component: { template: '<div />' } },
    ],
  });
}

describe('LibraryPage', () => {
  beforeEach(async () => {
    setActivePinia(createPinia());
    vi.mocked(listCourseDocuments).mockResolvedValue(DOCUMENTS);
    vi.mocked(listAllProblems).mockResolvedValue(PROBLEMS);
    vi.mocked(listAllExamples).mockResolvedValue(EXAMPLES);
    vi.mocked(listDocumentPages).mockResolvedValue([PAGE_DETAIL]);
    vi.mocked(getDocumentPage).mockResolvedValue(PAGE_DETAIL);
    vi.mocked(listChapters).mockResolvedValue(CHAPTERS);
    vi.mocked(listChapterFallbacks).mockResolvedValue(CHAPTERS);
    vi.mocked(listKcFallbacks).mockResolvedValue(buildKcs());
    vi.mocked(listKnowledgeComponents).mockResolvedValue(
      KCS.map((kc) => ({
        kcId: kc.kcId,
        kcCode: kc.kcCode,
        kcName: kc.kcName,
        chapterId: kc.chapterId,
        chapterOrder: kc.chapterOrder,
        difficulty: kc.difficulty,
        summaryMarkdown: kc.summaryMarkdown,
      })),
    );
    vi.mocked(searchCourseLibrary).mockResolvedValue([
      {
        pageId: 10707,
        documentId: 186,
        pageNo: 1,
        title: '计算思维与人工智能',
        snippet: '计算思维...片段',
        score: 5,
      },
    ]);
    vi.mocked(answerCourseware).mockRejectedValue(new Error('courseware answer unavailable'));
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('挂载后渲染合并章节目录（章名 + 页数 + KC），并调用 loadPage 取页', async () => {
    const router = createTestRouter();
    await router.push('/library');
    await router.isReady();
    const wrapper = mount(LibraryPage, {
      global: {
        plugins: [router],
        stubs: { AppLayout: APP_LAYOUT_STUB },
      },
    });
    await flushPromises();

    expect(vi.mocked(listCourseDocuments)).toHaveBeenCalled();
    expect(vi.mocked(listChapters)).toHaveBeenCalled();
    expect(vi.mocked(getDocumentPage)).toHaveBeenCalled();
    expect(wrapper.text()).toContain('计算工具与计算思维');
    expect(wrapper.text()).toContain('归纳与抽象');
    expect(wrapper.text()).not.toContain('7 份课件');
    expect(wrapper.text()).toContain('7 章 · 5 页 · 2 KC');
    const chapterHeads = wrapper.findAll('.chapter-head');
    expect(chapterHeads.length).toBe(7);
    expect(wrapper.find('[data-testid="chapter-doc-1"]').text()).toContain('3 页');
  });

  it('点击章节目录切换 docId，触发新的 getDocumentPage 调用但不触发路由导航', async () => {
    const router = createTestRouter();
    const replaceSpy = vi.spyOn(router, 'replace');
    await router.push('/library');
    await router.isReady();
    const wrapper = mount(LibraryPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    vi.mocked(getDocumentPage).mockClear();
    const ch2 = wrapper.find('[data-testid="chapter-doc-2"]');
    expect(ch2.exists()).toBe(true);
    await ch2.trigger('click');
    await flushPromises();

    expect(vi.mocked(getDocumentPage)).toHaveBeenCalledWith(187, 1);
    expect(replaceSpy).not.toHaveBeenCalled();
  });

  it('RAG 检索调用 searchCourseLibrary 并渲染命中片段', async () => {
    const router = createTestRouter();
    await router.push('/library');
    await router.isReady();
    const wrapper = mount(LibraryPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    const input = wrapper.find('[data-testid="library-rag-input"]');
    expect(input.exists()).toBe(true);
    await input.setValue('input 函数');
    await wrapper.find('.rag-form').trigger('submit.prevent');
    await flushPromises();

    expect(vi.mocked(searchCourseLibrary)).toHaveBeenCalledWith('input 函数', { topK: 6 });
    const results = wrapper.find('[data-testid="library-rag-results"]');
    expect(results.text()).toContain('计算思维...片段');
  });

  it('课件问答渲染 GraphRAG trace，展示共现补证据和噪声配额', async () => {
    const coursewareTraceAnswer = {
      answerMd: '字典词频统计需要先掌握 get/items/keys/values。[页 62]',
      citations: [
        {
          pageId: 4201,
          pageNo: 62,
          chapterId: 5,
          quote: '字典 get、items、keys、values 是完成词频统计前需要掌握的基本操作。',
        },
      ],
      noEvidence: false,
      route: 'graph',
      retrievalMode: 'graph',
      subgraph: [
        {
          fromKc: 'PY43-KC-041',
          toKc: 'PY43-KC-042',
          relation: 'co_occur',
        },
      ],
      graphTrace: {
        route: 'graph',
        retrievalMode: 'graph',
        topK: 6,
        hitCount: 3,
        directHitCount: 2,
        supplementalHitCount: 1,
        coOccurHitCount: 1,
        supplementalHitLimit: 2,
        supplementalHitRatio: 0.3333,
        relationCounts: { co_occur: 1 },
        noiseGuard: 'supplemental_hits_capped_at_top_k_div_3',
      },
    };
    vi.mocked(answerCourseware).mockResolvedValueOnce(coursewareTraceAnswer);
    const router = createTestRouter();
    await router.push('/library');
    await router.isReady();
    const wrapper = mount(LibraryPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    await wrapper
      .find('[data-testid="library-rag-input"]')
      .setValue('为什么字典词频统计要先理解 get items keys values');
    await wrapper.find('.rag-form').trigger('submit.prevent');
    await flushPromises();

    expect(vi.mocked(searchCourseLibrary)).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="library-rag-answer"]').text()).toContain('get/items/keys/values');
    const trace = wrapper.get('[data-testid="library-rag-trace"]');
    expect(trace.text()).toContain('route: graph');
    expect(trace.text()).toContain('co_occur');
    expect(trace.text()).toContain('补充证据 1 / 2');
    expect(trace.text()).toContain('共现补证据 1');
  });

  it('"返回小镇" 按钮 router.push 到 /world', async () => {
    const router = createTestRouter();
    const pushSpy = vi.spyOn(router, 'push');
    await router.push('/library');
    await router.isReady();
    const wrapper = mount(LibraryPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    pushSpy.mockClear();
    await wrapper.find('[data-testid="library-back"]').trigger('click');
    expect(pushSpy).toHaveBeenCalledWith({ path: '/world' });
  });

  it('阅读路径使用真实文档、章节、标题和页码，不渲染空占位破折号', async () => {
    vi.mocked(listCourseDocuments).mockResolvedValue([...DOCUMENTS, FLOW_DOC]);
    vi.mocked(listDocumentPages).mockResolvedValue([FLOW_COVER_PAGE]);
    vi.mocked(getDocumentPage).mockResolvedValue(FLOW_COVER_PAGE);

    const router = createTestRouter();
    await router.push('/library?docId=189&page=1');
    await router.isReady();
    const wrapper = mount(LibraryPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    const breadcrumb = wrapper.get('nav[aria-label="阅读路径"]').text();
    expect(breadcrumb).toContain('Pythontopia 图书馆');
    expect(breadcrumb).toContain('第 4 章');
    expect(breadcrumb).toContain('流程自动化');
    expect(breadcrumb).toContain('计算思维与人工智能（Python 语言）');
    expect(breadcrumb).toContain('第 1 / 74 页');
    expect(breadcrumb).not.toContain('—');
  });

  it('上一页、页码输入框、下一页使用同一分页控件尺寸类', async () => {
    const router = createTestRouter();
    await router.push('/library');
    await router.isReady();
    const wrapper = mount(LibraryPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    const prev = wrapper.get('[data-testid="library-prev-page"]');
    const pageJump = wrapper.find('[data-testid="library-page-jump"]');
    const next = wrapper.get('[data-testid="library-next-page"]');

    expect(pageJump.exists()).toBe(true);
    expect(prev.classes()).toContain('page-control');
    expect(pageJump.classes()).toContain('page-control');
    expect(next.classes()).toContain('page-control');
  });
});
