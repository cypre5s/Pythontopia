import type { LearningPathDto } from './Path';

export type EvaluationActedStatus = 'PENDING' | 'ACCEPTED' | 'KEPT_OLD' | 'PARTIAL';

export interface EvaluationMetricDto {
  metricKey: string;
  numerator: number;
  denominator: number;
  value: number;
  displayUnit: string;
  notes: string | null;
}

export interface EvaluationDto {
  id: number;
  userId: number;
  packId: number;
  periodStart: string;
  periodEnd: string;
  narrativeSummary: string;
  pathDiffSummary: string;
  draftPathId: number | null;
  activePathId: number | null;
  createdAt: string;
  actedStatus: EvaluationActedStatus;
  metrics: EvaluationMetricDto[];
  draftPath: LearningPathDto | null;
}

export interface EvaluationRequest {
  periodDays?: number;
}
