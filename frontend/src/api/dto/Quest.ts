export type QuestNodeType =
  | 'READ_RESOURCE'
  | 'SOLVE_PROBLEM'
  | 'FIX_BUG'
  | 'PLANT_KC'
  | 'PROFILE_UPDATE';

export type QuestSource = 'PATH_PLANNER' | 'CLASSROOM' | 'SYSTEM_DEFAULT';

export type QuestStatus =
  | 'OPEN'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'SKIPPED'
  | 'EXPIRED';

export type QuestRequiredEvidenceType =
  | 'submission'
  | 'resource'
  | 'page_read'
  | 'kc_mastery';

export interface QuestDto {
  id: number;
  userId: number;
  source: QuestSource;
  sourceRefId: number | null;
  title: string;
  description: string | null;
  kcCode: string | null;
  nodeType: QuestNodeType;
  payload: Record<string, unknown>;
  dueDate: string;
  priority: number;
  status: QuestStatus;
  requiredEvidenceType: QuestRequiredEvidenceType | null;
  requiredMasteryDelta: number | null;
  targetProblemIds: number[];
  targetResourceId: number | null;
  deepLinkRoute: string | null;
  deepLinkParams: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface QuestCompleteRequest {
  note?: string;
  submissionId?: number;
  resourceId?: number;
  pageReadId?: number;
  kcMasteryDelta?: number;
}

export interface QuestSkipRequest {
  reason?: string;
}
