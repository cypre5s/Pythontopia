/**
 * 样板具身任务「点亮整条街的路灯」——教 for 循环 + range()。
 *
 * 教育锚点：packId=43 KC『PY43-KC-031 for循环与遍历』
 *   （理解 range() 的 start/end/step 与「左闭右开」区间特性）。
 * 玩法：NPC（守夜人）给出真实需求——把整条街的 N 盏路灯逐一点亮。学生用
 *   `for lamp in range(...)` 调用世界动作 `light(lamp)`。世界目标 =
 *   可见条件「每盏路灯都被点亮」，而非隐藏断言；off-by-one 会直接表现为
 *   「第 N 盏路灯还黑着」。
 *
 * 世界动作 `light` 在 starterCode 里 `def` 出来：harness 仅追踪学生文件内定义的
 * 函数调用，这样真实 Pyodide 执行才会产生 `call` 事件供世界条件检查器读取。
 */

import type { CodeTheaterQuest } from './types';

/** 路灯盏数（第 1~4 盏）。小而可演示，且天然暴露 range 左闭右开的 off-by-one。 */
const LAMP_COUNT = 4;

/** 第 1..N 盏路灯的编号（1 基，与叙事「第几盏」一致）。 */
const LAMP_TARGETS: readonly number[] = Array.from(
  { length: LAMP_COUNT },
  (_, index) => index + 1,
);

export const LIGHT_STREET_LAMPS_QUEST: CodeTheaterQuest = {
  id: 'light-street-lamps',
  title: '点亮整条街的路灯',
  kcCodes: ['PY43-KC-031'],
  prompt:
    '【守夜人】镇中心大街排着 4 盏路灯（第 1~4 盏），天黑了还没亮。' +
    '我这有个动作 light(lamp)——你喊一声 light(几)，傀儡就会走到第几盏路灯把它点亮。' +
    '能不能用 for 循环，把这 4 盏路灯一盏不落地全点亮？',
  starterCode: [
    '# light(lamp)：让傀儡走到第 lamp 盏路灯并点亮它。',
    'def light(lamp):',
    '    print(f"点亮第 {lamp} 盏路灯")',
    '',
    '# 大街上有 4 盏路灯：第 1、2、3、4 盏。',
    '# 用 for + range 把每一盏都点亮——改一改下面的 range，别落下任何一盏。',
    'for lamp in range(1, 4):',
    '    light(lamp)',
    '',
  ].join('\n'),
  worldGoal: {
    kind: 'coverage',
    targets: LAMP_TARGETS,
    actionName: 'light',
    actionVerb: '点亮',
    targetNoun: '路灯',
    unit: '盏',
  },
};
