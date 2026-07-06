export type QuestStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED' | 'EXPIRED';

export type QuestSource = 'PATH_PLANNER' | 'CLASSROOM' | 'SYSTEM_DEFAULT';

export type QuestNodeType =
  | 'READ_RESOURCE'
  | 'SOLVE_PROBLEM'
  | 'FIX_BUG'
  | 'PLANT_KC'
  | 'PROFILE_UPDATE';

export interface Quest {
  id: number;
  userId: number;
  source: QuestSource;
  sourceRefId: number | null;
  title: string;
  description: string | null;
  kcCode: string | null;
  nodeType: QuestNodeType;
  payload: unknown;
  dueDate: string | null;
  priority: number;
  status: QuestStatus;
  requiredEvidenceType: string | null;
  requiredMasteryDelta: number | null;
  targetProblemIds: number[] | null;
  targetResourceId: number | null;
  deepLinkRoute: string | null;
  deepLinkParams: unknown;
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
