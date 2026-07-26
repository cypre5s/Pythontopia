import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';

vi.mock('@/api/mainline', () => ({
  getStageDetail: vi.fn(),
  postVideoProgress: vi.fn(),
  postPageRead: vi.fn(),
  postPagesRead: vi.fn(),
  postExampleDone: vi.fn(),
  answerPracticeQuestion: vi.fn(),
}));
vi.mock('@/api/chapterTest', () => ({
  prepareTest: vi.fn(),
}));

import StageLearnPage from '@/pages/StageLearnPage.vue';
import { getStageDetail, postExampleDone } from '@/api/mainline';
import { prepareTest } from '@/api/chapterTest';
import type { StageDetailDto, StageProgressDto } from '@/api/mainline';
import { ApiClientError } from '@/api/http';

const APP_LAYOUT_STUB = { template: '<div data-testid="layout-stub"><slot /></div>' };

// stage 学习页详情（A3 `@/api/mainline` 权威形状；overview 轻量，详情按需取，方案 A）。
function makeStageDetail(over: Partial<StageDetailDto> = {}): StageDetailDto {
  return {
    id: 7,
    chapter: 2,
    chapterTitle: 'Python 语言基础',
    index: 1,
    title: '变量与输入',
    kcCodes: ['PY43-KC-016'],
    videoKeyword: 'Python 变量',
    coursewarePageIds: [101, 102],
    exampleProblemIds: [201],
    requiredVideoSec: 240,
    requiredPages: 2,
    requiredExamples: 1,
    requiredQuestions: 0,
    videos: [],
    examples: [{ id: 201, displayNo: 'OJ-01', displayName: '圆面积计算', kcCode: 'PY43-KC-016' }],
    questions: [],
    videoWatchedSec: 0,
    pagesReadIds: [],
    examplesDoneIds: [],
    questionsCorrectIds: [],
    completed: false,
    ...over,
  };
}

// postExampleDone 返回（A3 真实形状）；本页进度合并取自子组件的 StageTaskProgress 事件，
// 不读此返回体，故字段值不影响断言，仅需类型自洽。
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
      { path: '/mainline/stage/:stageId(\\d+)', name: 'stage-learn', component: { template: '<div />' } },
      { path: '/mainline-map', name: 'mainline-map', component: { template: '<div />' } },
      { path: '/chapter-quiz/:chapter', name: 'chapter-quiz', component: { template: '<div />' } },
      { path: '/library', name: 'library', component: { template: '<div />' } },
      { path: '/training', name: 'training', component: { template: '<div />' } },
      { path: '/workshop', name: 'workshop', component: { template: '<div />' } },
      { path: '/world', name: 'world', component: { template: '<div />' } },
    ],
  });
}

async function mountAt(
  stageId: number,
): Promise<{ wrapper: ReturnType<typeof mount>; router: Router }> {
  const router = createTestRouter();
  await router.push(`/mainline/stage/${stageId}`);
  await router.isReady();
  const wrapper = mount(StageLearnPage, {
    global: { plugins: [router], stubs: { AppLayout: APP_LAYOUT_STUB } },
  });
  await flushPromises();
  return { wrapper, router };
}

