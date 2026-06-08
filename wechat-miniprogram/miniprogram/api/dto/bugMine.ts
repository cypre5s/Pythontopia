import { PublicProblem } from './submissions';

export type BugMineSessionStatus = 'OPEN' | 'RESOLVED' | 'ABANDONED';

export type BugMineCardType =
  | 'COUNTER_EXAMPLE'
  | 'VARIABLE_TRACE'
  | 'WRONG_ASSUMPTION'
  | 'HINT_LAYER_1'
  | 'HINT_LAYER_2'
  | 'HINT_LAYER_3';

export interface BugMineVariantProblem {
  id: number;
  title: string | null;
}

/** 五层下探深度步骤（L1 裸重试 → L5 同 KC 变式）。 */
export interface BugMineDepthStep {
  layer: number;
  key: string;
  title: string;
  description: string;
  unlocked: boolean;
}

/**
 * 分层卡片。`payload` 后端为任意 JSON（按 cardType 形态不同），客户端读取处窄化为下列形态之一。
 */
export interface BugMineCard {
  id: number;
  sessionId: number;
  cardType: BugMineCardType;
  payload: unknown;
  revealed: boolean;
  createdAt: string;
}

/** COUNTER_EXAMPLE 卡片 payload。 */
export interface CounterExamplePayload {
  stdin?: string;
  expected_stdout?: string;
  your_stdout?: string;
  diff_summary?: string;
  redacted?: boolean;
}

/** VARIABLE_TRACE 卡片 payload（逐行变量轨迹）。 */
export interface VariableTraceStep {
  line: number;
  locals: Record<string, string>;
}

export interface VariableTracePayload {
  steps?: VariableTraceStep[];
  truncated?: boolean;
  sandbox?: string;
  durationMs?: number;
}

/** WRONG_ASSUMPTION 卡片 payload。 */
export interface WrongAssumptionPayload {
  you_thought?: string;
  actually?: string;
  evidence_kc_codes?: string[];
}

/** HINT_LAYER_* 卡片 payload。 */
export interface HintLayerPayload {
  layer?: number;
  headline?: string;
  body?: string;
  related_pages?: number[];
}

export interface BugMineSession {
  id: number;
  userId: number;
  submissionId: number;
  problemId: number;
  questId: number | null;
  status: BugMineSessionStatus;
  retryCount: number;
  depthLayer: number;
  profileDepthTarget: number;
  masteryConfirmed: boolean;
  variantSubmissionId: number | null;
  openedAt: string;
  closedAt: string | null;
  variantProblem: BugMineVariantProblem | null;
  profileGuidance: string | null;
  depthSteps: BugMineDepthStep[];
  cards: BugMineCard[];
  /** 会话调试的完整题干（含样例），随会话一次性下发。 */
  problem: PublicProblem | null;
}

export interface BugMineErrorBookItem {
  sessionId: number;
  submissionId: number;
  problemId: number;
  questId: number | null;
  kcCode: string;
  problemTitle: string;
  status: BugMineSessionStatus;
  retryCount: number;
  depthLayer: number;
  profileDepthTarget: number;
  profileGuidance: string | null;
  masteryScore: number | null;
  minePriority: number | null;
  decayRisk: number | null;
  recommendationReason: string;
  mistakePatterns: string[];
  patternMatched: boolean;
  openedAt: string;
  variantProblem: BugMineVariantProblem | null;
  depthSteps: BugMineDepthStep[];
}

export interface BugMineEchoCandidate {
  kcCode: string;
  problemId: number;
  problemTitle: string;
  masteryScore: number | null;
  confidence: number | null;
  decayRisk: number | null;
  minePriority: number | null;
  recommendationReason: string;
}

export interface BugMineEchoOpenRequest {
  kcCode: string;
}

export interface BugMineOpenRequest {
  submissionId: number;
}

export interface BugMineCodeRequest {
  code: string;
}
