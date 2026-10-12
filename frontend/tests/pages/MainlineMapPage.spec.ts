import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory, createRouter, type Router } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MainlineMapPage from '@/pages/MainlineMapPage.vue';
import type {
  MainlineChapterDto,
  MainlineOverviewDto,
  MainlineStageDto,
} from '@api/mainline';

const mocks = vi.hoisted(() => ({
  getMainlineOverview: vi.fn(),
  postVideoProgress: vi.fn(),
  postPageRead: vi.fn(),
  postExampleDone: vi.fn(),
  postEndlessSummary: vi.fn(),
  postRecoveryErrorbook: vi.fn(),
}));

vi.mock('@api/mainline', () => mocks);

function stage(overrides: Partial<MainlineStageDto> = {}): MainlineStageDto {
  return {
    id: 1,
    index: 1,
    title: 's',
    videoWatchedSec: 0,
    requiredVideoSec: 240,
    pagesRead: 0,
    requiredPages: 2,
    examplesDone: 0,
    requiredExamples: 2,
    completed: false,
    ...overrides,
  };
}

function chapter(overrides: Partial<MainlineChapterDto> = {}): MainlineChapterDto {
  return {
    chapter: 1,
    title: '计算思维',
    unlocked: true,
    stars: 0,
    bestTestScore: null,
    stages: [],
    testStatus: 'NONE',
    starBreakdown: { test: 0, stage: 0, mastery: 0, recovered: [] },
    recovery: { errorbookDone: false, endlessDone: false },
    ...overrides,
  };
}

function makeOverview(): MainlineOverviewDto {
  return {
    totalStars: 4,
    chapters: [
      chapter({ chapter: 1, title: '计算思维', unlocked: true, stars: 3 }),
      chapter({ chapter: 2, title: '语言基础', unlocked: true, stars: 1 }),
      chapter({
        chapter: 3,
        title: '信息表示',
        unlocked: true,
        stars: 0,
        stages: [stage({ id: 31, index: 1, title: '数字与进制', videoWatchedSec: 120 })],
      }),
      chapter({ chapter: 4, title: '流程自动化', unlocked: false, stars: 0 }),
    ],
  };
}

function setupRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/mainline-map', component: MainlineMapPage },
      { path: '/world', component: { template: '<div data-testid="world-stub" />' } },
      { path: '/endless', component: { template: '<div data-testid="endless-stub" />' } },
      { path: '/expedition', component: { template: '<div data-testid="expedition-stub" />' } },
      { path: '/bug-mine', component: { template: '<div data-testid="bugmine-stub" />' } },
      { path: '/mainline/stage/:stageId', component: { template: '<div data-testid="stage-stub" />' } },
      { path: '/chapter-test/:chapter', component: { template: '<div data-testid="test-stub" />' } },
      { path: '/boss/:chapter', component: { template: '<div data-testid="boss-stub" />' } },
      { path: '/graduation', component: { template: '<div data-testid="graduation-stub" />' } },
    ],
  });
}

let wrapper: VueWrapper | null = null;

async function mountPage(): Promise<{ wrapper: VueWrapper; router: Router }> {
  const router = setupRouter();
  await router.push('/mainline-map');
  await router.isReady();
  const w = mount(MainlineMapPage, {
    attachTo: document.body,
    global: { plugins: [router] },
  });
  await flushPromises();
  await flushPromises();
  wrapper = w;
  return { wrapper: w, router };
}