describe('StageLearnPage（小节学习页 · 三件套编排）', () => {
  beforeEach(() => {
    // 内嵌 StageExamplePractice 的 useToastStore（R2 evidence 拒绝提示）需要活动 Pinia。
    setActivePinia(createPinia());
    vi.mocked(getStageDetail).mockResolvedValue(makeStageDetail());
    vi.mocked(postExampleDone).mockResolvedValue(
      makeStageProgress({ examplesDone: 1, examplesDoneIds: [201] }),
    );
    vi.mocked(prepareTest).mockResolvedValue({ status: 'GENERATING' });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('按 stageId 拉详情并渲染三件套与总览', async () => {
    const { wrapper } = await mountAt(7);

    expect(vi.mocked(getStageDetail)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(getStageDetail)).toHaveBeenCalledWith(7);
    expect(wrapper.find('[data-testid="stage-learn-page"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('变量与输入');
    expect(wrapper.text()).toContain('第 2 章');

    expect(wrapper.find('[data-testid="stage-video"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stage-courseware"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stage-practice"]').exists()).toBe(true);

    expect(wrapper.find('[data-testid="summary-video-val"]').text()).toBe('0:00 / 4:00');
    expect(wrapper.find('[data-testid="summary-courseware-val"]').text()).toContain('0 / 2');
    expect(wrapper.find('[data-testid="summary-practice-val"]').text()).toContain('0/1 OJ');

    wrapper.unmount();
  });

  it('子组件「练」打点后，本页合并服务端进度并刷新总览', async () => {
    const { wrapper } = await mountAt(7);

    expect(wrapper.find('[data-testid="summary-practice-val"]').text()).toContain('0/1 OJ');

    await wrapper.find('[data-testid="ex-mark-201"]').trigger('click');
    await flushPromises();

    expect(vi.mocked(postExampleDone)).toHaveBeenCalledWith(7, 201);
    expect(wrapper.find('[data-testid="summary-practice-val"]').text()).toContain('1/1 OJ');

    wrapper.unmount();
  });

  it('每章最后小节完成视频与 PPT 后开放章节小测', async () => {
    vi.mocked(getStageDetail).mockResolvedValue(
      makeStageDetail({
        chapter: 2,
        index: 3,
        videoWatchedSec: 240,
        pagesReadIds: [101, 102],
      }),
    );
    const { wrapper, router } = await mountAt(7);
    await wrapper.find('[data-testid="stage-toc-practice"]').trigger('click');
    const button = wrapper.find('[data-testid="stage-chapter-quiz"]');
    expect(button.exists()).toBe(true);
    expect(button.attributes('disabled')).toBeUndefined();

    await button.trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/chapter-quiz/2');
    wrapper.unmount();
  });

  it('每章最后小节完成视频与 PPT 后后台预生成章末大测试', async () => {
    vi.mocked(getStageDetail).mockResolvedValue(
      makeStageDetail({
        chapter: 2,
        index: 3,
        videoWatchedSec: 240,
        pagesReadIds: [101, 102],
      }),
    );

    const { wrapper } = await mountAt(7);
    await flushPromises();

    expect(vi.mocked(prepareTest)).toHaveBeenCalledWith(2, 'test');
    wrapper.unmount();
  });

  it('最终小节视频与 PPT 未达标时不预生成章末大测试', async () => {
    vi.mocked(getStageDetail).mockResolvedValue(
      makeStageDetail({ chapter: 2, index: 3, videoWatchedSec: 239 }),
    );

    const { wrapper } = await mountAt(7);
    await flushPromises();

    expect(vi.mocked(prepareTest)).not.toHaveBeenCalledWith(2, 'test');
    wrapper.unmount();
  });

  it('本节无关联课件页（requiredPages=0）时「学」判为已过线，章节小测可进入', async () => {
    // V156 seed：courseware_page_ids 默认 '{}'、required_pages 默认 0。
    // 后端 StageCompletion 对空课件判「已过线」，前端必须一致，否则小测按钮永久锁死。
    vi.mocked(getStageDetail).mockResolvedValue(
      makeStageDetail({
        chapter: 2,
        index: 3,
        coursewarePageIds: [],
        requiredPages: 0,
        pagesReadIds: [],
        videoWatchedSec: 240,
      }),
    );
    const { wrapper } = await mountAt(7);

    expect(wrapper.find('[data-testid="summary-courseware-val"]').text()).toBe('无需阅读');
    expect(wrapper.find('[data-testid="stage-toc-courseware"]').classes()).toContain(
      'stage-learn__toc-item--done',
    );

    await wrapper.find('[data-testid="stage-toc-practice"]').trigger('click');
    const button = wrapper.find('[data-testid="stage-chapter-quiz"]');
    expect(button.attributes('disabled')).toBeUndefined();
    expect(button.text()).toContain('进入章节小测');

    wrapper.unmount();
  });

  it('有课件页但未读满时「学」仍未过线，章节小测保持锁定', async () => {
    vi.mocked(getStageDetail).mockResolvedValue(
      makeStageDetail({ chapter: 2, index: 3, videoWatchedSec: 240, pagesReadIds: [101] }),
    );
    const { wrapper } = await mountAt(7);
    await wrapper.find('[data-testid="stage-toc-practice"]').trigger('click');
    expect(wrapper.find('[data-testid="stage-chapter-quiz"]').attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('章节小测在视频或 PPT 未完成时保持锁定', async () => {
    vi.mocked(getStageDetail).mockResolvedValue(
      makeStageDetail({ chapter: 2, index: 3, videoWatchedSec: 239 }),
    );
    const { wrapper } = await mountAt(7);
    await wrapper.find('[data-testid="stage-toc-practice"]').trigger('click');
    expect(wrapper.find('[data-testid="stage-chapter-quiz"]').attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('stage 不存在（404）时渲染未找到态', async () => {
    vi.mocked(getStageDetail).mockRejectedValue(
      new ApiClientError({ code: 'STAGE_NOT_FOUND', message: 'stage 不存在', details: [] }, 404),
    );
    const { wrapper } = await mountAt(999);
    expect(wrapper.find('[data-testid="stage-learn-missing"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stage-video"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('详情加载失败（非 404）时渲染错误态', async () => {
    vi.mocked(getStageDetail).mockRejectedValue(new Error('network down'));
    const { wrapper } = await mountAt(7);
    expect(wrapper.find('[data-testid="stage-learn-error"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('「返回冒险地图」跳 /mainline-map', async () => {
    const { wrapper, router } = await mountAt(7);
    const pushSpy = vi.spyOn(router, 'push');
    await wrapper.find('[data-testid="stage-learn-back"]').trigger('click');
    expect(pushSpy).toHaveBeenCalledWith({ path: '/mainline-map' });
    wrapper.unmount();
  });
});
