// 共享「代码挑战」契约（Wave 1 基座）。
//
// 抽取自无尽闯关（ArenaPage / EndlessPage）与代码舞台（CodeTheaterPage）两条做题链路的
// 公共形状，归一为一套与端无关的 DTO；Wave 2 由两个功能各自适配。
// 这里只定义数据形状，不绑定任何具体后端 API（判题由 composable 注入）。

import type { ArenaLevelKind } from '@/types/arena';

/** 挑战题型：复用闯关塔题型，外加自由练习 / 训练两类与具体关卡解耦的来源。 */
export type ChallengeKind = ArenaLevelKind | 'free' | 'training';

/** 评测模式：preview = 本地 Pyodide 预览（非权威）；judge = 后端正式判题（权威）。 */
export type ChallengeMode = 'preview' | 'judge';

/** 判定结论（与后端 JudgeResult 对齐的子集）。 */
export type Verdict = 'AC' | 'WA' | 'CE' | 'RE' | 'TLE';

/** 公开输入输出样例（不含隐藏用例）。 */
export interface ChallengeSample {
  input: string;
  output: string;
}

export interface CodeChallenge {
  id: string;
  kind: ChallengeKind;
  /** 锚定 KC code 列表（来自 packId=43 真课程包）。 */
  kcCodes: string[];
  /** 题面 Markdown。 */
  statement: string;
  samples: ChallengeSample[];
  difficulty: 1 | 2 | 3 | 4 | 5;
  /** 初始代码骨架（可选）。 */
  starterCode?: string;
  /** 课件引用页码（用于跳转 LibraryPage / 防幻觉证据）。 */
  evidencePageIds?: number[];
  mode: ChallengeMode;
}

export interface ChallengeResult {
  passed: boolean;
  passedCases: number;
  totalCases: number;
  verdict?: Verdict;
  feedback: string;
  /** 失败时第一个未过用例的提示（不泄露完整答案）。 */
  firstHint?: string;
  /** 实际输出（用于本地预览对照展示）。 */
  actualOutput?: string;
  stderr?: string;
}
