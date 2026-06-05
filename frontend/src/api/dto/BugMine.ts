export type BugMineSessionStatus = 'OPEN' | 'RESOLVED' | 'ABANDONED';

export type BugMineCardType =
  | 'COUNTER_EXAMPLE'
  | 'VARIABLE_TRACE'
  | 'WRONG_ASSUMPTION'
  | 'HINT_LAYER_1'
  | 'HINT_LAYER_2'
  | 'HINT_LAYER_3';

export interface BugMineCardDto {
  id: number;
  sessionId: number;
  cardType: BugMineCardType;
  payload: Record<string, unknown>;
  revealed: boolean;
  createdAt: string;
}

export interface BugMineVariantProblemDto {
  id: number;
  title: string;
}

export interface BugMineSessionDto {
  id: number;
  userId: number;
  submissionId: number;
  problemId: number;
  questId: number | null;
  status: BugMineSessionStatus;
  retryCount: number;
  depthLayer: number;
  openedAt: string;
  closedAt: string | null;
  variantProblem: BugMineVariantProblemDto | null;
  cards: BugMineCardDto[];
}

export interface BugMineDepthStepDto {
  layer: number;
  key: string;
  title: string;
  description: string;
  unlocked: boolean;
}

export interface BugMineErrorBookItemDto {
  sessionId: number;
  submissionId: number;
  problemId: number;
  questId: number | null;
  kcCode: string;
  problemTitle: string;
  status: BugMineSessionStatus;
  retryCount: number;
  depthLayer: number;
  masteryScore: number;
  mistakePatterns: string[];
  patternMatched: boolean;
  openedAt: string;
  variantProblem: BugMineVariantProblemDto | null;
  depthSteps: BugMineDepthStepDto[];
}

export interface BugMineOpenRequest {
  submissionId: number;
}

export interface BugMineRetryRequest {
  code: string;
}
