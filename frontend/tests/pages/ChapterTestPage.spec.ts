// 章测页集成测：开考闸门 → 去答案考卷渲染 → 作答/防作弊计数 → 提交 {answers, integrity}
// → 成绩 + 逐题解析；倒计时到点自动交卷（overtime）；提交前绝不渲染答案。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';

const apiMocks = vi.hoisted(() => ({
  prepareTest: vi.fn(),
  startTest: vi.fn(),
  submitTest: vi.fn(),
  getLatestTest: vi.fn(),
}));

vi.mock('@/api/chapterTest', async (importActual) => {
  const actual = await importActual<typeof import('@/api/chapterTest')>();
  return {
    ...actual,
    prepareTest: apiMocks.prepareTest,
    startTest: apiMocks.startTest,
    submitTest: apiMocks.submitTest,
    getLatestTest: apiMocks.getLatestTest,
  };
});

vi.mock('@/game/adapters/pyodide', () => ({
  getPyodideRunner: () => ({
    init: vi.fn().mockResolvedValue(undefined),
    run: vi.fn().mockResolvedValue({ stdout: '', stderr: '', durationMs: 1, isError: false }),
    dispose: vi.fn(),
  }),
}));

import ChapterTestPage from '@/pages/ChapterTestPage.vue';
import { setExamActive } from '@/components/chaptertest/useExamRouteGuard';
import type {
  ChapterTestPaperDto,
  ChapterTestResultDto,
  ChapterTestSubmitRequest,
} from '@/api/chapterTest';

const APP_LAYOUT_STUB = { template: '<div data-testid="layout-stub"><slot /></div>' };
const ROOT = { template: '<router-view />' };

let router: Router;

function setupRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/chapter-test/:chapter',
        component: ChapterTestPage,
        props: (r) => ({ chapter: Number(r.params.chapter), assessment: 'test' }),
      },
      {
        path: '/chapter-quiz/:chapter',
        component: ChapterTestPage,
        props: (r) => ({ chapter: Number(r.params.chapter), assessment: 'quiz' }),
      },
      { path: '/mainline-map', component: { template: '<div data-testid="map-stub"/>' } },
      { path: '/workshop', component: { template: '<div data-testid="ws-stub"/>' } },
    ],
  });
}

function makePaper(msUntilDeadline: number): ChapterTestPaperDto {
  return {
    testId: 't-1',
    chapter: 1,
    attemptNo: 1,
    status: 'IN_PROGRESS',
    timeLimitSec: 1800,
    startedAt: new Date().toISOString(),
    deadlineAt: new Date(Date.now() + msUntilDeadline).toISOString(),
    totalScore: 100,
    items: [
      { qid: 'c1', type: 'CHOICE', score: 8, stem: '选择题干', options: ['甲', '乙', '丙', '丁'] },
      { qid: 'j1', type: 'JUDGE', score: 5, stem: '判断题干' },
      { qid: 'f1', type: 'FILL', score: 5, stem: '用 ____ 遍历序列' },
      { qid: 'p1', type: 'CODING', score: 30, title: '编程题', statementMarkdown: '写代码', samples: [{ input: '1', output: '1' }] },
    ],
  };
}

const RESULT: ChapterTestResultDto = {
  testId: 't-1',
  chapter: 1,
  attemptNo: 1,
  status: 'SUBMITTED',
  score: 88,
  maxScore: 100,
  passed: true,
  submittedAt: new Date().toISOString(),
  integrity: { paste_blocked: 0, blur_events: 0, overtime: false },
  items: [
    { qid: 'c1', type: 'CHOICE', score: 8, awarded: 8, correct: true, explain: '甲对', correctChoice: 'A' },
    { qid: 'j1', type: 'JUDGE', score: 5, awarded: 5, correct: true, explain: '对', correctJudgement: true },
    { qid: 'f1', type: 'FILL', score: 5, awarded: 5, correct: true, explain: 'range', acceptedAnswers: ['range'] },
    { qid: 'p1', type: 'CODING', score: 30, awarded: 30, correct: true, explain: 'AC', passedTestCases: 3, totalTestCases: 3 },
  ],
  chapterStars: 1,
};

