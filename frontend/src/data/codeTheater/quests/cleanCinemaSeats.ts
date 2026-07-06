/**
 * 具身任务「打扫放映厅的每个座位」——教嵌套循环（双层 for + range）。
 *
 * 教育锚点：packId=43 KC『PY43-KC-036 循环嵌套』+『PY43-KC-031 for循环与遍历』。
 * 玩法：NPC（影院场务）给出真实需求——放映厅 2 排、每排 3 个座位（共 6 个），开场前
 *   每个座位都得打扫一遍。学生用「外层排、内层座」两层 for，对每个座位调用世界动作
 *   `clean(seat)`，座位号 = (排号-1)*3 + 座号。
 * 世界目标 = 可见条件「每个座位都打扫到」；内层 range 少数一个，会让**每一排都缺最后一个**
 *   座位（第 3、6 号）——这正是嵌套循环最典型的「内层边界写错，外层每轮都漏」误区。
 *
 * 世界动作 `clean` 在 starterCode 里 `def` 出来（见 types.ts CoverageWorldGoal.actionName）。
 */

import type { CodeTheaterQuest } from './types';

/** 放映厅座位号（2 排 × 3 座 = 第 1~6 号）。 */
const SEAT_TARGETS: readonly number[] = [1, 2, 3, 4, 5, 6];

export const CLEAN_CINEMA_SEATS_QUEST: CodeTheaterQuest = {
  id: 'clean-cinema-seats',
  title: '打扫放映厅的每个座位',
  kcCodes: ['PY43-KC-036', 'PY43-KC-031'],
  prompt:
    '【影院场务】放映厅有 2 排座位，每排 3 个，一共 6 个座位（座位号 =（排号-1）×3 + 座号，' +
    '即第 1~6 号）。开场前得把每个座位都打扫一遍。动作 clean(seat) 会让保洁走到第 seat 号座位打扫。' +
    '用两层 for（外层管"排"、内层管"座"）把 6 个座位一个不落地打扫到——当心内层别少数一个。',
  starterCode: [
    '# clean(seat)：让保洁走到第 seat 号座位打扫它。',
    'def clean(seat):',
    '    print(f"打扫第 {seat} 号座位")',
    '',
    '# 2 排、每排 3 个座位，共 6 个。座位号 =（排号-1）*3 + 座号。',
    '# 外层 for 走"排"，内层 for 走"座"——下面内层好像没数到每排的最后一个座位。',
    'for row in range(1, 3):',
    '    for col in range(1, 3):',
    '        seat = (row - 1) * 3 + col',
    '        clean(seat)',
    '',
  ].join('\n'),
  worldGoal: {
    kind: 'coverage',
    targets: SEAT_TARGETS,
    actionName: 'clean',
    actionVerb: '打扫',
    targetNoun: '座位',
    unit: '个',
  },
};
