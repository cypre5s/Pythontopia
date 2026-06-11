import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

// R-14「看」改造：站内内置课程视频播放（不再走 B 站检索）。
// 只 mock 打点端点 postVideoProgress；videoStreamUrl 是纯函数（@/api/courseVideo），无需 mock。
vi.mock('@/api/mainline', () => ({
  getMainlineOverview: vi.fn(),
  postVideoProgress: vi.fn(),
  postPageRead: vi.fn(),
  postExampleDone: vi.fn(),
}));

import StageVideoWatcher from '@/components/mainline-stage/StageVideoWatcher.vue';
import { postVideoProgress } from '@/api/mainline';
import type { StageProgressDto, StageVideoDto } from '@/api/mainline';

function makeVideo(id: number, title: string, durationSec: number): StageVideoDto {
  return { id, title, durationSec };
}

// 组件只 await 成功、不读返回体，字段值不影响断言；与 A3 真实 StageProgressDto 对齐。
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

function setVisibility(state: DocumentVisibilityState): void {
  Object.defineProperty(document, 'visibilityState', {
    configurable: true,
    get: () => state,
  });
  document.dispatchEvent(new Event('visibilitychange'));
}

/** 模拟 <video> 播放到 currentTime=t 并触发一次 timeupdate（jsdom 无真实播放）。 */
function playTo(el: HTMLVideoElement, t: number): void {
  Object.defineProperty(el, 'currentTime', { configurable: true, get: () => t });
  el.dispatchEvent(new Event('timeupdate'));
}

/** 以每秒 1 步（≤ MAX_TICK_GAP_SEC）从 from 推进到 to（含端点），逐帧触发 timeupdate。 */
function advance(el: HTMLVideoElement, from: number, to: number): void {
  for (let t = from; t <= to; t += 1) playTo(el, t);
}

function videoEl(wrapper: ReturnType<typeof mount>): HTMLVideoElement {
  return wrapper.get('[data-testid="stage-video-el"]').element as HTMLVideoElement;
}

// 每个用例后自动卸载组件：移除其 document.visibilitychange 监听，避免跨用例污染。
enableAutoUnmount(afterEach);

