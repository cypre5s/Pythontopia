import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';

vi.mock('@/api/mainline', () => ({
  getMainlineOverview: vi.fn(),
  postVideoProgress: vi.fn(),
  postPageRead: vi.fn(),
  postExampleDone: vi.fn(),
}));

import StageCoursewareReader from '@/components/mainline-stage/StageCoursewareReader.vue';
import { postPageRead } from '@/api/mainline';
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
      { path: '/library', name: 'library', component: { template: '<div />' } },
    ],
  });
}

describe('StageCoursewareReader（学 · 课件阅读打点）', () => {
  let router: Router;

  beforeEach(async () => {
    router = createTestRouter();
    await router.push('/');
    await router.isReady();
    vi.mocked(postPageRead).mockResolvedValue(makeStageProgress({ pagesRead: 1, pagesReadIds: [101] }));
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('「标记已读」调用 postPageRead(stageId, pageId) 并向上 emit 进度', async () => {
    const wrapper = mount(StageCoursewareReader, {
      props: { stageId: 7, pageIds: [101, 102], requiredPages: 0, readPageIds: [] },
      global: { plugins: [router] },
    });

    await wrapper.find('[data-testid="cw-mark-101"]').trigger('click');
    await flushPromises();

    expect(vi.mocked(postPageRead)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(postPageRead)).toHaveBeenCalledWith(7, 101);
    const progressEvents = wrapper.emitted('progress');
    expect(progressEvents).toBeTruthy();
    expect(progressEvents?.[0]?.[0]).toMatchObject({ stageId: 7, readPageIds: [101] });
    // 已读后按钮禁用、计数 1/2。
    expect(wrapper.find('[data-testid="cw-mark-101"]').attributes('disabled')).toBeDefined();
    expect(wrapper.find('[data-testid="stage-courseware-count"]').text()).toContain('1 / 2');
  });

  it('「阅读」外链指向 /library?pageId=（复用图书馆阅读器）', () => {
    const wrapper = mount(StageCoursewareReader, {
      props: { stageId: 7, pageIds: [101, 102], requiredPages: 0, readPageIds: [] },
      global: { plugins: [router] },
    });

    const open = wrapper.find('[data-testid="cw-open-102"]');
    expect(open.exists()).toBe(true);
    const href = open.attributes('href') ?? '';
    expect(href).toContain('/library');
    expect(href).toContain('pageId=102');
  });

  it('读满 required_pages 后「学」过线', async () => {
    vi.mocked(postPageRead).mockResolvedValue(makeStageProgress({ pagesRead: 1, pagesReadIds: [101] }));
    const wrapper = mount(StageCoursewareReader, {
      props: { stageId: 7, pageIds: [101, 102], requiredPages: 1, readPageIds: [] },
      global: { plugins: [router] },
    });

    await wrapper.find('[data-testid="cw-mark-101"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="stage-courseware-passed"]').exists()).toBe(true);
  });

  it('初始已读页渲染为已完成态', () => {
    const wrapper = mount(StageCoursewareReader, {
      props: { stageId: 7, pageIds: [101, 102], requiredPages: 0, readPageIds: [101] },
      global: { plugins: [router] },
    });
    expect(wrapper.find('[data-testid="cw-mark-101"]').attributes('disabled')).toBeDefined();
    expect(wrapper.find('[data-testid="cw-mark-102"]').attributes('disabled')).toBeUndefined();
    expect(wrapper.find('[data-testid="stage-courseware-count"]').text()).toContain('1 / 2');
  });
});
