/**
 * mainQuest store evidence 钩子（单一事实源版）。
 *
 * 重写后语义：5 个 onXxx 钩子只完成「当前 activeQuest」（=当前章第一个未完成子任务）：
 *   - 杜绝跨章预完成（旧实现遍历全部 35 任务命中即完成，使 bootstrap/刷新批量
 *     onMasteryReached 跨章乱序完成、整体进度虚高、与指针/左侧脱节）；
 *   - 杜绝章内乱序跳跃（只推进当前焦点任务，指引=进度=地图标记三者天然一致）。
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useMainQuestStore } from '@/stores/mainQuest';

type MainQuestStore = ReturnType<typeof useMainQuestStore>;

/** 顺序推进到指定章首（完成前置各章 5 任务 + 击败 boss），使该章 1 号任务成为 activeQuest。 */
function advanceToChapterStart(mq: MainQuestStore, chapter: number): void {
  for (let c = 1; c < chapter; c += 1) {
    for (let i = 1; i <= 5; i += 1) mq.completeSubQuest(`${c}-${i}`);
    mq.defeatBoss(c);
  }
}

describe('useMainQuestStore evidence hooks (单一事实源 · 仅完成 activeQuest)', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem('pyt_main_quest_progress_v1');
    }
  });

  it('onDialogClose: 与 mayor_py 对话 → 1-1（首个 activeQuest）自动 markComplete', () => {
    const mq = useMainQuestStore();
    expect(mq.progress.completedSubQuests).toEqual([]);
    const completed = mq.onDialogClose('mayor_py');
    expect(completed?.id).toBe('1-1');
    expect(mq.progress.completedSubQuests).toContain('1-1');
    expect(mq.activeQuest?.id).toBe('1-2');
  });

  it('onDialogClose: 与不相关 NPC 对话 → 不改变进度', () => {
    const mq = useMainQuestStore();
    expect(mq.onDialogClose('unknown_npc')).toBeNull();
    expect(mq.progress.completedSubQuests).toEqual([]);
  });

  it('onDialogClose: 已完成的任务不会重复触发', () => {
    const mq = useMainQuestStore();
    mq.onDialogClose('mayor_py');
    const again = mq.onDialogClose('mayor_py');
    expect(again).toBeNull();
    expect(mq.progress.completedSubQuests.filter((id) => id === '1-1')).toHaveLength(1);
  });

  it('onDialogClose: 1-1 完成后与 professor 对话 → 1-2', () => {
    const mq = useMainQuestStore();
    mq.onDialogClose('mayor_py');
    const completed = mq.onDialogClose('professor');
    expect(completed?.id).toBe('1-2');
  });

  it('章内乱序不跳跃：activeQuest=1-1 时 professor(1-2) 不会越过 1-1 完成', () => {
    const mq = useMainQuestStore();
    expect(mq.onDialogClose('professor')).toBeNull();
    expect(mq.progress.completedSubQuests).toEqual([]);
  });

  it('跨章不预完成：第 1 章时命中后续章 evidence 一律不触发', () => {
    const mq = useMainQuestStore();
    // 4-1 dialog(librarian_doc) / 2-3 submission(#476) / 2-5 mastery(KC-016)
    expect(mq.onDialogClose('librarian_doc')).toBeNull();
    expect(mq.onSubmissionPassed({ problemId: 476, kcCode: 'PY43-KC-016' })).toBeNull();
    expect(mq.onMasteryReached('PY43-KC-016', 45)).toBeNull();
    expect(mq.progress.completedSubQuests).toEqual([]);
  });

  it('onResourceCreated: 类型 / KC 不匹配当前 activeQuest（1-1 为 dialog）→ 不触发', () => {
    const mq = useMainQuestStore();
    expect(mq.onResourceCreated('PRACTICE', ['PY43-KC-002'])).toBeNull();
    expect(mq.onResourceCreated('STORYBOARD', ['PY43-KC-099'])).toBeNull();
  });

  it('onResourceCreated: HANDOUT + KC-003 → 1-3（当其为 activeQuest 时）', () => {
    const mq = useMainQuestStore();
    mq.onDialogClose('mayor_py'); // 1-1
    mq.onDialogClose('professor'); // 1-2 → activeQuest=1-3
    const completed = mq.onResourceCreated('HANDOUT', ['PY43-KC-003']);
    expect(completed?.id).toBe('1-3');
  });

  it('onResourceCreated: READING + KC-006 → 1-4（第 1 章学里程碑，无判题真题）', () => {
    const mq = useMainQuestStore();
    mq.onDialogClose('mayor_py');
    mq.onDialogClose('professor');
    mq.onResourceCreated('HANDOUT', ['PY43-KC-003']); // 1-3 → activeQuest=1-4
    const completed = mq.onResourceCreated('READING', ['PY43-KC-006']);
    expect(completed?.id).toBe('1-4');
  });

  it('onSubmissionPassed: 第 2 章 activeQuest=2-3 时命中 #476 → 完成 2-3', () => {
    const mq = useMainQuestStore();
    advanceToChapterStart(mq, 2);
    mq.completeSubQuest('2-1');
    mq.completeSubQuest('2-2'); // → activeQuest=2-3
    const completed = mq.onSubmissionPassed({ problemId: 476, kcCode: 'PY43-KC-016' });
    expect(completed?.id).toBe('2-3');
  });

  it('onMasteryReached: 第 2 章 activeQuest=2-5 时 KC-016≥40 → 完成 2-5', () => {
    const mq = useMainQuestStore();
    advanceToChapterStart(mq, 2);
    for (const i of [1, 2, 3, 4]) mq.completeSubQuest(`2-${i}`); // → activeQuest=2-5
    const completed = mq.onMasteryReached('PY43-KC-016', 45);
    expect(completed?.id).toBe('2-5');
  });

  it('onMasteryReached: 阈值不够 → 不触发', () => {
    const mq = useMainQuestStore();
    advanceToChapterStart(mq, 2);
    for (const i of [1, 2, 3, 4]) mq.completeSubQuest(`2-${i}`);
    expect(mq.onMasteryReached('PY43-KC-016', 20)).toBeNull();
  });

  it('onDialogClose: 第 4 章 activeQuest=4-1 时 librarian_doc → 完成 4-1（顺序结构课）', () => {
    const mq = useMainQuestStore();
    advanceToChapterStart(mq, 4);
    const completed = mq.onDialogClose('librarian_doc');
    expect(completed?.id).toBe('4-1');
  });

  it('onDialogClose: 第 5 章 activeQuest=5-1 时 coach_q → 完成 5-1（船长 Def 改派教务 Q）', () => {
    const mq = useMainQuestStore();
    advanceToChapterStart(mq, 5);
    const completed = mq.onDialogClose('coach_q');
    expect(completed?.id).toBe('5-1');
  });

  it('全章完成、boss 待打：activeQuest=null，钩子一律不再前移', () => {
    const mq = useMainQuestStore();
    for (let i = 1; i <= 5; i += 1) mq.completeSubQuest(`1-${i}`);
    expect(mq.activeQuest).toBeNull();
    expect(mq.bossPending).toBe(true);
    expect(mq.onDialogClose('mayor_py')).toBeNull();
    expect(mq.onMasteryReached('PY43-KC-016', 99)).toBeNull();
  });

  it('onBuildingVisited: 当前非 building 任务 / 走偏 → 不触发', () => {
    const mq = useMainQuestStore();
    expect(mq.onBuildingVisited('town_hub', 20, 20)).toBeNull();
  });

  it('persist 持久化：evidence 完成后跨 store 实例可读', () => {
    const mq = useMainQuestStore();
    mq.onDialogClose('mayor_py');
    expect(window.localStorage.getItem('pyt_main_quest_progress_v1')).toContain('1-1');
  });
});