describe('MainlineMapPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.getMainlineOverview.mockReset();
    mocks.getMainlineOverview.mockResolvedValue(makeOverview());
    mocks.postRecoveryErrorbook.mockReset();
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    vi.restoreAllMocks();
  });

  it('loads overview once and renders total stars + all chapter nodes with states', async () => {
    const { wrapper: w } = await mountPage();
    expect(mocks.getMainlineOverview).toHaveBeenCalledTimes(1);
    expect(w.get('[data-testid="mainline-total-stars"]').text()).toContain('4');

    expect(w.findAll('[data-testid^="mainline-node-btn-"]')).toHaveLength(4);
    expect(w.get('[data-testid="mainline-node-1"]').attributes('data-state')).toBe('cleared');
    expect(w.get('[data-testid="mainline-node-3"]').attributes('data-state')).toBe('in-progress');
    expect(w.get('[data-testid="mainline-node-4"]').attributes('data-state')).toBe('locked');
  });

  it('defaults selection to the first not-full unlocked chapter and shows its stage panel', async () => {
    const { wrapper: w } = await mountPage();
    // firstActionableChapter → 第 2 章（已解锁、stars 1 < 3）。
    expect(w.get('[data-testid="mainline-chapter-title"]').text()).toContain('语言基础');
    expect(w.get('[data-testid="mainline-node-2"]').classes()).toContain('node--selected');
  });

  it('clicking a node updates the stage panel to that chapter', async () => {
    const { wrapper: w } = await mountPage();
    await w.get('[data-testid="mainline-node-btn-3"]').trigger('click');
    await flushPromises();
    expect(w.get('[data-testid="mainline-chapter-title"]').text()).toContain('信息表示');
    expect(w.find('[data-testid="mainline-stage-31"]').exists()).toBe(true);
  });

  it('back button navigates to /world', async () => {
    const { wrapper: w, router } = await mountPage();
    await w.get('[data-testid="mainline-back"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/world');
  });

  it('entering a stage routes to /mainline/stage/:stageId', async () => {
    const { wrapper: w, router } = await mountPage();
    await w.get('[data-testid="mainline-node-btn-3"]').trigger('click');
    await flushPromises();
    await w.get('[data-testid="mainline-enter-stage-31"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/mainline/stage/31');
  });

  it('keeps chapter final test locked until all stages are complete', async () => {
    const { wrapper: w, router } = await mountPage();
    await w.get('[data-testid="mainline-node-btn-3"]').trigger('click');
    await flushPromises();

    const button = w.get('[data-testid="mainline-open-test"]');
    expect(button.attributes('disabled')).toBeDefined();
    await button.trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/mainline-map');
  });

  it('routes to chapter final test after all stages are complete', async () => {
    mocks.getMainlineOverview.mockResolvedValueOnce({
      totalStars: 0,
      chapters: [
        chapter({
          chapter: 1,
          title: '计算思维',
          unlocked: true,
          stars: 0,
          stages: [stage({ id: 11, completed: true, videoWatchedSec: 240, pagesRead: 2, examplesDone: 2 })],
        }),
      ],
    });
    const { wrapper: w, router } = await mountPage();

    const button = w.get('[data-testid="mainline-open-test"]');
    expect(button.attributes('disabled')).toBeUndefined();
    await button.trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.path).toBe('/chapter-test/1');
  });

  it('keeps drawer pointerup events from reaching the Phaser window listener', async () => {
    const { wrapper: w } = await mountPage();
    await w.get('[data-testid="mainline-node-btn-3"]').trigger('click');
    await flushPromises();

    const phaserPointerUp = vi.fn();
    window.addEventListener('pointerup', phaserPointerUp);
    try {
      await w.get('[data-testid="mainline-enter-stage-31"]').trigger('pointerup');
      expect(phaserPointerUp).not.toHaveBeenCalled();
    } finally {
      window.removeEventListener('pointerup', phaserPointerUp);
    }
  });

  it('章末 BOSS 入口跳 /boss/:chapter（仅 ≥1★ 的章节渲染）', async () => {
    const { wrapper: w, router } = await mountPage();
    // 默认选中第 2 章（1★）→ 有 BOSS 入口。
    await w.get('[data-testid="mainline-open-boss"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/boss/2');
  });

  it('opens the verified expedition with the selected chapter for recovery stars', async () => {
    const { wrapper: w, router } = await mountPage();

    await w.get('[data-testid="mainline-open-endless"]').trigger('click');
    await flushPromises();

    expect(router.currentRoute.value.path).toBe('/expedition');
    expect(router.currentRoute.value.query.focus).toBe('2');
  });

  it('未毕业时不渲染毕业典礼入口；七章全部 ≥1★ 时给出显式入口', async () => {
    const { wrapper: w } = await mountPage();
    expect(w.find('[data-testid="mainline-graduation"]').exists()).toBe(false);
    w.unmount();
    wrapper = null;

    mocks.getMainlineOverview.mockReset();
    mocks.getMainlineOverview.mockResolvedValue({
      totalStars: 7,
      chapters: [1, 2, 3, 4, 5, 6, 7].map((c) =>
        chapter({ chapter: c, title: `第${c}章`, unlocked: true, stars: 1 }),
      ),
    });
    const { wrapper: w2, router } = await mountPage();
    expect(w2.find('[data-testid="mainline-graduation"]').exists()).toBe(true);
    await w2.get('[data-testid="mainline-graduation"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/graduation');
  });

  it('连点「错题复盘补星」只发一次申领（防两条互斥提示同屏）', async () => {
    let resolveClaim: ((v: { starAwarded: boolean }) => void) | null = null;
    mocks.postRecoveryErrorbook.mockImplementation(
      () => new Promise<{ starAwarded: boolean }>((resolve) => { resolveClaim = resolve; }),
    );
    const { wrapper: w } = await mountPage();
    await w.get('[data-testid="mainline-node-btn-3"]').trigger('click');
    await flushPromises();

    const button = w.get('[data-testid="mainline-claim-errorbook"]');
    await button.trigger('click');
    await flushPromises();
    expect(button.attributes('disabled')).toBeDefined();
    await button.trigger('click');
    await flushPromises();

    expect(mocks.postRecoveryErrorbook).toHaveBeenCalledTimes(1);
    resolveClaim?.({ starAwarded: false });
    await flushPromises();
  });

  it('shows an error state with retry when overview load fails', async () => {
    mocks.getMainlineOverview.mockReset();
    mocks.getMainlineOverview.mockRejectedValueOnce(new Error('网络炸了'));
    const { wrapper: w } = await mountPage();
    expect(w.get('[data-testid="mainline-error"]').text()).toContain('网络炸了');

    mocks.getMainlineOverview.mockResolvedValueOnce(makeOverview());
    await w.get('[data-testid="mainline-retry"]').trigger('click');
    await flushPromises();
    await flushPromises();
    expect(w.find('[data-testid="mainline-error"]').exists()).toBe(false);
    expect(w.get('[data-testid="mainline-total-stars"]').text()).toContain('4');
  });
});
