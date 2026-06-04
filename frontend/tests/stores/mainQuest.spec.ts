import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';

const saveSlotApiMock = vi.hoisted(() => ({
  listSaveSlots: vi.fn(),
  createSaveSlot: vi.fn(),
  activateSaveSlot: vi.fn(),
  saveSlotSnapshot: vi.fn(),
}));
vi.mock('@/api/saveSlot', () => saveSlotApiMock);

import { useMainQuestStore, __resetSnapshotSyncForTests } from '@stores/mainQuest';
import { useSessionStore } from '@stores/session';
import { useWorldStore } from '@stores/world';
import { mainQuestEmptyProgress } from '@/types/mainQuest';

// 每个用例后清掉挂起的 snapshot 同步计时器并恢复真实计时器，避免跨用例泄漏。
afterEach(() => {
  __resetSnapshotSyncForTests();
  vi.useRealTimers();
});

describe('useMainQuestStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    window.localStorage.clear();
  });

  it('initial state defaults to chapter 1 / quest 1 (empty progress)', () => {
    const store = useMainQuestStore();
    expect(store.progress).toMatchObject(mainQuestEmptyProgress());
    expect(store.currentSubQuest?.id).toBe('1-1');
    expect(store.overallProgress).toBe(0);
  });

  it('togglePanel flips visible flag', () => {
    const store = useMainQuestStore();
    expect(store.panelOpen).toBe(false);
    store.togglePanel();
    expect(store.panelOpen).toBe(true);
    store.closePanel();
    expect(store.panelOpen).toBe(false);
    store.openPanel();
    expect(store.panelOpen).toBe(true);
  });

  it('chapterViews returns 7 chapters with 5 sub-quests each and boss meta', () => {
    const store = useMainQuestStore();
    const views = store.chapterViews;
    expect(views.length).toBe(7);
    for (let i = 0; i < 7; i += 1) {
      expect(views[i]?.subQuests.length).toBe(5);
      expect(views[i]?.boss.chapter).toBe(i + 1);
    }
  });

  it('completeSubQuest dedups and advances pointer within chapter', () => {
    const store = useMainQuestStore();
    expect(store.progress.currentSubQuest).toBe(1);
    store.completeSubQuest('1-1');
    expect(store.progress.completedSubQuests).toEqual(['1-1']);
    expect(store.progress.currentSubQuest).toBe(2);
    // 二次调用幂等
    store.completeSubQuest('1-1');
    expect(store.progress.completedSubQuests).toEqual(['1-1']);
    expect(store.progress.currentSubQuest).toBe(2);
  });

  it('completeSubQuest at index 5 caps pointer (waits boss)', () => {
    const store = useMainQuestStore();
    for (let idx = 1; idx <= 5; idx += 1) {
      store.completeSubQuest(`1-${idx}`);
    }
    expect(store.progress.currentSubQuest).toBe(5);
    expect(store.progress.completedSubQuests.length).toBe(5);
  });

  it('isCompleted / isBossDefeated getters reflect store state', () => {
    const store = useMainQuestStore();
    expect(store.isCompleted('1-1')).toBe(false);
    expect(store.isBossDefeated(1)).toBe(false);
    store.completeSubQuest('1-1');
    store.defeatBoss(1);
    expect(store.isCompleted('1-1')).toBe(true);
    expect(store.isBossDefeated(1)).toBe(true);
  });

  it('defeatBoss bumps chapter and unlocks milestone rewards', () => {
    const store = useMainQuestStore();
    const boss = store.defeatBoss(1);
    // R-13 mainQuests v2：boss 名按真课件章节命名（算盘灵 · 计算工具守卫）
    expect(boss?.name).toContain('算盘灵');
    expect(store.progress.currentChapter).toBe(2);
    expect(store.progress.currentSubQuest).toBe(1);
    expect(store.progress.unlockedMilestones).toContain('outfit:apprentice');
  });

  it('defeatBoss for chapter 7 caps chapter at 7 / quest 5', () => {
    const store = useMainQuestStore();
    store.progress.currentChapter = 7;
    store.progress.currentSubQuest = 5;
    store.defeatBoss(7);
    expect(store.progress.currentChapter).toBe(7);
    expect(store.progress.currentSubQuest).toBe(5);
    expect(store.progress.defeatedBosses).toContain(7);
  });

  it('recordAiProblem keeps newest 30 entries', () => {
    const store = useMainQuestStore();
    for (let i = 0; i < 35; i += 1) {
      store.recordAiProblem({
        problemId: `p${i}`,
        kc: 'PY43-KC-001',
        difficulty: 2,
        succeededInSec: 30,
        timestamp: new Date().toISOString(),
      });
    }
    expect(store.progress.recentAiProblems.length).toBe(30);
    expect(store.progress.recentAiProblems[0]?.problemId).toBe('p34');
    expect(store.progress.recentAiProblems[29]?.problemId).toBe('p5');
  });

  it('resetProgress clears all state to empty', () => {
    const store = useMainQuestStore();
    store.completeSubQuest('1-1');
    store.defeatBoss(1);
    store.resetProgress();
    expect(store.progress).toMatchObject(mainQuestEmptyProgress());
  });

  it('persists progress to localStorage and re-reads on new store instance', () => {
    const first = useMainQuestStore();
    first.completeSubQuest('1-1');
    first.completeSubQuest('1-2');
    first.defeatBoss(1);

    // 模拟刷新：重新初始化 pinia
    setActivePinia(createPinia());
    const second = useMainQuestStore();
    expect(second.progress.completedSubQuests).toEqual(['1-1', '1-2']);
    expect(second.progress.defeatedBosses).toContain(1);
    expect(second.progress.currentChapter).toBe(2);
  });

  it('hydrateFromSnapshot accepts MainQuest progress nested object', () => {
    const store = useMainQuestStore();
    store.hydrateFromSnapshot({
      mainQuest: {
        currentChapter: 3,
        currentSubQuest: 4,
        completedSubQuests: ['1-1', '1-2', '2-1'],
        defeatedBosses: [1],
        unlockedMilestones: [],
        recentAiProblems: [],
      },
    });
    expect(store.progress.currentChapter).toBe(3);
    expect(store.progress.completedSubQuests.length).toBe(3);
  });

  it('hydrateFromSnapshot ignores invalid input gracefully', () => {
    const store = useMainQuestStore();
    const original = { ...store.progress };
    store.hydrateFromSnapshot('not-an-object');
    store.hydrateFromSnapshot(null);
    store.hydrateFromSnapshot({});
    expect(store.progress).toEqual(original);
  });

  // ── R-14 S6 风险修复：hydrate 为单调合并，离线先行进度不被后端旧快照覆盖 ──
  it('hydrateFromSnapshot 不回退：本地更靠前时保留本地进度（后端旧快照不覆盖）', () => {
    const store = useMainQuestStore();
    // 本地：离线打到第 2 章、已完成 1-1..1-5 + 击败 boss1
    for (let idx = 1; idx <= 5; idx += 1) store.completeSubQuest(`1-${idx}`);
    store.defeatBoss(1);
    expect(store.progress.currentChapter).toBe(2);

    // 后端旧快照：还停在第 1 章、只完成 1-1
    store.hydrateFromSnapshot({
      mainQuest: {
        currentChapter: 1,
        currentSubQuest: 2,
        completedSubQuests: ['1-1'],
        defeatedBosses: [],
        unlockedMilestones: [],
        recentAiProblems: [],
      },
    });

    expect(store.progress.currentChapter).toBe(2); // 不回退到 1
    expect(store.progress.defeatedBosses).toContain(1); // boss 不丢
    expect(store.progress.completedSubQuests).toEqual(
      expect.arrayContaining(['1-1', '1-2', '1-3', '1-4', '1-5']),
    );
  });

  it('hydrateFromSnapshot 取并集：本地与后端各有独立进度时合并不丢', () => {
    const store = useMainQuestStore();
    store.completeSubQuest('1-1'); // 本地独有

    store.hydrateFromSnapshot({
      mainQuest: {
        currentChapter: 1,
        currentSubQuest: 3,
        completedSubQuests: ['1-2'], // 后端独有
        defeatedBosses: [],
        unlockedMilestones: ['title:list_master'],
        recentAiProblems: [],
      },
    });

    expect(store.progress.completedSubQuests).toEqual(
      expect.arrayContaining(['1-1', '1-2']),
    );
    expect(store.progress.unlockedMilestones).toContain('title:list_master');
    expect(store.progress.currentSubQuest).toBe(3); // 同章取更靠前指针
  });

  it('hydrateFromSnapshot 全新设备：本地空则完整采用后端进度', () => {
    const store = useMainQuestStore();
    store.hydrateFromSnapshot({
      mainQuest: {
        currentChapter: 4,
        currentSubQuest: 2,
        completedSubQuests: ['1-1', '1-2', '2-1', '3-1'],
        defeatedBosses: [1, 2, 3],
        unlockedMilestones: ['outfit:apprentice'],
        recentAiProblems: [],
      },
    });
    expect(store.progress.currentChapter).toBe(4);
    expect(store.progress.defeatedBosses).toEqual([1, 2, 3]);
    expect(store.progress.completedSubQuests).toHaveLength(4);
  });

  it('queueBoss / consumePendingBoss flow', () => {
    const store = useMainQuestStore();
    expect(store.consumePendingBoss()).toBeNull();
    store.queueBoss(3);
    expect(store.pendingBossChapter).toBe(3);
    expect(store.consumePendingBoss()).toBe(3);
    expect(store.consumePendingBoss()).toBeNull();
  });

  it('chapterViews status: completed > in_progress > available > locked', () => {
    const store = useMainQuestStore();
    store.completeSubQuest('1-1');
    const ch1 = store.chapterViews[0]!;
    const oneOne = ch1.subQuests.find((s) => s.id === '1-1');
    const oneTwo = ch1.subQuests.find((s) => s.id === '1-2');
    const oneFive = ch1.subQuests.find((s) => s.id === '1-5');
    expect(oneOne?.status).toBe('completed');
    expect(oneTwo?.status).toBe('in_progress');
    expect(oneFive?.status).toBe('locked');
  });

  it('overallProgress is fraction of completedSubQuests over total 35', () => {
    const store = useMainQuestStore();
    expect(store.overallProgress).toBe(0);
    store.completeSubQuest('1-1');
    store.completeSubQuest('1-2');
    expect(store.overallProgress).toBeCloseTo(2 / 35, 6);
  });
});

