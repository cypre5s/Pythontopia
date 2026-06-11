import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick, ref, type Ref } from 'vue';
import { createPinia } from 'pinia';
import StoryboardView from '../StoryboardView.vue';
import { createStoryboardVideoJob, generateImage, getMediaJob } from '@/api/media';
import { useSseStream } from '@/composables/useSseStream';
import { ApiClientError } from '@/api/http';
import type { MediaJobResult } from '@/api/media';
import type { SsePlan, SseTraceEvent } from '@/composables/useSseStream';
import type { StoryboardScene } from '@/api/dto/Workshop';

// gsap 仅做入场动画，jsdom 下置空避免噪声 / ticker 副作用。
vi.mock('gsap', () => ({
  default: { fromTo: vi.fn(), to: vi.fn(), set: vi.fn() },
}));

// 合成接入只关心 job 端点 + SSE；其它 media 调用（文生图）给个降级占位即可。
vi.mock('@/api/media', () => ({
  createStoryboardVideoJob: vi.fn(),
  getMediaJob: vi.fn(),
  generateImage: vi.fn(),
}));

vi.mock('@/composables/useSseStream', () => ({
  useSseStream: vi.fn(),
}));

type SseStatus = 'idle' | 'connecting' | 'streaming' | 'done' | 'error';

interface FakeStream {
  traces: Ref<SseTraceEvent[]>;
  tokens: Ref<string>;
  result: Ref<MediaJobResult | null>;
  error: Ref<{ code: string; message: string } | null>;
  status: Ref<SseStatus>;
  plan: Ref<SsePlan | null>;
  abort: ReturnType<typeof vi.fn>;
  start: ReturnType<typeof vi.fn>;
  /** 测试钩子：解决 start() 的 promise（模拟 SSE 流终止）。 */
  finish: () => void;
}

// 受控的 useSseStream 替身：start() 直到测试调用 finish() 前一直 pending（模拟流持续中），
// 期间测试可直接改写 traces/result/error/status 来驱动组件。
function makeFakeStream(): FakeStream {
  const traces = ref<SseTraceEvent[]>([]);
  const tokens = ref('');
  const result = ref<MediaJobResult | null>(null);
  const error = ref<{ code: string; message: string } | null>(null);
  const status = ref<SseStatus>('idle');
  const plan = ref<SsePlan | null>(null);
  let resolveStart: (() => void) | null = null;
  const start = vi.fn((_body: unknown) => {
    status.value = 'streaming';
    return new Promise<void>((res) => {
      resolveStart = res;
    });
  });
  const abort = vi.fn(() => {
    if (status.value === 'connecting' || status.value === 'streaming') status.value = 'done';
  });
  return {
    traces,
    tokens,
    result,
    error,
    status,
    plan,
    abort,
    start,
    finish: () => resolveStart?.(),
  };
}

function asStream(fake: FakeStream): ReturnType<typeof useSseStream> {
  return fake as unknown as ReturnType<typeof useSseStream>;
}

const sampleScenes: StoryboardScene[] = [
  { title: '变量', narration: '变量是存储数据的容器', visual_hint: '贴标签的盒子', kc_codes: ['KC01'], duration_seconds: 5, image_prompt: '一个贴标签的盒子' },
  { title: '循环', narration: 'for 循环重复执行', visual_hint: '转动的齿轮', kc_codes: ['KC02'], duration_seconds: 6, image_prompt: '转动的齿轮' },
  { title: '函数', narration: '函数封装一段逻辑', visual_hint: '输入输出机器', kc_codes: ['KC03'], duration_seconds: 4, image_prompt: '输入输出机器' },
];

function mountView() {
  return mount(StoryboardView, {
    props: { content: { scenes: sampleScenes } },
    global: {
      plugins: [createPinia()],
      // BilibiliVideoCards 是 SA5 组件且挂载即拉网络，测试里桩掉。
      stubs: { BilibiliVideoCards: true },
    },
  });
}

async function clickCompose(w: ReturnType<typeof mountView>): Promise<void> {
  await nextTick();
  await w.find('[data-testid="storyboard-compose"]').trigger('click');
  await flushPromises();
}

