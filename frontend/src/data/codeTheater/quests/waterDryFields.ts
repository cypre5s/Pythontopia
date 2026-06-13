/**
 * 样板具身任务「浇灌所有旱地」——教 for 循环 + range()。
 *
 * 教育锚点：packId=43 KC『PY43-KC-031 for循环与遍历』
 *   （summary：理解 range() 的 start/end/step 与「左闭右开」区间特性）。
 * 玩法：NPC（水井精灵）给出真实需求——把 N 块旱地逐一浇到。学生用
 *   `for plot in range(...)` 调用世界动作 `water(plot)`。世界目标 =
 *   可见条件「每块旱地都被浇到」，而非隐藏断言；off-by-one 会直接表现为
 *   「第 N 块旱地还干着」。
 */

import type { CodeTheaterQuest } from './types';

/** 旱地块数（第 1~4 块）。小而可演示，且天然暴露 range 左闭右开的 off-by-one。 */
const DRY_FIELD_COUNT = 4;

/** 第 1..N 块旱地的编号（1 基，与 NPC 叙事「第几块」一致）。 */
const DRY_FIELD_TARGETS: readonly number[] = Array.from(
  { length: DRY_FIELD_COUNT },
  (_, index) => index + 1,
);

export const WATER_DRY_FIELDS_QUEST: CodeTheaterQuest = {
  id: 'water-dry-fields',
  title: '浇灌所有旱地',
  kcCodes: ['PY43-KC-031'],
  prompt:
    '【水井精灵】村东头排着 4 块旱地（第 1~4 块），连日大旱都裂开了。' +
    '我这有个动作 water(plot)——你喊一声 water(几)，傀儡就会走到第几块旱地浇一桶水。' +
    '能不能用 for 循环，把这 4 块旱地一块不落地全浇一遍？',
  starterCode: [
    '# 村东头有 4 块旱地：第 1、2、3、4 块。',
    '# water(plot)：让傀儡走到第 plot 块旱地浇一桶水。',
    '# 用 for + range 把每一块旱地都浇到——改一改下面的 range，别落下任何一块。',
    'for plot in range(1, 4):',
    '    water(plot)',
    '',
  ].join('\n'),
  worldGoal: {
    kind: 'coverage',
    targets: DRY_FIELD_TARGETS,
    actionName: 'water',
    targetNoun: '旱地',
  },
};
