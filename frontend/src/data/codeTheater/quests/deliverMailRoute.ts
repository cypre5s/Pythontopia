/**
 * 样板具身任务「按名单挨家送信」——教列表 + for-each 遍历（遍历容器而非 range）。
 *
 * 教育锚点：packId=43 KC『PY43-KC-040 列表』+『PY43-KC-031 for循环与遍历』。
 * 玩法：NPC（邮差）给出真实需求——今天要给名单上的人家送信。学生用
 *   `for house in mailboxes:` 遍历列表，对每个门牌调用世界动作 `deliver(house)`。
 * 世界目标 = 可见条件「名单上每户都送到」；名单（列表字面量）写漏一户会直接表现为
 *   「第 N 号门牌还没送」——让「列表内容 / 遍历」体现为世界现状。
 *
 * 世界动作 `deliver` 在 starterCode 里 `def` 出来（见 types.ts CoverageWorldGoal.actionName）。
 */

import type { CodeTheaterQuest } from './types';

/** 今天要送达的门牌号（名单）。非连续，刻意区别于 range 任务，凸显「遍历列表」。 */
const MAILBOX_TARGETS: readonly number[] = [2, 5, 7];

export const DELIVER_MAIL_ROUTE_QUEST: CodeTheaterQuest = {
  id: 'deliver-mail-route',
  title: '按名单挨家送信',
  kcCodes: ['PY43-KC-040', 'PY43-KC-031'],
  prompt:
    '【邮差】今天有 3 封信，要送到第 2、5、7 号门牌这三户人家。' +
    '动作 deliver(house) 会把信送到第 house 号。我把门牌名单列成了一个列表，' +
    '可下面的名单好像漏了一户——把名单补全，再用 for 遍历它，把三封信都送到。',
  starterCode: [
    '# deliver(house)：把信送到第 house 号门牌。',
    'def deliver(house):',
    '    print(f"把信送到第 {house} 号")',
    '',
    '# 今天要送的门牌名单（第 2、5、7 号）——好像漏了一户，补全这个列表。',
    'mailboxes = [2, 5]',
    '',
    '# 用 for 遍历名单，挨家送达。',
    'for house in mailboxes:',
    '    deliver(house)',
    '',
  ].join('\n'),
  worldGoal: {
    kind: 'coverage',
    targets: MAILBOX_TARGETS,
    actionName: 'deliver',
    actionVerb: '送达',
    targetNoun: '门牌',
    unit: '号',
  },
};
