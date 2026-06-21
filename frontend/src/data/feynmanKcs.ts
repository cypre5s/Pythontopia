// 费曼反演「代码找茬」自测可用知识点集合。
//
// 玩法：学弟写一段含 bug 的代码 → 你限时找出 bug 行 / 讲清错因。
//
// 覆盖范围（2026-06：放开「所有 KC + 课件外概念」）：
// - 全部 65 个课程 KC 均可选（不再隐藏史实/概念 KC）。后端按 KC 中文名作 topic 让 LLM 生成贴该
//   KC 的 bug；偏史实/概念的 KC 走「概念模式」（无代表题，仅找茬+对话，不计掌握度，标「通识」）。
// - 课件外概念（如装饰器/正则/生成器）由 FeynmanPage 的自由输入直接开局（概念模式）。
// 后端 bug 库（services/pythontopia-graph/.../feynman/bug_bank.py）对 1-7 章有 curated 样本，
// 无样本时按 KC 主题 LLM 现生，bug 行一律 buggy/fixed diff 得出（零伪造）。

import { KC_NAMES } from './kcNames';

/** 该 code 是否为真实课程 KC（用于区分「课程 KC」与「课件外自由概念」）。 */
export function isFeynmanSelfTestKc(code: string): boolean {
  return Object.prototype.hasOwnProperty.call(KC_NAMES, code);
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
