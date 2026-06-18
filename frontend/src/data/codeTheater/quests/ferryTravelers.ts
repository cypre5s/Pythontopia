/**
 * 样板具身任务「把旅人逐一渡过河」——教 while 循环 + 计数器。
 *
 * 教育锚点：packId=43 KC『PY43-KC-032 while循环』。
 * 玩法：NPC（摆渡人）给出真实需求——渡口排着 N 位旅人，必须用 while 计数把他们
 *   一个不落地渡到对岸。学生维护计数器 `i`，调用世界动作 `ferry(i)`。
 * 世界目标 = 可见条件「每位旅人都被渡过」，循环条件写错（如 `while i < N`）会直接
 *   表现为「第 N 位旅人还没渡」——典型的 while 边界 off-by-one。
 *
 * 世界动作 `ferry` 在 starterCode 里 `def` 出来（见 types.ts CoverageWorldGoal.actionName）。
 */

import type { CodeTheaterQuest } from './types';

/** 旅人位数（第 1~5 位）。 */
const TRAVELER_COUNT = 5;

/** 第 1..N 位旅人的编号（1 基）。 */
const TRAVELER_TARGETS: readonly number[] = Array.from(
  { length: TRAVELER_COUNT },
  (_, index) => index + 1,
);

export const FERRY_TRAVELERS_QUEST: CodeTheaterQuest = {
  id: 'ferry-travelers',
  title: '把旅人逐一渡过河',
  kcCodes: ['PY43-KC-032'],
  prompt:
    '【摆渡人】渡口排着 5 位旅人（第 1~5 位），等着过河。' +
    '动作 ferry(n) 会把第 n 位旅人渡到对岸。这条河得用 while 一个一个数着渡——' +
    '帮我把 5 位旅人一个不落地全渡过去（当心循环条件别写漏了最后一位）。',
  starterCode: [
    '# ferry(n)：把第 n 位旅人渡到对岸。',
    'def ferry(n):',
    '    print(f"把第 {n} 位旅人渡过河")',
    '',
    '# 渡口有 5 位旅人：第 1~5 位。用 while + 计数器把他们都渡过去。',
    '# 想清楚：要渡到第 5 位，循环条件该写到几？',
    'i = 1',
    'while i <= 4:',
    '    ferry(i)',
    '    i = i + 1',
    '',
  ].join('\n'),
  worldGoal: {
    kind: 'coverage',
    targets: TRAVELER_TARGETS,
    actionName: 'ferry',
    actionVerb: '渡',
    targetNoun: '旅人',
    unit: '位',
  },
};
