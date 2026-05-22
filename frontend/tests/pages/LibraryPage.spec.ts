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
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('挂载后渲染 7 章导航 + 课件列表，并调用 loadPage 取页', async () => {
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
    expect(wrapper.text()).toContain('第一章：计算工具与计算思维');
    expect(wrapper.text()).toContain('第七章：归纳与抽象');
    const chapterHeads = wrapper.findAll('.chapter-head');
    expect(chapterHeads.length).toBe(7);
  });

  it('点击文档切换 docId，触发新的 getDocumentPage 调用', async () => {
    const router = createTestRouter();
    await router.push('/library');
    await router.isReady();
    const wrapper = mount(LibraryPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    vi.mocked(getDocumentPage).mockClear();
    const docButtons = wrapper.findAll('.doc-item');
    expect(docButtons.length).toBe(2);
    await docButtons[1]!.trigger('click');
    await flushPromises();

    expect(vi.mocked(getDocumentPage)).toHaveBeenCalledWith(187, 1);
    expect(router.currentRoute.value.query.docId).toBe('187');
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
    await wrapper.find('.back-button').trigger('click');
    expect(pushSpy).toHaveBeenCalledWith({ path: '/world' });
  });
});
