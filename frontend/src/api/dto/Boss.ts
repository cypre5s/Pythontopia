// R-13 §5 Phase 4：Boss 副本前后端契约。
// 后端 BossController 还没接入时，前端使用 src/api/boss.ts 的 mock fallback；
// 真后端接入后这些 DTO 形状不能变。

export interface BossProblemDto {
  /** 全局唯一题号（AI 生成题用 "ai-{ts}-{rand}"，golden test 用 "golden-{kc}-{i}"）。 */
  problemId: string;
  /** 当前所属章节 1..7。 */
  chapter: number;
  /** 当前阶段 1..4。 */
  stage: number;
  /** 推荐难度 1..5。 */
  difficulty: number;
  /** 题目关联的 KC code（用于 mastery 回写）。 */
  kcCode: string;
  /** 题面 Markdown。 */
  statement: string;
  /** 不超过 3 条提示，按 layer 索引由前端按需展开。 */
  hints: string[];
  /** 输入样例（公开）。 */
  sampleInput: string;
  /** 期望输出样例（公开）。 */
  sampleOutput: string;
  /** 测试用例总数（不含答案）。 */
  totalTestCases: number;
  /** 是否经过 5 重审查链（AI 出题）。 */
  reviewed: boolean;
  /** 是否来自 AI 出题（false = golden test 兜底）。 */
  aiGenerated: boolean;
}

export interface BossAttemptRequest {
  problemId: string;
  chapter: number;
  stage: number;
  code: string;
  /** 玩家做题时长（秒），用于自适应难度。 */
  elapsedSec: number;
}

export interface BossAttemptResultDto {
  problemId: string;
  passed: boolean;
  /** 通过的 test case 数（不含答案）。 */
  passedTestCases: number;
  totalTestCases: number;
  /** 失败时摘出第一个失败的 expected / actual（公开 sample 数据，不暴露隐藏测）。 */
  failureFirstHint?: string;
  /** 评分：1..100。 */
  score: number;
  /** 通过 / 失败的简短反馈（不要泄露完整答案）。 */
  feedback: string;
}

export interface BossSessionStatus {
  chapter: number;
  /** 当前阶段 1..4。 */
  stage: number;
  /** 已通过的阶段数。 */
  clearedStages: number;
  /** 当前题目。 */
  problem: BossProblemDto | null;
  /** 上一次 attempt 结果。 */
  lastAttempt: BossAttemptResultDto | null;
  /** 是否整场 boss 通关。 */
  victory: boolean;
}