function setHidden(hidden: boolean): void {
  Object.defineProperty(document, 'hidden', { configurable: true, value: hidden });
}

beforeEach(() => {
  setActivePinia(createPinia());
  router = setupRouter();
  setExamActive(false);
  setHidden(false);
  apiMocks.prepareTest.mockReset().mockResolvedValue({ status: 'READY' });
  apiMocks.getLatestTest.mockReset().mockResolvedValue(null);
  apiMocks.startTest.mockReset().mockResolvedValue(makePaper(1_800_000));
  apiMocks.submitTest.mockReset().mockResolvedValue(RESULT);
});

afterEach(() => {
  setExamActive(false);
  setHidden(false);
  vi.restoreAllMocks();
});

async function mountAt(chapter: number) {
  await router.push(`/chapter-test/${chapter}`);
  await router.isReady();
  const wrapper = mount(ROOT, {
    global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
  });
  await flushPromises();
  return wrapper;
}

async function mountQuizAt(chapter: number) {
  await router.push(`/chapter-quiz/${chapter}`);
  await router.isReady();
  const wrapper = mount(ROOT, {
    global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
  });
  await flushPromises();
  return wrapper;
}

describe('ChapterTestPage', () => {
  it('shows the pre-exam gate and triggers prepare + loadLatest', async () => {
    const wrapper = await mountAt(1);
    expect(wrapper.find('[data-testid="ct-gate"]').exists()).toBe(true);
    expect(apiMocks.prepareTest).toHaveBeenCalledWith(1, 'test');
    expect(apiMocks.getLatestTest).toHaveBeenCalledWith(1, 'test');
  });

  it('shows the 33-question chapter quiz contract and uses quiz APIs', async () => {
    const wrapper = await mountQuizAt(1);
    expect(wrapper.find('[data-testid="ct-gate"]').text()).toContain('章节小测');
    expect(wrapper.find('[data-testid="ct-gate"]').text()).toContain('10 道选择');
    expect(wrapper.find('[data-testid="ct-gate"]').text()).toContain('10 道填空');
    expect(wrapper.find('[data-testid="ct-gate"]').text()).toContain('10 道判断');
    expect(wrapper.find('[data-testid="ct-gate"]').text()).toContain('3 道编程');
    expect(wrapper.find('[data-testid="ct-gate"]').text()).toContain('60 分钟');
    expect(apiMocks.prepareTest).toHaveBeenCalledWith(1, 'quiz');
    expect(apiMocks.getLatestTest).toHaveBeenCalledWith(1, 'quiz');
  });

  it('shows the 99-question final contract and 120-minute duration', async () => {
    const wrapper = await mountAt(1);
    const gate = wrapper.find('[data-testid="ct-gate"]').text();
    expect(gate).toContain('章末大测试');
    expect(gate).toContain('30 道选择');
    expect(gate).toContain('30 道填空');
    expect(gate).toContain('30 道判断');
    expect(gate).toContain('9 道编程');
    expect(gate).toContain('120 分钟');
  });

  it('renders an error for an out-of-range chapter', async () => {
    const wrapper = await mountAt(9);
    expect(wrapper.find('[data-testid="ct-invalid"]').exists()).toBe(true);
    expect(apiMocks.prepareTest).not.toHaveBeenCalled();
  });

  it('starts the exam and renders the four answer-stripped question types', async () => {
    const wrapper = await mountAt(1);
    await wrapper.find('[data-testid="ct-start"]').trigger('click');
    await flushPromises();
    expect(apiMocks.startTest).toHaveBeenCalledWith(1, 'test');
    expect(wrapper.find('[data-testid="choice-question"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="judge-question"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="fill-question"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="coding-question"]').exists()).toBe(true);
  });

  it('never renders answers/explanations before submit', async () => {
    const wrapper = await mountAt(1);
    await wrapper.find('[data-testid="ct-start"]').trigger('click');
    await flushPromises();
    expect(wrapper.findAll('[data-testid^="result-item-"]').length).toBe(0);
    expect(wrapper.find('[data-testid="exam-result"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="choice-opt-c1-A"]').classes()).not.toContain('ct-opt--correct');
  });

  it('submits answers + integrity (paste/blur counted)', async () => {
    const wrapper = await mountAt(1);
    await wrapper.find('[data-testid="ct-start"]').trigger('click');
    await flushPromises();

    await wrapper.find('[data-testid="choice-opt-c1-B"]').trigger('click');
    await wrapper.find('[data-testid="judge-true-j1"]').trigger('click');
    await wrapper.find('[data-testid="fill-input-f1-0"]').setValue('range');
    await wrapper.find('[data-testid="coding-input-p1"]').setValue('print(1)');

    // 防作弊：两次粘贴 + 一次失焦。
    await wrapper.find('[data-testid="coding-input-p1"]').trigger('paste');
    await wrapper.find('[data-testid="coding-input-p1"]').trigger('paste');
    setHidden(true);
    document.dispatchEvent(new Event('visibilitychange'));
    await flushPromises();

    await wrapper.find('[data-testid="ct-submit"]').trigger('click');
    await flushPromises();

    expect(apiMocks.submitTest).toHaveBeenCalledTimes(1);
    const call = apiMocks.submitTest.mock.calls[0] as [
      number,
      string,
      ChapterTestSubmitRequest,
      string,
    ];
    expect(call[0]).toBe(1);
    expect(call[1]).toBe('t-1');
    const payload = call[2];
    expect(call[3]).toBe('test');
    expect(payload.integrity).toEqual({ paste_blocked: 2, blur_events: 1, overtime: false });
    const byQid = Object.fromEntries(payload.answers.map((a) => [a.qid, a]));
    expect(byQid.c1).toEqual({ qid: 'c1', type: 'CHOICE', choice: 'B' });
    expect(byQid.j1).toEqual({ qid: 'j1', type: 'JUDGE', judgement: true });
    expect(byQid.f1).toEqual({ qid: 'f1', type: 'FILL', blanks: ['range'] });
    expect(byQid.p1).toEqual({ qid: 'p1', type: 'CODING', code: 'print(1)' });
  });

  it('shows the result panel + per-item review after submit', async () => {
    const wrapper = await mountAt(1);
    await wrapper.find('[data-testid="ct-start"]').trigger('click');
    await flushPromises();
    await wrapper.find('[data-testid="ct-submit"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="exam-result"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="exam-result-score"]').text()).toBe('88');
    expect(wrapper.findAll('[data-testid^="result-item-"]').length).toBe(4);
    // 提交后才高亮正确选项（correctChoice=A）。
    expect(wrapper.find('[data-testid="choice-opt-c1-A"]').classes()).toContain('ct-opt--correct');
  });

  it('auto-submits with overtime flag when the countdown expires', async () => {
    vi.useFakeTimers();
    apiMocks.startTest.mockResolvedValue(makePaper(30_000));
    const wrapper = await mountAt(1);
    await wrapper.find('[data-testid="ct-start"]').trigger('click');
    await flushPromises();

    await vi.advanceTimersByTimeAsync(31_000);
    await flushPromises();

    expect(apiMocks.submitTest).toHaveBeenCalledTimes(1);
    const call = apiMocks.submitTest.mock.calls[0] as [
      number,
      string,
      ChapterTestSubmitRequest,
      string,
    ];
    expect(call[2].integrity.overtime).toBe(true);
    vi.useRealTimers();
  });

  it('marks the global exam-active flag while in progress', async () => {
    const wrapper = await mountAt(1);
    await wrapper.find('[data-testid="ct-start"]').trigger('click');
    await flushPromises();
    // 全局守卫单例应为 true（考试进行中）。
    const { isExamActive } = await import('@/components/chaptertest/useExamRouteGuard');
    expect(isExamActive()).toBe(true);
  });
});
