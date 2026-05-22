import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import ChapterNavPanel from '@/components/world/ChapterNavPanel.vue';
import { CHAPTERS } from '@/data/chapters';
import { useLearningStore } from '@stores/learning';
import type { CropTileDto } from '@/api/dto/CropTileDto';

// 隔离 Phaser 音频 adapter：jsdom 没法跑 Phaser sound manager。
const audioMocks = vi.hoisted(() => ({
  playSfx: vi.fn(),
  playBgm: vi.fn(),
  stopBgm: vi.fn(),
  setMuted: vi.fn(),
}));

vi.mock('@/composables/useAudio', () => ({
  ...audioMocks,
  useAudio: () => audioMocks,
}));

function crop(kcCode: string, partial: Partial<CropTileDto> = {}): CropTileDto {
  return {
    tileX: 0,
    tileY: 0,
    kcCode,
    status: 'SEED',
    unlocked: true,
    masteryScore: 0,
    growthStage: 0,
    ...partial,
  };
}

function buildRouter(initial = '/world'): Router {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/world',
        name: 'world',
        component: { template: '<div />' },
      },
    ],
  });
  void router.push(initial);
  return router;
}

async function mountPanel(initialRoute: string = '/world'): Promise<{
  wrapper: ReturnType<typeof mount>;
  router: Router;
}> {
  const router = buildRouter(initialRoute);
  await router.isReady();
  const wrapper = mount(ChapterNavPanel, {
    global: {
      plugins: [router],
    },
  });
  return { wrapper, router };
}

describe('ChapterNavPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    audioMocks.playSfx.mockReset();
    audioMocks.playBgm.mockReset();
    audioMocks.stopBgm.mockReset();
    audioMocks.setMuted.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('渲染 7 章按钮，testid 与图标路径与 CHAPTERS 一致', async () => {
    const { wrapper } = await mountPanel();
    const buttons = wrapper.findAll('[data-testid^="chapter-nav-"]');
    // chapter-nav-panel 容器也匹配，需要单独过滤章节按钮（slug 不为 panel）
    const chapterButtons = buttons.filter(
      (btn) => btn.attributes('data-testid') !== 'chapter-nav-panel',
    );
    expect(chapterButtons).toHaveLength(CHAPTERS.length);
    expect(CHAPTERS).toHaveLength(7);
    chapterButtons.forEach((btn, idx) => {
      const chapter = CHAPTERS[idx]!;
      expect(btn.attributes('data-testid')).toBe(`chapter-nav-${chapter.kcCode}`);
      const img = btn.find('img');
      expect(img.attributes('src')).toBe(chapter.iconPath);
    });
  });

  it('点击章节：调用 selectKc(范围内第一个 KC) 并同步 router query', async () => {
    const learning = useLearningStore();
    learning.field.crops = [
      crop('PY43-KC-008'),
      crop('PY43-KC-009'),
      crop('PY43-KC-010'),
    ];
    const selectKcSpy = vi.spyOn(learning, 'selectKc');

    const { wrapper, router } = await mountPanel('/world?foo=bar');
    await wrapper
      .find('[data-testid="chapter-nav-variable_assignment"]')
      .trigger('click');
    await flushPromises();

    expect(selectKcSpy).toHaveBeenCalledWith('PY43-KC-008');
    expect(audioMocks.playSfx).toHaveBeenCalledWith('sfx_click');
    expect(router.currentRoute.value.path).toBe('/world');
    expect(router.currentRoute.value.query.kc).toBe('PY43-KC-008');
    // 保留其它已有 query 参数
    expect(router.currentRoute.value.query.foo).toBe('bar');
  });

  it('crops 为空时回退到 range 起点的占位 KC（PY43-KC-NNN）', async () => {
    const learning = useLearningStore();
    learning.field.crops = [];
    const selectKcSpy = vi.spyOn(learning, 'selectKc');

    const { wrapper, router } = await mountPanel();
    await wrapper
      .find('[data-testid="chapter-nav-function_def_args_return"]')
      .trigger('click');
    await flushPromises();

    expect(selectKcSpy).toHaveBeenCalledWith('PY43-KC-054');
    expect(router.currentRoute.value.query.kc).toBe('PY43-KC-054');
  });

  it('hover 100ms 节流：连续 2 次 pointerenter 只触发 1 次 sfx_hover', async () => {
    vi.useFakeTimers();
    const baseNow = 10_000;
    vi.spyOn(performance, 'now').mockImplementation(() => baseNow);

    const { wrapper } = await mountPanel();
    const firstBtn = wrapper.find(
      '[data-testid="chapter-nav-chapter_basics"]',
    );

    await firstBtn.trigger('pointerenter');
    await firstBtn.trigger('pointerenter');

    const hoverCalls = audioMocks.playSfx.mock.calls.filter(
      (call) => call[0] === 'sfx_hover',
    );
    expect(hoverCalls).toHaveLength(1);
    expect(hoverCalls[0]).toEqual(['sfx_hover', { volume: 0.15 }]);

    // 超过 100ms 后再次 hover 应允许第二次播放
    (performance.now as unknown as ReturnType<typeof vi.fn>).mockImplementation(
      () => baseNow + 150,
    );
    await firstBtn.trigger('pointerenter');
    const hoverCallsAfter = audioMocks.playSfx.mock.calls.filter(
      (call) => call[0] === 'sfx_hover',
    );
    expect(hoverCallsAfter).toHaveLength(2);
  });

  it('当 field.activeKcCode 落入章节范围时，对应按钮带 active class', async () => {
    const learning = useLearningStore();
    // PY43-KC-040 ∈ control_flow_if_else (range 39~47)
    learning.field.activeKcCode = 'PY43-KC-040';

    const { wrapper } = await mountPanel();

    const activeBtn = wrapper.find(
      '[data-testid="chapter-nav-control_flow_if_else"]',
    );
    expect(activeBtn.classes()).toContain('chapter-btn--active');

    // 其它章节不应带 active class
    const otherBtn = wrapper.find('[data-testid="chapter-nav-chapter_basics"]');
    expect(otherBtn.classes()).not.toContain('chapter-btn--active');

    // 切换 active KC 后，active class 应转移
    learning.field.activeKcCode = 'PY43-KC-003';
    await flushPromises();
    expect(
      wrapper
        .find('[data-testid="chapter-nav-chapter_basics"]')
        .classes(),
    ).toContain('chapter-btn--active');
    expect(
      wrapper
        .find('[data-testid="chapter-nav-control_flow_if_else"]')
        .classes(),
    ).not.toContain('chapter-btn--active');
  });
});
