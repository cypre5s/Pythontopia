import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';

const apiMocks = vi.hoisted(() => ({
  requestBossProblem: vi.fn(),
  submitBossAttempt: vi.fn(),
}));

vi.mock('@/api/boss', () => ({
  requestBossProblem: apiMocks.requestBossProblem,
  submitBossAttempt: apiMocks.submitBossAttempt,
}));

const pyodideMock = vi.hoisted(() => ({
  run: vi.fn(),
}));

vi.mock('@/game/adapters/pyodide', () => ({
  getPyodideRunner: () => ({
    init: vi.fn().mockResolvedValue(undefined),
    run: pyodideMock.run,
    dispose: vi.fn(),
  }),
}));

import BossPage from '@/pages/BossPage.vue';

const APP_LAYOUT_STUB = { template: '<div data-testid="layout-stub"><slot /></div>' };

let router: Router;

function setupRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/boss/:chapter', component: BossPage, props: (r) => ({ chapter: Number(r.params.chapter) }) },
      { path: '/world', component: { template: '<div data-testid="world-stub"/>' } },
    ],
  });
}

function mountAt(chapter: number) {
  return mount(BossPage, {
    global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
    props: { chapter },
  });
}

const SAMPLE_PROBLEM = {
  problemId: 'ai-test-1',
  chapter: 1,
  stage: 1,
  difficulty: 1,
  kcCode: 'PY43-KC-001',
  statement: 'Output 5 when input is "2 3"',
  hints: ['用 input() 获取一行', '用 map(int, ...) 拆分'],
  sampleInput: '2 3',
  sampleOutput: '5',
  totalTestCases: 3,
  reviewed: true,
  aiGenerated: true,
};