describe('StoryboardView · 一键合成短视频（media job）', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(generateImage).mockResolvedValue({
      id: 1,
      kind: 'IMAGE',
      url: '/api/media/assets/1',
      mimeType: 'image/png',
      degraded: false,
      source: 'test',
      prompt: null,
      kcCodes: [],
    });
  });

  it('① 命中缓存：直接内联播放成片，不订阅 SSE', async () => {
    vi.mocked(createStoryboardVideoJob).mockResolvedValue({
      jobId: 1,
      status: 'SUCCESS',
      cached: true,
      assetId: 42,
    });

    const w = mountView();
    await clickCompose(w);
    await nextTick();

    const video = w.find('[data-testid="storyboard-video"]');
    expect(video.exists()).toBe(true);
    expect(video.attributes('src')).toBe('/api/media/assets/42');
    // 缓存命中不应建立 SSE 流。
    expect(useSseStream).not.toHaveBeenCalled();
    expect(w.find('[data-testid="storyboard-studio"]').exists()).toBe(false);
  });

  it('② 未命中缓存：订阅 SSE，进度显示插画师/配音师/剪辑师，result 后内联播放', async () => {
    vi.mocked(createStoryboardVideoJob).mockResolvedValue({
      jobId: 7,
      status: 'RUNNING',
      cached: false,
    });
    const fake = makeFakeStream();
    vi.mocked(useSseStream).mockReturnValue(asStream(fake));

    const w = mountView();
    await clickCompose(w);

    expect(useSseStream).toHaveBeenCalledWith('/api/media/jobs/7/stream');
    expect(fake.start).toHaveBeenCalledTimes(1);

    // worker 逐镜回写进度（插画师 → 配音师 → 剪辑师）。
    fake.traces.value = [
      { node: 'illustrator', status: 'running', elapsed_ms: 120, role_key: 'illustrator', message: '绘制分镜 1/3', percent: 20 },
      { node: 'narrator', status: 'running', elapsed_ms: 240, role_key: 'narrator', message: '合成旁白 2/3', percent: 60 },
      { node: 'editor', status: 'running', elapsed_ms: 360, role_key: 'editor', message: '拼接成片', percent: 90 },
    ];
    await nextTick();

    const studio = w.find('[data-testid="storyboard-studio"]');
    expect(studio.exists()).toBe(true);
    // 展开全员流水线，确认三位「展示性人物」都呈现。
    await studio.find('[data-testid="agent-studio-toggle"]').trigger('click');
    const studioText = studio.text();
    expect(studioText).toContain('插画师');
    expect(studioText).toContain('配音师');
    expect(studioText).toContain('剪辑师');

    // result 事件就绪 + 流终止。
    fake.result.value = { jobId: 7, assetId: 99, url: '/api/media/assets/99' };
    fake.status.value = 'done';
    fake.finish();
    await flushPromises();
    await nextTick();

    const video = w.find('[data-testid="storyboard-video"]');
    expect(video.exists()).toBe(true);
    expect(video.attributes('src')).toBe('/api/media/assets/99');
    // 成片播放后进度面板收起。
    expect(w.find('[data-testid="storyboard-studio"]').exists()).toBe(false);
  });

  it('③ SSE error(VIDEO_COMPOSE_UNAVAILABLE)：提示「服务端未装 ffmpeg」且不出视频', async () => {
    vi.mocked(createStoryboardVideoJob).mockResolvedValue({
      jobId: 5,
      status: 'RUNNING',
      cached: false,
    });
    const fake = makeFakeStream();
    vi.mocked(useSseStream).mockReturnValue(asStream(fake));

    const w = mountView();
    await clickCompose(w);

    fake.error.value = { code: 'VIDEO_COMPOSE_UNAVAILABLE', message: 'ffmpeg not found' };
    fake.status.value = 'error';
    fake.finish();
    await flushPromises();
    await nextTick();

    const banner = w.find('[data-testid="storyboard-compose-error"]');
    expect(banner.exists()).toBe(true);
    expect(banner.text()).toContain('ffmpeg');
    expect(w.find('[data-testid="storyboard-video"]').exists()).toBe(false);
    expect(w.find('[data-testid="storyboard-studio"]').exists()).toBe(false);
    // 降级后可重试：合成按钮回到可点状态。
    const btn = w.find('[data-testid="storyboard-compose"]');
    expect(btn.exists()).toBe(true);
    expect(btn.attributes('disabled')).toBeUndefined();
  });

  it('③b 创建作业即降级(ApiClientError VIDEO_COMPOSE_UNAVAILABLE)：提示并保留实时剧场', async () => {
    vi.mocked(createStoryboardVideoJob).mockRejectedValue(
      new ApiClientError(
        { code: 'VIDEO_COMPOSE_UNAVAILABLE', message: 'no ffmpeg', details: [], traceId: '', timestamp: '' },
        503,
      ),
    );

    const w = mountView();
    await clickCompose(w);
    await nextTick();

    const banner = w.find('[data-testid="storyboard-compose-error"]');
    expect(banner.exists()).toBe(true);
    expect(banner.text()).toContain('ffmpeg');
    expect(useSseStream).not.toHaveBeenCalled();
    expect(w.find('[data-testid="storyboard-video"]').exists()).toBe(false);
  });

  it('④ SSE 正常结束却无 result：轮询兜底拿到成片并播放', async () => {
    vi.mocked(createStoryboardVideoJob).mockResolvedValue({
      jobId: 8,
      status: 'RUNNING',
      cached: false,
    });
    vi.mocked(getMediaJob).mockResolvedValue({
      jobId: 8,
      status: 'SUCCESS',
      progress: {},
      assetId: 77,
      url: '/api/media/assets/77',
    });
    const fake = makeFakeStream();
    vi.mocked(useSseStream).mockReturnValue(asStream(fake));

    const w = mountView();
    await clickCompose(w);

    fake.status.value = 'done';
    fake.finish();
    await flushPromises();
    await nextTick();

    expect(getMediaJob).toHaveBeenCalledWith(8);
    const video = w.find('[data-testid="storyboard-video"]');
    expect(video.exists()).toBe(true);
    expect(video.attributes('src')).toBe('/api/media/assets/77');
  });
});
