// Boss 副本前后端契约：这些 DTO 形状同时约束 src/api/boss.ts 的 mock fallback 与真实后端，不可随意变更。

/**
 * 出题（boss / endless / arena）时前端附带的学生画像信号，让 PracticeAgent + ProfileAgent 依画像
 * 偏好出题而非随机抽 KC + 随机难度；后端未接画像（golden fallback）时这些字段被忽略。
 *
 * 各字段对齐 `LearnerProfileDto` 的抽取来源：
 *   Authenticated requests are rehydrated from server-side profile and mastery data.
 *   The fields remain for wire compatibility; user-declared values are preferences, not ability evidence.
 */
export interface ProfileSignalsDto {
  /** @deprecated authenticated KC selection comes from server-side mastery and review state. */
  weakKcCodes?: string[];
  /** 学生自评 Python 水平 0..5。 */
  pythonSelfRatedLevel?: number;
  /** @deprecated practice outcomes are calculated server-side. */
  successRate?: number;
  /** 偏好媒介：CODE / DIAGRAM / VIDEO / TEXT 等。 */
  mediaPriority?: string[];
  /** @deprecated emotional state is not inferred from practice. */
  resilience?: number;
  major?: string;
  learningGoalKind?: string;
  /** pack 43 中最多 6 个目标知识点。 */
  targetKcCodes?: string[];
  /** @deprecated fixed learning-style scores are not used for recommendations. */
  fieldDependent?: number;
  /** @deprecated fixed learning-style scores are not used for recommendations. */
  sequentialVsHolistic?: number;
  sessionMinutesPerDay?: number;
  /** Arena 关卡题型（若来自 ArenaPage） */
  questPreferredKind?: string;
}

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
  /** 可编辑初始代码（代码题 / 改错题 / Bug 矿洞用）。 */
  starterCode?: string;
  /** 含 bug 的原始代码（改错题 / Bug 矿洞用；不能把题干当代码）。 */
  buggyCode?: string;
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
  /** 课件引用页码（PdfQuestionCard 用于跳转 LibraryPage）。 */
  evidencePageIds?: number[];
}

/**
 * boss / endless / arena 出题请求；后端未接入这些扩展字段时忽略，接入后由 PracticeAgent + ProfileAgent 解析。
 */
export interface BossProblemRequest {
  /** 当前阶段 1..4（boss 副本）或随机（endless）。 */
  stage: number;
  /** 偏好锚定 KC（来自 ArenaPage 关卡 / Workshop chip）。 */
  preferredKc?: string;
  /** 题型偏好（fill_blank / debug / multi_round 等）。 */
  preferredKind?: string;
  /** 闯关塔单关 id（形如 "1-3"）：作为预生成 / 复用题的维度之一，后端按维度匹配 READY 行。 */
  arenaLevel?: string;
  /** 期望难度 1..5：用于「预取按当前难度」；普通出题留空由后端按学情推断。 */
  difficulty?: number;
  profileSignals?: ProfileSignalsDto;
  /** 当前学生的活跃远征 run；由后端校验并绑定判题奖励。 */
  expeditionRunId?: string;
  /** 将远征题目绑定到服务端签发地图中的一个具体节点。 */
  expeditionNodeId?: string;
  /** 超时重试保持同一 ID，避免重复签发或重复生成。 */
  requestId?: string;
}

export type BossPrefetchStatus = 'IDLE' | 'PENDING' | 'READY' | 'FAILED';

/** Server-persisted preload state. READY is emitted only after a reviewed problem is stored. */
export interface BossPrefetchStatusDto {
  jobId: number | null;
  status: BossPrefetchStatus;
  attempts: number;
  errorMessage: string | null;
  retryable: boolean;
  updatedAt: string | null;
}

export interface BossAttemptRequest {
  problemId: string;
  chapter: number;
  stage: number;
  code: string;
  /** 重试同一次网络提交时保持不变；后端据此返回原判题结果。 */
  attemptId?: string;
  /** 玩家做题时长（秒），用于自适应难度。 */
  elapsedSec: number;
  /** 上传画像信号，触发后端 PassiveProfileService 被动更新弱项 KC。 */
  profileSignals?: ProfileSignalsDto;
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
  problem: BossProblemDto | null;
  lastAttempt: BossAttemptResultDto | null;
  /** 是否整场 boss 通关。 */
  victory: boolean;
}
