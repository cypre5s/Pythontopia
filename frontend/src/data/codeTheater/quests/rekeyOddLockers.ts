/**
 * 具身任务「只给单数号储物柜换锁」——教 range 的步长 step（range(start, stop, step)）。
 *
 * 教育锚点：packId=43 KC『PY43-KC-031 for循环与遍历』
 *   （重点在 range() 的 step 参数与「左闭右开」停止边界）。
 * 玩法：NPC（锁匠）给出真实需求——只有单数号储物柜（第 1、3、5、7、9 号）对外出租、要全部换新锁；
 *   双数号是员工专用，一个都不能碰。学生用 `range(1, ?, 2)` 一次跨 2 个，专挑单数号调用 `relock(n)`。
 * 世界目标 = 可见条件「单数号全换、双数号没碰」（forbidExtra=true：碰了任一双数号即判未达成）。
 *   步长写对、停止边界（stop）也要够大才不会漏掉第 9 号——带 step 时尤其容易在末尾 off-by-one。
 *
 * 世界动作 `relock` 在 starterCode 里 `def` 出来（见 types.ts CoverageWorldGoal.actionName）。
 */

import type { CodeTheaterQuest } from './types';

/** 单数号储物柜（第 1、3、5、7、9 号）——必须且只能换这些。 */
const ODD_LOCKER_TARGETS: readonly number[] = [1, 3, 5, 7, 9];

export const REKEY_ODD_LOCKERS_QUEST: CodeTheaterQuest = {
  id: 'rekey-odd-lockers',
  title: '只给单数号储物柜换锁',
  kcCodes: ['PY43-KC-031'],
  prompt:
    '【锁匠】储物间一排 9 个储物柜（第 1~9 号）。只有"单数号"（第 1、3、5、7、9 号）对外出租，' +
    '要全部换上新锁；双数号是员工专用，一个都不能碰！动作 relock(n) 会给第 n 号储物柜换锁。' +
    '用 range 的"步长"一次跨 2 个，专挑单数号——别漏了最后的第 9 号。',
  starterCode: [
    '# relock(n)：给第 n 号储物柜换上新锁。',
    'def relock(n):',
    '    print(f"给第 {n} 号储物柜换锁")',
    '',
    '# 单数号：第 1、3、5、7、9 号。range 第三个参数是"步长"，设成 2 就能只挑单数号。',
    '# 但下面的 stop 边界好像差了一点——想清楚要换到第 9 号，stop 该写到几？',
    'for n in range(1, 9, 2):',
    '    relock(n)',
    '',
  ].join('\n'),
  worldGoal: {
    kind: 'coverage',
    targets: ODD_LOCKER_TARGETS,
    actionName: 'relock',
    actionVerb: '换锁',
    targetNoun: '储物柜',
    unit: '个',
    forbidExtra: true,
  },
};
