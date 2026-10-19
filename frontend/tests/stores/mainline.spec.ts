import { setActivePinia, createPinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  chapterNodeState,
  chapterStageProgress,
  clampStars,
  isStageComplete,
  useMainlineStore,
} from '@stores/mainline';
import type {
  MainlineChapterDto,
  MainlineOverviewDto,
  MainlineStageDto,
  StageProgressDto,
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
    kcCodes: [],
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
        stages: [
          stage({ id: 31, index: 1, title: 's1', videoWatchedSec: 120, pagesRead: 1, examplesDone: 0, completed: false }),
          stage({ id: 32, index: 2, title: 's2', videoWatchedSec: 240, pagesRead: 2, examplesDone: 2, completed: true }),
        ],
      }),
      chapter({ chapter: 4, title: '流程自动化', unlocked: false, stars: 0 }),
    ],
  };
}

describe('mainline pure helpers', () => {
  it('chapterNodeState maps unlocked/stars to three states', () => {
    expect(chapterNodeState({ unlocked: false, stars: 0 })).toBe('locked');
    expect(chapterNodeState({ unlocked: true, stars: 0 })).toBe('in-progress');
    expect(chapterNodeState({ unlocked: true, stars: 1 })).toBe('cleared');
    expect(chapterNodeState({ unlocked: true, stars: 3 })).toBe('cleared');
  });

  it('clampStars clamps to 0..max and floors', () => {
    expect(clampStars(-2)).toBe(0);
    expect(clampStars(2.9)).toBe(2);
    expect(clampStars(9)).toBe(3);
    expect(clampStars(Number.NaN)).toBe(0);
    expect(clampStars(5, 5)).toBe(5);
  });

  it('isStageComplete 只信后端 completed（本地三计数达标但客观题未全对时不得显示完成）', () => {
    expect(isStageComplete(stage({ completed: true }))).toBe(true);
    // 三个计数全达标但服务端判未完成（客观题维度 overview 不下发）→ 仍未完成。
    expect(
      isStageComplete(
        stage({ videoWatchedSec: 240, pagesRead: 2, examplesDone: 2, completed: false }),
      ),
    ).toBe(false);
    expect(
      isStageComplete(
        stage({ videoWatchedSec: 120, pagesRead: 2, examplesDone: 2, completed: false }),
      ),
    ).toBe(false);
  });

  it('chapterStageProgress counts completed stages', () => {
    const ch = makeOverview().chapters[2]!;
    expect(chapterStageProgress(ch)).toEqual({ done: 1, total: 2 });
  });
});

describe('mainline store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mocks.getMainlineOverview.mockReset();
    mocks.postVideoProgress.mockReset();
    mocks.postPageRead.mockReset();
    mocks.postExampleDone.mockReset();
    mocks.postEndlessSummary.mockReset();
    mocks.postRecoveryErrorbook.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('load() fetches overview, fills state and defaults selectedChapter', async () => {
    mocks.getMainlineOverview.mockResolvedValue(makeOverview());
    const store = useMainlineStore();
    await store.load();

    expect(mocks.getMainlineOverview).toHaveBeenCalledTimes(1);
    expect(store.isLoaded).toBe(true);
    expect(store.totalStars).toBe(4);
    expect(store.chapters).toHaveLength(4);
    // firstActionableChapter = 第一个未满星的已解锁章 → 第 2 章（stars 1）。
    expect(store.selectedChapter).toBe(2);
    expect(store.error).toBeNull();
  });

  it('load() does not refetch when already loaded unless forced', async () => {
    mocks.getMainlineOverview.mockResolvedValue(makeOverview());
    const store = useMainlineStore();
    await store.load();
    await store.load();
    expect(mocks.getMainlineOverview).toHaveBeenCalledTimes(1);
    await store.load(true);
    expect(mocks.getMainlineOverview).toHaveBeenCalledTimes(2);
  });

  it('nodeStateOf / chapterByNumber reflect overview', async () => {
    mocks.getMainlineOverview.mockResolvedValue(makeOverview());
    const store = useMainlineStore();
    await store.load();
    expect(store.nodeStateOf(1)).toBe('cleared');
    expect(store.nodeStateOf(2)).toBe('cleared');
    expect(store.nodeStateOf(3)).toBe('in-progress');
    expect(store.nodeStateOf(4)).toBe('locked');
    expect(store.chapterByNumber(3)?.title).toBe('信息表示');
    expect(store.chapterByNumber(99)).toBeNull();
  });

  it('load() error path keeps overview null and records message', async () => {
    mocks.getMainlineOverview.mockRejectedValue(new Error('boom'));
    const store = useMainlineStore();
    await store.load();
    expect(store.isLoaded).toBe(false);
    expect(store.error).toBe('boom');
    expect(store.loading).toBe(false);
  });

  it('reportVideoProgress patches the matching stage in place', async () => {
    mocks.getMainlineOverview.mockResolvedValue(makeOverview());
    const store = useMainlineStore();
    await store.load();

    const progress: StageProgressDto = {
      stageId: 31,
      chapter: 3,
      index: 1,
      title: 's1',
      videoWatchedSec: 240,
      requiredVideoSec: 240,
      pagesRead: 1,
      requiredPages: 2,
      examplesDone: 0,
      requiredExamples: 2,
      completed: false,
    };
    mocks.postVideoProgress.mockResolvedValue(progress);

    const returned = await store.reportVideoProgress(31, 120);
    expect(mocks.postVideoProgress).toHaveBeenCalledWith(31, 120);
    expect(returned).toEqual(progress);
    const patched = store.chapterByNumber(3)?.stages.find((s) => s.id === 31);
    expect(patched?.videoWatchedSec).toBe(240);
    // 其它 stage 不受影响。
    expect(store.chapterByNumber(3)?.stages.find((s) => s.id === 32)?.completed).toBe(true);
  });

  it('submitEndlessSummary reloads overview when a star is awarded', async () => {
    mocks.getMainlineOverview.mockResolvedValue(makeOverview());
    mocks.postEndlessSummary.mockResolvedValue({ starAwarded: true });
    const store = useMainlineStore();
    await store.load();
    expect(mocks.getMainlineOverview).toHaveBeenCalledTimes(1);

    const request = { chapter: 3, runId: '123e4567-e89b-12d3-a456-426614174000' };
    const awarded = await store.submitEndlessSummary(request);
    expect(awarded).toBe(true);
    expect(mocks.postEndlessSummary).toHaveBeenCalledWith(request);
    // 发放补星 → reload（再拉一次 overview）。
    expect(mocks.getMainlineOverview).toHaveBeenCalledTimes(2);
  });

  it('claimErrorbookRecovery does not reload when no star awarded', async () => {
    mocks.getMainlineOverview.mockResolvedValue(makeOverview());
    mocks.postRecoveryErrorbook.mockResolvedValue({ starAwarded: false });
    const store = useMainlineStore();
    await store.load();

    const awarded = await store.claimErrorbookRecovery(3);
    expect(awarded).toBe(false);
    expect(mocks.postRecoveryErrorbook).toHaveBeenCalledWith(3);
    expect(mocks.getMainlineOverview).toHaveBeenCalledTimes(1);
  });
});
