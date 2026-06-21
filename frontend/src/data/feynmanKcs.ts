// 费曼反演「代码找茬」自测可用知识点集合。
//
// 自测玩法是「学弟写一段含 bug 的代码 → 你限时找出 bug 行」，因此只对**能写出可调试代码**的
// 知识点开放。课程包 ch1 里有若干纯计算机发展史 / 抽象概念 KC（没有可找茬的代码），从自测
// 选择器里隐藏，避免「在『中国计算机发展成就』上做代码找茬」这种文不对题的体验。
//
// 其余编程相关 KC 均可玩：后端 bug 库（services/pythontopia-graph/.../feynman/bug_bank.py）
// 对课程包 1-7 章逐章都有 curated 样本，按所选 KC 的章节抽题，故「可用」≈「可写代码的 KC」。

import { KC_NAMES } from './kcNames';

/** 纯史实 / 抽象概念、无代码可找茬的 KC：从费曼自测选择器隐藏。 */
const NON_CODING_KCS: ReadonlySet<string> = new Set([
  'PY43-KC-001', // 中国计算机发展成就（史实）
  'PY43-KC-002', // 计算工具的演变（史实）
  'PY43-KC-005', // 计算机发展历程与趋势（史实）
  'PY43-KC-007', // 计算科学与计算机科学（概念辨析）
  'PY43-KC-015', // 程序设计语言发展历史（史实）
  'PY43-KC-027', // 汉字编码体系（概念）
]);

/** 该 KC 是否可用于费曼「代码找茬」自测（真实课程 KC 且非纯概念史实）。 */
export function isFeynmanSelfTestKc(code: string): boolean {
  return Object.prototype.hasOwnProperty.call(KC_NAMES, code) && !NON_CODING_KCS.has(code);
}

/** 费曼自测可选的全部 KC 编码（按编号升序），供选择器只展示「可用知识点」。 */
export const FEYNMAN_SELF_TEST_KCS: readonly string[] = Object.keys(KC_NAMES)
  .filter(isFeynmanSelfTestKc)
  .sort((a, b) => a.localeCompare(b));

export interface SuggestedKc {
  code: string;
  label: string;
}

/**
 * 常用入口（点一下即开课）：覆盖各章典型编程主题，均取自可用集合。
 * 「包括但不限于课件」——挑的是学生最常练、最容易踩坑的编程点，而非按课件目录罗列。
 */
export const FEYNMAN_SUGGESTED_KCS: readonly SuggestedKc[] = [
  { code: 'PY43-KC-013', label: '输入与类型转换' },
  { code: 'PY43-KC-031', label: 'for 循环与遍历' },
  { code: 'PY43-KC-035', label: '多条件判断与逻辑' },
  { code: 'PY43-KC-040', label: '列表基本操作' },
  { code: 'PY43-KC-042', label: '字典基本操作' },
  { code: 'PY43-KC-048', label: '文件操作' },
  { code: 'PY43-KC-060', label: '函数变量作用域' },
].filter((kc) => isFeynmanSelfTestKc(kc.code));