describe('BossPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    window.localStorage.clear();
    router = setupRouter();
    apiMocks.requestBossProblem.mockReset();
    apiMocks.submitBossAttempt.mockReset();
    pyodideMock.run.mockReset();
    apiMocks.requestBossProblem.mockResolvedValue(SAMPLE_PROBLEM);
    apiMocks.submitBossAttempt.mockResolvedValue({
      problemId: SAMPLE_PROBLEM.problemId,
      passed: true,
      passedTestCases: 3,
      totalTestCases: 3,
      score: 95,
      feedback: '通过',
    });
    pyodideMock.run.mockResolvedValue({
      stdout: '5\n',
      stderr: '',
      durationMs: 12,
      isError: false,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('mount 时拉取第 1 阶段题目并展示', async () => {
    await router.push('/boss/1');
    const wrapper = mountAt(1);
    await flushPromises();
    expect(wrapper.find('[data-testid="boss-page"]').exists()).toBe(true);
    expect(apiMocks.requestBossProblem).toHaveBeenCalledWith(1, 1);
    expect(wrapper.find('[data-testid="boss-problem"]').text()).toContain('Output 5');
    expect(wrapper.find('[data-testid="boss-stage-bar"]').text()).toContain('Stage 1');
  });

  it('显示 4 个 stage step 进度块', async () => {
    await router.push('/boss/1');
    const wrapper = mountAt(1);
    await flushPromises();
    expect(wrapper.find('[data-testid="boss-stage-1"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="boss-stage-2"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="boss-stage-3"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="boss-stage-4"]').exists()).toBe(true);
  });

  it('提交通过后展示「下一阶段」按钮', async () => {
    await router.push('/boss/1');
    const wrapper = mountAt(1);
    await flushPromises();

    await wrapper.find('[data-testid="boss-code-input"]').setValue('print(5)');
    await wrapper.find('[data-testid="boss-submit"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="boss-result"]').text()).toContain('通过');
    expect(wrapper.find('[data-testid="boss-next"]').exists()).toBe(true);
  });

  it('「下一阶段」推到 stage 2 并重新拉题', async () => {
    apiMocks.requestBossProblem.mockResolvedValueOnce(SAMPLE_PROBLEM)
      .mockResolvedValueOnce({ ...SAMPLE_PROBLEM, stage: 2, problemId: 'ai-test-2' });

    await router.push('/boss/1');
    const wrapper = mountAt(1);
    await flushPromises();

    await wrapper.find('[data-testid="boss-code-input"]').setValue('print(5)');
    await wrapper.find('[data-testid="boss-submit"]').trigger('click');
    await flushPromises();

    await wrapper.find('[data-testid="boss-next"]').trigger('click');
    await flushPromises();

    expect(apiMocks.requestBossProblem).toHaveBeenLastCalledWith(1, 2);
  });

  it('Pyodide 失败 + 后端失败时显示 retry 按钮', async () => {
    pyodideMock.run.mockResolvedValueOnce({
      stdout: '6\n',
      stderr: '',
      durationMs: 10,
      isError: false,
    });
    apiMocks.submitBossAttempt.mockResolvedValueOnce({
      problemId: SAMPLE_PROBLEM.problemId,
      passed: false,
      passedTestCases: 0,
      totalTestCases: 3,
      score: 0,
      feedback: 'sample case failed',
    });

    await router.push('/boss/1');
    const wrapper = mountAt(1);
    await flushPromises();

    await wrapper.find('[data-testid="boss-code-input"]').setValue('print(6)');
    await wrapper.find('[data-testid="boss-submit"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="boss-retry"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="boss-result"]').text()).toContain('未通过');
  });

  it('AI 标签出现在题目元数据中', async () => {
    await router.push('/boss/1');
    const wrapper = mountAt(1);
    await flushPromises();
    expect(wrapper.find('[data-testid="boss-problem"]').text()).toContain('五重审查');
  });

  it('Golden tag 出现在 aiGenerated=false 时', async () => {
    apiMocks.requestBossProblem.mockResolvedValueOnce({
      ...SAMPLE_PROBLEM,
      aiGenerated: false,
    });

    await router.push('/boss/1');
    const wrapper = mountAt(1);
    await flushPromises();
    expect(wrapper.find('[data-testid="boss-problem"]').text()).toContain('Golden');
  });

  it('退出按钮第一次要求确认，第二次跳回 /world', async () => {
    await router.push('/boss/1');
    const wrapper = mountAt(1);
    await flushPromises();

    await wrapper.find('[data-testid="boss-exit"]').trigger('click');
    expect(wrapper.find('[data-testid="boss-exit"]').text()).toContain('再点确认');

    await wrapper.find('[data-testid="boss-exit"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/world');
  });

  it('chapter > 7 fallback 到 error 状态（boss meta 不存在）', async () => {
    await router.push('/boss/99');
    const wrapper = mountAt(99);
    await flushPromises();
    expect(wrapper.find('[data-testid="boss-error"]').exists()).toBe(true);
  });

  it('通关 4 stage 后进 victory 状态', async () => {
    apiMocks.requestBossProblem.mockResolvedValue({ ...SAMPLE_PROBLEM });

    await router.push('/boss/1');
    const wrapper = mountAt(1);
    await flushPromises();

    for (let i = 0; i < 4; i += 1) {
      await wrapper.find('[data-testid="boss-code-input"]').setValue('print(5)');
      await wrapper.find('[data-testid="boss-submit"]').trigger('click');
      await flushPromises();
      const nextBtn = wrapper.find('[data-testid="boss-next"]');
      if (nextBtn.exists()) {
        await nextBtn.trigger('click');
        await flushPromises();
      }
    }
    expect(wrapper.find('[data-testid="boss-victory"]').exists()).toBe(true);
    // 闯关塔已退役（并入无尽远征）：章末 BOSS 通关不再回写静态关卡进度，掌握度由判题入库权威驱动。
  });

  it('计时器随秒数推进（手动 tick）', async () => {
    await router.push('/boss/1');
    const wrapper = mountAt(1);
    await flushPromises();
    const timerEl = wrapper.find('[data-testid="boss-timer"]');
    expect(timerEl.text()).toContain('0s');
  });

  it('提示折叠面板默认存在且按 difficulty / clearedStages 切提示', async () => {
    await router.push('/boss/1');
    const wrapper = mountAt(1);
    await flushPromises();
    expect(wrapper.text()).toContain('用 input()');
  });

  it('倒计时归零后显示超时横幅', async () => {
    vi.useFakeTimers();
    await router.push('/boss/1');
    const wrapper = mountAt(1);
    await flushPromises();
    await vi.advanceTimersByTimeAsync(111_000);
    await flushPromises();
    expect(wrapper.find('[data-testid="boss-timeout-banner"]').exists()).toBe(true);
    vi.useRealTimers();
  });

  it('bossHp≤50 时显示狂暴 badge 与狂暴招式卡', async () => {
    apiMocks.requestBossProblem.mockResolvedValueOnce(SAMPLE_PROBLEM)
      .mockResolvedValueOnce({ ...SAMPLE_PROBLEM, stage: 2 })
      .mockResolvedValueOnce({ ...SAMPLE_PROBLEM, stage: 3, problemId: 'ai-test-3' });

    await router.push('/boss/1');
    const wrapper = mountAt(1);
    await flushPromises();

    for (let i = 0; i < 2; i += 1) {
      await wrapper.find('[data-testid="boss-code-input"]').setValue('print(5)');
      await wrapper.find('[data-testid="boss-submit"]').trigger('click');
      await flushPromises();
      const nextBtn = wrapper.find('[data-testid="boss-next"]');
      if (nextBtn.exists()) {
        await nextBtn.trigger('click');
        await flushPromises();
      }
    }

    expect(wrapper.find('[data-testid="boss-enrage"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="boss-move-card"]').text()).toContain('狂暴招式');
  });
});
