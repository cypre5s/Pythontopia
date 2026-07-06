export interface ProfileDimension {
  key: string;
  label: string;
  score: number;
  confidence: number;
  evidenceCount: number;
  selfEditable: boolean;
  tags: string[];
}

export interface SelfDimensionRequest {
  scoreDelta?: number;
  evidenceIncrement?: number;
  tagsAdd?: string[];
  metadata?: Record<string, unknown>;
}

export interface DimensionUpdateResult {
  userId: number;
  dimensionKey: string;
  finalDimension: unknown;
}

export interface MasterySnapshot {
  kcCode: string;
  masteryScore: number;
  confidence: number;
  lastEventAt: string | null;
  latestSource: string | null;
}

/** GET /api/learner/profile 的顶层标量字段（嵌套维度对象此处不展开，按需扩展）。 */
export interface LearnerProfile {
  profileId: number | null;
  userId: number;
  packId: number | null;
  major: string | null;
  narrativeSummary: string | null;
}
