import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';

// A3 (fe-map) 的 @/api/mainline 尚未落地：用 factory mock，使本测试独立通过。
vi.mock('@/api/mainline', () => ({
  getMainlineOverview: vi.fn(),
  postVideoProgress: vi.fn(),
  postPageRead: vi.fn(),
  postExampleDone: vi.fn(),
}));
vi.mock('@/api/bilibiliVideo', () => ({
  searchBilibili: vi.fn(),
}));

import StageVideoWatcher from '@/components/mainline-stage/StageVideoWatcher.vue';
import { postVideoProgress } from '@/api/mainline';
import type { StageProgressDto } from '@/api/mainline';
import { searchBilibili } from '@/api/bilibiliVideo';
import type { BilibiliVideoDto } from '@/api/bilibiliVideo';

function makeVideo(bvid: string): BilibiliVideoDto {
  return {
    bvid,
    title: 'Python for 循环讲解',
    author: 'UP主',
    description: '',
    aiSummary: '',
    coverUrl: '',
    durationSec: 480,
    play: 1000,
    danmaku: 10,
    pubdate: 0,
    url: `https://www.bilibili.com/video/${bvid}`,
  };
}

// 与 A3 真实 StageProgressDto 对齐（组件只 await 成功、不读返回体，字段值不影响断言）。
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

describe('StageVideoWatcher（看 · B站站内计时）', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    setVisibility('visible');
    vi.mocked(searchBilibili).mockResolvedValue([makeVideo('BV1abc123')]);
    vi.mocked(postVideoProgress).mockResolvedValue(makeStageProgress());
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('用 video_keyword 检索并嵌入 player.bilibili.com iframe', async () => {
    const wrapper = mount(StageVideoWatcher, {
      props: { stageId: 7, videoKeyword: 'Python for循环', requiredVideoSec: 600, initialWatchedSec: 0 },
    });
    await vi.advanceTimersByTimeAsync(0);
    await nextTick();

    expect(vi.mocked(searchBilibili)).toHaveBeenCalledWith('Python for循环', 1);
    const iframe = wrapper.find('[data-testid="stage-video-iframe"]');
    expect(iframe.exists()).toBe(true);
    const src = iframe.attributes('src') ?? '';
    expect(src).toContain('player.bilibili.com/player.html');
    expect(src).toContain('bvid=BV1abc123');
  });

  it('可见时每 10 秒批量上报一次 deltaSec=10', async () => {
    const wrapper = mount(StageVideoWatcher, {
      props: { stageId: 7, videoKeyword: 'Python 变量', requiredVideoSec: 600, initialWatchedSec: 0 },
    });
    await vi.advanceTimersByTimeAsync(0);
    await nextTick();
    expect(vi.mocked(postVideoProgress)).not.toHaveBeenCalled();

    // 推进 10 秒（10 次 tick）→ 攒满 1 批 → 上报一次。
    await vi.advanceTimersByTimeAsync(10_000);

    expect(vi.mocked(postVideoProgress)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(postVideoProgress)).toHaveBeenCalledWith(7, 10);
    expect(wrapper.find('[data-testid="stage-video-count"]').text()).toContain('0:10');

    // 再推进 10 秒 → 第 2 批。
    await vi.advanceTimersByTimeAsync(10_000);
    expect(vi.mocked(postVideoProgress)).toHaveBeenCalledTimes(2);
    expect(vi.mocked(postVideoProgress)).toHaveBeenLastCalledWith(7, 10);
  });

  it('累计达 requiredVideoSec 即停表、过线、不再上报', async () => {
    const wrapper = mount(StageVideoWatcher, {
      props: { stageId: 7, videoKeyword: 'Python 输入', requiredVideoSec: 5, initialWatchedSec: 0 },
    });
    await vi.advanceTimersByTimeAsync(0);
    await nextTick();

    // 推进远超 5 秒：应在第 5 秒过线后停表。
    await vi.advanceTimersByTimeAsync(20_000);

    expect(vi.mocked(postVideoProgress)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(postVideoProgress)).toHaveBeenCalledWith(7, 5);
    expect(wrapper.emitted('passed')).toBeTruthy();
    expect(wrapper.find('[data-testid="stage-video-passed"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stage-video-count"]').text()).toContain('0:05 / 0:05');
  });

  it('标签页不可见时不计时、不上报', async () => {
    setVisibility('hidden');
    const wrapper = mount(StageVideoWatcher, {
      props: { stageId: 7, videoKeyword: 'Python 列表', requiredVideoSec: 600, initialWatchedSec: 0 },
    });
    await vi.advanceTimersByTimeAsync(0);
    await nextTick();

    await vi.advanceTimersByTimeAsync(15_000);

    expect(vi.mocked(postVideoProgress)).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="stage-video-count"]').text()).toContain('0:00');

    // 切回可见后恢复计时。
    setVisibility('visible');
    await vi.advanceTimersByTimeAsync(3_000);
    expect(wrapper.find('[data-testid="stage-video-count"]').text()).toContain('0:03');
  });

  it('检索无结果时降级占位、不渲染 iframe、不计时', async () => {
    vi.mocked(searchBilibili).mockResolvedValue([]);
    const wrapper = mount(StageVideoWatcher, {
      props: { stageId: 7, videoKeyword: '无此视频', requiredVideoSec: 600, initialWatchedSec: 0 },
    });
    await vi.advanceTimersByTimeAsync(0);
    await nextTick();

    expect(wrapper.find('[data-testid="stage-video-iframe"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="stage-video-empty"]').exists()).toBe(true);

    await vi.advanceTimersByTimeAsync(15_000);
    expect(vi.mocked(postVideoProgress)).not.toHaveBeenCalled();
  });
});