// ───────────────── R-14 S6：后端存档槽同步（主线进度跨设备跟随） ─────────────────
function authenticate(): void {
  useSessionStore().$patch({
    token: 'demo-token',
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    user: { id: 1, username: 'demo', displayName: 'Demo', role: 'STUDENT' },
  });
}

function makeSlot(slotIndex: number) {
  return {
    slotIndex,
    displayName: '自动存档',
    isActive: true,
    createdAt: '2026-06-04T00:00:00Z',
    lastPlayedAt: '2026-06-04T00:00:00Z',
    snapshot: { version: 1, mainQuest: {} },
    sceneName: 'WorldScene',
    unlockedBuildings: [],
    accumulatedPlaySeconds: 0,
    inventoryItems: [],
  };
}

describe('useMainQuestStore · R-14 S6 后端存档同步', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    window.localStorage.clear();
    __resetSnapshotSyncForTests();
    saveSlotApiMock.createSaveSlot.mockReset();
    saveSlotApiMock.saveSlotSnapshot.mockReset();
    saveSlotApiMock.createSaveSlot.mockResolvedValue(makeSlot(0));
    saveSlotApiMock.saveSlotSnapshot.mockResolvedValue(makeSlot(0));
  });

  it('登录 + 有 active slot：mutation 经 debounce 写入该槽 snapshot', async () => {
    vi.useFakeTimers();
    authenticate();
    useWorldStore().activeSlot = makeSlot(0);
    const store = useMainQuestStore();

    store.completeSubQuest('1-1');
    expect(saveSlotApiMock.saveSlotSnapshot).not.toHaveBeenCalled(); // debounce 窗口内

    await vi.advanceTimersByTimeAsync(1100);
    expect(saveSlotApiMock.saveSlotSnapshot).toHaveBeenCalledTimes(1);
    const call = saveSlotApiMock.saveSlotSnapshot.mock.calls[0]!;
    expect(call[0]).toBe(0);
    expect(call[1]).toMatchObject({ version: 1, mainQuest: { completedSubQuests: ['1-1'] } });
    expect(saveSlotApiMock.createSaveSlot).not.toHaveBeenCalled();
  });

  it('连续多次 mutation 合并为一次 debounced 写入', async () => {
    vi.useFakeTimers();
    authenticate();
    useWorldStore().activeSlot = makeSlot(0);
    const store = useMainQuestStore();

    store.completeSubQuest('1-1');
    store.completeSubQuest('1-2');
    store.completeSubQuest('1-3');
    await vi.advanceTimersByTimeAsync(1100);

    expect(saveSlotApiMock.saveSlotSnapshot).toHaveBeenCalledTimes(1);
    expect(saveSlotApiMock.saveSlotSnapshot.mock.calls[0]![1]).toMatchObject({
      mainQuest: { completedSubQuests: ['1-1', '1-2', '1-3'] },
    });
  });

  it('无 active slot：自动创建 slot 0（自动存档）再写 snapshot', async () => {
    vi.useFakeTimers();
    authenticate();
    useWorldStore().activeSlot = null;
    const store = useMainQuestStore();

    store.defeatBoss(1);
    await vi.advanceTimersByTimeAsync(1100);

    expect(saveSlotApiMock.createSaveSlot).toHaveBeenCalledWith(0, '自动存档');
    expect(saveSlotApiMock.saveSlotSnapshot).toHaveBeenCalledTimes(1);
    expect(saveSlotApiMock.saveSlotSnapshot.mock.calls[0]![0]).toBe(0);
  });

  it('未登录：不写后端，仅 localStorage 兜底', async () => {
    vi.useFakeTimers();
    useWorldStore().activeSlot = makeSlot(0);
    const store = useMainQuestStore();

    store.completeSubQuest('1-1');
    await vi.advanceTimersByTimeAsync(1100);

    expect(saveSlotApiMock.saveSlotSnapshot).not.toHaveBeenCalled();
    expect(saveSlotApiMock.createSaveSlot).not.toHaveBeenCalled();
    expect(store.progress.completedSubQuests).toEqual(['1-1']);
  });

  it('hydrateFromSnapshot 回灌不触发反向写（无回声）', async () => {
    vi.useFakeTimers();
    authenticate();
    useWorldStore().activeSlot = makeSlot(0);
    const store = useMainQuestStore();

    store.hydrateFromSnapshot({
      mainQuest: {
        currentChapter: 3,
        currentSubQuest: 1,
        completedSubQuests: ['1-1', '1-2'],
        defeatedBosses: [1],
        unlockedMilestones: [],
        recentAiProblems: [],
      },
    });
    await vi.advanceTimersByTimeAsync(1100);

    expect(store.progress.currentChapter).toBe(3);
    expect(saveSlotApiMock.saveSlotSnapshot).not.toHaveBeenCalled();
  });
});
