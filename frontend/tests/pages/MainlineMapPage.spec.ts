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
      { path: '/bug-mine', component: { template: '<div data-testid="bugmine-stub" />' } },
      { path: '/mainline/stage/:stageId', component: { template: '<div data-testid="stage-stub" />' } },
      { path: '/chapter-test/:chapter', component: { template: '<div data-testid="test-stub" />' } },
    ],
  });
}

let wrapper: VueWrapper | null = null;

async function mountPage(): Promise<{ wrapper: VueWrapper; router: Router }> {
  const router = setupRouter();
  await router.push('/mainline-map');
  await router.isReady();
  const w = mount(MainlineMapPage, { global: { plugins: [router] } });
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