describe('StageVideoWatcher（看 · 站内课程视频计时）', () => {
  beforeEach(() => {
    setVisibility('visible');
    vi.mocked(postVideoProgress).mockReset();
    vi.mocked(postVideoProgress).mockResolvedValue(makeStageProgress());
  });

  it('渲染内置播放器 + 本章视频清单（标题/时长 mm:ss，默认播第 1 个）', () => {
    const wrapper = mount(StageVideoWatcher, {
      props: {
        stageId: 7,
        videos: [makeVideo(101, '第一节 变量', 65), makeVideo(102, '第二节 输入', 130)],
        requiredVideoSec: 160,
        initialWatchedSec: 0,
      },
    });

    expect(wrapper.find('[data-testid="stage-video"]').exists()).toBe(true);
    // 默认选中并加载清单第 1 个，<video> 直连后端字节流端点。
    expect(wrapper.find('[data-testid="stage-video-el"]').attributes('src')).toContain(
      '/api/course/videos/101',
    );

    const item1 = wrapper.find('[data-testid="stage-video-item-101"]');
    const item2 = wrapper.find('[data-testid="stage-video-item-102"]');
    expect(item1.exists()).toBe(true);
    expect(item2.exists()).toBe(true);
    expect(item1.classes()).toContain('stage-video__item--current');
    expect(item2.classes()).not.toContain('stage-video__item--current');
    // 标题 + 时长 mm:ss。
    expect(item1.text()).toContain('第一节 变量');
    expect(item1.text()).toContain('1:05');
    expect(item2.text()).toContain('2:10');
    // 进度文案：已观看 0:00 / 2:40（mmss(160)=2:40）。
    expect(wrapper.find('[data-testid="stage-video-count"]').text()).toContain('0:00 / 2:40');
  });

  it('点击清单项切换当前播放视频（高亮迁移 + 换流地址）', async () => {
    const wrapper = mount(StageVideoWatcher, {
      props: {
        stageId: 7,
        videos: [makeVideo(101, '第一节', 65), makeVideo(102, '第二节', 130)],
        requiredVideoSec: 600,
        initialWatchedSec: 0,
      },
    });

    await wrapper.get('[data-testid="stage-video-item-102"] button').trigger('click');
    await nextTick();

    expect(wrapper.find('[data-testid="stage-video-el"]').attributes('src')).toContain(
      '/api/course/videos/102',
    );
    expect(wrapper.find('[data-testid="stage-video-item-102"]').classes()).toContain(
      'stage-video__item--current',
    );
    expect(wrapper.find('[data-testid="stage-video-item-101"]').classes()).not.toContain(
      'stage-video__item--current',
    );
  });

  it('timeupdate 按前进量累计观看，攒满 30s 批量上报一次 deltaSec=30（对齐服务端每分钟 2 次有效限频）', async () => {
    const wrapper = mount(StageVideoWatcher, {
      props: {
        stageId: 7,
        videos: [makeVideo(1, '讲解', 600)],
        requiredVideoSec: 600,
        initialWatchedSec: 0,
      },
    });
    const el = videoEl(wrapper);
    expect(vi.mocked(postVideoProgress)).not.toHaveBeenCalled();

    // 播放 0→29s：未攒满一批，不上报。
    advance(el, 1, 29);
    await flushPromises();
    expect(vi.mocked(postVideoProgress)).not.toHaveBeenCalled();

    // 播放到 30s → 攒满 1 批 → 上报一次。
    advance(el, 30, 30);
    await flushPromises();
    expect(vi.mocked(postVideoProgress)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(postVideoProgress)).toHaveBeenCalledWith(7, 30);
    expect(wrapper.find('[data-testid="stage-video-count"]').text()).toContain('0:30');
    // 成功后向上 emit 当前累计观看秒数。
    const progress = wrapper.emitted('progress');
    expect(progress?.[0]?.[0]).toMatchObject({ stageId: 7, videoWatchedSec: 30 });

    // 继续播放 30→60s → 第 2 批。
    advance(el, 31, 60);
    await flushPromises();
    expect(vi.mocked(postVideoProgress)).toHaveBeenCalledTimes(2);
    expect(vi.mocked(postVideoProgress)).toHaveBeenLastCalledWith(7, 30);
    expect(wrapper.find('[data-testid="stage-video-count"]').text()).toContain('1:00');
  });

  it('单帧大跳（拖动/快进）不计入观看时长', async () => {
    const wrapper = mount(StageVideoWatcher, {
      props: {
        stageId: 7,
        videos: [makeVideo(1, '讲解', 600)],
        requiredVideoSec: 600,
        initialWatchedSec: 0,
      },
    });
    const el = videoEl(wrapper);

    // 直接拖到 300s（单帧前进 300 > 阈值）→ 只对齐基准、不计时、不上报。
    playTo(el, 300);
    await flushPromises();
    expect(vi.mocked(postVideoProgress)).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="stage-video-count"]').text()).toContain('0:00');

    // 从 300s 正常往后逐帧播 1s 仍照常计时。
    advance(el, 301, 303);
    await flushPromises();
    expect(wrapper.find('[data-testid="stage-video-count"]').text()).toContain('0:03');
  });

  it('累计达 requiredVideoSec 即停表、过线、不再上报', async () => {
    const wrapper = mount(StageVideoWatcher, {
      props: {
        stageId: 7,
        videos: [makeVideo(1, '讲解', 60)],
        requiredVideoSec: 5,
        initialWatchedSec: 0,
      },
    });
    const el = videoEl(wrapper);

    // 播放远超 5s：应在第 5s 过线后停表（后续 timeupdate 不再计时/上报）。
    advance(el, 1, 12);
    await flushPromises();

    expect(vi.mocked(postVideoProgress)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(postVideoProgress)).toHaveBeenCalledWith(7, 5);
    expect(wrapper.emitted('passed')).toBeTruthy();
    expect(wrapper.find('[data-testid="stage-video-passed"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stage-video-count"]').text()).toContain('0:05 / 0:05');
  });

  it('上报失败时退回 pending，下一批连同失败量一起补报（不丢量）', async () => {
    vi.mocked(postVideoProgress).mockRejectedValueOnce(new Error('network'));
    const wrapper = mount(StageVideoWatcher, {
      props: {
        stageId: 7,
        videos: [makeVideo(1, '讲解', 600)],
        requiredVideoSec: 600,
        initialWatchedSec: 0,
      },
    });
    const el = videoEl(wrapper);

    // 第 1 批 30s 上报失败 → pending 退回 30（watched 本地权威仍记 30）。
    advance(el, 1, 30);
    await flushPromises();
    expect(vi.mocked(postVideoProgress)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(postVideoProgress)).toHaveBeenNthCalledWith(1, 7, 30);
    expect(wrapper.find('[data-testid="stage-video-count"]').text()).toContain('0:30');

    // 再播 1s → pending 达 31(≥批量阈值) → 立刻重试，把失败的 30 连同新增 1 一起补报。
    advance(el, 31, 31);
    await flushPromises();
    expect(vi.mocked(postVideoProgress)).toHaveBeenCalledTimes(2);
    expect(vi.mocked(postVideoProgress)).toHaveBeenLastCalledWith(7, 31);
    expect(wrapper.find('[data-testid="stage-video-count"]').text()).toContain('0:31');
  });

  it('标签页不可见时不计时、不上报，切回可见后恢复', async () => {
    setVisibility('hidden');
    const wrapper = mount(StageVideoWatcher, {
      props: {
        stageId: 7,
        videos: [makeVideo(1, '讲解', 600)],
        requiredVideoSec: 600,
        initialWatchedSec: 0,
      },
    });
    const el = videoEl(wrapper);

    // 不可见期间即便有 timeupdate（后台续播）也不计时。
    playTo(el, 5);
    playTo(el, 10);
    playTo(el, 15);
    await flushPromises();
    expect(vi.mocked(postVideoProgress)).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="stage-video-count"]').text()).toContain('0:00');

    // 切回可见后从当前位置（15s）逐帧恢复计时。
    setVisibility('visible');
    advance(el, 16, 18);
    await flushPromises();
    expect(wrapper.find('[data-testid="stage-video-count"]').text()).toContain('0:03');
  });

  it('本章无内置视频时显示占位、不渲染播放器、不计时', async () => {
    const wrapper = mount(StageVideoWatcher, {
      props: {
        stageId: 7,
        videos: [],
        requiredVideoSec: 240,
        initialWatchedSec: 0,
      },
    });

    expect(wrapper.find('[data-testid="stage-video-empty"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stage-video-el"]').exists()).toBe(false);
    await flushPromises();
    expect(vi.mocked(postVideoProgress)).not.toHaveBeenCalled();
  });
});
