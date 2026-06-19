// 题型枚举（KindRouter / 无尽出题 / 无尽远征共用）。
//
// 注：静态闯关塔（ArenaLevel / ArenaLevelProgress / arenaLevels 70 关 / useArenaStore）已随
// 「无尽远征」(/expedition) 上线退役；此处仅保留仍在用的题型枚举 ArenaLevelKind。

/** 题型：决定走哪条评测链 / KindRouter 渲染哪张卡。 */
export type ArenaLevelKind =
  | 'pdf_question'   // 看 PDF + 单选 / 简答
  | 'fill_blank'     // 代码填空
  | 'debug'          // 改错题
  | 'short_answer'   // 简答 + grounding 评分
  | 'closed_loop'    // 调试闭环（堆栈定位 → 修）
  | 'dual_track'     // 双轨：Pyodide 预览 + 后端判题
  | 'multi_round'    // 多轮组合（3 题 input/print 等）
  | 'teach_back'     // 教学反转（向 NPC 解释概念）
  | 'bug_mine'       // 随机 ImportError 30 秒挑战
  | 'boss';          // 章末 boss 4 阶段副本
