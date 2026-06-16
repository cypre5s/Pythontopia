import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';

vi.mock('@/api/courseLibrary', () => ({
  listAllProblems: vi.fn(),
  loadPlayerProblemProgress: vi.fn(),
  listKcFallbacks: vi.fn(),
}));
vi.mock('@/api/course', () => ({
  listKnowledgeComponents: vi.fn(),
}));

import ProblemListPage from '@/pages/ProblemListPage.vue';
import {
  listAllProblems,
  listKcFallbacks,
  loadPlayerProblemProgress,
} from '@/api/courseLibrary';
import { listKnowledgeComponents } from '@/api/course';
import type {
  CourseProblemSummary,
  PlayerProblemProgress,
} from '@/api/dto/Library';

const APP_LAYOUT_STUB = { template: '<div data-testid="layout-stub"><slot /></div>' };

const PROBLEMS: CourseProblemSummary[] = [
  buildProblem(476, 'PY43-KC-016', '圆面积计算', 'Low'),
  buildProblem(477, 'PY43-KC-025', '个十百千获取', 'Low'),
  buildProblem(478, 'PY43-KC-022', '大写字母加密', 'Medium'),
  buildProblem(479, 'PY43-KC-032', '莱布尼茨级数', 'High'),
];

function buildProblem(
  id: number,
  kc: string,
  title: string,
  difficulty: CourseProblemSummary['difficulty'],
): CourseProblemSummary {
  return {
    problemId: id,
    packId: 43,
    kcCode: kc,
    title,
    difficulty,
    statementMarkdown: `${title} 题面描述（demo）。`,
    inputDescription: '输入',
    outputDescription: '输出',
    sourceLabel: '',
    sourcePages: [],
    teachingExplanation: '',
    commonMistakes: ['常见失误 1', '常见失误 2'],
    samples: [],
  };
}

const KCS = [
  { kcId: 1847, kcCode: 'PY43-KC-016', kcName: '基础数值运算', chapterId: 180, chapterOrder: 2, difficulty: 'basic', summaryMarkdown: '' },
  { kcId: 1854, kcCode: 'PY43-KC-025', kcName: '整数位提取', chapterId: 181, chapterOrder: 3, difficulty: 'basic', summaryMarkdown: '' },
  { kcId: 1855, kcCode: 'PY43-KC-022', kcName: '字符串与字符编码', chapterId: 181, chapterOrder: 3, difficulty: 'basic', summaryMarkdown: '' },
  { kcId: 1860, kcCode: 'PY43-KC-032', kcName: '循环结构', chapterId: 182, chapterOrder: 4, difficulty: 'basic', summaryMarkdown: '' },
];

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/problems', name: 'problems', component: { template: '<div />' } },
      { path: '/training', name: 'training', component: { template: '<div />' } },
      { path: '/world', name: 'world', component: { template: '<div />' } },
    ],
  });
}

describe('ProblemListPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(listAllProblems).mockResolvedValue(PROBLEMS);
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
    const progress = new Map<number, PlayerProblemProgress>([
      [
        476,
        { problemId: 476, status: 'PASSED', attempts: 2, lastSubmittedAt: '2026-05-21T10:00:00Z' },
      ],
      [
        477,
        { problemId: 477, status: 'FAILED', attempts: 3, lastSubmittedAt: '2026-05-22T10:00:00Z' },
      ],
      [
        478,
        { problemId: 478, status: 'NOT_TRIED', attempts: 0, lastSubmittedAt: null },
      ],
      [
        479,
        { problemId: 479, status: 'NOT_TRIED', attempts: 0, lastSubmittedAt: null },
      ],
    ]);
    vi.mocked(loadPlayerProblemProgress).mockResolvedValue(progress);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('渲染所有题目并显示玩家状态', async () => {
    const router = createTestRouter();
    await router.push('/problems');
    await router.isReady();
    const wrapper = mount(ProblemListPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    const rows = wrapper.findAll('.problem-row:not(.problem-row--head)');
    expect(rows.length).toBe(PROBLEMS.length);
    expect(wrapper.text()).toContain('圆面积计算');
    expect(wrapper.text()).toContain('已通过');
    expect(wrapper.text()).toContain('失败');
  });

  it('知识点下拉仅含本题库关联的 KC', async () => {
    const router = createTestRouter();
    await router.push('/problems');
    await router.isReady();
    const wrapper = mount(ProblemListPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    const kcSelect = wrapper.find('[data-testid="filter-kc"]');
    const options = kcSelect.findAll('option');
    // 1 个「全部」+ 4 个有题的 KC（016/025/022/032）
    expect(options.length).toBe(5);
    expect(kcSelect.text()).not.toContain('PY43-KC-001');
    expect(wrapper.text()).toContain('知识点（4）');
  });

  it('搜索栏按标题过滤题目', async () => {
    const router = createTestRouter();
    await router.push('/problems');
    await router.isReady();
    const wrapper = mount(ProblemListPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    await wrapper.find('[data-testid="filter-search"]').setValue('莱布尼茨');
    await flushPromises();

    const rowsAfter = wrapper.findAll('.problem-row:not(.problem-row--head)');
    expect(rowsAfter.length).toBe(1);
    expect(wrapper.text()).toContain('莱布尼茨级数');
  });

  it('难度下拉过滤题目', async () => {
    const router = createTestRouter();
    await router.push('/problems');
    await router.isReady();
    const wrapper = mount(ProblemListPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    const diffSelect = wrapper.find('[data-testid="filter-difficulty"]');
    await diffSelect.setValue('Medium');
    await flushPromises();

    const rowsAfter = wrapper.findAll('.problem-row:not(.problem-row--head)');
    expect(rowsAfter.length).toBe(1);
    expect(wrapper.text()).toContain('大写字母加密');
  });

  it('点击 "去做" 按钮 router.push 到 /training?problemId=', async () => {
    const router = createTestRouter();
    const pushSpy = vi.spyOn(router, 'push');
    await router.push('/problems');
    await router.isReady();
    const wrapper = mount(ProblemListPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    pushSpy.mockClear();
    const goButtons = wrapper.findAll('[data-testid="go-training"]');
    expect(goButtons.length).toBe(PROBLEMS.length);
    await goButtons[0]!.trigger('click');

    expect(pushSpy).toHaveBeenCalledTimes(1);
    const payload = pushSpy.mock.calls[0]?.[0] as {
      path?: string;
      query?: Record<string, unknown>;
    };
    expect(payload.path).toBe('/training');
    expect(payload.query?.problemId).toBe('476');
  });

  it('点击题目标题直达做题界面 /training（不再行内展开预览）', async () => {
    const router = createTestRouter();
    const pushSpy = vi.spyOn(router, 'push');
    await router.push('/problems');
    await router.isReady();
    const wrapper = mount(ProblemListPage, {
      global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    });
    await flushPromises();

    pushSpy.mockClear();
    const titleButtons = wrapper.findAll('.row-title');
    await titleButtons[0]!.trigger('click');

    expect(wrapper.find('.row-preview').exists()).toBe(false);
    const payload = pushSpy.mock.calls[0]?.[0] as {
      path?: string;
      query?: Record<string, unknown>;
    };
    expect(payload.path).toBe('/training');
    expect(payload.query?.problemId).toBe('476');
  });
});
