import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import BilibiliVideoCards from '@/components/video/BilibiliVideoCards.vue';
import VideoInsightView from '@/components/video/VideoInsightView.vue';
import {
  fetchBilibiliInsight,
  searchBilibili,
  type BilibiliInsightDto,
  type BilibiliVideoDto,
} from '@/api/bilibiliVideo';

// Track E：整条链路只 mock api 层（fetchBilibiliInsight / searchBilibili），不打真实网络。
vi.mock('@/api/bilibiliVideo', () => ({
  searchBilibili: vi.fn(),
  fetchBilibiliInsight: vi.fn(),
}));

const SAMPLE_VIDEOS: BilibiliVideoDto[] = [
  {
    bvid: 'BV1xx411c7XD',
    title: 'for 循环讲解',
    author: 'UP主小明',
    description: 'UP 主原始简介',
    aiSummary: '速览：讲 for 循环与 range 的写法',
    coverUrl: 'https://i2.hdslb.com/x.jpg',
    durationSec: 754,
    play: 123456,
    danmaku: 678,
    pubdate: 1600000000,
    url: 'https://www.bilibili.com/video/BV1xx411c7XD',
  },
];

function makeInsight(overrides: Partial<BilibiliInsightDto> = {}): BilibiliInsightDto {
  return {
    bvid: 'BV1xx411c7XD',
    overview: '这段视频讲了 for 循环与 range 的配合用法，建议先看前两分钟。',
    keyPoints: [
      { tSec: 32, label: 'for 与 range 基本写法', source: 'ai_conclusion' },
      { tSec: 92, label: 'range(start, stop, step) 三参数', source: 'ai_conclusion' },
    ],
    basedOn: 'ai_conclusion',
    degraded: false,
    ...overrides,
  };
}

describe('VideoInsightView（纯展示）', () => {
  it('renders the overview and clickable key points (mm:ss + label, new-tab jump links)', () => {
    const insight = makeInsight();
    const wrapper = mount(VideoInsightView, {
      props: {
        bvid: insight.bvid,
        overview: insight.overview,
        keyPoints: insight.keyPoints,
        degraded: insight.degraded,
      },
    });

    expect(wrapper.find('[data-testid="video-insight-overview"]').text()).toContain(
      'for 循环与 range',
    );

    const jumps = wrapper.findAll('[data-testid="video-insight-jump"]');
    expect(jumps).toHaveLength(2);

    const first = jumps[0];
    expect(first.find('.video-insight__time').text()).toBe('0:32');
    expect(first.find('.video-insight__label').text()).toContain('for 与 range');
    expect(first.attributes('href')).toBe('https://www.bilibili.com/video/BV1xx411c7XD?t=32');
    expect(first.attributes('target')).toBe('_blank');
    expect(first.attributes('rel')).toBe('noopener noreferrer');

    // mm:ss：92 秒 → 1:32（验证时间点拼接 / 进位正确）。
    const second = jumps[1];
    expect(second.find('.video-insight__time').text()).toBe('1:32');
    expect(second.attributes('href')).toBe('https://www.bilibili.com/video/BV1xx411c7XD?t=92');
  });

  it('shows only the overview + neutral hint when degraded (never fabricates time points)', () => {
    const wrapper = mount(VideoInsightView, {
      props: {
        bvid: 'BV1xx411c7XD',
        overview: '仅概述（降级态）。',
        // 即便给了时间点，degraded 也必须屏蔽，绝不渲染。
        keyPoints: [{ tSec: 10, label: '不应出现的时间点', source: 'pages' }],
        degraded: true,
      },
    });

    expect(wrapper.find('[data-testid="video-insight-overview"]').text()).toContain('仅概述');
    expect(wrapper.find('[data-testid="video-insight-empty"]').text()).toContain(
      '暂无可用关键时间点',
    );
    expect(wrapper.findAll('[data-testid="video-insight-jump"]')).toHaveLength(0);
  });

  it('shows the neutral hint when key points are empty (not degraded)', () => {
    const wrapper = mount(VideoInsightView, {
      props: { bvid: 'BV1xx411c7XD', overview: '有概述但无时间轴。', keyPoints: [], degraded: false },
    });

    expect(wrapper.find('[data-testid="video-insight-overview"]').text()).toContain('有概述');
    expect(wrapper.find('[data-testid="video-insight-empty"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-testid="video-insight-jump"]')).toHaveLength(0);
  });

  it('falls back to a neutral overview placeholder when overview is empty (no fabrication)', () => {
    const wrapper = mount(VideoInsightView, {
      props: { bvid: 'BV1xx411c7XD', overview: '', keyPoints: [], degraded: true },
    });
    expect(wrapper.find('[data-testid="video-insight-overview"]').text()).toContain(
      'AI 概述暂不可用',
    );
  });

  it('emits "jump" with the clicked key point', async () => {
    const insight = makeInsight();
    const wrapper = mount(VideoInsightView, {
      props: {
        bvid: insight.bvid,
        overview: insight.overview,
        keyPoints: insight.keyPoints,
        degraded: false,
      },
    });

    await wrapper.find('[data-testid="video-insight-jump"]').trigger('click');
    const jumpEvents = wrapper.emitted('jump');
    expect(jumpEvents).toBeTruthy();
    expect(jumpEvents?.[0]?.[0]).toMatchObject({ tSec: 32, label: 'for 与 range 基本写法' });
  });
});

