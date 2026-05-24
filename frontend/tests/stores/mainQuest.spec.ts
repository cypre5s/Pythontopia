import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useMainQuestStore } from '@stores/mainQuest';
import { mainQuestEmptyProgress } from '@/types/mainQuest';

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
