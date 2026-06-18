/**
 * 具身任务「从最远的烽火台倒着点回主城」——sequence 顺序覆盖型示例（教 range 逆序步长）。
 *
 * 教育锚点：packId=43 KC『PY43-KC-031 for循环与遍历』
 *   （重点在 range() 的负步长 / 逆序遍历：range(5, 0, -1)）。
 * 玩法：NPC（烽火台守将）给出真实需求——边境一线 5 座烽火台，第 1 座最近(挨着主城)、第 5 座最远。
 *   敌情来了必须从最远的第 5 座开始，一座接一座往回点，信号才能一站站传回主城；顺序反了就接不上。
 * 世界目标 = `sequence`（顺序覆盖）：必须按 [5,4,3,2,1] 的顺序调用世界动作 `signal(tower)`，
 *   错序即未达成。起手代码用 `range(1, 6)`（从近到远）顺序正好反了——让「遍历方向写反」
 *   体现为「信号没按链路接上」，而非一个看不见的顺序断言。
 *
 * 世界动作 `signal` 在 starterCode 里 `def` 出来（见 types.ts SequenceWorldGoal.actionName）。
 */

import type { CodeTheaterQuest } from './types';

/** 规定点燃顺序：从最远的第 5 座倒着点回第 1 座（数组顺序即先后，刻意非升序）。 */
const BEACON_SEQUENCE: readonly number[] = [5, 4, 3, 2, 1];

export const LIGHT_BEACON_CHAIN_QUEST: CodeTheaterQuest = {
  id: 'light-beacon-chain',
  title: '从最远的烽火台倒着点回主城',
  kcCodes: ['PY43-KC-031'],
  prompt:
    '【烽火台守将】边境一线 5 座烽火台：第 1 座最近（挨着主城），第 5 座最远。' +
    '敌情来了，得从最远的第 5 座开始，一座接一座往回点（5→4→3→2→1），信号才能一站站传回主城，' +
    '顺序反了就接不上！动作 signal(tower) 会点燃第 tower 座烽火台。' +
    '现在的代码是从近到远点（顺序反了）——用 range 的负步长，改成从第 5 座倒着点回第 1 座。',
  starterCode: [
    '# signal(tower)：点燃第 tower 座烽火台，把信号往回传一站。',
    'def signal(tower):',
    '    print(f"点燃第 {tower} 座烽火台")',
    '',
    '# 必须从最远的第 5 座往回点：5 → 4 → 3 → 2 → 1。',
    '# 下面是从第 1 座点到第 5 座，方向正好反了——把 range 改成倒着数（提示：负步长）。',
    'for tower in range(1, 6):',
    '    signal(tower)',
    '',
  ].join('\n'),
  worldGoal: {
    kind: 'sequence',
    targets: BEACON_SEQUENCE,
    actionName: 'signal',
    actionVerb: '点燃',
    targetNoun: '烽火台',
    unit: '座',
  },
};
