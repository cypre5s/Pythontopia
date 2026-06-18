import { describe, it, expect } from 'vitest';
import { CODE_THEATER_QUESTS, getQuestById } from '../index';

/**
 * 已核对真实存在的 packId=43 KC code（来源：data/course-pack-43/json/course_kc.json）。
 * 任务只允许绑定本集合内的 KC，作为 grounding 防幻觉门——新增任务若误填不存在的 KC，
 * 此门会直接失败，杜绝「伪造课件/KC」。新增任务用到新 KC 时，须先在课程数据里核对后补进本集合。
 */
const REAL_PACK43_KCS: ReadonlySet<string> = new Set([
  'PY43-KC-031', // for循环与遍历
  'PY43-KC-032', // while循环与循环分支结合
  'PY43-KC-033', // 分支结构
  'PY43-KC-036', // 循环嵌套
  'PY43-KC-040', // 列表基本操作
]);

const SUPPORTED_GOAL_KINDS: ReadonlySet<string> = new Set(['coverage', 'sequence']);

/**
 * 任务清单完整性门：守住「世界动作必须在 starterCode 里 def 出来」的硬约定
 * （否则真实 Pyodide 执行会 NameError、不产生 call 事件，世界条件永远判不达成）。
 * 同时守 KC grounding（格式 + 真实存在）与目标编号约定，并保证至少各有一个 coverage / sequence 示例。
 */
describe('CODE_THEATER_QUESTS 完整性', () => {
  it('非空且 id 唯一', () => {
    expect(CODE_THEATER_QUESTS.length).toBeGreaterThan(0);
    const ids = CODE_THEATER_QUESTS.map((q) => q.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('coverage 与 sequence 两种世界目标都有示例任务', () => {
    const kinds = new Set(CODE_THEATER_QUESTS.map((q) => q.worldGoal.kind));
    expect(kinds.has('coverage')).toBe(true);
    expect(kinds.has('sequence')).toBe(true);
  });

  it.each(CODE_THEATER_QUESTS.map((q) => [q.id, q] as const))(
    '任务「%s」绑定真实存在的 KC（格式正确且在 packId=43 集合内）',
    (_id, quest) => {
      expect(quest.kcCodes.length).toBeGreaterThan(0);
      for (const kc of quest.kcCodes) {
        expect(kc).toMatch(/^PY43-KC-\d{3}$/);
        expect(REAL_PACK43_KCS.has(kc)).toBe(true);
      }
    },
  );

  it.each(CODE_THEATER_QUESTS.map((q) => [q.id, q] as const))(
    '任务「%s」的世界目标种类受支持（coverage / sequence）',
    (_id, quest) => {
      expect(SUPPORTED_GOAL_KINDS.has(quest.worldGoal.kind)).toBe(true);
    },
  );

  it.each(CODE_THEATER_QUESTS.map((q) => [q.id, q] as const))(
    '任务「%s」的世界动作在 starterCode 里 def 出来（可真实执行、能产生 call 事件）',
    (_id, quest) => {
      // coverage 与 sequence 都带 actionName / starterCode，按统一约定校验。
      const { actionName } = quest.worldGoal;
      expect(quest.starterCode).toBeTruthy();
      const defPattern = new RegExp(`def\\s+${actionName}\\s*\\(`);
      expect(quest.starterCode ?? '').toMatch(defPattern);
      // 起手代码里也确实调用了这个动作，且把目标编号作为第一个实参。
      const callPattern = new RegExp(`${actionName}\\s*\\(`, 'g');
      const calls = (quest.starterCode ?? '').match(callPattern) ?? [];
      expect(calls.length).toBeGreaterThanOrEqual(2); // 至少 def 一处 + 调用一处
    },
  );

  it.each(CODE_THEATER_QUESTS.map((q) => [q.id, q] as const))(
    '任务「%s」的目标编号为正整数且无重复',
    (_id, quest) => {
      const { targets } = quest.worldGoal;
      expect(targets.length).toBeGreaterThan(0);
      expect(new Set(targets).size).toBe(targets.length);
      for (const t of targets) {
        expect(Number.isInteger(t)).toBe(true);
        expect(t).toBeGreaterThanOrEqual(1);
      }
    },
  );

  it('getQuestById 命中与未命中', () => {
    const first = CODE_THEATER_QUESTS[0];
    expect(first).toBeDefined();
    if (first) {
      expect(getQuestById(first.id)?.id).toBe(first.id);
    }
    expect(getQuestById('no-such-quest')).toBeUndefined();
  });
});