describe('BilibiliVideoCards · AI 讲解 / 看点', () => {
  beforeEach(() => {
    vi.mocked(searchBilibili).mockReset();
    vi.mocked(fetchBilibiliInsight).mockReset();
  });

  it('expands an inline insight panel and renders overview + key points on toggle', async () => {
    vi.mocked(searchBilibili).mockResolvedValueOnce(SAMPLE_VIDEOS);
    vi.mocked(fetchBilibiliInsight).mockResolvedValueOnce(makeInsight());

    const wrapper = mount(BilibiliVideoCards, { props: { keyword: 'for循环 Python' } });
    await flushPromises();

    // 默认不展开。
    expect(wrapper.find('[data-testid="bili-insight-panel"]').exists()).toBe(false);

    await wrapper.find('[data-testid="bili-insight-toggle"]').trigger('click');
    await flushPromises();

    // question 缺省 → 用关键词兜底；kcCodes 缺省 → []。
    expect(fetchBilibiliInsight).toHaveBeenCalledWith('BV1xx411c7XD', 'for循环 Python', []);

    expect(wrapper.find('[data-testid="bili-insight-panel"]').exists()).toBe(true);
    expect(wrapper.findComponent(VideoInsightView).exists()).toBe(true);
    expect(wrapper.find('[data-testid="video-insight-overview"]').text()).toContain(
      'for 循环与 range',
    );

    const jumps = wrapper.findAll('[data-testid="video-insight-jump"]');
    expect(jumps).toHaveLength(2);
    expect(jumps[0].attributes('href')).toBe('https://www.bilibili.com/video/BV1xx411c7XD?t=32');
  });

  it('forwards explicit question + kcCodes props to fetchBilibiliInsight', async () => {
    vi.mocked(searchBilibili).mockResolvedValueOnce(SAMPLE_VIDEOS);
    vi.mocked(fetchBilibiliInsight).mockResolvedValueOnce(makeInsight());

    const wrapper = mount(BilibiliVideoCards, {
      props: { keyword: 'for循环', question: 'range 的 step 怎么用？', kcCodes: ['KC_LOOP_FOR'] },
    });
    await flushPromises();

    await wrapper.find('[data-testid="bili-insight-toggle"]').trigger('click');
    await flushPromises();

    expect(fetchBilibiliInsight).toHaveBeenCalledWith('BV1xx411c7XD', 'range 的 step 怎么用？', [
      'KC_LOOP_FOR',
    ]);
  });

  it('reports a key point link opening without claiming that the video was consumed', async () => {
    vi.mocked(searchBilibili).mockResolvedValueOnce(SAMPLE_VIDEOS);
    vi.mocked(fetchBilibiliInsight).mockResolvedValueOnce(makeInsight());

    const wrapper = mount(BilibiliVideoCards, { props: { keyword: 'for循环' } });
    await flushPromises();
    await wrapper.find('[data-testid="bili-insight-toggle"]').trigger('click');
    await flushPromises();

    await wrapper.find('[data-testid="video-insight-jump"]').trigger('click');
    expect(wrapper.emitted('opened')).toBeTruthy();
    expect(wrapper.emitted('consumed')).toBeUndefined();
  });

  it('renders only overview + neutral hint when the insight is degraded', async () => {
    vi.mocked(searchBilibili).mockResolvedValueOnce(SAMPLE_VIDEOS);
    vi.mocked(fetchBilibiliInsight).mockResolvedValueOnce(
      makeInsight({
        overview: '该视频整体相关，但拿不到精确时间轴。',
        keyPoints: [],
        basedOn: 'metadata_only',
        degraded: true,
      }),
    );

    const wrapper = mount(BilibiliVideoCards, { props: { keyword: 'for循环' } });
    await flushPromises();
    await wrapper.find('[data-testid="bili-insight-toggle"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="video-insight-overview"]').text()).toContain(
      '拿不到精确时间轴',
    );
    expect(wrapper.find('[data-testid="video-insight-empty"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-testid="video-insight-jump"]')).toHaveLength(0);
  });

  it('shows a loading hint while the insight request is in flight', async () => {
    vi.mocked(searchBilibili).mockResolvedValueOnce(SAMPLE_VIDEOS);
    let resolveInsight: ((value: BilibiliInsightDto) => void) | undefined;
    vi.mocked(fetchBilibiliInsight).mockImplementationOnce(
      () =>
        new Promise<BilibiliInsightDto>((resolve) => {
          resolveInsight = resolve;
        }),
    );

    const wrapper = mount(BilibiliVideoCards, { props: { keyword: 'for循环' } });
    await flushPromises();
    await wrapper.find('[data-testid="bili-insight-toggle"]').trigger('click');

    // 请求尚未 resolve → loading 占位。
    expect(wrapper.find('[data-testid="bili-insight-loading"]').exists()).toBe(true);

    resolveInsight?.(makeInsight());
    await flushPromises();

    expect(wrapper.find('[data-testid="bili-insight-loading"]').exists()).toBe(false);
    expect(wrapper.findComponent(VideoInsightView).exists()).toBe(true);
  });

  it('shows an error placeholder + retry when the insight request fails (fail-fast, no fake)', async () => {
    vi.mocked(searchBilibili).mockResolvedValueOnce(SAMPLE_VIDEOS);
    vi.mocked(fetchBilibiliInsight).mockRejectedValueOnce(new Error('graph down'));

    const wrapper = mount(BilibiliVideoCards, { props: { keyword: 'for循环' } });
    await flushPromises();
    await wrapper.find('[data-testid="bili-insight-toggle"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="bili-insight-error"]').exists()).toBe(true);
    expect(wrapper.findComponent(VideoInsightView).exists()).toBe(false);

    // 重试成功 → 渲染 insight。
    vi.mocked(fetchBilibiliInsight).mockResolvedValueOnce(makeInsight());
    await wrapper.find('[data-testid="bili-insight-retry"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="bili-insight-error"]').exists()).toBe(false);
    expect(wrapper.findComponent(VideoInsightView).exists()).toBe(true);
  });

  it('collapses the panel when the toggle is clicked again', async () => {
    vi.mocked(searchBilibili).mockResolvedValueOnce(SAMPLE_VIDEOS);
    vi.mocked(fetchBilibiliInsight).mockResolvedValueOnce(makeInsight());

    const wrapper = mount(BilibiliVideoCards, { props: { keyword: 'for循环' } });
    await flushPromises();

    const toggle = wrapper.find('[data-testid="bili-insight-toggle"]');
    await toggle.trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="bili-insight-panel"]').exists()).toBe(true);

    await toggle.trigger('click');
    expect(wrapper.find('[data-testid="bili-insight-panel"]').exists()).toBe(false);
    // 收起后再展开复用缓存，不再二次请求。
    await toggle.trigger('click');
    await flushPromises();
    expect(vi.mocked(fetchBilibiliInsight)).toHaveBeenCalledTimes(1);
  });
});
