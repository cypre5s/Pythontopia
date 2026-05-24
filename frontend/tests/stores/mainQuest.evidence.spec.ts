/**
 * R-13 §-1 critical fix spec：mainQuest store 5 个 onXxx evidence 钩子。
 *
 * 修复前：completeSubQuest 无任何外部调用，主线 35 任务的 evidence 触发链路全断 —
 * 玩家与 NPC 对话、用工坊生成资源、提交代码后任务永远卡住。
 *
 * 本 spec 覆盖 5 个钩子（onDialogClose / onResourceCreated / onSubmissionPassed /
 * onMasteryReached / onBuildingVisited）的边界 + 正常路径。
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useMainQuestStore } from '@/stores/mainQuest';

describe('useMainQuestStore evidence hooks (R-13 §-1 critical fix)', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem('pyt_main_quest_progress_v1');
    }
  });

  it('onDialogClose: 与 mayor_py 对话完成 → 1-1 自动 markComplete', () => {
    const mq = useMainQuestStore();
    expect(mq.progress.completedSubQuests).toEqual([]);
    const completed = mq.onDialogClose('mayor_py');
    expect(completed?.id).toBe('1-1');
    expect(mq.progress.completedSubQuests).toContain('1-1');
    expect(mq.progress.currentSubQuest).toBe(2);
  });

  it('onDialogClose: 与不相关 NPC 对话 → 不改变进度', () => {
    const mq = useMainQuestStore();
    expect(mq.onDialogClose('unknown_npc')).toBeNull();
    expect(mq.progress.completedSubQuests).toEqual([]);
  });

  it('onDialogClose: 已完成的任务不会重复触发', () => {
    const mq = useMainQuestStore();
    mq.onDialogClose('mayor_py');
    const second = mq.onDialogClose('mayor_py');
    expect(second).toBeNull();
    expect(mq.progress.completedSubQuests.filter((id) => id === '1-1')).toHaveLength(1);
  });

  it('onResourceCreated: STORYBOARD + PY43-KC-002 → 1-2 markComplete', () => {
    const mq = useMainQuestStore();
    // 先解锁 1-1 让 1-2 成为当前
    mq.onDialogClose('mayor_py');
    const completed = mq.onResourceCreated('STORYBOARD', ['PY43-KC-002']);
    expect(completed?.id).toBe('1-2');
    expect(mq.progress.completedSubQuests).toContain('1-2');
  });

  it('onResourceCreated: resourceType 不匹配 → 不触发', () => {
    const mq = useMainQuestStore();
    expect(mq.onResourceCreated('PRACTICE', ['PY43-KC-002'])).toBeNull();
  });

  it('onResourceCreated: KC 不重合 → 不触发', () => {
    const mq = useMainQuestStore();
    expect(mq.onResourceCreated('STORYBOARD', ['PY43-KC-099'])).toBeNull();
  });

  it('onSubmissionPassed: problemId=0 placeholder 命中 KC → 完成对应任务', () => {
    const mq = useMainQuestStore();
    const completed = mq.onSubmissionPassed({ kcCode: 'PY43-KC-006' });
    expect(completed?.id).toBe('1-4');
  });

  it('onMasteryReached: KC + 阈值 → 完成对应任务', () => {
    const mq = useMainQuestStore();
    // 1-5 需要 PY43-KC-007 任意，2-5 需要 PY43-KC-016 ≥ 40
    const completed = mq.onMasteryReached('PY43-KC-016', 45);
    expect(completed?.id).toBe('2-5');
  });

  it('onMasteryReached: 阈值不够 → 不触发', () => {
    const mq = useMainQuestStore();
    expect(mq.onMasteryReached('PY43-KC-016', 20)).toBeNull();
  });

  it('onBuildingVisited: 走到 campus_east (12, 15) ±1 → 完成 4-1', () => {
    const mq = useMainQuestStore();
    const completed = mq.onBuildingVisited('campus_east', 12, 15);
    expect(completed?.id).toBe('4-1');
  });

  it('onBuildingVisited: 走偏 → 不触发', () => {
    const mq = useMainQuestStore();
    expect(mq.onBuildingVisited('campus_east', 20, 20)).toBeNull();
  });

  it('persist 持久化：evidence 完成后跨 store 实例可读', () => {
    const mq = useMainQuestStore();
    mq.onDialogClose('mayor_py');
    expect(window.localStorage.getItem('pyt_main_quest_progress_v1')).toContain('1-1');
  });
});
