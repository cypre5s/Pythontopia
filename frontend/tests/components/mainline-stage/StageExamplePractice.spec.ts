import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';

vi.mock('@/api/mainline', () => ({
  getMainlineOverview: vi.fn(),
  postVideoProgress: vi.fn(),
  postPageRead: vi.fn(),
  postExampleDone: vi.fn(),
}));

import StageExamplePractice from '@/components/mainline-stage/StageExamplePractice.vue';
import { postExampleDone } from '@/api/mainline';
import type { StageProgressDto } from '@/api/mainline';

// 与 A3 真实 StageProgressDto 对齐（组件只 await 成功、不读返回体）。
function makeStageProgress(over: Partial<StageProgressDto> = {}): StageProgressDto {
  return {
    stageId: 7,
    chapter: 2,
    index: 1,
    title: '变量与输入',
    videoWatchedSec: 0,
    requiredVideoSec: 240,
    pagesRead: 0,
    requiredPages: 0,
    examplesDone: 0,
    requiredExamples: 0,
    completed: false,
    ...over,
  };
}

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/training', name: 'training', component: { template: '<div />' } },
      { path: '/workshop', name: 'workshop', component: { template: '<div />' } },
    ],
  });
}

describe('StageExamplePractice（练 · 例题完成打点）', () => {
  let router: Router;

  beforeEach(async () => {
    router = createTestRouter();
    await router.push('/');
    await router.isReady();
    vi.mocked(postExampleDone).mockResolvedValue(makeStageProgress({ examplesDone: 1, examplesDoneIds: [201] }));
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('「标记完成」调用 postExampleDone(stageId, problemId)（缺省不传 submissionId）', async () => {
    const wrapper = mount(StageExamplePractice, {
      props: { stageId: 7, problemIds: [201, 202], requiredExamples: 0, doneProblemIds: [] },
      global: { plugins: [router] },
    });

    await wrapper.find('[data-testid="ex-mark-201"]').trigger('click');
    await flushPromises();

    expect(vi.mocked(postExampleDone)).toHaveBeenCalledTimes(1);
    // 缺省时仅两参（与服务端「无提交证据」语义一致）。
    expect(vi.mocked(postExampleDone)).toHaveBeenCalledWith(7, 201);
    expect(vi.mocked(postExampleDone).mock.calls[0]).toHaveLength(2);

    const progressEvents = wrapper.emitted('progress');
    expect(progressEvents).toBeTruthy();
    expect(progressEvents?.[0]?.[0]).toMatchObject({ stageId: 7, doneProblemIds: [201] });
    expect(wrapper.find('[data-testid="ex-mark-201"]').attributes('disabled')).toBeDefined();
  });

  it('「去训练场」外链指向 /training?problemId=（真实判题）', () => {
    const wrapper = mount(StageExamplePractice, {
      props: { stageId: 7, problemIds: [201, 202], requiredExamples: 0, doneProblemIds: [] },
      global: { plugins: [router] },
    });
    const train = wrapper.find('[data-testid="ex-train-202"]');
    expect(train.exists()).toBe(true);
    const href = train.attributes('href') ?? '';
    expect(href).toContain('/training');
    expect(href).toContain('problemId=202');
  });

  it('AI 导学陪练入口指向已合并的百晓工坊 /workshop', () => {
    const wrapper = mount(StageExamplePractice, {
      props: { stageId: 7, problemIds: [201], requiredExamples: 1, doneProblemIds: [] },
      global: { plugins: [router] },
    });
    const tutor = wrapper.find('[data-testid="stage-practice-tutor"]');
    expect(tutor.exists()).toBe(true);
    expect(tutor.attributes('href') ?? '').toContain('/workshop');
  });

  it('完成 required_examples 后「练」过线', async () => {
    const wrapper = mount(StageExamplePractice, {
      props: { stageId: 7, problemIds: [201, 202], requiredExamples: 1, doneProblemIds: [] },
      global: { plugins: [router] },
    });

    await wrapper.find('[data-testid="ex-mark-201"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="stage-practice-passed"]').exists()).toBe(true);
  });
});
