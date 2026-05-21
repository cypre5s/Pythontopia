export type PathStatus = 'ACTIVE' | 'ARCHIVED' | 'DRAFT';

export type PathNodeType =
  | 'READ_RESOURCE'
  | 'SOLVE_PROBLEM'
  | 'FIX_BUG'
  | 'PLANT_KC'
  | 'PROFILE_UPDATE';

export type SuggestedResourceType =
  | 'HANDOUT'
  | 'MINDMAP'
  | 'PRACTICE'
  | 'CODE_LAB'
  | 'READING'
  | 'STORYBOARD';

export interface LearningPathNodeDto {
  id: number;
  pathId: number;
  dayIndex: number;
  orderInDay: number;
  kcCode: string;
  nodeType: PathNodeType;
  reasonSummary: string;
  evidenceSummary: string;
  suggestedResourceType: SuggestedResourceType | null;
  suggestedProblemId: number | null;
  sourceClassroomQuestId: number | null;
}

export interface LearningPathDto {
  id: number;
  userId: number;
  packId: number;
  version: number;
  status: PathStatus;
  goalSummary: string;
  generatedAt: string;
  nextReviewAt: string | null;
  sourceRunId: number | null;
  nodes: LearningPathNodeDto[];
}

export interface PlanPathResponse {
  path: LearningPathDto;
  archivedPathIds: number[];
  generatedQuestCount: number;
  runStatus: string;
  groundingMessage: string;
}
