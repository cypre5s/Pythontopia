import type { QuestNodeType, QuestRequiredEvidenceType, QuestStatus } from './Quest';

export interface ClassroomQuestDto {
  id: number;
  classroomId: number;
  title: string;
  description: string | null;
  kcCodes: string[];
  nodeType: QuestNodeType;
  payload: Record<string, unknown>;
  dueDate: string;
  createdBy: number;
  createdAt: string;
  status: string;
  targetProblemId: number | null;
  targetResourceType: string | null;
  studentQuestId: number | null;
  studentQuestStatus: QuestStatus | null;
  requiredEvidenceType: QuestRequiredEvidenceType | null;
  targetProblemIds: number[];
  deepLinkRoute: string | null;
  deepLinkParams: Record<string, unknown>;
}

export interface ClassroomQuestCreateRequest {
  title: string;
  description?: string;
  kcCodes: string[];
  targetProblemId?: number;
  targetResourceType?: string;
  dueDate: string;
}

export interface ClassroomQuestPublishResponse {
  classroomQuest: ClassroomQuestDto;
  totalStudents: number;
  createdQuests: number;
  skippedDuplicates: number;
}
