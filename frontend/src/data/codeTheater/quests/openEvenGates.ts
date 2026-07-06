/**
 * 样板具身任务「只开双数号城门」——教 if 分支 + 取模判断（配合 for 遍历）。
 *
 * 教育锚点：packId=43 KC『PY43-KC-033 if条件分支』+『PY43-KC-031 for循环与遍历』。
 * 玩法：NPC（城门官）给出真实需求——城墙有 6 座城门，只有双数号是安全的，
 *   单数号后面是陷阱。学生在 `for gate in range(1, 7)` 里加 `if gate % 2 == 0:`，
 *   只对双数号调用世界动作 `unlock(gate)`。
 * 世界目标 = 可见条件「双数号城门都开、单数号一座没碰」（forbidExtra=true：
 *   开错任一单数号即判未达成），让「分支选对没选对」体现为世界现状。
 *
 * 世界动作 `unlock` 在 starterCode 里 `def` 出来（见 types.ts CoverageWorldGoal.actionName）。
 */

import type { CodeTheaterQuest } from './types';

/** 双数号城门（第 2、4、6 座）——必须且只能开这些。 */
const EVEN_GATE_TARGETS: readonly number[] = [2, 4, 6];

export const OPEN_EVEN_GATES_QUEST: CodeTheaterQuest = {
  id: 'open-even-gates',
  title: '只开双数号城门',
  kcCodes: ['PY43-KC-033', 'PY43-KC-031'],
  prompt:
    '【城门官】城墙一排 6 座城门（第 1~6 座）。只有"双数号"（第 2、4、6 座）是安全的，' +
    '单数号后面全是陷阱，一座都不能碰！动作 unlock(gate) 会打开第 gate 座城门。' +
    '现在的代码把 6 座全开了——加一个 if 判断，只在双数号时才 unlock。',
  starterCode: [
    '# unlock(gate)：让傀儡走到第 gate 座城门并打开它。',
    'def unlock(gate):',
    '    print(f"打开第 {gate} 座城门")',
    '',
    '# 6 座城门：第 1~6 座。只开双数号（第 2、4、6 座），单数号是陷阱！',
    '# 提示：用 if gate % 2 == 0 判断「双数号」，只在成立时才 unlock(gate)。',
    'for gate in range(1, 7):',
    '    unlock(gate)',
    '',
  ].join('\n'),
  worldGoal: {
    kind: 'coverage',
    targets: EVEN_GATE_TARGETS,
    actionName: 'unlock',
    actionVerb: '打开',
    targetNoun: '城门',
    unit: '座',
    forbidExtra: true,
  },
};
